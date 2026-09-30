package com.example.dms_appdetect.risk;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Locale;

/**
 * Manages a 60-second interactive test session for QA/Testers.
 * Guaranteed to execute all callbacks on Android Main UI Thread.
 */
public final class OneMinuteTestSession {
    public static final long SESSION_DURATION_MS = 60_000L;

    public interface SessionListener {
        void onTick(int secondsRemaining, double currentRisk, RiskDecision.Zone zone);
        void onCompleted(Summary summary);
    }

    public static final class Summary {
        public final int durationSec;
        public final double peakRisk;
        public final int finalSafetyScore;
        public final String rank;
        public final int yawnCount;
        public final int distractionCount;
        public final int microsleepCount;
        public final int emergencySleepCount;
        public final int orangeAlerts;
        public final int redAlerts;

        public Summary(int durationSec, double peakRisk, int finalSafetyScore, String rank,
                       int yawnCount, int distractionCount, int microsleepCount,
                       int emergencySleepCount, int orangeAlerts, int redAlerts) {
            this.durationSec = durationSec;
            this.peakRisk = peakRisk;
            this.finalSafetyScore = finalSafetyScore;
            this.rank = rank;
            this.yawnCount = yawnCount;
            this.distractionCount = distractionCount;
            this.microsleepCount = microsleepCount;
            this.emergencySleepCount = emergencySleepCount;
            this.orangeAlerts = orangeAlerts;
            this.redAlerts = redAlerts;
        }

        public String getFormattedReport() {
            return String.format(Locale.US,
                    "=== BÁO CÁO TEST 1 PHÚT ===\n"
                            + "Thời lượng: %ds\n"
                            + "Điểm rủi ro đỉnh điểm (Rmax): %.1f/100\n"
                            + "Điểm an toàn (SafetyScore): %d/100\n"
                            + "Xếp hạng: %s\n"
                            + "---------------------------\n"
                            + "Ngáp: %d | Lơ đãng: %d\n"
                            + "Vi ngủ (1.2s-2s): %d | Ngủ sâu: %d\n"
                            + "Cảnh báo Cam: %d | Cảnh báo Đỏ: %d",
                    durationSec, peakRisk, finalSafetyScore, rank,
                    yawnCount, distractionCount, microsleepCount, emergencySleepCount,
                    orangeAlerts, redAlerts);
        }
    }

    private final RiskEngine engine;
    private final SessionListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long sessionStartUptimeMs = -1L;
    private boolean active = false;
    private double peakRisk = 0.0;
    private int yawnCount = 0;
    private int distractionCount = 0;
    private int microsleepCount = 0;
    private int emergencySleepCount = 0;
    private int lastSecRemaining = -1;

    // Independent timer runnable to guarantee tick every second on Main thread
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            long now = SystemClock.uptimeMillis();
            long elapsed = now - sessionStartUptimeMs;
            long remainingMs = Math.max(0, SESSION_DURATION_MS - elapsed);
            int secRemaining = (int) Math.ceil(remainingMs / 1000.0);

            if (secRemaining != lastSecRemaining) {
                lastSecRemaining = secRemaining;
                if (listener != null) {
                    RiskDecision.Zone currentZone = engine.getRiskScore() >= 80 ? RiskDecision.Zone.RED
                            : (engine.getRiskScore() >= 60 ? RiskDecision.Zone.ORANGE
                            : (engine.getRiskScore() >= 30 ? RiskDecision.Zone.YELLOW : RiskDecision.Zone.GREEN));
                    listener.onTick(secRemaining, engine.getRiskScore(), currentZone);
                }
            }

            if (elapsed >= SESSION_DURATION_MS) {
                finishSession();
            } else {
                mainHandler.postDelayed(this, 500); // Check every 500ms for smooth 1s updates
            }
        }
    };

    public OneMinuteTestSession(RiskEngine engine, SessionListener listener) {
        this.engine = engine;
        this.listener = listener;
    }

    public synchronized void start(long nowUptimeMs) {
        this.sessionStartUptimeMs = nowUptimeMs > 0 ? nowUptimeMs : SystemClock.uptimeMillis();
        this.active = true;
        this.peakRisk = 0.0;
        this.yawnCount = 0;
        this.distractionCount = 0;
        this.microsleepCount = 0;
        this.emergencySleepCount = 0;
        this.lastSecRemaining = -1;
        engine.reset();
        engine.setTestModeCompressed(true);

        mainHandler.removeCallbacks(timerRunnable);
        mainHandler.post(timerRunnable);
    }

    public synchronized void stop() {
        if (!active) return;
        active = false;
        mainHandler.removeCallbacks(timerRunnable);
        engine.setTestModeCompressed(false);
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized void onViolation(RiskEngine.ViolationType violation) {
        if (!active || violation == null) return;
        switch (violation) {
            case YAWN:
                yawnCount++;
                break;
            case DISTRACTION:
                distractionCount++;
                break;
            case MICROSLEEP:
                microsleepCount++;
                break;
            case EMERGENCY_SLEEP:
                emergencySleepCount++;
                break;
            default:
                break;
        }
    }

    public synchronized void onTick(long nowUptimeMs, double currentRisk, RiskDecision.Zone zone) {
        if (!active || sessionStartUptimeMs <= 0) return;
        if (currentRisk > peakRisk) {
            peakRisk = currentRisk;
        }
    }

    public synchronized Summary finishSession() {
        if (!active) return null;
        active = false;
        mainHandler.removeCallbacks(timerRunnable);
        engine.setTestModeCompressed(false);

        Summary summary = new Summary(
                (int) (SESSION_DURATION_MS / 1000L),
                peakRisk,
                engine.getSafetyScore(),
                RiskEngine.calculateRank(engine.getSafetyScore()),
                yawnCount,
                distractionCount,
                microsleepCount,
                emergencySleepCount,
                engine.getOrangeCount(),
                engine.getRedCount());

        mainHandler.post(() -> {
            if (listener != null) {
                listener.onCompleted(summary);
            }
        });
        return summary;
    }
}
