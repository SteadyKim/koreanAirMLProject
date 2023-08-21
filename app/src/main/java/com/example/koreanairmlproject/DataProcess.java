package com.example.koreanairmlproject;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;

import androidx.camera.core.ImageProxy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;

public class DataProcess {
    private Context context;
    private String[] classes;

    public DataProcess(Context context) {
        this.context = context;
    }

    public String[] getClasses() {
        return classes;
    }

    public static final int BATCH_SIZE = 1;
    public static final int INPUT_SIZE = 640;
    public static final int PIXEL_SIZE = 3;

    public static final String FILE_NAME = "bestepoch50.onnx";

    public static final String LABEL_NAME = "labels.txt";


    public Bitmap imageToBitmap(ImageProxy imageProxy) {
        Bitmap bitmap = imageProxy.toBitmap();

        return Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
    }

    public FloatBuffer bitmapToFloatBuffer(Bitmap bitmap) {
        float imageSTD = 255.0f;
        FloatBuffer buffer = FloatBuffer.allocate(BATCH_SIZE * PIXEL_SIZE * INPUT_SIZE * INPUT_SIZE);
        buffer.rewind();

        int area = INPUT_SIZE * INPUT_SIZE;
        int[] bitmapData = new int[area];

        bitmap.getPixels(
                bitmapData,
                0,
                bitmap.getWidth(),
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight()
        );
        // ARGB 형태의 32 bit -> 16 bit RGB
        for (int i = 0; i < INPUT_SIZE - 1; i++) {
            for (int j = 0; j < INPUT_SIZE - 1; j++) {
                int idx = INPUT_SIZE *  640 + j;
                int pixelValue = bitmapData[idx];

                buffer.put(idx, ((pixelValue >> 16 & 0xff) / imageSTD));
                buffer.put(idx + area, ((pixelValue >> 8 & 0xff) / imageSTD));
                buffer.put(idx + area * 2, ((pixelValue & 0xff) / imageSTD));
            }
        }

        buffer.rewind();

        return buffer;
    }


    public void loadModel() {
        AssetManager assetManager = context.getAssets();
        File outputFile = new File(context.getFilesDir(), FILE_NAME);
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = assetManager.open(FILE_NAME);
            outputStream = new FileOutputStream(outputFile);

            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public void loadLabel() {
        ArrayList<String> classList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(LABEL_NAME)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                classList.add(line);
            }
            classes = classList.toArray(new String[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<Result> outputsToNMSPredictions(Object[] outputs) {
        float confidenceThreshold = 0.75f;
        ArrayList<Result> results = new ArrayList<>();
        int rows;
        int cols;

        Object[] outputArray = (Object[]) outputs[0];
        rows = outputArray.length;
        cols = ((float[]) outputArray[0]).length;

        // 배열의 형태를 [84 8400] -> [8400 84] 로 변환
        float[][] output = new float[cols][rows];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                output[j][i] = ((float[]) ((Object[]) outputs[0])[i])[j];
            }
        }

        for (int i = 0; i < cols; i++) {
            int detectionsClass = -1;
            float maxScore = 0f;
            float[] classArray = new float[classes.length];
            // label만 따로 빼서 1차원 배열 만들기
            System.arraycopy(output[i], 4, classArray, 0, classes.length);

            // label 중에서 가장 큰 값 선정하기
            for (int j = 0; j < classes.length; j++) {
                if (classArray[j] > maxScore) {
                    detectionsClass = j;
                    maxScore = classArray[j];
                }
            }

            if (maxScore > confidenceThreshold) {
                float xPos = output[i][0] * 640;
                float yPos = output[i][1] * 640;
                float width = output[i][2] * 640;
                float height = output[i][3] * 640;

                // 사각형은 화면 밖으로 나갈 수 없으니 화면을 넘기면 최대 화면 값을 가지게 한다.
                RectF rectF = new RectF(
                        Math.max(0f, xPos - width / 2f),
                        Math.max(0f, yPos - height / 2f),
                        Math.min(INPUT_SIZE - 1f, xPos + width / 2f),
                        Math.min(INPUT_SIZE - 1f, yPos + height / 2f)
                );
                Result result = new Result(detectionsClass, maxScore, rectF);
                results.add(result);
            }
        }

        return nms(results);
    }

    private ArrayList<Result> nms(ArrayList<Result> results) {
        ArrayList<Result> list = new ArrayList<>();
        for (int i = 0; i < classes.length; i++) {
            // 1. 클래스 중에서 가장 높은 확률값을 가졌던 클래스 찾기
            PriorityQueue<Result> pq = new PriorityQueue<>(50, new Comparator<Result>() {

                @Override
                public int compare(Result o1, Result o2) {
                    return Float.compare(o1.score, o2.score);
                }
            });

            ArrayList<Result> classResults = new ArrayList<>();
            for (Result result : results) {
                if (result.classIndex == i) {
                    classResults.add(result);
                }
            }

            pq.addAll(classResults);

            //NMS
            while (!pq.isEmpty()) {
                Result max = pq.poll();
                list.add(max);

                // 교집합 비율 확인하고  75% 넘기면 제거
                for (Result detection : pq) {
                    if (boxIOU(max.rectF, detection.rectF) < 0.75f) {
                        pq.add(detection);
                    }
                }
            }

        }
        return list;
    }

    // 겹치는 비율 (교집합/합집합)
    private float boxIOU(RectF a, RectF b) {
        return boxIntersection(a, b) / boxUnion(a, b);
    }

    // 교집합
    private float boxIntersection(RectF a, RectF b) {
        // x1, x2 == 각 rect 객체의 중심 x or y값, w1, w2 == 각 rect 객체의 넓이 or 높이
        float w = overlap(
                (a.left + a.right) / 2f, a.right - a.left,
                (b.left + b.right) / 2f, b.right - b.left
        );
        float h = overlap(
                (a.top + a.bottom) / 2f, a.bottom - a.top,
                (b.top + b.bottom) / 2f, b.bottom - b.top
        );

        return (w < 0 || h < 0) ? 0f : w * h;
    }

    // 합집합
    private float boxUnion(RectF a, RectF b) {
        float i = boxIntersection(a, b);
        return (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - i;
    }

    // 서로 겹치는 부분의 길이
    private float overlap(float x1, float w1, float x2, float w2) {
        float l1 = x1 - w1 / 2;
        float l2 = x2 - w2 / 2;
        float left = Math.max(l1, l2);
        float r1 = x1 + w1 / 2;
        float r2 = x2 + w2 / 2;
        float right = Math.min(r1, r2);
        return Math.max(0f, right - left);
    }

}
