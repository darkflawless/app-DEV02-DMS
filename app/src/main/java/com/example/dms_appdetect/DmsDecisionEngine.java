package com.example.dms_appdetect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Time based decision logic. No Android or MediaPipe dependency. */
public final class DmsDecisionEngine {
    private static final class EyeInterval {
        final long startMs;
        final long endMs;
        final boolean closed;

        EyeInterval(long startMs, long endMs, boolean closed) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.closed = closed;
        }
    }

    private static final class EyeObservation {
        final long timeMs;
        final boolean closed;

        EyeObservation(long timeMs, boolean closed) {
            this.timeMs = timeMs;
            this.closed = closed;
        }
    }

    private double eyeClosedThreshold = DmsConfig.EAR_CLOSED;
    private boolean eyeDetectionEnabled = true;
    private final List<double[]> calibration = new ArrayList<>();
    private final Deque<EyeObservation> eyeObservations = new ArrayDeque<>();
    private final Deque<EyeInterval> eyeIntervals = new ArrayDeque<>();
    private long calibrationStartMs = -1;
    private double neutralPitch = Double.NaN;
    private double neutralYaw = Double.NaN;
    private double neutralRoll = Double.NaN;
    private long lastTimestampMs = -1;
    private long firstEyeMs = -1;
    private long previousEyeMs = -1;
    private boolean previousEyeClosed;
    private long previousPoseMs = -1;
    private boolean previousHeadAway;
    private long headStartMs = -1;
    private long previousMouthMs = -1;
    private long yawnStartMs = -1;
    private long faceLostStartMs = -1;
    private DmsDecision.State currentState = DmsDecision.State.CALIBRATING;
    private long currentStateSinceMs = -1;
    private long clearCandidateSinceMs = -1;

    public synchronized void configureEyeDetection(boolean enabled, double threshold) {
        eyeDetectionEnabled = enabled && DmsConfig.validPersonalEarThreshold(threshold);
        if (eyeDetectionEnabled) eyeClosedThreshold = threshold;
        eyeObservations.clear();
        eyeIntervals.clear();
    }
    public synchronized void reset() {
        calibration.clear();
        calibrationStartMs = -1;
        neutralPitch = neutralYaw = neutralRoll = Double.NaN;
        lastTimestampMs = firstEyeMs = previousEyeMs = -1;
        previousEyeClosed = false;
        previousHeadAway = false;
        previousPoseMs = headStartMs = -1;
        previousMouthMs = yawnStartMs = faceLostStartMs = -1;
        eyeIntervals.clear();
        eyeObservations.clear();
        currentState = DmsDecision.State.CALIBRATING;
        currentStateSinceMs = clearCandidateSinceMs = -1;
    }

    public synchronized DmsDecision process(FeatureSample sample) {
        long t = sample.timestampMs;
        if (t <= lastTimestampMs) {
            return make(currentState, messageFor(currentState), t, 0, 0, false);
        }
        lastTimestampMs = t;

        if (Double.isNaN(neutralPitch)) {
            collectCalibration(sample);
            String guidance = sample.facePresent && !sample.poseValid
                    ? "Giữ mặt rõ và đủ gần camera" : "Nhìn thẳng để hiệu chuẩn";
            return transition(DmsDecision.State.CALIBRATING,
                    guidance + " (" + calibration.size() + "/25)", t, 0, 0);
        }

        if (!sample.facePresent) {
            if (faceLostStartMs < 0) faceLostStartMs = t;
            clearFeatureStreaks();
            if (t - faceLostStartMs >= DmsConfig.FACE_LOST_ALERT_MS) {
                return transition(DmsDecision.State.FACE_UNAVAILABLE,
                        "Không quan sát được tài xế", t, 0, 0);
            }
            return transition(DmsDecision.State.VISION_DEGRADED,
                    "Đang tìm khuôn mặt", t, 0, 0);
        }
        faceLostStartMs = -1;

        boolean eyeClosed = false;
        if (sample.eyeValid) {
            eyeClosed = eyeDetectionEnabled && sample.ear() < eyeClosedThreshold;
            if (firstEyeMs < 0) firstEyeMs = t;
            if (previousEyeMs >= 0 && t - previousEyeMs <= DmsConfig.MAX_SAMPLE_GAP_MS) {
                eyeIntervals.addLast(new EyeInterval(previousEyeMs, t, previousEyeClosed));
            }
            previousEyeMs = t;
            previousEyeClosed = eyeClosed;
            addEyeObservation(t, eyeClosed);
        } else {
            if (previousEyeMs >= 0 && t - previousEyeMs > DmsConfig.MAX_SAMPLE_GAP_MS) {
                previousEyeMs = -1;
            }
            if (!eyeObservations.isEmpty()
                    && t - eyeObservations.peekLast().timeMs
                    > DmsConfig.EYE_OBSERVATION_MAX_GAP_MS) {
                eyeObservations.clear();
            }
        }
        trimEyeWindow(t);

        boolean mouthOpen = sample.mouthValid && sample.mar > DmsConfig.MAR_YAWN;
        boolean frontalYawn = false;
        boolean headAway = false;
        String headReason = "Đầu lệch khỏi hướng quan sát";
        if (sample.poseValid) {
            double pitch = sample.pitch - neutralPitch;
            double yaw = sample.yaw - neutralYaw;
            double roll = sample.roll - neutralRoll;
            boolean frontalSides = Math.abs(yaw) <= DmsConfig.YAWN_FRONTAL_YAW_DEG
                    && Math.abs(roll) <= DmsConfig.YAWN_FRONTAL_ROLL_DEG;
            frontalYawn = frontalSides
                    && pitch >= DmsConfig.PITCH_DOWN_DURING_YAWN_DEG
                    && pitch <= DmsConfig.PITCH_UP_DURING_YAWN_DEG;
            // Opening the jaw may perturb the fitted face pose. Keep warning on a
            // clear downward tilt, but do not call a mild yawn-related shift a nod.
            double pitchDownLimit = mouthOpen && frontalSides
                    ? DmsConfig.PITCH_DOWN_DURING_YAWN_DEG : DmsConfig.PITCH_DOWN_DEG;
            double pitchUpLimit = mouthOpen && frontalSides
                    ? DmsConfig.PITCH_UP_DURING_YAWN_DEG : DmsConfig.PITCH_UP_DEG;
            if (pitch < pitchDownLimit) {
                headAway = true;
                headReason = "Đầu cúi xuống";
            } else if (pitch > pitchUpLimit) {
                headAway = true;
                headReason = "Đầu ngửa lên";
            } else if (Math.abs(yaw) > DmsConfig.YAW_DEG) {
                headAway = true;
                headReason = "Đầu quay sang bên";
            } else if (Math.abs(roll) > DmsConfig.ROLL_DEG) {
                headAway = true;
                headReason = "Đầu nghiêng";
            }
            if (!headAway || !previousHeadAway || previousPoseMs < 0
                    || t - previousPoseMs > DmsConfig.MAX_SAMPLE_GAP_MS) {
                headStartMs = -1;
            } else if (headStartMs < 0) {
                headStartMs = previousPoseMs;
            }
            previousPoseMs = t;
            previousHeadAway = headAway;
        } else {
            previousPoseMs = headStartMs = -1;
            previousHeadAway = false;
        }

        if (mouthOpen && frontalYawn) {
            if (previousMouthMs < 0 || t - previousMouthMs > DmsConfig.MAX_SAMPLE_GAP_MS) {
                yawnStartMs = t;
            }
            previousMouthMs = t;
        } else {
            yawnStartMs = previousMouthMs = -1;
        }

        double[] ratio = eyeRatio(t);
        double coverage = ratio[0];
        double closedRatio = ratio[1];
        boolean drowsy = sustainedEyeClosure(t, eyeClosed);
        boolean fatigue = sample.eyeValid
                && t - firstEyeMs >= DmsConfig.PERCLOS_WINDOW_MS
                && coverage >= DmsConfig.PERCLOS_MIN_COVERAGE
                && closedRatio >= DmsConfig.PERCLOS_WARNING;

        if (drowsy) {
            return transition(DmsDecision.State.DROWSY, "Nguy hiểm: mắt nhắm kéo dài", t,
                    coverage, closedRatio);
        }
        if (headAway && headStartMs >= 0
                && t - headStartMs >= DmsConfig.HEAD_ALERT_MS) {
            return transition(DmsDecision.State.HEAD_AWAY, headReason, t, coverage, closedRatio);
        }
        if (fatigue) {
            return transition(DmsDecision.State.FATIGUE_WARNING,
                    "Mắt nhắm nhiều: nên nghỉ ngơi", t, coverage, closedRatio);
        }
        if (mouthOpen && frontalYawn && yawnStartMs >= 0
                && t - yawnStartMs >= DmsConfig.YAWN_ALERT_MS) {
            return transition(DmsDecision.State.YAWN, "Phát hiện ngáp kéo dài", t,
                    coverage, closedRatio);
        }
        if (!sample.eyeValid || !sample.poseValid) {
            return transition(DmsDecision.State.VISION_DEGRADED,
                    "Tầm nhìn hạn chế: kiểm tra ánh sáng/kính", t, coverage, closedRatio);
        }
        if (eyeClosed) {
            return transition(DmsDecision.State.EYES_CLOSED_PENDING,
                    "Đang theo dõi thời lượng nhắm mắt", t, coverage, closedRatio);
        }
        return transition(DmsDecision.State.MONITORING, "Đang giám sát", t,
                coverage, closedRatio);
    }

    private void collectCalibration(FeatureSample sample) {
        if (!sample.facePresent || !sample.poseValid
                || (sample.mouthValid && sample.mar > DmsConfig.MAR_YAWN)) return;
        double[] pose = {sample.pitch, sample.yaw, sample.roll};
        if (!calibration.isEmpty()) {
            double[] anchor = calibration.get(0);
            if (Math.abs(pose[0] - anchor[0]) > 8
                    || Math.abs(pose[1] - anchor[1]) > 8
                    || Math.abs(pose[2] - anchor[2]) > 8) {
                calibration.clear();
                calibrationStartMs = -1;
            }
        }
        if (calibrationStartMs < 0) calibrationStartMs = sample.timestampMs;
        calibration.add(pose);
        if (calibration.size() >= 25 && sample.timestampMs - calibrationStartMs >= 1000) {
            neutralPitch = median(0);
            neutralYaw = median(1);
            neutralRoll = median(2);
            calibration.clear();
        }
    }

    private double median(int axis) {
        List<Double> values = new ArrayList<>();
        for (double[] p : calibration) values.add(p[axis]);
        Collections.sort(values);
        return values.get(values.size() / 2);
    }

    private void clearFeatureStreaks() {
        previousEyeMs = -1;
        eyeObservations.clear();
        previousPoseMs = headStartMs = -1;
        previousHeadAway = false;
        previousMouthMs = yawnStartMs = -1;
    }

    private void addEyeObservation(long t, boolean closed) {
        if (!eyeObservations.isEmpty()
                && t - eyeObservations.peekLast().timeMs
                > DmsConfig.EYE_OBSERVATION_MAX_GAP_MS) {
            eyeObservations.clear();
        }
        eyeObservations.addLast(new EyeObservation(t, closed));
        while (!eyeObservations.isEmpty()
                && t - eyeObservations.peekFirst().timeMs > DmsConfig.EYE_ALERT_WINDOW_MS) {
            eyeObservations.removeFirst();
        }
    }

    private boolean sustainedEyeClosure(long t, boolean currentlyClosed) {
        if (!currentlyClosed || eyeObservations.size() < DmsConfig.EYE_ALERT_MIN_SAMPLES) {
            return false;
        }
        int closedCount = 0;
        long firstClosedMs = -1;
        for (EyeObservation observation : eyeObservations) {
            if (observation.closed) {
                closedCount++;
                if (firstClosedMs < 0) firstClosedMs = observation.timeMs;
            }
        }
        return firstClosedMs >= 0
                && t - firstClosedMs >= DmsConfig.EYE_ALERT_MIN_SPAN_MS
                && closedCount / (double) eyeObservations.size()
                >= DmsConfig.EYE_ALERT_CLOSED_FRACTION;
    }
    private void trimEyeWindow(long t) {
        long start = t - DmsConfig.PERCLOS_WINDOW_MS;
        while (!eyeIntervals.isEmpty() && eyeIntervals.peekFirst().endMs <= start) {
            eyeIntervals.removeFirst();
        }
    }

    private double[] eyeRatio(long t) {
        long start = t - DmsConfig.PERCLOS_WINDOW_MS;
        long validMs = 0;
        long closedMs = 0;
        for (EyeInterval item : eyeIntervals) {
            long duration = item.endMs - Math.max(item.startMs, start);
            if (duration <= 0) continue;
            validMs += duration;
            if (item.closed) closedMs += duration;
        }
        return new double[] {
                validMs / (double) DmsConfig.PERCLOS_WINDOW_MS,
                validMs == 0 ? 0 : closedMs / (double) validMs
        };
    }

    private DmsDecision transition(DmsDecision.State proposed, String message, long t,
                                   double coverage, double ratio) {
        if (currentState != proposed && isAlert(currentState)
                && priority(proposed) < priority(currentState)) {
            if (clearCandidateSinceMs < 0) clearCandidateSinceMs = t;
            long recovery = currentState == DmsDecision.State.DROWSY ? 300 : 500;
            if (t - clearCandidateSinceMs < recovery) {
                return make(currentState, messageFor(currentState), t, coverage, ratio, false);
            }
        }
        clearCandidateSinceMs = -1;
        boolean entered = currentState != proposed;
        if (entered || currentStateSinceMs < 0) {
            currentState = proposed;
            currentStateSinceMs = t;
        }
        return make(currentState, message, t, coverage, ratio, entered && isAlert(proposed));
    }

    private DmsDecision make(DmsDecision.State state, String message, long t,
                             double coverage, double ratio, boolean entered) {
        return new DmsDecision(state, severity(state), message,
                currentStateSinceMs < 0 ? t : currentStateSinceMs,
                coverage, ratio, entered);
    }

    private static int priority(DmsDecision.State state) {
        if (state == DmsDecision.State.DROWSY) return 4;
        if (state == DmsDecision.State.FACE_UNAVAILABLE
                || state == DmsDecision.State.VISION_DEGRADED) return 3;
        if (state == DmsDecision.State.HEAD_AWAY) return 2;
        if (state == DmsDecision.State.FATIGUE_WARNING
                || state == DmsDecision.State.YAWN) return 1;
        return 0;
    }

    private static boolean isAlert(DmsDecision.State state) {
        return state == DmsDecision.State.DROWSY
                || state == DmsDecision.State.HEAD_AWAY
                || state == DmsDecision.State.FATIGUE_WARNING
                || state == DmsDecision.State.YAWN
                || state == DmsDecision.State.FACE_UNAVAILABLE;
    }

    private static DmsDecision.Severity severity(DmsDecision.State state) {
        if (state == DmsDecision.State.DROWSY) return DmsDecision.Severity.CRITICAL;
        if (isAlert(state)) return DmsDecision.Severity.WARNING;
        if (state == DmsDecision.State.MONITORING) return DmsDecision.Severity.NORMAL;
        return DmsDecision.Severity.INFO;
    }

    private static String messageFor(DmsDecision.State state) {
        switch (state) {
            case DROWSY: return "Nguy hiểm: mắt nhắm kéo dài";
            case HEAD_AWAY: return "Đầu lệch khỏi hướng quan sát";
            case FATIGUE_WARNING: return "Mắt nhắm nhiều: nên nghỉ ngơi";
            case YAWN: return "Phát hiện ngáp kéo dài";
            case FACE_UNAVAILABLE: return "Không quan sát được tài xế";
            default: return "Đang giám sát";
        }
    }
}


