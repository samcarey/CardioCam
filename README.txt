GEORGIA INSTITUTE OF TECHNOLOGY
SCHOOL OF ELECTRICAL AND COMPUTER ENGINEERING
ECE 6258 - DIGITAL IMAGE PROCESSING
Fall 2015
Term Project

By Sam Carey

This Android application allows the user to amplify a certain temporal frequency band.
Based on MIT video magnification project: http://people.csail.mit.edu/mrub/vidmag/

The CardioCamProcessor.java and fragment_camera2_basic.xml files are written by me.

The Camera2BasicFragment.java file has been significantly augmented from its original state (in the Camera2Basic example app). My additions to this file are noted by the comment “Sam Carey //%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%” with all following lines of code being my additions until another “//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%.”

There may be other minor tweaks in other files as well.

To run:
1) Download and install the latest Java SE Development Kit.

2) Download and install Android Studio with the latest SDK (23).

3) From the “Welcome to Android Studio” screen, select “Open an Existing Android Studio Project.”

4) There may be errors thrown, suggesting additional dependencies/downloads. There should be an option for resolving these automatically.

5) Make sure your Android phone has USB debugging/developer mode turned on. Instructions vary by model.

6) If you are on Windows, install the device-specific driver for your phone model:
http://developer.android.com/tools/extras/oem-usb.html#Drivers

6) Plug in your Android phone.

7) Select the "Run 'Application'" button in the top toolbar.

8) Select your Android phone from the list (if you enabled debugging mode correctly) and hit OK

8) Your phone may prompt you for permissions the first time. Hit OK and then hit run again.

The application should build, upload, install, and run automatically.


CardioCam has several data displays and options:
Top row, left to right:
    Information button
    Number of buffers processed so far
    Current calculated frame rate
    Switch camera button (this restarts everything)
Second row:
    Ratio between color amplification and over all amplification for pass-band (editable)
    Amplification factor for all channels for pass-band (editable)
    Current buffer size and FFT length (not yet editable)
Third row:
    Units Button: if active, frequencies entered will be interpreted as Hz instead of bpm
    Overlay Button: if active, unfiltered frames will be superimposed with the filtered frames
    Bandwidth of pass-band (editable)
    Center Frequency of pass-band (editable)

The display is especially crazy when the app first starts, so it has to settle. I suspect this is the automatic lighting adjustment that is throwing everything off. In general, this app is unstable, so please be patient and try restarting it if it doesn’t work at first.

For magnifying heart rate, you will have to adjust the center frequency to what you suspect your heart rate to be, mount your phone on a tripod, ensure there is a moderate amount of white light on your face, and then sit VERY still in front of it for at least 15 seconds or so. Frankly, it’s hard to tell if it’s working.

Thanks and Gig'em!