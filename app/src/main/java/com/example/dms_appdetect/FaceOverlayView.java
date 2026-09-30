package com.example.dms_appdetect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

/** Diagnostic eye and mouth traces on the front camera preview. */
public final class FaceOverlayView extends View {
    private static final int[] LEFT_EYE = {33, 160, 158, 133, 153, 144, 33};
    private static final int[] RIGHT_EYE = {362, 385, 387, 263, 373, 380, 362};
    private static final int[] MOUTH = {61, 81, 13, 311, 291, 402, 14, 178, 61};
    private final Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mouthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] xs;
    private float[] ys;
    private int imageWidth;
    private int imageHeight;

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        eyePaint.setColor(Color.rgb(80, 245, 220));
        eyePaint.setStyle(Paint.Style.STROKE);
        eyePaint.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
        mouthPaint.setColor(Color.rgb(255, 190, 100));
        mouthPaint.setStyle(Paint.Style.STROKE);
        mouthPaint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
    }

    public void setLandmarks(List<NormalizedLandmark> landmarks, int width, int height) {
        if (landmarks == null || landmarks.size() <= 402) {
            xs = ys = null;
        } else {
            xs = new float[landmarks.size()];
            ys = new float[landmarks.size()];
            for (int i = 0; i < landmarks.size(); i++) {
                xs[i] = landmarks.get(i).x();
                ys[i] = landmarks.get(i).y();
            }
            imageWidth = width;
            imageHeight = height;
        }
        invalidate();
    }

    private boolean overlayEnabled = true;

    public void setOverlayEnabled(boolean enabled) {
        this.overlayEnabled = enabled;
        invalidate();
    }

    public boolean isOverlayEnabled() {
        return overlayEnabled;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!overlayEnabled || xs == null || imageWidth <= 0 || imageHeight <= 0) return;
        // CameraX PreviewView defaults to FILL_CENTER; its front preview is mirrored.
        float scale = Math.max(getWidth() / (float) imageWidth,
                getHeight() / (float) imageHeight);
        float left = (getWidth() - imageWidth * scale) / 2;
        float top = (getHeight() - imageHeight * scale) / 2;
        trace(canvas, LEFT_EYE, eyePaint, scale, left, top);
        trace(canvas, RIGHT_EYE, eyePaint, scale, left, top);
        trace(canvas, MOUTH, mouthPaint, scale, left, top);
    }

    private void trace(Canvas canvas, int[] points, Paint paint,
                       float scale, float left, float top) {
        Path path = new Path();
        for (int i = 0; i < points.length; i++) {
            int index = points[i];
            float x = getWidth() - (left + xs[index] * imageWidth * scale);
            float y = top + ys[index] * imageHeight * scale;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
    }
}
