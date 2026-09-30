package com.example.dms_appdetect;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Rate limited alert output. Never queues a sound for every camera frame. */
public final class AlertPlayer {
    private final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
    private final Vibrator vibrator;
    private boolean muted;
    private long lastPlayedMs = -1;
    private DmsDecision.State lastState;

    public AlertPlayer(Context context) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public boolean isMuted() {
        return muted;
    }

    public void playTestCue() {
        if (!muted) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
    }
    public void maybePlay(DmsDecision decision, com.example.dms_appdetect.risk.RiskDecision riskDecision) {
        if (muted || decision == null || decision.severity.ordinal() < DmsDecision.Severity.WARNING.ordinal()) {
            lastState = decision != null ? decision.state : null;
            return;
        }

        // Only play if there is an active violation currently occurring
        long now = SystemClock.uptimeMillis();
        boolean isRed = riskDecision != null && riskDecision.zone == com.example.dms_appdetect.risk.RiskDecision.Zone.RED;
        long cooldown = isRed ? 1000 : 2500;

        if (!decision.newAlert && decision.state == lastState
                && lastPlayedMs >= 0 && now - lastPlayedMs < cooldown) {
            return;
        }

        lastState = decision.state;
        lastPlayedMs = now;

        int duration;
        int toneType;
        if (isRed) {
            duration = 500;
            toneType = ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK;
        } else if (riskDecision != null && riskDecision.zone == com.example.dms_appdetect.risk.RiskDecision.Zone.ORANGE) {
            duration = 300;
            toneType = ToneGenerator.TONE_PROP_BEEP2;
        } else {
            duration = 180;
            toneType = ToneGenerator.TONE_PROP_BEEP;
        }

        try {
            tone.startTone(toneType, duration);
        } catch (Exception ignored) {}

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }
    }

    public void release() {
        tone.release();
    }
}
