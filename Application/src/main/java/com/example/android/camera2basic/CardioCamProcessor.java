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
import java.util.Arrays;
import java.util.List;

public class CardioCamProcessor {

    public CardioCamProcessor(int width, int height, Boolean flip) {
        this.flip = flip;
        this.tempYuv = new Mat(height + height / 2, width, CvType.CV_8UC1);
        this.tempRgb = new Mat(height, width, typeDisp);
        this.tempBm = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888); //swapped dims
        this.width = (int) Math.round(width / Math.pow(2, gBlurLevels));
        this.height = (int) Math.round(height / Math.pow(2, gBlurLevels));
        this.frameBuffer = new CcFrame[bufferSize];
        for (int i = 0; i < frameBuffer.length; i++) {
            frameBuffer[i] = new CcFrame();
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
        halves = Mat.ones(this.width*this.height, 1, typeDFT1);
        Core.multiply(halves, midpoint, halves);
        halves.convertTo(halves, typeDFT1c);

        rgb = new Mat(height, width, typeDisp);
    }

    public String getFrameRate(){
        return count + ", " + threeDecimals.format(frameRate);
    }

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
        Imgproc.cvtColor(tempYuv, tempRgb, Imgproc.COLOR_YUV420p2BGR);
        tempRgb.copyTo(blurLevels.get(0));
        blurDown(gBlurLevels);
        rgb = blurLevels.get(gBlurLevels);
        Imgproc.cvtColor(rgb, frameBuffer[index].yuv, Imgproc.COLOR_RGB2YUV);
        packUnfiltered();
    }

    private void packUnfiltered(){
        frameBuffer[index].yuv.clone().convertTo(typeConverted, typeDFT3);
        Core.split(typeConverted, channels);

        for (int i = 0 ; i < width ; i++) {
            insertionPoint = stackY.col(index).rowRange(i * height, (i + 1) * height);
            channels.get(0).col(i).copyTo(insertionPoint);
            insertionPoint = stackU.col(index).rowRange(i * height, (i + 1) * height);
            channels.get(1).col(i).copyTo(insertionPoint);
            insertionPoint = stackV.col(index).rowRange(i * height, (i + 1) * height);
            channels.get(2).col(i).copyTo(insertionPoint);
        }

    }

    private void temporalFilter(){
        Core.dft(stackY, stackYc, Core.DFT_ROWS | Core.DFT_COMPLEX_OUTPUT, 0);
        Core.dft(stackU, stackUc, Core.DFT_ROWS | Core.DFT_COMPLEX_OUTPUT, 0);
        Core.dft(stackV, stackVc, Core.DFT_ROWS | Core.DFT_COMPLEX_OUTPUT, 0);

        freqThresh();

        Core.dft(stackYc, stackY, Core.DFT_ROWS | Core.DFT_INVERSE | Core.DFT_SCALE | Core.DFT_REAL_OUTPUT, 0);
        Core.dft(stackUc, stackU, Core.DFT_ROWS | Core.DFT_INVERSE | Core.DFT_SCALE | Core.DFT_REAL_OUTPUT, 0);
        Core.dft(stackVc, stackV, Core.DFT_ROWS | Core.DFT_INVERSE | Core.DFT_SCALE | Core.DFT_REAL_OUTPUT, 0);

        count++;
    }

    private void freqThresh(){
        int lowerIndex = (int) Math.round(lowerFreq/frameRate*(bufferSize-1));
        int upperIndex = (int) Math.round(upperFreq/frameRate*(bufferSize-1));
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

    private void killFreq(int index){
        insertionPoint = stackYc.col(index);
        zeros.copyTo(insertionPoint);
        insertionPoint = stackUc.col(index);
        halves.copyTo(insertionPoint);
        insertionPoint = stackVc.col(index);
        halves.copyTo(insertionPoint);
    }

    private void ampFreq(int index){
        double[][][] array;
        insertionPoint = stackYc.col(index);
        array = scan(insertionPoint);
        Core.multiply(insertionPoint, luminAlpha, insertionPoint);
        array = scan(insertionPoint);
        insertionPoint = stackUc.col(index);
        //array = scan(halves);
        //Core.subtract(insertionPoint, halves, insertionPoint);
        Core.multiply(insertionPoint, chromAlpha, insertionPoint);
        //Core.add(insertionPoint, halves, insertionPoint);

        insertionPoint = stackVc.col(index);
        //Core.subtract(insertionPoint, halves, insertionPoint);
        Core.multiply(insertionPoint, chromAlpha, insertionPoint);
        //Core.add(insertionPoint, halves, insertionPoint);
    }

    private double[][][] scan(Mat mat){
        double[][][] array  = new double[mat.rows()][mat.cols()][2];
        for (int i = 0 ; i < mat.rows() ; i++){
            for (int j = 0 ; j < mat.cols() ; j++){
                array[i][j] = mat.get(i,j);
            }
        }
        return array;
    }

    private void unpackFiltered(){
        for (int index = 0 ; index < bufferSize ; index++) {
            for (int i = 0; i < width; i++) {
                insertionPoint = channels.get(0).col(i);
                stackY.col(index).rowRange(i*height,(i+1)*height).copyTo(insertionPoint);
                insertionPoint = channels.get(1).col(i);
                stackU.col(index).rowRange(i*height,(i+1)*height).copyTo(insertionPoint);
                insertionPoint = channels.get(2).col(i);
                stackV.col(index).rowRange(i*height,(i+1)*height).copyTo(insertionPoint);
            }

            Core.merge(channels, typeConverted);
            typeConverted.convertTo(frameBuffer[index].yuvFiltered, typeDisp);
        }
    }

    public void addImage(Image image){
        incrementIndex();
        image2mat(image);
    }

    private void incrementIndex(){
        if (index >= bufferSize - 1){
            index = 0;
            bufferFull();
        }else{
            index++;
        }
    }

    private void bufferFull(){
        newTime = System.nanoTime();
        refreshPeriod = (newTime-oldTime)*1e-9;
        if (oldTime != 0) frameRate = bufferSize/(refreshPeriod);
        oldTime = newTime;
        temporalFilter();
        //amplify();
        unpackFiltered();
        filteredReady = true;
    }

    public Bitmap getBitmap(){
        if (filteredReady){
            Imgproc.cvtColor(frameBuffer[index].yuvFiltered, blurLevels.get(gBlurLevels), Imgproc.COLOR_YUV2RGB);
            blurUp(gBlurLevels);
            if (superposition) {
                Core.add(tempRgb, blurLevels.get(0), dispRgb);
            }else{
                dispRgb = blurLevels.get(0);
            }
        } else {
            dispRgb = tempRgb;
        }

        if (flip) {
            Core.flip(dispRgb.t(), dispRgb, -1);
        }else {
            Core.flip(dispRgb.t(), dispRgb, 1);
        }

        Utils.matToBitmap(dispRgb, tempBm);
        return tempBm;
    }

    private void blurDown(int level){
        if (level > 1){
            blurDown(level-1);
        }
        Imgproc.pyrDown(blurLevels.get(level-1), blurLevels.get(level), blurLevels.get(level).size());
    }

    private void blurUp(int level){
        if (level > 1){
            blurUp(level - 1);
        }

        Mat input = blurLevels.get(gBlurLevels-level+1);
        Mat output = blurLevels.get(gBlurLevels-level);
        Size size = blurLevels.get(gBlurLevels-level).size();
        Imgproc.pyrUp(input, output, size);
    }

    private class CcFrame{

        public CcFrame(){
            yuv = new Mat(height, width, typeDisp);
            yuvFiltered = new Mat(height, width, typeDisp);
        }

        public Mat yuvFiltered;
        public Mat yuv;
    }

    Mat rgb;
    int index = 0;
    int bufferSize = 64;
    CcFrame[] frameBuffer;
    DecimalFormat zeroDecimals = new DecimalFormat("###");
    DecimalFormat threeDecimals = new DecimalFormat("###.###");
    int width = 0;
    int height = 0;
    Mat dispRgb;
    int gBlurLevels = 5;
    Mat tempYuv;
    Mat tempRgb;
    Bitmap tempBm;
    ArrayList<Mat> blurLevels = new ArrayList<>();
    byte[] imageData;
    Image.Plane[] planes;
    ByteBuffer byteBuffer;
    long oldTime = 0;
    long newTime = 0;
    double frameRate = 10;
    double refreshPeriod = 10;
    Mat stackY;
    Mat stackU;
    Mat stackV;
    Mat stackYc;
    Mat stackUc;
    Mat stackVc;
    Mat typeConverted;
    int typeDFT1 = CvType.CV_32FC1; //type == CV_32FC1 || type == CV_32FC2 || type == CV_64FC1 || type == CV_64FC2
    int typeDFT1c = CvType.CV_32FC2; //type == CV_32FC1 || type == CV_32FC2 || type == CV_64FC1 || type == CV_64FC2
    int typeDFT3 = CvType.CV_32FC3;
    int typeDisp = CvType.CV_8UC3;
    List<Mat> channels = new ArrayList<>();
    Mat insertionPoint;
    int count = 0;
    Boolean filteredReady = false;
    double indicator = 0;
    double beatsPerMinuteL = 65;
    double beatsPerMinuteU = 80;
    double lowerFreq = beatsPerMinuteL/60; //bps
    double upperFreq = beatsPerMinuteU/60;
    Mat zeros;
    Mat halves;
    Mat chromAlphas;
    Mat luminAlphas;
    double alpha = 25;
    double chromAtten = 1;
    Scalar luminAlpha = new Scalar(alpha,alpha);
    Scalar chromAlpha = new Scalar(alpha*chromAtten,alpha*chromAtten);
    Scalar midpoint = new Scalar((255-1)*bufferSize/2);
    Boolean superposition = false;
    Boolean amplify = true;
    Boolean flip = true;
}
