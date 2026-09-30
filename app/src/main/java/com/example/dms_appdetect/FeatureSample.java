package com.example.dms_appdetect;

/** One analyzed camera frame. Invalid measurements are NaN, never a safe default. */
public final class FeatureSample {
    public final long timestampMs;
    public final boolean facePresent;
    public final boolean eyeValid;
    public final boolean mouthValid;
    public final boolean poseValid;
    public final double earLeft;
    public final double earRight;
    public final double mar;
    public final double pitch;
    public final double yaw;
    public final double roll;
    public final double luminance;

    public FeatureSample(long timestampMs, boolean facePresent, boolean eyeValid,
                         boolean mouthValid, boolean poseValid, double earLeft,
                         double earRight, double mar, double pitch, double yaw,
                         double roll, double luminance) {
        this.timestampMs = timestampMs;
        this.facePresent = facePresent;
        this.eyeValid = eyeValid;
        this.mouthValid = mouthValid;
        this.poseValid = poseValid;
        this.earLeft = earLeft;
        this.earRight = earRight;
        this.mar = mar;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.luminance = luminance;
    }

    public static FeatureSample noFace(long timestampMs, double luminance) {
        return new FeatureSample(timestampMs, false, false, false, false,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, luminance);
    }

    public double ear() {
        return eyeValid ? (earLeft + earRight) / 2.0 : Double.NaN;
    }
}
