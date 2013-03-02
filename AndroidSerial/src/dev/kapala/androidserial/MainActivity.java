package dev.kapala.androidserial;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;  
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends Activity
{
	//Items on the screen
    TextView status_text;
    EditText recvd_msg_box;
    EditText send_msg_box;
    Button openButton;
    Button sendButton;
    Button closeButton;
    
    //various adapters
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    
    //data structures used in reading text
    byte[] readBuffer;
    int readBufferPosition = 0;
    int counter = 0;    
    public boolean keep_reading = true;
    public listenForDataTask dataTask = null;    
    final byte delimiter = 10; //This is the ASCII code for a newline character
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        //Initialize screen items
        openButton = (Button)findViewById(R.id.open_button);
        sendButton = (Button)findViewById(R.id.send_msg_button);
        closeButton = (Button)findViewById(R.id.close_button);
        //myLabel = (TextView)findViewById(R.id.status_text);
        status_text = (TextView)findViewById(R.id.status_text);
        recvd_msg_box = (EditText)findViewById(R.id.recvd_msg_box);
        //myTextbox = (EditText)findViewById(R.id.send_msg_box);
        send_msg_box = (EditText)findViewById(R.id.send_msg_box);
        
        //Disable these buttons until connection is est'd
        sendButton.setEnabled(false);
        closeButton.setEnabled(false);
        
        //Set focus to the sending message box
        send_msg_box.setText("rgb:12,45,67");
        send_msg_box.requestFocus();
        
        //Check if bluetooth is available.  If not, quit
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
        	status_text.setText("No bluetooth adapter available");
        	return;
        }
        
        
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
            	//Call sendData function
                sendData();
            }
        });
        
        //Close button
        closeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
 
                	//Try to close the bt connection.  
                    closeBT();                

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
        	
        	//Enable the send and close buttons
        	sendButton.setEnabled(true);
        	closeButton.setEnabled(true);
        	
        	return true;
        }
        else {
        	//Change status to error message
        	status_text.setText("Error finding device");
        	return false;
        }
    }
    
    void openBT() 
    {
    	try {
    		//Open up the input and output streams to the bt device
	        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
	        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);        
	        mmSocket.connect();
	        mmOutputStream = mmSocket.getOutputStream();
	        mmInputStream = mmSocket.getInputStream();
	        
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
			recvd_msg_box.setText(message[0]);
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
     */
    void sendData()
    {
    	try {
    		String msg = send_msg_box.getText().toString();
    		msg += "\n";
    		mmOutputStream.write(msg.getBytes());
    		status_text.setText("Data Sent");
    		send_msg_box.setText("rgb:12,45,67");
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
        }
        catch (IOException ex) { 
        	Toast.makeText(getApplicationContext(), "Error closing connection: " + ex.toString(), Toast.LENGTH_LONG).show();
        	Log.i("closeButton onClick", ex.toString());
        }
    }

    
    //Unused
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
}
