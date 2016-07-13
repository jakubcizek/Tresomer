package cz.cizek.tresomer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.format.Time;
import android.util.Log;

public class ServiceEngine extends Service implements SensorEventListener {
	
	public class MyBinder extends Binder{
		ServiceEngine getService(){
			return ServiceEngine.this;
		}
	}
	private final IBinder binder = new MyBinder();
	private float velocity_agregation_x = 0;
    private float velocity_agregation_y = 0;
    private float velocity_agregation_z = 0;
	public float velocity_average_x = 0;
    public float velocity_average_y = 0;
    public float velocity_average_z = 0;
    public int velocity_average_counter = 0;
    public int velocity_counter = 0;
    public Location lastGPS = null;
	private SensorManager sm;
	public boolean isRecording = false;
	private Sensor sensor_accelerometer;
	private PowerManager pm;
	private WakeLock wl;
    private LocationManager lm;
	public static final String TRESOMER_AVERAGE_READY_ACTION = "cz.cizek.broadcaster.data_message";
    public static final String TRESOMER_GPS_READY_ACTION = "cz.cizek.broadcaster.gps_message";
    private static final int AGREGATION_STEP = 100;
	private String tag = "Třesoměr služba";
	private FileOutputStream fos = null;
	private Time today = new Time(Time.getCurrentTimezone());
	private Timer timer;
    private String dbpath = "";
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return binder;
	}
	
	@Override
	public void onCreate(){
		super.onCreate();
		Log.v(tag,"Startuji službu...");
		//Inicializace Gyroskopu
		sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor_accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        //Inicialziace GPS
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5f, gpsReceiver);
        //Wake lock pro jistotu
        pm = (PowerManager)getApplicationContext().getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ServiceUploadWakeLock");
		wl.acquire();

	}
	
	public void onDestroy(){
		super.onDestroy();
		try{
			wl.release();
		}
		catch(Exception e){}

        try{
            lm.removeUpdates(gpsReceiver);
        }
        catch(Exception e){}

		try{
    		stopScanning();		
    	}
    	catch(Exception e){}
		try{
    		closeDatabase();		
    	}
    	catch(Exception e){}
	}
	
	public void startScanning() {
        resetValues();
        //Příprava databáze
        openDatabase();
		sm.registerListener(this, sensor_accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

		Log.v(tag,"Startuji listener");
		startGyroCounter();
		Log.v(tag,"Startuji časovač");
		isRecording = true;
	}

	public void stopScanning() {
		timer.cancel();
		sm.unregisterListener(this);
		Log.v(tag,"Vypínám listener");
		isRecording = false;
        closeDatabase();
	}

    private LocationListener gpsReceiver = new LocationListener() {
        public void onLocationChanged(Location location) {
            lastGPS = location;
            broadcast(TRESOMER_GPS_READY_ACTION);
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {}

        public void onProviderEnabled(String provider) {}

        public void onProviderDisabled(String provider) {}
    };



    public void startNotification(){

        Notification n;

        if (android.os.Build.VERSION.SDK_INT < 16){
            n = new Notification.Builder(getApplicationContext())
                    .setContentTitle("Měřím otřesy").setContentText("A ukládám do CSV")
                    .setWhen(System.currentTimeMillis())
                    .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, new Intent(getApplicationContext(),MainActivity.class), 0))
                    .setSmallIcon(R.drawable.upload).getNotification();
        }else{
            n = new Notification.Builder(getApplicationContext())
                    .setContentTitle("Měřím otřesy")
                    .setContentText("A ukládám do CSV")
                    .setSmallIcon(R.drawable.upload)
                    .setWhen(System.currentTimeMillis())
                    .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, new Intent(getApplicationContext(),MainActivity.class), 0))
                    .build();
        }
		 try{
			 startForeground(1, n); 
		 }catch(Exception e){}
	}
	
	public void stopNotification(){
		try{
			stopForeground(true);
		}
		catch(Exception e){}
	}
	
	private void broadcast(String message) {
		Intent intent = new Intent();
		intent.setAction(message);
		sendBroadcast(intent);
	}
    public void write(String value) {
        try {
            fos.write(value.getBytes());
        } catch (IOException e) {
            Log.e(tag,"Nelze zapisovat do souboru: " + e.getMessage());
        }
    }

   public void resetValues(){
       velocity_counter = 0;
       velocity_average_counter = 0;
       velocity_agregation_y = 0;
       velocity_agregation_x = 0;
       velocity_agregation_z = 0;
       velocity_average_x = 0;
       velocity_average_y = 0;
       velocity_average_z = 0;
   }
	
	private void startGyroCounter() {
		timer = new Timer();
		timer.schedule(new TimerTask(){
			@Override
			public void run() {
				Log.v(tag,"Nová agregovaná hodnota!");
                velocity_average_x = velocity_agregation_x / velocity_counter;
                velocity_average_y = velocity_agregation_y / velocity_counter;
				velocity_average_z = velocity_agregation_z / velocity_counter;
				velocity_average_counter++;
                Double latitude = 0.0;
                Double longitude = 0.0;
                if(lastGPS != null){
                    latitude = lastGPS.getLatitude();
                    longitude = lastGPS.getLongitude();
                }
				today.setToNow();
				String line = today.format("%k:%M:%S") + ";" +
                              Float.toString(velocity_average_x).replace('.',',') + ";" +
                              Float.toString(velocity_average_y).replace('.',',') + ";" +
                              Float.toString(velocity_average_z).replace('.',',') + ";" +
                              Double.toString(latitude).replace('.', ',') + ";" +
                              Double.toString(longitude).replace('.', ',') + ";" +
                              "\n";
				Log.v(tag, "Zapisuji: " + line);
                write(line);
				broadcast(TRESOMER_AVERAGE_READY_ACTION);
				velocity_agregation_x = 0;
                velocity_agregation_y = 0;
                velocity_agregation_z = 0;
                velocity_counter = 0;
			}
		}, AGREGATION_STEP, AGREGATION_STEP);
	}
	
	public void openDatabase() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = sdf.format(new Date());
        dbpath = Environment.getExternalStorageDirectory() + "/tresomer_" + timestamp + ".csv";
        Log.v(tag,"Vytvářím soubor: " + dbpath);
		File file = new File(dbpath);
		if(!file.isFile()){
			try {
				file.createNewFile();
			} catch (IOException e) {
				Log.e(tag,"Nemohu vytvořit databázi. Není aktivní mass storage? Zpráva:"+e.getMessage());
			}
		}
		try{
			fos = new FileOutputStream(dbpath, true);
		} catch(FileNotFoundException e){
			Log.e(tag,"Nelze otevřít databázi: "+e.getMessage());
		}
	}
	
	public void closeDatabase(){
		try {
			fos.close();
		} catch (IOException e) {
			Log.e(tag,"Nelze zavřít databázi: "+e.getMessage());
		}
	}

	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}

	public void onSensorChanged(SensorEvent event) {

		if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            velocity_agregation_x += Math.abs(event.values[0]);
            velocity_agregation_y += Math.abs(event.values[1]);
            velocity_agregation_z += Math.abs(event.values[2]);
            velocity_counter++;
        }
	}
}
