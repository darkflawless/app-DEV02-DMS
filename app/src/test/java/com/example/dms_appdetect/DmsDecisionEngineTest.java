package com.example.dms_appdetect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public final class DmsDecisionEngineTest {
    private static FeatureSample sample(long t, double ear) {
        return new FeatureSample(t, true, true, true, true,
                ear, ear, 0.10, 0, 0, 0, 100);
    }

    private static DmsDecisionEngine calibrated() {
        DmsDecisionEngine engine = new DmsDecisionEngine();
        for (long t = 0; t <= 1250; t += 50) {
            engine.process(new FeatureSample(t, true, false, false, true,
                    Double.NaN, Double.NaN, Double.NaN, 0, 0, 0, 100));
        }
        return engine;
    }

    @Test
    public void naturalBlinkDoesNotBecomeDrowsy() {
        DmsDecisionEngine engine = calibrated();
        for (long t = 2000; t <= 2250; t += 50) {
            assertNotEquals(DmsDecision.State.DROWSY, engine.process(sample(t, 0.15)).state);
        }
        assertNotEquals(DmsDecision.State.DROWSY,
                engine.process(sample(2300, 0.32)).state);
    }

    @Test
    public void oneSecondOfObservedClosureRaisesDrowsy() {
        DmsDecisionEngine engine = calibrated();
        DmsDecision result = null;
        for (long t = 2000; t <= 3000; t += 50) {
            result = engine.process(sample(t, 0.15));
        }
        assertEquals(DmsDecision.State.DROWSY, result.state);
    }

    @Test
    public void droppedFramesStillAllowAlertWithRepeatedClosedObservations() {
        DmsDecisionEngine engine = calibrated();
        long[] times = {2000, 2050, 2150, 2450, 2500, 2600, 2720};
        DmsDecision result = null;
        for (long t : times) result = engine.process(sample(t, 0.15));
        assertEquals(DmsDecision.State.DROWSY, result.state);
    }

    @Test
    public void sparseClosedSamplesDoNotCreateAnAlert() {
        DmsDecisionEngine engine = calibrated();
        engine.process(sample(2000, 0.15));
        DmsDecision result = engine.process(sample(2800, 0.15));
        assertNotEquals(DmsDecision.State.DROWSY, result.state);
    }

    @Test
    public void aBriefOpenReadingDoesNotEraseLongClosure() {
        DmsDecisionEngine engine = calibrated();
        long[] times = {2000, 2100, 2200, 2400, 2500, 2600, 2700};
        DmsDecision result = null;
        for (long t : times) {
            result = engine.process(sample(t, t == 2400 ? 0.30 : 0.15));
        }
        assertEquals(DmsDecision.State.DROWSY, result.state);
    }

    @Test
    public void uncalibratedEyeAlarmDoesNotWarnOnLowEar() {
        DmsDecisionEngine engine = calibrated();
        engine.configureEyeDetection(false, Double.NaN);
        DmsDecision result = null;
        for (long t = 2000; t <= 3500; t += 50) {
            result = engine.process(sample(t, 0.05));
        }
        assertNotEquals(DmsDecision.State.DROWSY, result.state);
    }
    @Test
    public void personalThresholdSeparatesOpenAndClosedEyes() {
        DmsDecisionEngine engine = calibrated();
        engine.configureEyeDetection(true, 0.248);
        for (long t = 2000; t <= 3000; t += 100) {
            assertNotEquals(DmsDecision.State.DROWSY, engine.process(sample(t, 0.315)).state);
        }
        DmsDecision result = null;
        for (long t = 3100; t <= 4200; t += 100) {
            result = engine.process(sample(t, 0.050));
        }
        assertEquals(DmsDecision.State.DROWSY, result.state);
    }

    @Test
    public void invalidStoredThresholdCannotEnableEyeAlarm() {
        DmsDecisionEngine engine = calibrated();
        engine.configureEyeDetection(true, 0.9);
        DmsDecision result = null;
        for (long t = 2000; t <= 3500; t += 100) {
            result = engine.process(sample(t, 0.050));
        }
        assertNotEquals(DmsDecision.State.DROWSY, result.state);
    }
    private static FeatureSample poseAndMouth(long t, double mar, double pitch,
                                              double yaw, double roll) {
        return new FeatureSample(t, true, true, true, true,
                0.32, 0.32, mar, pitch, yaw, roll, 100);
    }

    @Test
    public void frontalYawnWithMildPitchShiftIsNotHeadDown() {
        DmsDecisionEngine engine = calibrated();
        DmsDecision result = null;
        for (long t = 2000; t <= 2800; t += 100) {
            result = engine.process(poseAndMouth(t, 0.70, -14, 0, 0));
            assertNotEquals(DmsDecision.State.HEAD_AWAY, result.state);
        }
        assertEquals(DmsDecision.State.YAWN, result.state);
    }

    @Test
    public void clearDownwardHeadMovementStillAlertsWhileMouthOpen() {
        DmsDecisionEngine engine = calibrated();
        DmsDecision result = null;
        for (long t = 2000; t <= 2800; t += 100) {
            result = engine.process(poseAndMouth(t, 0.70, -23, 0, 0));
        }
        assertEquals(DmsDecision.State.HEAD_AWAY, result.state);
    }

    @Test
    public void openMouthWhileLookingSidewaysIsNotYawn() {
        DmsDecisionEngine engine = calibrated();
        DmsDecision result = null;
        for (long t = 2000; t <= 2800; t += 100) {
            result = engine.process(poseAndMouth(t, 0.70, 0, 14, 0));
        }
        assertNotEquals(DmsDecision.State.YAWN, result.state);
    }

    @Test
    public void headDirectionLabelsMatchCorrectedPitchConvention() {
        DmsDecisionEngine down = calibrated();
        DmsDecision downResult = null;
        for (long t = 2000; t <= 2500; t += 100) {
            downResult = down.process(poseAndMouth(t, 0.10, -43.6, 0, 0));
        }
        assertEquals(DmsDecision.State.HEAD_AWAY, downResult.state);
        assertEquals("Đầu cúi xuống", downResult.message);

        DmsDecisionEngine up = calibrated();
        DmsDecision upResult = null;
        for (long t = 2000; t <= 2500; t += 100) {
            upResult = up.process(poseAndMouth(t, 0.10, 35.3, 0, 0));
        }
        assertEquals(DmsDecision.State.HEAD_AWAY, upResult.state);
        assertEquals("Đầu ngửa lên", upResult.message);
    }

    @Test
    public void frontalYawnWithMildUpwardPitchShiftIsNotHeadUp() {
        DmsDecisionEngine engine = calibrated();
        DmsDecision result = null;
        for (long t = 2000; t <= 2800; t += 100) {
            result = engine.process(poseAndMouth(t, 0.70, 16, 0, 0));
            assertNotEquals(DmsDecision.State.HEAD_AWAY, result.state);
        }
        assertEquals(DmsDecision.State.YAWN, result.state);
    }

    @Test
    public void unavailablePoseCannotProduceHeadDown() {
        DmsDecisionEngine engine = calibrated();
        DmsDecision result = null;
        for (long t = 2000; t <= 2800; t += 100) {
            result = engine.process(new FeatureSample(t, true, true, false, false,
                    0.32, 0.32, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, 100));
        }
        assertNotEquals(DmsDecision.State.HEAD_AWAY, result.state);
    }

    @Test
    public void faceLossIsUnavailableRatherThanDistraction() {
        DmsDecisionEngine engine = calibrated();
        engine.process(sample(2000, 0.32));
        engine.process(FeatureSample.noFace(2050, 20));
        DmsDecision result = engine.process(FeatureSample.noFace(2450, 20));
        assertEquals(DmsDecision.State.FACE_UNAVAILABLE, result.state);
    }
}
