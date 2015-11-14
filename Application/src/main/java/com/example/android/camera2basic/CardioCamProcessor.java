package com.example.android.camera2basic;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class CardioCamProcessor {

    public CardioCamProcessor(int width, int height) {
        this.tempYuv = new Mat(height + height / 2, width, CvType.CV_8UC1);
        this.tempRgb = new Mat(height, width, CvType.CV_8UC3);
        this.tempBm = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888); //swapped dims
        this.width = Math.round(width / dimenRatio);
        this.height = Math.round(height / dimenRatio);
        this.frameBuffer = new CcFrame[bufferSize];
        for (int i = 0; i < frameBuffer.length; i++) {
            frameBuffer[i] = new CcFrame();
        }
        this.dispRgb = new Mat(width, height, CvType.CV_8UC3); // swapped dimensions from rgb
        for (int level = 0; level <= gBlurLevels; level++) {
            blurLevels.add(new Mat((int) Math.round(height / (Math.pow(2, level))),
                    (int) Math.round(width / (Math.pow(2, level))), CvType.CV_8UC3));
        }
        imageData = new byte[width*height*ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)/8];
    }

    public String getMeansRGB(){
        double[] means = new double[3];
        double[] meansTemp;
        for (int i = 0 ; i < bufferSize ; i++) {
            meansTemp = Core.mean(frameBuffer[i].rgb).val;
            means[0] += meansTemp[0];
            means[1] += meansTemp[1];
            means[2] += meansTemp[2];
        }
        means[0] /= bufferSize;
        means[1] /= bufferSize;
        means[2] /= bufferSize;
        return round(means[0]) + ", " + round(means[1]) + ", " + round(means[2]);
    }

    private void image2mat(Image image, int index){
        planes = image.getPlanes();

        byteBuffer = planes[0].getBuffer();
        int lastIndex = byteBuffer.remaining();
        byteBuffer.get(imageData, 0, lastIndex);
        int pixelStride = planes[1].getPixelStride();

        for (int i = 1; i < 3; i++) {
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
        frameBuffer[index].rgb = blurLevels.get(gBlurLevels).clone();
    }

    public void addImage(Image image){
        incrementIndex();
        image2mat(image, index);
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

    public Bitmap getBitmap(){
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
        Imgproc.pyrUp(blurLevels.get(gBlurLevels-level+1), blurLevels.get(gBlurLevels -level), blurLevels.get(gBlurLevels -level).size());
    }

    private class CcFrame{

        public CcFrame(){
            rgb = new Mat(height, width, CvType.CV_8UC3);
        }

        public Mat rgb;
    }

    int index = 0;
    int bufferSize = 50;
    CcFrame[] frameBuffer;
    DecimalFormat df = new DecimalFormat("###");
    int width = 0;
    int height = 0;
    Mat dispRgb;
    int gBlurLevels = 5;
    int dimenRatio = (int) Math.round(Math.pow(2, gBlurLevels));
    Mat tempYuv;
    Mat tempRgb;
    Bitmap tempBm;
    ArrayList<Mat> blurLevels = new ArrayList<>();
    byte[] imageData;
    Image.Plane[] planes;
    ByteBuffer byteBuffer;
}
