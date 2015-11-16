package com.example.android.camera2basic;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CardioCamProcessor {

    public CardioCamProcessor(int width, int height) {
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
        mask = new Mat(this.width*this.height, bufferSize, typeDFT1);
        typeConverted = new Mat(this.height, this.width, typeDFT3);
        Mat ones = Mat.ones(mask.rows(),1,typeDFT1);
        Mat zeros = Mat.zeros(mask.rows(), 1, typeDFT1);
        int lowerFreq = 0;
        int upperFreq = mask.cols();
        for (int i = 0 ; i < mask.cols() ; i++){
            Mat col = mask.col(i);
            if (i >= lowerFreq && i <= upperFreq){
                ones.copyTo(col);
            }else{
                zeros.copyTo(col);
            }
        }
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
        Imgproc.cvtColor(tempYuv, blurLevels.get(0), Imgproc.COLOR_YUV420p2BGR);
        blurDown(gBlurLevels);
        frameBuffer[index].rgb = blurLevels.get(gBlurLevels);
        Imgproc.cvtColor(frameBuffer[index].rgb, frameBuffer[index].yuv, Imgproc.COLOR_RGB2YUV);
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
        Core.dft(stackY, stackY, Core.DFT_ROWS, 0);
        Core.multiply(stackY, mask, stackY);
        Core.dft(stackY, stackY, Core.DFT_ROWS | Core.DFT_INVERSE | Core.DFT_SCALE, 0);

        Core.dft(stackU, stackU, Core.DFT_ROWS, 0);
        Core.multiply(stackU, mask, stackU);
        Core.dft(stackU, stackU, Core.DFT_ROWS | Core.DFT_INVERSE | Core.DFT_SCALE, 0);

        Core.dft(stackV, stackV, Core.DFT_ROWS, 0);
        Core.multiply(stackV, mask, stackV);
        Core.dft(stackV, stackV, Core.DFT_ROWS | Core.DFT_INVERSE | Core.DFT_SCALE, 0);
        count++;
    }

    private void unpackFiltered(){
        for (int index = 0 ; index < bufferSize ; index++) {
            for (int i = 0; i < width; i++) {
                insertionPoint = channels.get(0).col(i);
                stackY.col(index).rowRange(i * height, (i + 1) * height).copyTo(insertionPoint);
                insertionPoint = channels.get(1).col(i);
                stackU.col(index).rowRange(i * height, (i + 1) * height).copyTo(insertionPoint);
                insertionPoint = channels.get(2).col(i);
                stackV.col(index).rowRange(i * height, (i + 1) * height).copyTo(insertionPoint);
            }

            Core.merge(channels, typeConverted);
            typeConverted.convertTo(frameBuffer[index].yuvFiltered, typeDisp);
        }
        filteredReady = true;
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
        if (oldTime != 0) frameRate = bufferSize/((newTime-oldTime)*1e-9);
        oldTime = newTime;
        temporalFilter();
        unpackFiltered();
    }


    public Bitmap getBitmap(){

        if (filteredReady){
            //color order? DataTypes?
            Imgproc.cvtColor(frameBuffer[index].yuvFiltered, blurLevels.get(gBlurLevels), Imgproc.COLOR_YUV2RGB);
        }else{
            frameBuffer[index].rgb.copyTo(blurLevels.get(gBlurLevels));
        }

        blurUp(gBlurLevels);
        Core.flip(blurLevels.get(0).t(), dispRgb, -1);
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
            rgb = new Mat(height, width, typeDisp);
            yuv = new Mat(height, width, typeDisp);
            yuvFiltered = new Mat(height, width, typeDisp);
        }

        public Mat rgb;
        public Mat yuvFiltered;
        public Mat yuv;
    }

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
    double frameRate = 30;
    double refreshPeriod = 10;
    Mat stackY;
    Mat stackU;
    Mat stackV;
    Mat mask;
    Mat typeConverted;
    int typeDFT1 = CvType.CV_32FC1; //type == CV_32FC1 || type == CV_32FC2 || type == CV_64FC1 || type == CV_64FC2
    int typeDFT3 = CvType.CV_32FC3;
    int typeDisp = CvType.CV_8UC3;
    List<Mat> channels = new ArrayList<>();
    Mat insertionPoint;
    int count = 0;
    Boolean filteredReady = false;
    double indicator = 0;

}
