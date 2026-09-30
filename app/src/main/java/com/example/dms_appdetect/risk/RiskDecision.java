package com.example.dms_appdetect.risk;

/**
 * Output data structure from RiskEngine for a 1-second update cycle.
 */
public final class RiskDecision {
    public enum Zone {
        GREEN("VÙNG XANH", "An toàn", 0xFF80E5D2),
        YELLOW("VÙNG VÀNG", "Cảnh giác Lv1", 0xFFFFE066),
        ORANGE("VÙNG CAM", "Nguy cơ cao Lv2", 0xFFFF9933),
        RED("VÙNG ĐỎ", "Khẩn cấp Lv3 HITL", 0xFFFF4D4D);

        public final String title;
        public final String description;
        public final int color;

        Zone(String title, String description, int color) {
            this.title = title;
            this.description = description;
            this.color = color;
        }
    }

    public enum AlertType {
        NONE,
        CHIME_GENTLE,   // Yellow zone: Chime nhẹ + rung
        SIREN_75DB,     // Orange zone: Còi 75dB 300ms + voice chú ý
        SIREN_100DB     // Red zone: Còi hú max 100dB liên tục + voice dừng xe
    }

    public final double riskScore;
    public final Zone zone;
    public final AlertType alertType;
    public final String violationName;
    public final double deltaR;
    public final double contextMultiplier;
    public final long holdTimeRemainingMs;
    public final boolean isHolding;
    public final String cooldownStatus;
    public final int safetyScore;
    public final String rank;
    public final boolean isNewAlert;
    public final long timestampMs;

    public RiskDecision(
            double riskScore,
            Zone zone,
            AlertType alertType,
            String violationName,
            double deltaR,
            double contextMultiplier,
            long holdTimeRemainingMs,
            boolean isHolding,
            String cooldownStatus,
            int safetyScore,
            String rank,
            boolean isNewAlert,
            long timestampMs) {
        this.riskScore = riskScore;
        this.zone = zone;
        this.alertType = alertType;
        this.violationName = violationName;
        this.deltaR = deltaR;
        this.contextMultiplier = contextMultiplier;
        this.holdTimeRemainingMs = holdTimeRemainingMs;
        this.isHolding = isHolding;
        this.cooldownStatus = cooldownStatus;
        this.safetyScore = safetyScore;
        this.rank = rank;
        this.isNewAlert = isNewAlert;
        this.timestampMs = timestampMs;
    }
}
