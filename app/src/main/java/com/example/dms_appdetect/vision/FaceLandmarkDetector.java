package com.example.dms_appdetect.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

/**
 * Encapsulates MediaPipe FaceLandmarker initialization and async inference.
 * Completely decoupled from camera and UI.
 */
public final class FaceLandmarkDetector {
    private static final String TAG = "FaceLandmarkDetector";
    private static final String MODEL_NAME = "face_landmarker.task";

    public interface LandmarkListener {
        void onLandmarks(FaceLandmarkerResult result, MPImage inputImage);
        void onError(Throwable error);
    }

    private final Context context;
    private final LandmarkListener listener;
    private FaceLandmarker landmarker;
    private volatile boolean isReady;

    public FaceLandmarkDetector(@NonNull Context context, @NonNull LandmarkListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public synchronized void initialize() throws Exception {
        if (landmarker != null) return;
        BaseOptions base = BaseOptions.builder()
                .setModelAssetPath(MODEL_NAME)
                .build();
        FaceLandmarker.FaceLandmarkerOptions options =
                FaceLandmarker.FaceLandmarkerOptions.builder()
                        .setBaseOptions(base)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setNumFaces(1)
                        .setOutputFacialTransformationMatrixes(true)
                        .setResultListener((result, mpImage) -> {
                            if (isReady && listener != null) {
                                listener.onLandmarks(result, mpImage);
                            }
                        })
                        .setErrorListener(error -> {
                            Log.e(TAG, "MediaPipe error: " + error.getMessage());
                            if (listener != null) listener.onError(error);
                        })
                        .build();
        landmarker = FaceLandmarker.createFromOptions(context, options);
        isReady = true;
    }

    public synchronized void detectAsync(Bitmap bitmap, long timestampMs) {
        if (!isReady || landmarker == null) return;
        try {
            MPImage mpImage = new BitmapImageBuilder(bitmap).build();
            landmarker.detectAsync(mpImage, timestampMs);
        } catch (Exception e) {
            Log.e(TAG, "detectAsync failed", e);
            if (listener != null) listener.onError(e);
        }
    }

    public synchronized boolean isReady() {
        return isReady && landmarker != null;
    }

    public synchronized void close() {
        isReady = false;
        if (landmarker != null) {
            try {
                landmarker.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing landmarker", e);
            }
            landmarker = null;
        }
    }
}
