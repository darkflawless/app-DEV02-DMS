package com.example.dms_appdetect;

public final class DmsDecision {
    public enum State {
        CALIBRATING, MONITORING, EYES_CLOSED_PENDING, DROWSY,
        FATIGUE_WARNING, HEAD_AWAY, YAWN, FACE_UNAVAILABLE, VISION_DEGRADED
    }

    public enum Severity { NORMAL, INFO, WARNING, CRITICAL }

    public final State state;
    public final Severity severity;
    public final String message;
    public final long sinceMs;
    public final double eyeCoverage;
    public final double closedRatio;
    public final boolean newAlert;

    public DmsDecision(State state, Severity severity, String message,
                       long sinceMs, double eyeCoverage, double closedRatio,
                       boolean newAlert) {
        this.state = state;
        this.severity = severity;
        this.message = message;
        this.sinceMs = sinceMs;
        this.eyeCoverage = eyeCoverage;
        this.closedRatio = closedRatio;
        this.newAlert = newAlert;
    }
}
