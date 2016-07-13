package cz.cizek.tresomer;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MainActivity extends Activity implements SensorEventListener {

	
	private ServiceConnection serviceConncetion = new ServiceConnection(){
    	public void onServiceConnected(ComponentName name, IBinder service){
    		serviceEngine = ((ServiceEngine.MyBinder)service).getService();
    		isRecording = serviceEngine.isRecording;
            txtRefreshInfo.setText("Data se každých " + serviceEngine.AGREGATION_STEP + " ms ukládají do souboru tresomer.csv");
    		if(isRecording == true){
                serviceEngine.stopNotification();
            }
    		if((isRecording == false)&&(txtTimerList.length() < 10)){
    			txtTimerList.setText("");
    		}
            if(serviceEngine.lastGPS != null)
            {
                setGPSInfobox(serviceEngine.lastGPS);
            }
    		Log.v(tag,"Služba je připojená");
    	}
    	public void onServiceDisconnected(ComponentName name){
    		serviceEngine = null;
    		Log.v(tag,"Služba je odpojená");
    	}
    };
	
    
    private ServiceEngine serviceEngine;

	private TextView txtTimerList;
	private TextView txtCounter;
    private TextView txtLiveX;
    private TextView txtLiveY;
    private TextView txtLiveZ;
    private TextView txtGPS;
    private TextView txtRefreshInfo;
	private String tag = "Třesoměr aktivita";
	private Time today = new Time(Time.getCurrentTimezone());
	private boolean isRecording = false;
    private Menu mainMenu;
    private SensorManager sm;
    private Sensor sensor_accelerometer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor_accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
       
        setContentView(R.layout.activity_main);
        txtTimerList = (TextView)findViewById(R.id.txtTimerList);
        txtCounter = (TextView)findViewById(R.id.txtCounter);
        txtLiveX = (TextView)findViewById(R.id.txtLiveX);
        txtLiveY = (TextView)findViewById(R.id.txtLiveY);
        txtLiveZ = (TextView)findViewById(R.id.txtLiveZ);
        txtGPS = (TextView)findViewById(R.id.txtGPS);
        txtRefreshInfo = (TextView)findViewById(R.id.txtRefreshInfo);
    }
    

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        mainMenu = menu;
        return true;
    }
    
    private void startScanning(){
    	//txtTimerList.setText("");
    	serviceEngine.startScanning();
        isRecording = true;
    }
    
    private void stopScanning(){
    	serviceEngine.stopScanning();
        isRecording = false;
    }
    
    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_start:
                txtTimerList.setText("");
                startScanning();
                item.setEnabled(false);
                mainMenu.findItem(R.id.menu_stop).setEnabled(true);
                return true;
            case R.id.menu_stop:
                stopScanning();
                item.setEnabled(false);
                mainMenu.findItem(R.id.menu_start).setEnabled(true);
                serviceEngine.write("\n");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
	
	@Override
	protected void onResume() {
		super.onResume();
		try{
    		startService(new Intent(this,ServiceEngine.class));
    		bindService(new Intent(this,ServiceEngine.class), serviceConncetion, Context.BIND_AUTO_CREATE);
    		Log.v(tag,"Služba ServiceEngine spuštěná včetně bindingu");
    	}
		catch(Exception e){
    		Log.e(tag,"Chyba při spouštění služby: "+e.getMessage());
    	}
		try{
			registerReceiver(velocityReceiver, new IntentFilter(ServiceEngine.TRESOMER_AVERAGE_READY_ACTION));
		}
		catch(Exception e){
			Log.e(tag,"Nejde zaregistrovat velocityReceiver: "+e.getMessage());
		}

        try{
            sm.registerListener(this, sensor_accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        catch(Exception e){
            Log.e(tag,"Nejde zaregistrovat velocityLiveReceiver: "+e.getMessage());
        }

        try{
            registerReceiver(gpsReceiver, new IntentFilter(ServiceEngine.TRESOMER_GPS_READY_ACTION));
        }
        catch(Exception e){
            Log.e(tag,"Nejde zaregistrovat gpsReceiver: "+e.getMessage());
        }
		
	}
	
	private void updateLabels(float[] gyro, int counter) {
		txtCounter.setText("Počet měření: " + Integer.toString(counter));
		today.setToNow();
        txtTimerList.setText(today.format("%k:%M:%S") + ": " + Float.toString(gyro[0])+", "+Float.toString(gyro[1])+", "+Float.toString(gyro[2])+ "\n" +txtTimerList.getText());
	}

    private BroadcastReceiver gpsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setGPSInfobox(serviceEngine.lastGPS);
        }
    };

    private void setGPSInfobox(Location l){
        txtGPS.setText("Lat: " + round(l.getLatitude(),7) + "°\n" +
                       "Lon: " + round(l.getLongitude(),7) + "°\n" +
                       "Výška: " + Math.round(l.getAltitude()) + " m\n" +
                       "Odchylka: " + Math.round(l.getAccuracy()) + " m");
    }
	
	private BroadcastReceiver velocityReceiver = new BroadcastReceiver(){
    	@Override
    	public void onReceive(Context context, Intent intent){
    		float gyro_x = serviceEngine.velocity_average_x;
            float gyro_y = serviceEngine.velocity_average_y;
            float gyro_z = serviceEngine.velocity_average_z;
    		int counter = serviceEngine.velocity_average_counter;
            float[] gyro = {gyro_x, gyro_y, gyro_z};
    		updateLabels(gyro, counter);
            mainMenu.findItem(R.id.menu_stop).setEnabled(true);
            mainMenu.findItem(R.id.menu_start).setEnabled(false);
    	}
    };

    public void onSensorChanged(SensorEvent event) {

        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            txtLiveX.setText("X: " + String.valueOf(Math.abs(Math.round(event.values[0]))) + " m/s-2");
            txtLiveY.setText("Y: " + String.valueOf(Math.abs(Math.round(event.values[1]))) + " m/s-2");
            txtLiveZ.setText("Z: " + String.valueOf(Math.abs(Math.round(event.values[2]))) + " m/s-2");
        }
    }
 
	@Override
	protected void onPause() {
		super.onPause();

        try{
            sm.unregisterListener(this);
        }catch(Exception e){}
		
		if(isRecording == true){
			serviceEngine.startNotification();
			unbindService(serviceConncetion);
		}
		else{
			unbindService(serviceConncetion);
			stopService(new Intent(this,ServiceEngine.class));
		}

        try{
            unregisterReceiver(gpsReceiver);
        }catch(Exception e){};
		
		try{
			unregisterReceiver(velocityReceiver);
		}catch(Exception e){}
		finish();
	}

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
	
	public void onConfigurationChanged(Configuration newConfig) { 
		//ignore orientation change 
		super.onConfigurationChanged(newConfig); 
	}
		
}
