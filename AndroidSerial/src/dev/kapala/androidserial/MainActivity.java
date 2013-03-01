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
                try {
                	//Need to reset this value so new instances keep working
                	keep_reading = true;
                	
                	//Find the appropriate device, connect to it
                    findBT();		
                    openBT();
                }
                catch (IOException ex) { 
                	Toast.makeText(getApplicationContext(), "Error trying to connect to bluetooth device: " + ex.toString(), Toast.LENGTH_LONG).show();
                	Log.i("openButton OnClick", ex.toString());
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
                try {
                	//Try to close the bt connection.  
                    closeBT();
                }
                catch (IOException ex) { 
                	Toast.makeText(getApplicationContext(), "Error closing connection: " + ex.toString(), Toast.LENGTH_LONG);
                	Log.i("closeButton onClick", ex.toString());
                }
            }
        });
    }
    
    
    /**
     * Scan through all of the bt devices connected to the phone, locate the proper module ("linvor")
     */
    void findBT()
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
        }
        else {
        	//Change status to error message
        	status_text.setText("Error finding device");
        }
    }
    
    void openBT() throws IOException
    {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);        
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();
        
        //beginListenForData();
        dataTask = new listenForDataTask();
        dataTask.execute();
        
        status_text.setText("Bluetooth Opened");
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
    
    private class listenForDataTask extends AsyncTask<String, String, Object> {
       
        int readBufferPosition = 0;
        byte[] readBuffer = new byte[1024];
        private String data = "";
        
        @Override
		protected Void doInBackground(String... urlparams) {
			
			while (keep_reading) {					
				try  {				
	                int bytesAvailable = mmInputStream.available();        
	                
	                if(bytesAvailable > 0) {
	                	byte[] packetBytes = new byte[bytesAvailable];
	                	mmInputStream.read(packetBytes);
	                	for(int i=0;i<bytesAvailable;i++) {
	                		byte b = packetBytes[i];
	                		if(b == delimiter) {
	                			byte[] encodedBytes = new byte[readBufferPosition];
	                			System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
	                			data = new String(encodedBytes, "US-ASCII");
	                			readBufferPosition = 0;                            

	                            //recvd_msg_box.setText(data);
	                            if (data == null){
	                            	recvd_msg_box.setText("Null message");
	                            }
	                            
	
	                        }
	                        else
	                        {
	                            readBuffer[readBufferPosition++] = b;
	                        }
	                	}
	                	publishProgress(data);
	                	data = "";
	                }
                
	            } 
	            catch (IOException ex) {
	                keep_reading = false;
	                //return ex;
	            }
			}
			return null;
		}
		
		@Override
		protected void onProgressUpdate(String... message) {
			recvd_msg_box.setText(message[0]);
		}
		
		/** 
		 * Called whenever the async task stops for an error (shouldn't be called after it is cancelled)  
		 * 
		 */
		@Override
	    protected void onPostExecute(Object obj_returned) {
	        // Either switch to the next activity or notify the UI thread to switch
			/*if (obj_returned instanceof String) {
					recvd_msg_box.setText((String)obj_returned);
			}
	    	else*/
			if (obj_returned instanceof Exception) {
	    		Toast.makeText(getApplicationContext(), 
						"Error getting data: " + ((Exception)obj_returned).toString(), 
						Toast.LENGTH_LONG).show();
	    		Log.i("MainActivity", "Exception occurred: " + ((Exception)obj_returned).toString());
	    	}
			else {
				if (obj_returned == null) {
					//recvd_msg_box.setText("Null obj");
				}
				else {
					//login failure.  Notify user
		        	Toast.makeText(getApplicationContext(), 
		        					"Unknown Error.  Please try again: " + obj_returned.getClass().toString(), 
		        					Toast.LENGTH_LONG).show();
				}
			}
	    }
		
		@Override
		protected void onCancelled() {
			keep_reading = false;
		}
    }
    
    
    /**
     * Send the data to the arduino
     * Append a newline as the terminating character
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
     * Close all of the streams/sockets, stop the async task that's running
     * @throws IOException
     */
    void closeBT() throws IOException
    {

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

    
    //Unused
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
}