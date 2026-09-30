package com.example.dms_appdetect.risk;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pure Java implementation of the DriverGuard Dynamic Risk Engine & Fleet Safety Scorecard.
 * Reference: Risk-score-v2.md (Version 3.0 Production Standard).
 */
public final class RiskEngine {
    public enum ViolationType {
        NONE("Bình thường", 0),
        YAWN("Ngáp sâu / Uể oải", RiskConfig.DELTA_R_YAWN),
        DISTRACTION("Ngoảnh mặt / Nhìn điện thoại", RiskConfig.DELTA_R_DISTRACTION),
        MICROSLEEP("Vi ngủ buồng lái", RiskConfig.DELTA_R_MICROSLEEP),
        EMERGENCY_SLEEP("Ngủ gật nguy cấp", RiskConfig.DELTA_R_EMERGENCY_SLEEP);

        public final String label;
        public final double deltaR;

        ViolationType(String label, double deltaR) {
            this.label = label;
            this.deltaR = deltaR;
        }
    }

    private double riskScore = 0.0;
    private long holdUntilMs = -1L;
    private long lastUpdateMs = -1L;

    // Context factors
    private double speedFactor = RiskConfig.K_SPEED_OPEN;       // Mặc định đường trường 1.00
    private double timeFactor = RiskConfig.K_TIME_DAY;          // Mặc định ban ngày 1.00
    private double drivingDurationFactor = RiskConfig.K_DRIVE_UNDER_2H; // Mặc định < 2h 1.00

    // Customizable Hold Time & Cooldown rate for testing
    private long customHoldDurationMs = RiskConfig.HOLD_TIME_STANDARD_MS; // Mặc định 30s
    private double customDecayMultiplier = 1.0;                           // Mặc định 1.0x

    // Test mode configuration (Compressed time for 1-minute test)
    private boolean testModeCompressed = false;
    private double testModeDecayScale = 5.0; // Tua nhanh hạ nhiệt gấp 5 lần khi test 1 phút

    // Safety Score tracking
    private int safetyScore = RiskConfig.INITIAL_SAFETY_SCORE;
    private final AtomicInteger orangeCount = new AtomicInteger(0);
    private final AtomicInteger redCount = new AtomicInteger(0);
    private final AtomicInteger totalViolations = new AtomicInteger(0);
    private RiskDecision.Zone lastZone = RiskDecision.Zone.GREEN;

    public synchronized void reset() {
        riskScore = 0.0;
        holdUntilMs = -1L;
        lastUpdateMs = -1L;
        safetyScore = RiskConfig.INITIAL_SAFETY_SCORE;
        orangeCount.set(0);
        redCount.set(0);
        totalViolations.set(0);
        lastZone = RiskDecision.Zone.GREEN;
    }

    public synchronized void setContextFactors(double speedFactor, double timeFactor, double drivingDurationFactor) {
        this.speedFactor = speedFactor;
        this.timeFactor = timeFactor;
        this.drivingDurationFactor = drivingDurationFactor;
    }

    public synchronized void setSpeedPreset(double speedK) {
        this.speedFactor = speedK;
    }

    public synchronized void setTimePreset(double timeK) {
        this.timeFactor = timeK;
    }

    public synchronized void setDrivingDurationHours(double hours) {
        if (hours < 2.0) {
            this.drivingDurationFactor = RiskConfig.K_DRIVE_UNDER_2H;
        } else if (hours <= 4.0) {
            this.drivingDurationFactor = RiskConfig.K_DRIVE_2H_TO_4H;
        } else {
            this.drivingDurationFactor = RiskConfig.K_DRIVE_OVER_4H;
        }
    }

    public synchronized void setCustomHoldDurationMs(long durationMs) {
        this.customHoldDurationMs = durationMs;
    }

    public synchronized long getCustomHoldDurationMs() {
        return customHoldDurationMs;
    }

    public synchronized void setCustomDecayMultiplier(double multiplier) {
        this.customDecayMultiplier = multiplier;
    }

    public synchronized double getCustomDecayMultiplier() {
        return customDecayMultiplier;
    }

    public synchronized void setTestModeCompressed(boolean compressed) {
        this.testModeCompressed = compressed;
    }

    public double getContextMultiplier() {
        return speedFactor * timeFactor * drivingDurationFactor;
    }

