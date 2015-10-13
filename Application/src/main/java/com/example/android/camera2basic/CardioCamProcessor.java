package com.example.android.camera2basic;

import android.graphics.ImageFormat;
import android.media.Image;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;

public class CardioCamProcessor {

    public CardioCamProcessor(){
        df = new DecimalFormat("###");
    }

    public String analyze(Image image){

        Mat yuv = image2YUV(image);
        Mat bgr = yuv2bgr(yuv);
        double[] means = Core.mean(bgr).val;

        return round(means[2]) + ", " + round(means[1]) + ", " + round(means[0]);
    }

    private Mat yuv2bgr(Mat yuv){
        Mat bgr = new Mat();
        Imgproc.cvtColor(yuv, bgr, Imgproc.COLOR_YUV420p2RGB);
        return bgr;
    }

    private Mat image2YUV(Image image){
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

        Mat yuv = new Mat(image.getHeight() + image.getHeight() / 2,
                image.getWidth(), CvType.CV_8UC1);
        yuv.put(0, 0, imageData);
        return yuv;
    }

    private String round(double num){
        return df.format(num);
    }

    DecimalFormat df;
}
