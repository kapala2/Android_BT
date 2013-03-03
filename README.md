Android_BT
==========

Android-Arduino connection via bluetooth module.  

A basic application for communicating between a bluetooth-enabled android device and arduino board (must have a running bluetooth dongle)

The app can pass messages between both devices, and by passing a particular string, can control the color
of an RGB led connected to the arduino board. 
  The format of this string is: "rgb:(integer),(integer),(integer)".  \n is automatically added to the end of every string passed.
  If the string does not match that format, it will be interpreted as a normal message, and the led is not changed.

To use:
  The arduino device should be set up and running, with the BT device powered and paired with the android device
  Click the "open" button to connect the android device to the arduino, and to open the streams between the two.
  A status message is displayed at the top.  If "Bluetooth Opened" is displayed, the connection is ready
  Messages can be typed in the lower text box (the upper box is disabled).  Click "Send Message" to pass the message to the arduino
    If everything was set up correctly, the message will show up in the arduino serial monitor.
  Click "close" to close the connection between devices.
    Communincation will be disabled ("Send Message", "Send Color", "close" buttons) until a new connection is est'd (click "Open").
  Use the sliders to select the individual values for each of red, green, and blue.  Click "Send Colors" to pass the colors to the arduino.

