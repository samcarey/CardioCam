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
        bgr = new Mat();
    }

    public String analyze(Image image){
        planes = image.getPlanes();

        imageData = new byte[image.getWidth() * image.getHeight()
                * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];

        buffer = planes[0].getBuffer();
        lastIndex = buffer.remaining();
        buffer.get(imageData, 0, lastIndex);
        pixelStride = planes[1].getPixelStride();

        for (int i = 1; i < 3; i++) {
            buffer = planes[i].getBuffer();
            planeData = new byte[buffer.remaining()];
            buffer.get(planeData);

            for (int j = 0; j < planeData.length; j += pixelStride) {
                imageData[lastIndex++] = planeData[j];
            }
        }

        yuv = new Mat(image.getHeight() + image.getHeight() / 2,
                image.getWidth(), CvType.CV_8UC1);
        yuv.put(0, 0, imageData);

        Imgproc.cvtColor(yuv, bgr, Imgproc.COLOR_YUV420p2RGB);

        double[] means = Core.mean(bgr).val;

        return df.format(means[2]) + ", " + df.format(means[1]) + ", " + df.format(means[0]);
    }


    ByteBuffer buffer;
    byte[] imageData;
    Image.Plane[] planes;
    int pixelStride;
    int lastIndex;
    byte[] planeData;
    DecimalFormat df;
    Mat bgr;
    Mat yuv;
}
