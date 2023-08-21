package com.example.koreanairmlproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public class RectView extends View {

    private ArrayList<Result> results;
    private String[] classes;

    private Paint textPaint = new Paint();

    public RectView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        textPaint.setTextSize(35f);
        textPaint.setColor(Color.WHITE);
    }

    public void transformRect(ArrayList<Result> results) {
        int width = getWidth();
        int height = getHeight();
        float scaleX = (float) width / DataProcess.INPUT_SIZE;
        float scaleY = scaleX * 9f / 19f;
        float realY = width * 9f / 19f;
        float diffY = realY - height;

        for (Result result : results) {
            result.rectF.left *= scaleX;
            result.rectF.right *= scaleX;

            result.rectF.top = result.rectF.top * scaleY - (diffY / 2f);
            result.rectF.bottom = result.rectF.bottom * scaleY - (diffY / 2f);
        }

        this.results = results;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (results != null) {
            for (Result result : results) {
                canvas.drawRect(result.rectF, findPaint(result.classIndex));
                canvas.drawText(
                        classes[result.classIndex] + ", " + Math.round(result.score * 100) + "%",
                        result.rectF.left,
                        result.rectF.top,
                        textPaint
                );
            }
        }
        super.onDraw(canvas);
    }

    public void setClassLabel(String[] classes) {
        this.classes = classes;
    }

    private Paint findPaint(int classIndex) {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10.0f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeMiter(100f);

        int color;
        switch (classIndex) {
            // Add your color assignments here...
            default:
                color = Color.DKGRAY;
                break;
        }
        paint.setColor(color);
        return paint;
    }
}