    /**
     * Update the Risk Engine state at a given timestamp with a detected violation.
     */
    public synchronized RiskDecision update(long timestampMs, ViolationType violation) {
        if (lastUpdateMs < 0) {
            lastUpdateMs = timestampMs;
        }
        long dtMs = Math.max(0, timestampMs - lastUpdateMs);
        long prevUpdateMs = lastUpdateMs;
        lastUpdateMs = timestampMs;

        double kContext = getContextMultiplier();
        double deltaR = violation != null ? violation.deltaR : 0.0;
        boolean hasViolation = violation != null && violation != ViolationType.NONE;

        if (hasViolation) {
            totalViolations.incrementAndGet();
            double addedRisk = deltaR * kContext;
            riskScore = Math.min(100.0, riskScore + addedRisk);

            // Set Hold Time (Uses compressed 5s for 1-minute test, or tester configured hold time)
            long holdDuration = testModeCompressed
                    ? RiskConfig.HOLD_TIME_TEST_MS
                    : customHoldDurationMs;
            holdUntilMs = timestampMs + holdDuration;
        } else {
            // No violation: check Hold Time & Cooldown
            if (timestampMs >= holdUntilMs) {
                // Only decay for the time that elapsed AFTER holdUntilMs
                long cooldownStartMs = Math.max(prevUpdateMs, holdUntilMs);
                long cooldownDtMs = Math.max(0, timestampMs - cooldownStartMs);
                double dtSec = cooldownDtMs / 1000.0;

                double decayScale = testModeCompressed ? testModeDecayScale : customDecayMultiplier;
                double ratePerSec = (riskScore >= 60.0
                        ? RiskConfig.COOLDOWN_DANGER_PER_SEC
                        : RiskConfig.COOLDOWN_SAFE_PER_SEC) * decayScale;

                double cooldownAmount = ratePerSec * dtSec;
                riskScore = Math.max(0.0, riskScore - cooldownAmount);
            }
        }

        // Determine current Zone
        RiskDecision.Zone currentZone;
        if (riskScore >= 80.0) {
            currentZone = RiskDecision.Zone.RED;
        } else if (riskScore >= 60.0) {
            currentZone = RiskDecision.Zone.ORANGE;
        } else if (riskScore >= 30.0) {
            currentZone = RiskDecision.Zone.YELLOW;
        } else {
            currentZone = RiskDecision.Zone.GREEN;
        }

        // Check Zone transition and SafetyScore penalties
        boolean isNewAlert = false;
        RiskDecision.AlertType alertType = RiskDecision.AlertType.NONE;

        if (currentZone != lastZone) {
            if (currentZone == RiskDecision.Zone.RED) {
                isNewAlert = true;
                alertType = RiskDecision.AlertType.SIREN_100DB;
                redCount.incrementAndGet();
                safetyScore = Math.max(0, safetyScore + RiskConfig.PENALTY_RED);
            } else if (currentZone == RiskDecision.Zone.ORANGE && lastZone != RiskDecision.Zone.RED) {
                isNewAlert = true;
                alertType = RiskDecision.AlertType.SIREN_75DB;
                orangeCount.incrementAndGet();
                safetyScore = Math.max(0, safetyScore + RiskConfig.PENALTY_ORANGE);
            } else if (currentZone == RiskDecision.Zone.YELLOW && lastZone == RiskDecision.Zone.GREEN) {
                isNewAlert = true;
                alertType = RiskDecision.AlertType.CHIME_GENTLE;
            }
            lastZone = currentZone;
        }

        long holdRemainingMs = Math.max(0, holdUntilMs - timestampMs);
        boolean isHolding = holdRemainingMs > 0;
        String cooldownStatus;
        if (riskScore <= 0.05) {
            riskScore = 0.0;
            cooldownStatus = "🟢 Tỉnh táo (R=0)";
        } else if (isHolding) {
            long sec = (long) Math.ceil(holdRemainingMs / 1000.0);
            cooldownStatus = String.format(Locale.US, "⏳ Giữ nhiệt: còn %ds (chưa giảm)", sec);
        } else {
            double decayScale = testModeCompressed ? testModeDecayScale : customDecayMultiplier;
            double ratePer10s = (riskScore >= 60.0 ? 2.0 : 4.0) * decayScale;
            cooldownStatus = String.format(Locale.US, "❄️ Đang hạ nhiệt (-%.1fđ/10s)", ratePer10s);
        }

        String rank = calculateRank(safetyScore);

        return new RiskDecision(
                riskScore,
                currentZone,
                alertType,
                hasViolation ? violation.label : null,
                deltaR,
                kContext,
                holdRemainingMs,
                isHolding,
                cooldownStatus,
                safetyScore,
                rank,
                isNewAlert,
                timestampMs);
    }

    public static String calculateRank(int score) {
        if (score >= 90) return "Hạng A (Bác tài Tinh Hoa)";
        if (score >= 75) return "Hạng B (Đạt chuẩn An toàn)";
        if (score >= 60) return "Hạng C (Cần Cải Thiện)";
        return "Hạng D (Nguy Cơ Cao)";
    }

    public synchronized double getRiskScore() {
        return riskScore;
    }

    public synchronized int getSafetyScore() {
        return safetyScore;
    }

    public synchronized int getOrangeCount() {
        return orangeCount.get();
    }

    public synchronized int getRedCount() {
        return redCount.get();
    }

    public synchronized int getTotalViolations() {
        return totalViolations.get();
    }
}
