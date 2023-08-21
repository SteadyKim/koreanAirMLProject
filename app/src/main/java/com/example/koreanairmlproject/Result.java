package com.example.koreanairmlproject;

import android.graphics.RectF;

public class Result {
    public int classIndex;
    public float score;
    public RectF rectF;

    public Result(int classIndex, float score, RectF rectF) {
        this.classIndex = classIndex;
        this.score = score;
        this.rectF = rectF;
    }
}
