package com.example.dms_appdetect.risk;

/**
 * Standard configuration for the Dynamic Risk Engine & Fleet Safety Scorecard.
 * Reference: Risk-score-v2.md (Version 3.0 Production Standard).
 */
public final class RiskConfig {
    private RiskConfig() {}

    // Violation Risk Points (Delta R)
    public static final double DELTA_R_YAWN = 15.0;            // Nhóm 1: Mệt mỏi nhẹ (Ngáp >= 2.0s)
    public static final double DELTA_R_DISTRACTION = 25.0;     // Nhóm 2: Mất tập trung (Lệch đầu/nhìn điện thoại >= 2.0s)
    public static final double DELTA_R_MICROSLEEP = 45.0;      // Nhóm 3: Vi ngủ buồng lái (1.2s - 2.0s hoặc gục đầu)
    public static final double DELTA_R_EMERGENCY_SLEEP = 70.0; // Nhóm 4: Ngủ gật nguy cấp (>= 2.0s)

    // Context Multipliers: Speed (K_van_toc)
    public static final double K_SPEED_JAM = 0.25;      // < 5 km/h (Chống báo nhầm khi kẹt xe)
    public static final double K_SPEED_URBAN = 0.80;    // 5 - 35 km/h
    public static final double K_SPEED_OPEN = 1.00;     // 35 - 70 km/h (Đường trường)
    public static final double K_SPEED_HIGHWAY = 1.60;  // >= 70 km/h (Cao tốc nguy hiểm)

    // Context Multipliers: Time of Day (K_khung_gio)
    public static final double K_TIME_DAY = 1.00;       // Ban ngày
    public static final double K_TIME_AFTERNOON = 1.15; // Đầu giờ chiều (Tester preset)
    public static final double K_TIME_NIGHT = 1.35;     // Đêm khuya (00:00 - 05:30)

    // Context Multipliers: Driving Duration (K_thoi_gian_lai)
    public static final double K_DRIVE_UNDER_2H = 1.00; // < 2 giờ
    public static final double K_DRIVE_2H_TO_4H = 1.20; // 2 - 4 giờ
    public static final double K_DRIVE_OVER_4H = 1.50;  // > 4 giờ

    // Hold Time & Cooldown
    public static final long HOLD_TIME_STANDARD_MS = 30_000L; // Giữ nhiệt 30s sau vi phạm
    public static final long HOLD_TIME_TEST_MS = 5_000L;      // Giữ nhiệt 5s trong chế độ test 1 phút
    public static final double COOLDOWN_DANGER_PER_SEC = 0.20; // R >= 60: -2 điểm mỗi 10s (-0.2đ/s)
    public static final double COOLDOWN_SAFE_PER_SEC = 0.40;   // R < 60: -4 điểm mỗi 10s (-0.4đ/s)

    // Risk Zones Thresholds
    public static final double ZONE_GREEN_MAX = 29.99;
    public static final double ZONE_YELLOW_MAX = 59.99;
    public static final double ZONE_ORANGE_MAX = 79.99;
    public static final double ZONE_RED_MAX = 100.0;

    // Safety Scorecard
    public static final int INITIAL_SAFETY_SCORE = 100;
    public static final int PENALTY_ORANGE = -3;
    public static final int PENALTY_RED = -10;
    public static final int PENALTY_DRIVE_OVER_4H = -10;
    public static final int REWARD_SAFE_60MIN = 2;
    public static final int REWARD_REST_20MIN = 3;
    public static final int REWARD_CLEAN_SHIFT = 5;
}
