package com.example.dms_appdetect;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import java.util.List;

/** Geometry from Face Landmarker. Pose signs must be checked on a mounted phone. */
public final class FaceFeatureExtractor {
    private FaceFeatureExtractor() {}

    public static FeatureSample extract(FaceLandmarkerResult result, long timestampMs,
                                        int width, int height, double luma,
                                        boolean sunglassesMode) {
        if (result.faceLandmarks().isEmpty()) {
            return FeatureSample.noFace(timestampMs, luma);
        }
        List<NormalizedLandmark> p = result.faceLandmarks().get(0);
        if (p.size() <= 402) {
            return new FeatureSample(timestampMs, true, false, false, false,
                    Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, luma);
        }

        double faceWidth = dist(p, 234, 454, width, height);
        boolean faceLargeEnough = faceWidth >= Math.max(55, width * 0.11);
        boolean poseMouthFaceLargeEnough = faceWidth >= Math.max(
                DmsConfig.MIN_POSE_MOUTH_FACE_WIDTH_PX,
                width * DmsConfig.MIN_POSE_MOUTH_FACE_WIDTH_RATIO);
        double leftWidth = dist(p, 33, 133, width, height);
        double rightWidth = dist(p, 362, 263, width, height);
        boolean eyeValid = faceLargeEnough && !sunglassesMode && luma >= 35
                && leftWidth >= Math.max(5, width * 0.011)
                && rightWidth >= Math.max(5, width * 0.011)
                && eyeLandmarksInsideFrame(p);
        double earLeft = Double.NaN;
        double earRight = Double.NaN;
        if (eyeValid) {
            earLeft = (dist(p, 160, 144, width, height)
                    + dist(p, 158, 153, width, height)) / (2 * leftWidth);
            earRight = (dist(p, 385, 380, width, height)
                    + dist(p, 387, 373, width, height)) / (2 * rightWidth);
            eyeValid = Double.isFinite(earLeft) && Double.isFinite(earRight);
        }

        double mouthWidth = dist(p, 61, 291, width, height);
        boolean mouthValid = poseMouthFaceLargeEnough && luma >= 25 && mouthWidth >= Math.max(7, width * 0.014);
        double mar = mouthValid
                ? (dist(p, 13, 14, width, height)
                + dist(p, 81, 178, width, height)
                + dist(p, 311, 402, width, height)) / (3 * mouthWidth)
                : Double.NaN;

        boolean poseValid = poseMouthFaceLargeEnough && result.facialTransformationMatrixes().isPresent()
                && !result.facialTransformationMatrixes().get().isEmpty();
        double pitch = Double.NaN;
        double yaw = Double.NaN;
        double roll = Double.NaN;
        if (poseValid) {
            // MediaPipe exposes a flat 4x4 column-major matrix.
            float[] m = result.facialTransformationMatrixes().get().get(0);
            poseValid = m.length >= 16;
            if (poseValid) {
                pitch = pitchDegreesFromMatrix(m);
                yaw = Math.toDegrees(Math.asin(clamp(-m[2], -1, 1)));
                roll = Math.toDegrees(Math.atan2(m[1], m[0]));
                poseValid = Double.isFinite(pitch) && Double.isFinite(yaw)
                        && Double.isFinite(roll);
            }
        }
        return new FeatureSample(timestampMs, true, eyeValid, mouthValid, poseValid,
                earLeft, earRight, mar, pitch, yaw, roll, luma);
    }

    // In the upright front-camera image, observed head-up produced a negative
    // atan2(m[6], m[10]) and head-down a positive one. Expose up as positive.
    static double pitchDegreesFromMatrix(float[] m) {
        return -Math.toDegrees(Math.atan2(m[6], m[10]));
    }

    private static boolean eyeLandmarksInsideFrame(List<NormalizedLandmark> points) {
        int[] indices = {33, 133, 160, 144, 158, 153, 362, 263, 385, 380, 387, 373};
        for (int index : indices) {
            NormalizedLandmark point = points.get(index);
            if (point.x() < 0.02 || point.x() > 0.98
                    || point.y() < 0.02 || point.y() > 0.98) {
                return false;
            }
        }
        return true;
    }
    private static double dist(List<NormalizedLandmark> p, int a, int b, int w, int h) {
        double dx = (p.get(a).x() - p.get(b).x()) * w;
        double dy = (p.get(a).y() - p.get(b).y()) * h;
        return Math.hypot(dx, dy);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
