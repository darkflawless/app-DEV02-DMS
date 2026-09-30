package com.example.dms_appdetect;

/** Provisional values copied from the prototype and the v1 plan. Tune on labelled video. */
public final class DmsConfig {
    private DmsConfig() {}

    public static final String VERSION = "dms-v1.5-pitch-direction";
    public static final double EAR_CLOSED = 0.22;
    public static boolean validPersonalEarThreshold(double value) {
        return Double.isFinite(value) && value >= 0.05 && value <= 0.50;
    }
    public static final double MAR_YAWN = 0.52;
    // A small face makes mouth and head-pose estimates unreliable on this camera.
    public static final double MIN_POSE_MOUTH_FACE_WIDTH_RATIO = 0.16;
    public static final int MIN_POSE_MOUTH_FACE_WIDTH_PX = 80;
    public static final double YAWN_FRONTAL_YAW_DEG = 12.0;
    public static final double YAWN_FRONTAL_ROLL_DEG = 15.0;
    public static final double PITCH_DOWN_DURING_YAWN_DEG = -18.0;
    public static final double PITCH_UP_DURING_YAWN_DEG = 20.0;
    public static final double PITCH_DOWN_DEG = -12.0;
    public static final double PITCH_UP_DEG = 14.0;
    public static final double YAW_DEG = 16.0;
    public static final double ROLL_DEG = 20.0;
    public static final long MAX_SAMPLE_GAP_MS = 150;
    public static final long EYE_ALERT_WINDOW_MS = 1200;
    public static final long EYE_ALERT_MIN_SPAN_MS = 700;
    public static final int EYE_ALERT_MIN_SAMPLES = 5;
    public static final double EYE_ALERT_CLOSED_FRACTION = 0.80;
    public static final long EYE_OBSERVATION_MAX_GAP_MS = 900;
    public static final long HEAD_ALERT_MS = 400;
    public static final long FACE_LOST_ALERT_MS = 400;
    public static final long YAWN_ALERT_MS = 700;
    public static final long PERCLOS_WINDOW_MS = 10_000;
    public static final double PERCLOS_MIN_COVERAGE = 0.70;
    public static final double PERCLOS_WARNING = 0.40;
}
