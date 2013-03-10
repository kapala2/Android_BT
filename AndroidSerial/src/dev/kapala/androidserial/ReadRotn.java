package dev.kapala.androidserial;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class ReadRotn extends Activity implements SensorEventListener {
	
	Button exit_screen;
	EditText x_rotn_box, y_rotn_box, z_rotn_box;
	
    //Rotation sensor items
    private SensorManager sensor_mgr;
    private Sensor rotn_sensor;
    final SensorEventListener sel = this;
    
    float[] values;
    float x_rotn, y_rotn, z_rotn;

	@Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.read_rotn);
        
        exit_screen = (Button)findViewById(R.id.exit_button);
        x_rotn_box = (EditText)findViewById(R.id.x_rotn_value);
        y_rotn_box = (EditText)findViewById(R.id.y_rotn_value);
        z_rotn_box = (EditText)findViewById(R.id.z_rotn_value);
        
	    //Init the rotation sensor
	    sensor_mgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
	    rotn_sensor = sensor_mgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
	    
	    exit_screen.setOnClickListener(new View.OnClickListener() {			
			@Override
			public void onClick(View v) {
				//Unregister the listener, quit
				sensor_mgr.unregisterListener(sel);
				finish();				
			}
		});
	    
    }

    
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		//read the values
		values = event.values;
		x_rotn = values[0];
		y_rotn = values[1];
		z_rotn = values[2];
		
		//Asign the values to the appropriate boxes
		x_rotn_box.setText("X: " + x_rotn);
		y_rotn_box.setText("Y: " + y_rotn);
		z_rotn_box.setText("Z: " + z_rotn);
		
		//NOTE: For what I am planning, x is roughly between 10 (screen facing left) and -10 (screen facing right).  Z is positive.  Y is irrelevant
	}
	

	@Override
	protected void onResume() {
		super.onResume();
		// register this class as a listener for the orientation and
		// accelerometer sensors
		sensor_mgr.registerListener(sel,
				sensor_mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
        SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	protected void onPause() {
		// unregister listener
		super.onPause();
		sensor_mgr.unregisterListener(sel);
	}
	


}
