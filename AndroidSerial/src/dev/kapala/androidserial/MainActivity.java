package dev.kapala.androidserial;


import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.EditText;  
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends Activity implements SensorEventListener
{
	//Items on the screen
    TextView status_text;
    EditText recvd_msg_box;
    EditText send_msg_box;
    Button openButton;
    Button sendButton;
    Button closeButton;
    Button send_color_button;
    Button twist_servo_button;
    Button read_photo_button;
    Button read_rotn_button;
    SeekBar r_bar;
    SeekBar g_bar;
    SeekBar b_bar;
    
    //various adapters
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    
    //SMS manager for sending/recv text
    SmsManager sms_mgr = null;
    PendingIntent sentPI;
    final String SENT_MSG = "Message Sent";
    
    //data structures used in reading text
    byte[] readBuffer;
    int readBufferPosition = 0;
    int counter = 0;    
    public boolean keep_reading = true;
    public listenForDataTask dataTask = null;    
    final byte delimiter = 10; //This is the ASCII code for a newline character
    
    //Data structures used in reading the slider color values
    private int r_value = 0;
    private int g_value = 0;
    private int b_value = 0;
    private String color_message = "";
    private final String SERVO_MESSAGE = "servo:";
    private final String PHOTO_MESSAGE = "photo:";
    
    //Dialog box items
    Dialog read_sensors_view;
    TextView photo_box;
    Button cancel_dialog_button;
    boolean sensor_window_open = false;
    
    //Rotation sensor items
    private SensorManager sensor_mgr;
    private Sensor rotn_sensor;
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        //Initialize screen items
        initScreen();   
        
        
        //Init the sms_manager
        sms_mgr = SmsManager.getDefault();
        sentPI = PendingIntent.getBroadcast(this, 0,new Intent(SENT_MSG), 0);
        
        //Init the rotation sensor
        sensor_mgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotn_sensor = sensor_mgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        
        //Disable these buttons until connection is est'd
        sendButton.setEnabled(false);
        closeButton.setEnabled(false);
        send_color_button.setEnabled(false);
        twist_servo_button.setEnabled(false);
        read_photo_button.setEnabled(false);        
        
        //Set focus to the sending message box
        send_msg_box.setText("");
        send_msg_box.requestFocus();
        
        //Check if bluetooth is available.  If not, quit
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
        	status_text.setText("No bluetooth adapter available");
        	return;
        }            

        
        //Set the various listeners
        setListeners();
        
      
    }
    
    @Override
    protected void onPause() {
    	//sensor_mgr.unregisterListener( );
    }
    
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		//Do nothing here
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
	
		if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
			//event.values;
		}
	}
	
	
	/**
	 * initScreen
	 *  - Initializes all of the screen elements
	 */
	private void initScreen() {
		
		//Alternate read sensors view dialog
        read_sensors_view = new Dialog(this);
        read_sensors_view.setContentView(R.layout.read_photo);
        read_sensors_view.setTitle("Sensor values!");
        photo_box = (TextView)read_sensors_view.findViewById(R.id.photo_value);
        cancel_dialog_button = (Button)read_sensors_view.findViewById(R.id.cancel_dialog_button);
        
        //Initialize Buttons
        openButton = (Button)findViewById(R.id.open_button);
        sendButton = (Button)findViewById(R.id.send_msg_button);
        closeButton = (Button)findViewById(R.id.close_button);
        send_color_button = (Button)findViewById(R.id.send_color_button);
        twist_servo_button = (Button)findViewById(R.id.twist_servo_button);
        read_photo_button = (Button)findViewById(R.id.read_photo_button);
        read_rotn_button = (Button)findViewById(R.id.read_rotn_button);
        
        //Init the text boxes used on the screen
        status_text = (TextView)findViewById(R.id.status_text);
        recvd_msg_box = (EditText)findViewById(R.id.recvd_msg_box);
        send_msg_box = (EditText)findViewById(R.id.send_msg_box);    
        //myLabel = (TextView)findViewById(R.id.status_text);
        //myTextbox = (EditText)findViewById(R.id.send_msg_box);

        //Sliders used to select each color
        r_bar = (SeekBar)findViewById(R.id.r_value_bar);
        g_bar = (SeekBar)findViewById(R.id.g_value_bar);
        b_bar = (SeekBar)findViewById(R.id.b_value_bar);
        
	}
	
	/**
	 * setListeners
	 *  - Sets all of the onClickListeners for the buttons.  
	 */
	private void setListeners() {
		
		 /**
         * listener for new dialog cancel button.  
         * Just closes the dialog.
         */
        cancel_dialog_button.setOnClickListener(new View.OnClickListener() {			
			@Override
			public void onClick(View v) {
				// Just close the box
				read_sensors_view.cancel();
				sensor_window_open = false;
			}
		});
        
        //Open Button
        openButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	//Need to reset this value so new AsyncTask instances keep working
            	keep_reading = true;
            	
            	//Find the appropriate device, connect to it
            	//Only (try to) open the connection if the connection was found
                if (findBT()) {                    
                	openBT();
                }
                else {
                	//If the BT device was not found, notify user
                	Toast.makeText(getApplicationContext(),
                					"Unable to find BT device.  Please check that all settings are correct.", 
                					Toast.LENGTH_LONG).show();
                }
            }
        });
        
        //Send Button
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)  {
            	//Call sendData function, pass the text from the send message box            	
                sendData(send_msg_box.getText().toString());
            }
        });
        
        //Close button
        closeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { 
                	//Try to close the bt connection.  
                    closeBT();
            }
        });
        
        //Send Color Button
        send_color_button.setOnClickListener(new View.OnClickListener() {			
			@Override
			public void onClick(View v) {
				//Get all of the color values from the slider
				r_value = r_bar.getProgress();
				g_value = g_bar.getProgress();
				b_value = b_bar.getProgress();
				
				//Construct the message that will be recognized by the arduino
				//Format: "rgb:(red_value),(green_value),(blue_value)"
				//Terminating newline is added by the sendData function
				color_message = ("rgb:" + r_value + "," + g_value + "," + b_value); 
				
				sendData(color_message);
			}
		});
        
        //Twist Servo Button
        twist_servo_button.setOnClickListener(new View.OnClickListener() {        	
        	@Override
        	public void onClick(View v) {
        		sendData(SERVO_MESSAGE);
        	}
        });
        
        //Read PhotoResistor button
        read_photo_button.setOnClickListener(new View.OnClickListener() {			
			@Override
			public void onClick(View v) {
				// Send the "photo:" tag to start the arduino sending photoresistor data
				sendData(PHOTO_MESSAGE);				
				
				read_sensors_view.show();
				sensor_window_open = true;
				
			}
		});
        
        /**
         * read_rotn_button listener
         * Handoff to fn to read rotn value, write to screen
         */
        read_rotn_button.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// Call the function to handle this
				//sensor_mgr.registerListener(listener, sensors, rate)
				read_rotn();
				
			}
		});
	}
    
    
    /**
     * findBT()
     *  - Scan through all of the bt devices connected to the phone, locate the proper module ("linvor")
     *  - Updates the status bar text as to whether the device was found
     */
    boolean findBT()
    {
    	boolean device_found = false;

        //Turn on BT if it is not already
        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }
        
        //Get the set of bonded devices, iterate until the desired device is found
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("linvor")) 
                {
                    mmDevice = device;
                    device_found = true;
                    break;
                }
            }
        }
        
        //Update the screen (enable buttons, set status text) if device found
        if (device_found) {
        	status_text.setText("Bluetooth Device Found");
        	return true;
        }
        else {
        	//Change status to error message
        	status_text.setText("Error finding device");
        	return false;
        }
    }
    
    /**
     * openBT()
     *  - Opens the connection to the bluetooth device
     *  - Once a connection is opened, enable all of the communication buttons
     *  - Start a new background task to continually listen for data
     */
    void openBT() 
    {
    	try {
    		//Open up the input and output streams to the bt device
	        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
	        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);        
	        mmSocket.connect();
	        mmOutputStream = mmSocket.getOutputStream();
	        mmInputStream = mmSocket.getInputStream();
	        
        	//Enable the send and close buttons
        	sendButton.setEnabled(true);
        	closeButton.setEnabled(true);
        	send_color_button.setEnabled(true);
        	twist_servo_button.setEnabled(true);
        	read_photo_button.setEnabled(true);
	        
	        //Start a new background task to read from the bt device
	        dataTask = new listenForDataTask();
	        dataTask.execute();
	        
	        status_text.setText("Bluetooth Opened");
    	}
    	catch (IOException io_ex) {
    		Toast.makeText(getApplicationContext(), "Error opening connection to BT device: " + io_ex.toString(), Toast.LENGTH_LONG).show();
    		Log.i("openBT", "Error opening bt connection: " + io_ex.toString());
    		status_text.setText("Could not open BT Connection");
    	}
    	catch (NullPointerException np_ex) {
    		Toast.makeText(getApplicationContext(), "Null pointer in openBt.  Check uuid is set correctly.", Toast.LENGTH_LONG).show();    				
    		Log.i("openBT", "Null pointer in openBT.  Probably uuid.");
    		status_text.setText("Could not open BT Connection");
    	}
    	catch (IllegalArgumentException ia_ex) {
    		Toast.makeText(getApplicationContext(), "Illegal argument exception in openBt.  Check uuid is set correctly.", Toast.LENGTH_LONG).show();    				
    		Log.i("openBT", "Illegal argument in openBT.  Probably uuid not formatted correctly.");
    		status_text.setText("Could not open BT Connection");
    	}
    	catch (IllegalStateException is_ex) {
    		Toast.makeText(getApplicationContext(), "Illegal state exception in openBt.  listenForDataTask is already running.", Toast.LENGTH_LONG).show();    				
    		Log.i("openBT", "Illegal state exception in openBt.  listenForDataTask is already running.");
    		status_text.setText("Could not open BT Connection");
    	}
    	
    }
    

    
    
    /**
     * listenForDataTask
     * 	- An AsyncTask implementation to handle reading from the bt device
     * 	- Possibly a blocking call, so it is moved to a separate task
     * 	- When the entire message is read, the result is passed to the 'onProgressUpdate' function, which displays the message
     * 	- the doInBackground task will continually loop until an error is encountered, or the "close" button is pressed
     * @author Matthew
     *
     */
    private class listenForDataTask extends AsyncTask<String, String, Object> {
       
        int readBufferPosition = 0;
        byte[] readBuffer = new byte[1024];
        private String data = "";
        byte read_byte;
        byte[] packetBytes;
        
        String msg_header = "";
        String arg_1 = "";
        String arg_2 = "";
        int msg_length = 0;
        final int ASCII_COLON = 58;
        
        
        
        /**
         * doInBackground
         * 	- Responsible for reading from the bt input stream, collecting the entire message
         * 	- Keeps reading until an error is encountered, or the connection is closed.
         */
        @Override
		protected Object doInBackground(String... read_params) {
        	
        	int bytesAvailable = 0;
			
			while (keep_reading) {					
				try  {
					//Get the length of the message that is available
	                bytesAvailable = mmInputStream.available();        
	                
	                if(bytesAvailable > 0) {
	                	
	                	//TODO: find out why all of these data structures are necessary
	                	packetBytes = new byte[bytesAvailable];
                        mmInputStream.read(packetBytes);
	                	for(int i=0;i<bytesAvailable;i++) {
	                		read_byte = packetBytes[i];
	                		if(read_byte == delimiter) {
	                			byte[] encodedBytes = new byte[readBufferPosition];
	                			System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
	                			data = new String(encodedBytes, "US-ASCII");
	                			
	                			//data = new String(readBuffer, "US-ASCII");
	                			readBufferPosition = 0;                            

	                            //if there was an error converting to string, put in status bar
	                            if (data == null){
	                            	recvd_msg_box.setText("Null message");
	                            }
	                        }
	                        else { 
	                        	readBuffer[readBufferPosition++] = read_byte; 
	                        }
	                	}//End for loop     	
              		                	
	                	//once the entire message has been read, display it on the screen
	                	publishProgress(data);
	                	data = "";
	                }//End bytesAvailable if
                
	            }//end try 
	            catch (IOException ex) {
	                keep_reading = false;
	                return ex;
	            }
			}//end while
			return null;
		}
		
        /**
         * onProgressUpdate
         *  - Take the message passed from doInBackground, write it to the text box
         */
		@Override
		protected void onProgressUpdate(String... message) {			

			
			//TODO: a lot of error handling here
			try {
				msg_length = message[0].length();
				
				if (message[0] == null) {
					recvd_msg_box.setText("null message");
				}				
				else {
					
					if (msg_length > 3){					
						//grab the header for this message
						msg_header = message[0].substring(0,3);
						if (msg_header.equals("txt")) {
							//arg_1 = message[0].substring(4, message[0].indexOf(ASCII_COLON));		//recepient
							arg_1 = message[0].substring(4, 14);
							arg_2 = message[0].substring(16, msg_length);		//msg_length, because newline not added
							
							//sms_mgr.sendTextMessage(arg_1, null, arg_2, sentPI, null);	//try to send the message
							recvd_msg_box.setText(arg_2);
						}
						else if (msg_header.equals("pht") && sensor_window_open){		//only try to write to this when its open
							photo_box.setText("photo: " + message[0].substring(4, msg_length));
							
						}	
						else {
							recvd_msg_box.setText("Unrecognized header: " + message[0]);
						}
					}
					else {
						//recvd_msg_box.setText("wrong length: " + msg_length);
						recvd_msg_box.setText("the message: " + message[0] + " " + msg_length);
					}
				}
			}
			catch (IndexOutOfBoundsException iob_ex) {
				//If the message was not at least 3 characters, it cannot be the "sms:" message, just set it
				recvd_msg_box.setText("Try failed");
			}
			catch (Exception np_ex) {
				Toast.makeText(getApplicationContext(), "Error in onProgUpdate: "+np_ex.toString(), Toast.LENGTH_LONG).show();
			}
			


		}
		
		/** 
		 * onPostExecute
		 * 	- Called whenever the async task stops for an error 
		 * 		- From developer page: "This method won't be invoked if the task was cancelled"
		 *  - Indicates the reason that the reading stopped.  
		 *  	- If an exception is returned, it's an error.  
		 *  	- If null, then the function exited normally
		 * 
		 */
		@Override
	    protected void onPostExecute(Object obj_returned) {
			if (obj_returned instanceof Exception) {
	    		Toast.makeText(getApplicationContext(), 
						"Error while getting data: " + ((Exception)obj_returned).toString(), 
						Toast.LENGTH_LONG).show();
	    		Log.i("MainActivity", "Exception occurred: " + ((Exception)obj_returned).toString());
	    	}
	    }
		
		/**
		 * onCancelled
		 * 	- Set the keep_reading flag to false, so the while loop in doInBackground stops
		 */
		@Override
		protected void onCancelled() {
			keep_reading = false;
		}
    }
    
    
    /**
     * sendData()
     * 	- Send the data to the bt device
     *  - Append a newline as the terminating character
     *  @param send_data - A string to be sent to the BT device
     */
    void sendData(String send_data)
    {
    	try {
    		//String msg = send_msg_box.getText().toString();
    		send_data += "\n";
    		mmOutputStream.write(send_data.getBytes());
    		status_text.setText("Data Sent");
    		send_msg_box.setText("");
    	}
    	catch (Exception ex) {
    		Log.i("sendData", "There was an error sending data: " + ex.toString());
    		status_text.setText("Error sending Data");
    	}
    }
    
    /**
     * closeBT()
     * 	- Close all of the streams/sockets
     * 	- stop the async task that's running
     * 	- Disable the send and close buttons until a connection is opened again
     */
    void closeBT(){
        try {
        	//Try to close the bt connection.  
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
            status_text.setText("Bluetooth Closed");
            
            //Cancel the listening task
            dataTask.cancel(true);        
            
            //Disable these buttons until connection is est'd
            sendButton.setEnabled(false);
            closeButton.setEnabled(false);
            send_color_button.setEnabled(false);
            twist_servo_button.setEnabled(false);
            read_photo_button.setEnabled(false);
        }
        catch (IOException ex) { 
        	Toast.makeText(getApplicationContext(), "Error closing connection: " + ex.toString(), Toast.LENGTH_LONG).show();
        	Log.i("closeButton onClick", ex.toString());
        }
    }
    
    /**
     * Reads the value of the rotation from the Rotation Vector Sensor, outputs a string representation of that to the screen.
     * Returns nothing
     */
    void read_rotn() {    	
    	//rotn_sensor.toString();    	
    }

    
    //Unused
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
}

