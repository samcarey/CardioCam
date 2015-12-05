GEORGIA INSTITUTE OF TECHNOLOGY
SCHOOL OF ELECTRICAL AND COMPUTER ENGINEERING
ECE 6258 - DIGITAL IMAGE PROCESSING
Fall 2015
Term Project

By Sam Carey

This Android application allows the user to amplify a certain temporal frequency band.
Based on MIT video magnification project: http://people.csail.mit.edu/mrub/vidmag/

To run:
1) Download and install Android Studio
2) Click File > New > Import Project
3) Select Camera2Basic project folder
4) Select OK
5) Make sure your Android phone has USB debugging mode is turned on. Instructions vary by model.
6) Plug in your Android phone
7) Select the "Run 'Application'" button in the top toolbar
8) Select your Android phone from the list (if you enabled debugging mode correctly) and hit OK
The application should build, upload, install, and run.

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

Thanks and Gig'em!