package com.example.koreanairmlproject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private DataProcess dataProcess;

    private RectView rectView;

    private OrtEnvironment ortEnvironment;

    private OrtSession session;

    private static final int PERMISSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        dataProcess = new DataProcess(MainActivity.this);
        rectView = findViewById(R.id.rectView);

        // 자동 꺼짐 해제
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 권한 허용
        setPermission();

        // 모델 불러오기
        load();

        //s 카메라 켜기
        setCamera();
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "권한을 허용하지 않으면 사용할 수 없습니다.", Toast.LENGTH_SHORT);

                    finish();
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void setPermission() {
        ArrayList<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);

        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION);
            }
        }
    }

    private void setCamera() {
        try {
            // 카메라 제공 객체
            ProcessCameraProvider processCameraProvider = ProcessCameraProvider.getInstance(this).get();

            // 전체 화면
            previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

            // 전면 카메라
            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

            // 16:9 화면으로 받아오기
            Preview preview = new Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9).build();

            // preview에서 받아와서 previewView에 보여주기
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // 분석 중이면 그 다음 화면이 대기중인 것이 아니라, 계속 받아오는 화면으로 새로고침하기. 분석이 끝나면 그 최신 사진을 다시 분석하게
            ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

            analysis.setAnalyzer(Executors.newSingleThreadExecutor(), new ImageAnalysis.Analyzer() {
                @Override
                public void analyze(@NonNull ImageProxy image) {
                    imageProcess(image);
                    image.close();
                }
            });

            // 카메라의 수명 주기를 메인 액티비티에 귀속시키기
            processCameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis);

        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    private void imageProcess(ImageProxy imageProxy) {

        Bitmap bitmap = dataProcess.imageToBitmap(imageProxy);
        FloatBuffer floatBuffer = dataProcess.bitmapToFloatBuffer(bitmap);
        String inputName = session.getInputNames().iterator().next();

        // 모델의 요구 입력값 [1, 3, 640, 640] [배치 사이즈, 픽셀(RGB), 너비, 높이], 모델마다 크기는 다를 수 있음.
        long[] shape = new long[]{
                DataProcess.BATCH_SIZE,
                DataProcess.PIXEL_SIZE,
                DataProcess.INPUT_SIZE,
                DataProcess.INPUT_SIZE
        };

        OnnxTensor inputTensor = null;
        try {
            inputTensor = OnnxTensor.createTensor(ortEnvironment, floatBuffer, shape);
            OrtSession.Result resultTensor = session.run(Collections.singletonMap(inputName, inputTensor));
            Object[] outputs = (Object[]) resultTensor.get(0).getValue(); // [1 82 8400]
            ArrayList<Result> results = dataProcess.outputsToNMSPredictions(outputs);

            rectView.transformRect(results);
            rectView.invalidate();

        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }


    private void load() {
        dataProcess.loadModel();
        dataProcess.loadLabel();

        ortEnvironment = OrtEnvironment.getEnvironment();
        try {
            ortEnvironment.createSession(
                    this.getFilesDir().getAbsolutePath() + "/" + DataProcess.FILE_NAME,
                    new OrtSession.SessionOptions()
            );
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }

        rectView.setClassLabel(dataProcess.getClasses());
    }
}