/*  void beginListenForData()
{
    final Handler handler = new Handler(); 
    final byte delimiter = 10; //This is the ASCII code for a newline character
    
    stopWorker = false;
    readBufferPosition = 0;
    readBuffer = new byte[1024];
    workerThread = new Thread(new Runnable()
    {
        public void run()
        {                
           while(!Thread.currentThread().isInterrupted() && !stopWorker)
           {
                try 
                {
                    int bytesAvailable = mmInputStream.available();                        
                    if(bytesAvailable > 0)
                    {
                        byte[] packetBytes = new byte[bytesAvailable];
                        mmInputStream.read(packetBytes);
                        for(int i=0;i<bytesAvailable;i++)
                        {
                            byte b = packetBytes[i];
                            if(b == delimiter)
                            {
                                byte[] encodedBytes = new byte[readBufferPosition];
                                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                final String data = new String(encodedBytes, "US-ASCII");
                                readBufferPosition = 0;
                                
                                handler.post(new Runnable()
                                {
                                    public void run()
                                    {
                                    	recvd_msg_box.setText(data);
                                    }
                                });
                            }
                            else
                            {
                                readBuffer[readBufferPosition++] = b;
                            }
                        }
                    }
                } 
                catch (IOException ex) 
                {
                    stopWorker = true;
                }
           }
        }
    });

    workerThread.start();
}*/
