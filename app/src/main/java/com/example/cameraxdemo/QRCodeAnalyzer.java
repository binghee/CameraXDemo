package com.example.cameraxdemo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

/**
 * Created by homeh on 2020/3/16.
 */
public class QRCodeAnalyzer implements ImageAnalysis.Analyzer {
    private static final String TAG = QRCodeAnalyzer.class.getName();
    private ArrayList<AnalysisCallBack> listeners = new ArrayList<>();

    void onFrameAnalyze(AnalysisCallBack listener){
        listeners.add(listener);
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        if (listeners.isEmpty()) return;

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] yuvByte = new byte[ySize + uSize + vSize];
        yBuffer.get(yuvByte, 0, ySize);
        vBuffer.get(yuvByte, ySize, vSize);
        uBuffer.get(yuvByte, ySize + vSize, uSize);

        LuminanceSource luminanceSource = new PlanarYUVLuminanceSource(yuvByte,image.getWidth(),image.getHeight(),0,0,image.getWidth(),image.getHeight(),false);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(luminanceSource));
        MultiFormatReader multiFormatReader = new MultiFormatReader();
        try {
            Result result = multiFormatReader.decode(binaryBitmap);
            Log.d(TAG, Arrays.toString(result.getResultPoints()));
            for (AnalysisCallBack callBack : listeners) {
                callBack.onAnalysis(result.getText());
            }
        } catch (NotFoundException e) {
            for (AnalysisCallBack callBack : listeners) {
                callBack.onAnalysis(null);
            }
        }
        image.close();
    }

    private Bitmap yuvImageToBitmap(@NonNull Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }
}
