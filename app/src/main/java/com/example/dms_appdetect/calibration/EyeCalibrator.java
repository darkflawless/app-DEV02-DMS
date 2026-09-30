package com.example.dms_appdetect.calibration;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.dms_appdetect.DmsConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Manages the 11-second personal eye EAR calibration routine.
 * Completely decoupled from Camera and UI rendering.
 */
public final class EyeCalibrator {
    private static final String EYE_PREFS = "eye_test";
    private static final String EYE_THRESHOLD_KEY = "closed_threshold";

    public interface Listener {
        void onPhaseChanged(String phase, String title, String detail, boolean playCue);
        void onCompleted(boolean success, double threshold, String summary, String detail);
    }

    public static final class CalibrationResult {
        public final boolean success;
        public final double threshold;
        public final double openMedian;
        public final double closedMedian;
        public final int openCount;
        public final int closedCount;

        public CalibrationResult(boolean success, double threshold, double openMedian,
                                 double closedMedian, int openCount, int closedCount) {
            this.success = success;
            this.threshold = threshold;
            this.openMedian = openMedian;
            this.closedMedian = closedMedian;
            this.openCount = openCount;
            this.closedCount = closedCount;
        }
    }

    private final SharedPreferences prefs;
    private final List<Double> openEyeSamples = new ArrayList<>();
    private final List<Double> closedEyeSamples = new ArrayList<>();
    private long startMs = -1;
    private boolean active;
    private String currentPhase = "IDLE";
    private Listener listener;

    public EyeCalibrator(Context context) {
        this.prefs = context.getSharedPreferences(EYE_PREFS, Context.MODE_PRIVATE);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized void start(long nowMs) {
        this.startMs = nowMs;
        this.active = true;
        this.openEyeSamples.clear();
        this.closedEyeSamples.clear();
        this.currentPhase = "PREP";
        if (listener != null) {
            listener.onPhaseChanged("PREP", "Chuẩn bị: nhìn thẳng", "3 giây nữa bắt đầu giữ mắt MỞ", false);
        }
    }

    public synchronized void stop() {
        this.active = false;
        this.startMs = -1;
        this.currentPhase = "IDLE";
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized String getCurrentPhase() {
        return currentPhase;
    }

    public synchronized void onFrame(long timestampMs, boolean eyeValid, double ear) {
        if (!active || startMs <= 0) return;
        long elapsed = timestampMs - startMs;

        if (elapsed < 3000) {
            if (!"PREP".equals(currentPhase)) {
                currentPhase = "PREP";
                if (listener != null) {
                    listener.onPhaseChanged("PREP", "Chuẩn bị: nhìn thẳng", "Sắp bắt đầu giữ mắt MỞ", false);
                }
            }
        } else if (elapsed < 7000) {
            if (!"OPEN".equals(currentPhase)) {
                currentPhase = "OPEN";
                if (listener != null) {
                    listener.onPhaseChanged("OPEN", "GIỮ MẮT MỞ", "4 giây  •  nhìn thẳng vào camera", true);
                }
            }
            if (eyeValid && Double.isFinite(ear) && elapsed >= 3500) {
                openEyeSamples.add(ear);
            }
        } else if (elapsed < 11000) {
            if (!"CLOSED".equals(currentPhase)) {
                currentPhase = "CLOSED";
                if (listener != null) {
                    listener.onPhaseChanged("CLOSED", "NHẮM MẮT", "4 giây  •  nhắm mắt tự nhiên", true);
                }
            }
            if (eyeValid && Double.isFinite(ear) && elapsed >= 7500) {
                closedEyeSamples.add(ear);
            }
        } else {
            finishCalibration();
        }
    }

    public synchronized CalibrationResult finishCalibration() {
        if (!active) return null;
        active = false;
        currentPhase = "DONE";

        int openCount = openEyeSamples.size();
        int closedCount = closedEyeSamples.size();
        double openMedian = percentile(openEyeSamples, 0.50);
        double closedMedian = percentile(closedEyeSamples, 0.50);
        double openLow = percentile(openEyeSamples, 0.25);
        double closedHigh = percentile(closedEyeSamples, 0.75);
        double threshold = closedHigh + 0.75 * (openLow - closedHigh);

        boolean separated = openCount >= 8 && closedCount >= 8
                && openLow - closedHigh >= 0.02
                && DmsConfig.validPersonalEarThreshold(threshold);

        if (separated) {
            saveThreshold(threshold);
        } else {
            clearThreshold();
        }

        CalibrationResult result = new CalibrationResult(
                separated, threshold, openMedian, closedMedian, openCount, closedCount);

        if (listener != null) {
            String title = separated ? "Kiểm tra mắt đạt" : "EAR chưa phân biệt được mắt";
            String detail = String.format(Locale.US,
                    "Mở %.3f (%d mẫu)  •  Nhắm %.3f (%d mẫu)",
                    openMedian, openCount, closedMedian, closedCount);
            listener.onCompleted(separated, threshold, title, detail);
        }

        return result;
    }

    public double getSavedThreshold() {
        float val = prefs.getFloat(EYE_THRESHOLD_KEY, Float.NaN);
        return DmsConfig.validPersonalEarThreshold(val) ? val : Double.NaN;
    }

    public void saveThreshold(double threshold) {
        prefs.edit().putFloat(EYE_THRESHOLD_KEY, (float) threshold).apply();
    }

    public void clearThreshold() {
        prefs.edit().remove(EYE_THRESHOLD_KEY).apply();
    }

    public static double percentile(List<Double> values, double fraction) {
        if (values.isEmpty()) return Double.NaN;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get((int) Math.floor((sorted.size() - 1) * fraction));
    }
}
