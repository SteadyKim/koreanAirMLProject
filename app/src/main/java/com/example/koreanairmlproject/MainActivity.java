package com.example.koreanairmlproject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.koreanairmlproject.ml.Epoch10;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private Button selectBtn, predictBtn, captureBtn;
    private TextView result;
    private Bitmap bitmap;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /**
         * permission
         */
        getPermission();


        selectBtn = findViewById(R.id.selectBtn);
        predictBtn = findViewById(R.id.predictBtn);
        captureBtn = findViewById(R.id.captureBtn);
        result = findViewById(R.id.result);
        imageView = findViewById(R.id.imageView);

        /**
         * button
         */
        selectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, 10);
            }
        });

        captureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, 12);
            }
        });


        predictBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
//                    extracted();


                      Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true);

                      Epoch10 model = Epoch10.newInstance(MainActivity.this);
                      TensorImage tensorImage = TensorImage.fromBitmap(resizedBitmap);

                      Epoch10.Outputs outputs = model.process(tensorImage);
                      TensorBuffer outputAsTensorBuffer = outputs.getOutputAsTensorBuffer();
                      @org.checkerframework.checker.nullness.qual.NonNull float[] floatArray = outputAsTensorBuffer.getFloatArray();


                      float[][] twoDArray = new float[82][8400];
                      int flatIndex = 0;
                      for (int row = 0; row < 82; row++) {
                          for (int col = 0; col < 8400; col++) {
                              twoDArray[row][col] = floatArray[flatIndex];
                              flatIndex++;
                          }
                      }

                      float[][] transposedArray = new float[8400][82];
                      for (int row = 0; row < 82; row++) {
                            for (int col = 0; col < 8400; col++) {
                                transposedArray[col][row] = twoDArray[row][col];
                            }
                       }

                      // max 값 구하기
                      float[][] maxArray = new float[8400][5];

                      for(int row = 0 ; row < 8400; row++) {
                          // 4 부터
                          float[] slicedArray = Arrays.copyOfRange(transposedArray[row], 4, transposedArray[row].length);
                          int max = getMaxIndex(slicedArray);

                          // 좌표 + 최대 인덱스 저장하기
                          maxArray[row][0] = transposedArray[row][0];
                          maxArray[row][1] = transposedArray[row][1];
                          maxArray[row][2] = transposedArray[row][2];
                          maxArray[row][3] = transposedArray[row][3];
                          maxArray[row][4] = max;
                      }

                    for (float[] floats : maxArray) {
                        System.out.print(floats[0] + " "  + floats[1] + " " + floats[2] + " " + floats[3] + " " + floats[4]);
                        System.out.println();
                    }

                      model.close();
                     


                } catch (IOException e) {
                    // TODO Handle the exception
                }

            }

            private void extracted() throws IOException {
                Interpreter model = new Interpreter(loadModelFile());

                // resize bitmap to target format
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true);
                TensorImage tensorImage = TensorImage.fromBitmap(resizedBitmap);

                // init ImageProcessor
                ImageProcessor imageProcessor = new ImageProcessor.Builder()
                        .add(new ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))
                        .add(new NormalizeOp(0, 255))
                        .add(new CastOp(DataType.FLOAT32))
                        .build();

                // convert image to fit using imageProcessor
                TensorImage inputTensorImage = imageProcessor.process(tensorImage);

                float[][][] outputArray = new float[1][82][8400];
                ByteBuffer inputBuffer = inputTensorImage.getBuffer();

                model.run(inputBuffer, outputArray);
                System.out.println("outputArray = " + outputArray[0]);
                float maxPoint = 0f;
                for (float[] floats : outputArray[0]) {
                    float maxi = getMaxIndex(floats);
                    if (maxi >=  maxPoint) {
                        maxPoint = maxi;
                    }
                }

                System.out.println("maxPoint = " + maxPoint);
            }
        });

    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("epoch10.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

//    public float[][][] preprocessImage(Bitmap bitmap) {
//        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true);
//        TensorImage tensorImage = TensorImage.fromBitmap(resizedBitmap);
//        ByteBuffer inputData = tensorImage.getBuffer();
//
//        float[][][] inputArray = new float[1][640][640];
//
//        for (int y = 0; y < 640; y++) {
//            for (int x = 0; x < 640; x++) {
//                int pixelValue = resizedBitmap.getPixel(x, y);
//                inputArray[0][x][y] = (float) Color.red(pixelValue) / 255.0f;
//            }
//        }
//
//        return inputArray;
//
//}
private int getMaxIndex(float[] arr) {
        int maxIndex = 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] > arr[maxIndex]) {
                maxIndex = i;
            }
        }

        return maxIndex;
    }

    private void getPermission() {
        if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, 11);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 11) {
            if (grantResults.length > 0) {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    this.getPermission();

                }
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 10) {
            if (data != null) {
                Uri uri = data.getData();
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                    imageView.setImageBitmap(bitmap);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        else if (requestCode == 12) {
            bitmap = (Bitmap) data.getExtras().get("data");
            imageView.setImageBitmap(bitmap);
        }
    }
}