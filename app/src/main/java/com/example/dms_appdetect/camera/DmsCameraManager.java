package com.example.dms_appdetect.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages CameraX lifecycle, surface binding, and frame preprocessing.
 * Decoupled from AI and UI logic.
 */
public final class DmsCameraManager {
    private static final String TAG = "DmsCameraManager";
    private static final int MAX_ANALYSIS_EDGE = 640;
    private static final long MIN_FRAME_INTERVAL_MS = 75; // Throttle ~13 FPS for AI inference

    public interface FrameListener {
        void onFrameReady(Bitmap uprightBitmap, long timestamp, double luma);
    }

    private final Context context;
    private final ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private volatile boolean isRunning;
    private long lastSubmittedMs;
    private boolean loggedResolution;

    public DmsCameraManager(Context context) {
        this.context = context.getApplicationContext();
        this.cameraExecutor = Executors.newSingleThreadExecutor();
    }

    public void start(
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull Preview.SurfaceProvider surfaceProvider,
            @NonNull FrameListener frameListener,
            @NonNull Consumer<Throwable> onError) {
        if (isRunning) return;
        isRunning = true;
        lastSubmittedMs = 0;
        loggedResolution = false;

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            if (!isRunning) return;
            try {
                cameraProvider = future.get();
                cameraProvider.unbindAll();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(surfaceProvider);

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setResolutionSelector(new ResolutionSelector.Builder()
                                .setResolutionStrategy(new ResolutionStrategy(
                                        new Size(640, 480),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                                .build())
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, image -> processImageProxy(image, frameListener));

                cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        analysis);
            } catch (Exception e) {
                Log.e(TAG, "Failed to bind CameraX", e);
                isRunning = false;
                onError.accept(e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void processImageProxy(@NonNull ImageProxy image, @NonNull FrameListener listener) {
        if (!isRunning) {
            image.close();
            return;
        }
        try {
            long frameNow = SystemClock.uptimeMillis();
            if (lastSubmittedMs > 0 && frameNow - lastSubmittedMs < MIN_FRAME_INTERVAL_MS) {
                return;
            }

            int rotation = image.getImageInfo().getRotationDegrees();
            Bitmap bitmap = image.toBitmap();
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            float scale = Math.min(1f, MAX_ANALYSIS_EDGE
                    / (float) Math.max(bitmap.getWidth(), bitmap.getHeight()));
            matrix.postScale(scale, scale);

            Bitmap upright = Bitmap.createBitmap(
                    bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(),
                    matrix, true);

            if (!loggedResolution) {
                loggedResolution = true;
                Log.i(TAG, "Analysis image " + image.getWidth() + "x" + image.getHeight()
                        + " -> " + upright.getWidth() + "x" + upright.getHeight());
            }

            long timestamp = SystemClock.uptimeMillis();
            if (timestamp <= lastSubmittedMs) timestamp = lastSubmittedMs + 1;
            lastSubmittedMs = timestamp;

            double luma = estimateLuma(upright);
            listener.onFrameReady(upright, timestamp, luma);
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing camera frame", e);
        } finally {
            image.close();
        }
    }

    public static double estimateLuma(Bitmap bitmap) {
        double sum = 0;
        int n = 0;
        int h = bitmap.getHeight();
        int w = bitmap.getWidth();
        for (int y = h / 16; y < h; y += h / 8) {
            for (int x = w / 16; x < w; x += w / 8) {
                int color = bitmap.getPixel(x, y);
                sum += 0.2126 * Color.red(color)
                        + 0.7152 * Color.green(color)
                        + 0.0722 * Color.blue(color);
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    public void stop() {
        isRunning = false;
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
            } catch (Exception e) {
                Log.w(TAG, "Error unbinding camera", e);
            }
        }
    }

    public void release() {
        stop();
        cameraExecutor.shutdown();
    }
}
