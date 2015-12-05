package com.example.android.camera2basic;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class CardioCamProcessor {

    //Initialize member objects. See object descriptions at bottom of class
    public CardioCamProcessor(int width, int height, Boolean flip) {
        this.flip = flip;
        this.tempYuv = new Mat(height + height / 2, width, CvType.CV_8UC1);
        this.tempRgb = new Mat(height, width, typeDisp);
        this.tempBm = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888); //swapped dims
        this.width = (int) Math.round(width / Math.pow(2, gBlurLevels));
        this.height = (int) Math.round(height / Math.pow(2, gBlurLevels));
        this.yuvFiltered = new Mat[bufferSize];
        for (int i = 0; i < yuvFiltered.length; i++) {
            yuvFiltered[i] = new Mat(height, width, typeDisp);
        }
        this.dispRgb = new Mat(width, height, typeDisp); // swapped dimensions from rgb
        for (int level = 0; level <= gBlurLevels; level++) {
            blurLevels.add(new Mat((int) Math.round(height / (Math.pow(2, level))),
                    (int) Math.round(width / (Math.pow(2, level))), typeDisp));
        }
        imageData = new byte[width*height*ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)/8];

        stackY = new Mat(this.width*this.height, bufferSize, typeDFT1);
        stackU = new Mat(this.width*this.height, bufferSize, typeDFT1);
        stackV = new Mat(this.width*this.height, bufferSize, typeDFT1);

        stackYc = new Mat(this.width*this.height, bufferSize, typeDFT1c);
        stackUc = new Mat(this.width*this.height, bufferSize, typeDFT1c);
        stackVc = new Mat(this.width*this.height, bufferSize, typeDFT1c);

        typeConverted = new Mat(this.height, this.width, typeDFT3);
        zeros = Mat.zeros(this.width * this.height, 1, typeDFT1c);
        halves = Mat.ones(this.width * this.height, 1, typeDFT1);
        Core.multiply(halves, midpoint, halves);
        halves.convertTo(halves, typeDFT1c);
        yuv = new Mat(height, width, typeDisp);
    }

    //add image to algorithm and increment pointer in circular frame buffer
    public void addImage(Image image){
        incrementIndex();
        image2mat(image);
    }

    //Convert image from the format given by the camera to an OpenCV Mat and load it into the stack
    private void image2mat(Image image){
        planes = image.getPlanes();
        byteBuffer = planes[0].getBuffer();
        int lastIndex = byteBuffer.remaining();
        byteBuffer.get(imageData, 0, lastIndex);
        int pixelStride = planes[1].getPixelStride();

        for (int i = 1 ; i < 3 ; i++) {
            byteBuffer = planes[i].getBuffer();
            byte[] planeData = new byte[byteBuffer.remaining()];
            byteBuffer.get(planeData);

            for (int j = 0; j < planeData.length; j += pixelStride) {
                imageData[lastIndex++] = planeData[j];
            }
        }

        tempYuv.put(0, 0, imageData);
        Imgproc.cvtColor(tempYuv, tempRgb, Imgproc.COLOR_YUV420p2BGR);  //this converts to rgb instead of bgr (typo?)
        tempRgb.copyTo(blurLevels.get(0));      //load into downsampler
        blurDown(gBlurLevels);                  //recursively blur and downsample
        Imgproc.cvtColor(blurLevels.get(gBlurLevels), yuv, Imgproc.COLOR_RGB2YUV); //convert to yuv with full color resolution
        packUnfiltered();   //load onto stack
    }

    //Load downsampled image into stack. columns are stacked on top of each other into one column
    private void packUnfiltered(){
        yuv.clone().convertTo(typeConverted, typeDFT3); //convert to floating point
        Core.split(typeConverted, channels);            //split color channels

        for (int i = 0 ; i < width ; i++) {
            channels.get(0).col(i).copyTo(stackY.col(index).rowRange(i*height, (i+1)*height));
            channels.get(1).col(i).copyTo(stackU.col(index).rowRange(i*height, (i+1)*height));
            channels.get(2).col(i).copyTo(stackV.col(index).rowRange(i*height, (i+1)*height));
        }

    }

    //Null certain temporal frequencies while amplifying others
    private void temporalFilter(){
        //apply dft along rows (pixels), across frames (columns), with respect to time
        Core.dft(stackY, stackYc, dftOptions, 0);
        Core.dft(stackU, stackUc, dftOptions, 0);
        Core.dft(stackV, stackVc, dftOptions, 0);
        freqThresh();   //kill or amplify frequencies
        //invert transform
        Core.dft(stackYc, stackY, idftOptions, 0);
        Core.dft(stackUc, stackU, idftOptions, 0);
        Core.dft(stackVc, stackV, idftOptions, 0);
    }

    //amplify frequencies within a certain band, kill the others.
    private void freqThresh(){
        int lowerIndex = (int) Math.round(lowerFreq/frameRate*(bufferSize-1));  //stack row for lower freq
        int upperIndex = (int) Math.round(upperFreq/frameRate*(bufferSize-1));  //stack row for upper freq
        for (int index = 0 ; index < bufferSize ; index++){
            if (index < lowerIndex || index > upperIndex){
                killFreq(index);
            }else{
                if(amplify){
                    ampFreq(index);
                }
            }
        }
    }

    //set row to zero in all channels
    private void killFreq(int index){
        zeros.copyTo(stackYc.col(index));
        halves.copyTo(stackUc.col(index));
        halves.copyTo(stackVc.col(index));
    }

    //amplify row in all channels
    private void ampFreq(int index){
        Core.multiply(stackYc.col(index), luminAlpha, stackYc.col(index));
        Core.multiply(stackUc.col(index), chromAlpha, stackUc.col(index));  //separate amplification factor for color
        Core.multiply(stackVc.col(index), chromAlpha, stackVc.col(index));
    }

    //inverse of packUnfiltered() function
    private void unpackFiltered(){
        for (int index = 0 ; index < bufferSize ; index++) {
            for (int i = 0; i < width; i++) {
                stackY.col(index).rowRange(i*height,(i+1)*height).copyTo(channels.get(0).col(i));
                stackU.col(index).rowRange(i*height,(i+1)*height).copyTo(channels.get(1).col(i));
                stackV.col(index).rowRange(i*height,(i+1)*height).copyTo(channels.get(2).col(i));
            }

            Core.merge(channels, typeConverted);
            typeConverted.convertTo(yuvFiltered[index], typeDisp);
        }
    }

    //increment index for frame buffer circularly, triggering processing on wrap-around
    private void incrementIndex(){
        if (index >= bufferSize - 1){
            index = 0;
            bufferFull();
        }else{
            index++;
        }
    }

    //triggered whenever the buffer wraps-around. Update frame rate and do processing
    private void bufferFull(){
        newTime = System.nanoTime();
        refreshPeriod = (newTime-oldTime)*1e-9;
        if (oldTime != 0) frameRate = bufferSize/(refreshPeriod);
        oldTime = newTime;
        temporalFilter();
        unpackFiltered();
        filteredReady = true;   // ok to start displaying results
        count++;
    }

    //called to produce frame (from processing results) for displaying on screen
    public Bitmap getBitmap(){
        if (filteredReady){ //only if there are results to show
            Imgproc.cvtColor(yuvFiltered[index], blurLevels.get(gBlurLevels), Imgproc.COLOR_YUV2RGB);
            blurUp(gBlurLevels);
            if (superposition) {    //simply add filtered results from last few second to current preview.
                Core.add(tempRgb, blurLevels.get(0), dispRgb);
            }else{
                dispRgb = blurLevels.get(0);
            }
        } else {
            dispRgb = tempRgb;  //Just show current preview.
        }

        //We need to flip the image differently depending on the camera being used (maybe this is the programmer's fault?)
        if (flip) {
            Core.flip(dispRgb.t(), dispRgb, -1);    //flip horizontal and vertical (after transpose)
        }else {
            Core.flip(dispRgb.t(), dispRgb, 1);     //flip horizontal (after transpose)
        }

        Utils.matToBitmap(dispRgb, tempBm);     //convert to bitmap for display
        return tempBm;
    }

    //Recursive function for applying gaussian blurring and downsampling
    private void blurDown(int level){
        if (level > 1){
            blurDown(level - 1);
        }
        Imgproc.pyrDown(blurLevels.get(level - 1), blurLevels.get(level), blurLevels.get(level).size());  //blur then downsample
    }

    //Recursive function for applying upsampling and gaussian blurring
    private void blurUp(int level){
        if (level > 1){
            blurUp(level - 1);
        }

        Mat input = blurLevels.get(gBlurLevels-level+1);
        Mat output = blurLevels.get(gBlurLevels-level);
        Size size = blurLevels.get(gBlurLevels-level).size();
        Imgproc.pyrUp(input, output, size); //upsample then blur
    }

    //Send number of buffers processed and calculated frame rate for displaying on front panel
    public String getFrameRate(){
        return count + ", " + displayFormat.format(frameRate);
    }

    //Functions for changing parameters from the front panel of the application
    public void setChroma(double val){
        chromAtten = val;
        chromAlpha = new Scalar(alpha*chromAtten,alpha*chromAtten);
    }
    public void setAlpha(double val){
        alpha = val;
        luminAlpha = new Scalar(alpha,alpha);
        chromAlpha = new Scalar(alpha*chromAtten,alpha*chromAtten);
    }
    public void setBandwidth(double val, Boolean unitBpm){
        if (unitBpm){
            bandwidthBpm = val;
        }else{
            bandwidthBpm = val*60;
        }
        recalculateFreqs();
    }
    public void setCenterFreq(double val, Boolean unitBpm){
        if (unitBpm){
            centerFreqBpm = val;
        }else{
            centerFreqBpm = val*60;
        }
        recalculateFreqs();
    }
    private void recalculateFreqs(){
        beatsPerMinuteU = centerFreqBpm+bandwidthBpm/2;
        beatsPerMinuteL = centerFreqBpm-bandwidthBpm/2;
        upperFreq = beatsPerMinuteU/60; //bpm to bps
        lowerFreq = beatsPerMinuteL/60;
    }
    public void setSuperposition(Boolean val){
        superposition = val;
    }

    /*
    // Converts mat to standard array, so that its contents can be inspected while in debug mode
    private double[][][] scan(Mat mat){
        double[][][] array  = new double[mat.rows()][mat.cols()][2];
        for (int i = 0 ; i < mat.rows() ; i++){
            for (int j = 0 ; j < mat.cols() ; j++){
                array[i][j] = mat.get(i,j);
            }
        }
        return array;
    }
    */

    Mat yuv;                //newest, low-res, yuv image from camera
    int index = 0;          //current position in circular frame buffer
    int bufferSize = 64;    //number of frames kept track of and processed (low res)
    Mat[] yuvFiltered;      //result of applying temporal frequency filtering to frame buffer
    DecimalFormat displayFormat = new DecimalFormat("###.#");   //round to one decimal (for frame rate)
    int width = 0;          //low-res width for images
    int height = 0;         //low-res height for images
    Mat dispRgb;            //high-res output image that will be displayed
    Bitmap tempBm;          //bitmap of dispRGB
    Mat tempYuv;            //fresh image which is color sampled at 4:2:0
    Mat tempRgb;            //full color-sampled rgb image from tempYuv. used as background in overlay mode.
    int gBlurLevels = 5;    //number of times that the input image should be gaussian blurred and downsampled before processing
    ArrayList<Mat> blurLevels = new ArrayList<>();  //as the images are downsampled recursively, they will fit into these pyramid levels

    //intermediate step for unpacking yuv image from camera
    byte[] imageData;
    Image.Plane[] planes;
    ByteBuffer byteBuffer;

    //for calculating frame rate
    long oldTime = 0;
    long newTime = 0;
    double frameRate = 10;
    double refreshPeriod = 10;

    //This contains the last <bufferSize> low-res frames captured.
    //Each column is a frame, with its columns all stacked on top of eachother
    //Each row represents a pixel
    Mat stackY;     //Luminance
    Mat stackU;     //U color channel
    Mat stackV;     //V color channel

    //The result of applying DFT with respect to time to each pixel (row). Complex.
    Mat stackYc;
    Mat stackUc;
    Mat stackVc;

    Mat typeConverted; //intermediate step for converting between unsigned integer and floating point
    int typeDFT1 = CvType.CV_32FC1;     //32 bit floating point, single channel (real only)
    int typeDFT1c = CvType.CV_32FC2;    //32 bit floating point, two channel (complex)
    int typeDFT3 = CvType.CV_32FC3;     //32 bit floating point, three channel (real only, color)
    int typeDisp = CvType.CV_8UC3;      //8 bit unsigned, three channel (real only, color)
    int dftOptions = Core.DFT_ROWS|Core.DFT_COMPLEX_OUTPUT;     //perform dft with along rows (with respect to time)
    int idftOptions = Core.DFT_ROWS|Core.DFT_INVERSE|Core.DFT_SCALE|Core.DFT_REAL_OUTPUT;   //invert dft with scaling for normalization
    List<Mat> channels = new ArrayList<>(); //intermediate step for splitting color channels
    int count = 0;                          // number of buffers processed
    Boolean filteredReady = false;          // until the first buffer is processed, don't try to show results
    double beatsPerMinuteL = 70;            // lower end of bandpass filter in beats per minute
    double beatsPerMinuteU = 80;            // upper end of bandpass filter in beats per minute
    double lowerFreq = beatsPerMinuteL/60; //bpm to bps
    double upperFreq = beatsPerMinuteU/60;
    double centerFreqBpm = (beatsPerMinuteL+beatsPerMinuteU)/2; //These are selected on front panel
    double bandwidthBpm = beatsPerMinuteU-beatsPerMinuteL;


    Mat zeros;   //entire row of zeros for nulling certain luminance frequencies
    Mat halves;  //entire row for nulling certain color frequencies (zero is in the middle of the range of possible values for color)
    double alpha = 10;      //bandpass amplification factor
    double chromAtten = 1;  //ratio of color amplification to overall amplification
    Scalar luminAlpha = new Scalar(alpha,alpha);
    Scalar chromAlpha = new Scalar(alpha*chromAtten,alpha*chromAtten);
    Scalar midpoint = new Scalar((255-1)*bufferSize/2); //find the middle value for color channels (indicating zero)
    Boolean superposition = false;      //optionally include the live previe along with results of filtering
    Boolean amplify = true;             //whether or not to amplify passed frequencies
    Boolean flip = true;                //input from one camera is flipped while the other is not
}
