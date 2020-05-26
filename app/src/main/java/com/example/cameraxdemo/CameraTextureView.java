package com.example.cameraxdemo;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceRequest;

/**
 * Created by homeh on 2020/3/8.
 * 3:4
 */
public class CameraTextureView extends TextureView implements Preview.SurfaceProvider {
    private ExecutorService executors = Executors.newSingleThreadExecutor();

    public CameraTextureView(Context context) {
        super(context);
    }

    public CameraTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CameraTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CameraTextureView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        Configuration mConfiguration = this.getResources().getConfiguration(); //获取设置的配置信息
        int ori = mConfiguration.orientation; //获取屏幕方向
        if (ori == Configuration.ORIENTATION_LANDSCAPE) {
            int height = MeasureSpec.getSize(heightMeasureSpec);
            setMeasuredDimension(height /3*4 , height);
        } else if (ori == Configuration.ORIENTATION_PORTRAIT) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width,width/3*4);
        }

    }

    @Override
    public void onSurfaceRequested(@NonNull SurfaceRequest request) {
        if (executors.isShutdown()){
            request.willNotProvideSurface();
            return;
        }
        Surface surface = new Surface(getSurfaceTexture());
        request.provideSurface(surface , executors,(result) -> {
            result.getSurface().release();
        });
    }
}
