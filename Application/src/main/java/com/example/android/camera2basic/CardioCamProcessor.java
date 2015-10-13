package com.example.android.camera2basic;

import android.graphics.ImageFormat;
import android.media.Image;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.*;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;

public class CardioCamProcessor {

    public CardioCamProcessor(int width, int height){
        this.width = width;
        this.height = height;
        frameBuffer = new CcFrame[bufferSize];
        for (int i = 0 ; i < frameBuffer.length ; i++) frameBuffer[i] = new CcFrame();
    }

    public String getMeansRGB(){
        double[] means = Core.mean(frameBuffer[index].rgb).val;
        return round(means[0]) + ", " + round(means[1]) + ", " + round(means[2]);
    }

    private void yuv2rgb(int index){
        //BGR and RGB conversions are backwards for some reason
        Imgproc.cvtColor(frameBuffer[index].yuv, frameBuffer[index].rgb, Imgproc.COLOR_YUV420p2BGR);
    }

    private void image2yuv(Image image, int index){
        Image.Plane[] planes = image.getPlanes();

        byte[] imageData = new byte[image.getWidth() * image.getHeight()
                * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];

        ByteBuffer buffer = planes[0].getBuffer();
        int lastIndex = buffer.remaining();
        buffer.get(imageData, 0, lastIndex);
        int pixelStride = planes[1].getPixelStride();

        for (int i = 1; i < 3; i++) {
            buffer = planes[i].getBuffer();
            byte[] planeData = new byte[buffer.remaining()];
            buffer.get(planeData);

            for (int j = 0; j < planeData.length; j += pixelStride) {
                imageData[lastIndex++] = planeData[j];
            }
        }

        frameBuffer[index].yuv.put(0, 0, imageData);
    }

    public void addImage(Image image){
        incrementIndex();
        image2yuv(image, index);
        yuv2rgb(index);
    }

    private String round(double num){
        return df.format(num);
    }

    private void incrementIndex(){
        if (index >= bufferSize - 1){
            index = 0;
        }else{
            index++;
        }
    }

    int index = 0;
    int bufferSize = 10;
    CcFrame[] frameBuffer;
    DecimalFormat df = new DecimalFormat("###");
    int width = 0;
    int height = 0;
    Mat temp = new Mat();

    public Mat getRGB(){
        Core.flip(frameBuffer[index].rgb.t(), temp, -1);
        return temp;
    }

    private class CcFrame{

        public CcFrame(){
            yuv = new Mat(height + height / 2, width, CvType.CV_8UC1);
            rgb = new Mat(height, width, CvType.CV_8UC3);
        }

        public Mat yuv;
        public Mat rgb;
    }
}
