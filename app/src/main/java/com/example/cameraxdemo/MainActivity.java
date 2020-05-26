package com.example.cameraxdemo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.example.cameraxdemo.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getName();
    private static final String FILENAME = "yyyyMMddHHmmss";
    private static final String PHOTO_EXTENSION = ".jpg";
    private static final double RATIO_4_3_VALUE = 4.0 / 3.0;
    private static final double RATIO_16_9_VALUE = 16.0 / 9.0;

    private ActivityMainBinding binding;

    private String[] permissionString = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private ExecutorService executors;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private int flashMode = ImageCapture.FLASH_MODE_OFF;
    private Preview preview;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private MediaActionSound mediaActionSound = new MediaActionSound();
    private Camera camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this));
        setContentView(binding.getRoot());

        executors = Executors.newSingleThreadExecutor();

        checkPermission(permissionString);
        updateCameraUi();
        if (checkPermissionAllGranted(permissionString)) {
            binding.textureView.post(this::bindCameraUseCase);
        }
        binding.textureView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateTransform());
    }

    @Override
    protected void onDestroy() {
        mediaActionSound.release();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            boolean isAllGranted = true;
            // 判断是否所有的权限都已经授予了
            for (int grant : grantResults) {
                if (grant != PackageManager.PERMISSION_GRANTED) {
                    isAllGranted = false;
                    break;
                }
            }
            if (isAllGranted) {
                binding.textureView.post(this::bindCameraUseCase);
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("权限未授予")
                        .setMessage("有权限没有授予，可能影响使用,请前往授权")
                        .setPositiveButton("确定", (dialog, which) -> checkPermission(permissionString))
                        .setNegativeButton("取消", (dialog, which) -> showToast("有权限没有授予，可能影响使用,请前往授权"));
                builder.create().show();
            }
        }
    }

    private void updateCameraUi() {
        binding.buttonCaptureImage.setOnClickListener(v -> takePicture());
        binding.buttonCaptureImage.setOnLongClickListener(v -> {
            flashMode = imageCapture.getFlashMode() == ImageCapture.FLASH_MODE_ON ? ImageCapture.FLASH_MODE_OFF : ImageCapture.FLASH_MODE_ON;
            bindCameraUseCase();
            return false;
        });
        binding.buttonSwitchCamera.setOnClickListener(v -> {
            lensFacing = lensFacing == CameraSelector.LENS_FACING_FRONT ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
            bindCameraUseCase();
        });
        binding.textView.setOnClickListener(v -> {
            String msg = binding.textView.getText().toString();
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager != null) {
                ClipData data = ClipData.newPlainText("qr_result", msg);
                clipboardManager.setPrimaryClip(data);
            } else {
                showToast("剪切板错误");
            }
            if (msg.contains("http")){
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(msg)));
            }
        });
    }

    private void bindCameraUseCase() {
        int screenAspectRatio = AspectRatio.RATIO_4_3;

        int rotation = (int) binding.textureView.getRotation();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getApplicationContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                preview = new Preview.Builder()
                        .setTargetAspectRatio(screenAspectRatio)
                        .setTargetRotation(rotation)
                        .build();
                preview.setSurfaceProvider(binding.textureView);

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetAspectRatio(screenAspectRatio)
                        .setTargetRotation(rotation)
                        .setFlashMode(flashMode)
                        .build();

                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetAspectRatio(screenAspectRatio)
                        .setTargetRotation(rotation)
                        .build();
                QRCodeAnalyzer qrCodeAnalyzer = new QRCodeAnalyzer();
                qrCodeAnalyzer.onFrameAnalyze(msg -> binding.textureView.post(() -> {
                    if (msg == null) {
                        binding.textView.setText(R.string.qr_code_not_found);
                    } else {
                        binding.textView.setText(msg);
                    }
                }));
                imageAnalysis.setAnalyzer(executors, qrCodeAnalyzer);

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(MainActivity.this, cameraSelector, preview, imageCapture, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(getApplicationContext()));
    }

    private Uri takePicture() {
        final Uri[] imageUri = new Uri[1];
        if (imageCapture != null) {
            ImageCapture.Metadata metadata = new ImageCapture.Metadata();
            metadata.setReversedHorizontal(lensFacing == CameraSelector.LENS_FACING_FRONT);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, new SimpleDateFormat(FILENAME, Locale.CHINA).format(System.currentTimeMillis()) + PHOTO_EXTENSION);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    .setMetadata(metadata)
                    .build();
            imageCapture.takePicture(outputFileOptions, executors, new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    imageUri[0] = outputFileResults.getSavedUri();
                    runOnUiThread(() -> {
                        assert imageUri[0] != null;
                        showToast("Saved photo:\n" + imageUri[0].getPath());
                    });
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    runOnUiThread(() -> showToast("ERROR:\n" + exception.getMessage()));
                    Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                }
            });
            // 快门声
            mediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
        }
        return imageUri[0];
    }

    private void updateTransform() {
        Matrix matrix = new Matrix();

        float centerX = binding.textureView.getWidth() / 2f;
        float centerY = binding.textureView.getHeight() / 2f;
        float[] rotations = {0, 90, 180, 270};
        float rotationDegrees = rotations[binding.textureView.getDisplay().getRotation()];

        matrix.postRotate(-rotationDegrees, centerX, centerY);

        // Finally, apply transformations to our TextureView
        binding.textureView.setTransform(matrix);
        Log.d(TAG, binding.textureView.getWidth() + ":" + binding.textureView.getHeight());
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void showDialog(@NonNull String msg) {
        AlertDialog msgDialog;
        if (msg.contains("http")) {
            msgDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.qr_code_found)
                    .setMessage(msg)
                    .setNegativeButton("进入", (dialog, which) -> {
                        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboardManager != null) {
                            ClipData data = ClipData.newPlainText("qr_result", msg);
                            clipboardManager.setPrimaryClip(data);
                        } else {
                            showToast("剪切板错误");
                        }
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(msg)));
                    })
                    .setNegativeButton("取消", null)
                    .create();
        } else {
            msgDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.qr_code_found)
                    .setMessage(msg)
                    .setPositiveButton("复制", (dialog, which) -> {
                        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboardManager != null) {
                            ClipData data = ClipData.newPlainText("qr_result", msg);
                            clipboardManager.setPrimaryClip(data);
                        } else {
                            showToast("剪切板错误");
                        }
                    })
                    .setNegativeButton("取消", null)
                    .create();
        }
        if (!msgDialog.isShowing()) {
            msgDialog.show();
        }
    }

    // 计算显示比例
    private int aspectRatio(int width, int height) {
        double previewRatio = (double) Math.max(width, height) / Math.min(width, height);
        if (Math.abs(previewRatio - RATIO_4_3_VALUE) <= Math.abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3;
        }
        return AspectRatio.RATIO_16_9;
    }


    public void checkPermission(String[] permissions) {
        int targetSdkVersion = 0;
        try {
            final PackageInfo info = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
            targetSdkVersion = info.applicationInfo.targetSdkVersion;//获取应用的Target版本
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (targetSdkVersion >= Build.VERSION_CODES.M) {
                // 检查是否有相应的权限
                boolean isAllGranted = checkPermissionAllGranted(permissions);
                if (isAllGranted) {
                    return;
                }
                // 一次请求多个权限, 如果其他有权限是已经授予的将会自动忽略掉
                ActivityCompat.requestPermissions(this, permissions, 1);
            }
        }
    }

    private boolean checkPermissionAllGranted(@NonNull String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
