package com.example.dms_appdetect.risk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for RiskEngine strictly validating formulas from Risk-score-v2.md.
 */
public final class RiskEngineTest {
    private RiskEngine engine;

    @Before
    public void setUp() {
        engine = new RiskEngine();
    }

    @Test
    public void initialScoreIsZeroAndSafe() {
        RiskDecision d = engine.update(1000L, RiskEngine.ViolationType.NONE);
        assertEquals(0.0, d.riskScore, 0.001);
        assertEquals(RiskDecision.Zone.GREEN, d.zone);
        assertEquals(100, d.safetyScore);
        assertEquals(RiskDecision.AlertType.NONE, d.alertType);
        assertTrue(d.rank.contains("Hạng A"));
    }

    /**
     * Ví dụ 1 trong Risk-score-v2.md:
     * Kẹt xe (v < 5km/h => K=0.25), 09:30 sáng (K=1.00), lái 45p (K=1.00) => K_bối_cảnh = 0.25.
     * Ngáp >= 2s (+15đ).
     * R(t) = 0 + 15 * 0.25 = 3.75 điểm => VÙNG XANH, không cảnh báo còi.
     */
    @Test
    public void testExample1TrafficJamYawnStaysGreen() {
        engine.setSpeedPreset(RiskConfig.K_SPEED_JAM); // 0.25
        engine.setTimePreset(RiskConfig.K_TIME_DAY);     // 1.00
        engine.setDrivingDurationHours(0.75);           // 1.00

        assertEquals(0.25, engine.getContextMultiplier(), 0.001);

        RiskDecision d = engine.update(1000L, RiskEngine.ViolationType.YAWN);
        assertEquals(3.75, d.riskScore, 0.01);
        assertEquals(RiskDecision.Zone.GREEN, d.zone);
        assertEquals(RiskDecision.AlertType.NONE, d.alertType);
        assertEquals(100, d.safetyScore); // Không bị trừ điểm
    }

    /**
     * Ví dụ 2 trong Risk-score-v2.md:
     * Cao tốc (K=1.60), 01:45 sáng (K=1.35), lái 3.5h (K=1.20) => K_bối_cảnh = 1.6 * 1.35 * 1.2 = 2.592.
     * Điểm trước đó: 25.
     * Bác tài vi ngủ 1.5s (+45đ).
     * R(t) = min(100, 25 + 45 * 2.592) = min(100, 25 + 116.64) = 100 điểm => VÙNG ĐỎ, còi hú Max!
     */
    @Test
    public void testExample2HighwayLateNightMicrosleepSpikesToRed() {
        engine.setSpeedPreset(RiskConfig.K_SPEED_HIGHWAY); // 1.60
        engine.setTimePreset(RiskConfig.K_TIME_NIGHT);      // 1.35
        engine.setDrivingDurationHours(3.5);              // 1.20

        assertEquals(2.592, engine.getContextMultiplier(), 0.001);

        // Giả lập điểm trước đó = 25 bằng cách inject một vi phạm hoặc offset
        // 25 / 2.592 = 9.645
        // Ta chạy trực tiếp microsleep: 45 * 2.592 = 116.64 > 100
        RiskDecision d = engine.update(1000L, RiskEngine.ViolationType.MICROSLEEP);
        assertEquals(100.0, d.riskScore, 0.001);
        assertEquals(RiskDecision.Zone.RED, d.zone);
        assertEquals(RiskDecision.AlertType.SIREN_100DB, d.alertType);
        assertTrue(d.isNewAlert);
        assertEquals(90, d.safetyScore); // Trừ 10 điểm đỏ
    }

    /**
     * Kiểm tra tính giữ nhiệt (Hold Time = 30s):
     * Sau vi phạm, điểm số không được hạ ngay trong 30 giây tiếp theo.
     */
    @Test
    public void testHoldTimePreservesScore() {
        engine.setSpeedPreset(RiskConfig.K_SPEED_OPEN); // 1.0
        engine.setTimePreset(RiskConfig.K_TIME_DAY);    // 1.0
        engine.setDrivingDurationHours(1.0);           // 1.0

        // T0: Distraction (+25)
        RiskDecision d1 = engine.update(1000L, RiskEngine.ViolationType.DISTRACTION);
        assertEquals(25.0, d1.riskScore, 0.001);

        // T + 10s: Không vi phạm nhưng vẫn trong Hold Time 30s => điểm giữ nguyên 25
        RiskDecision d2 = engine.update(11000L, RiskEngine.ViolationType.NONE);
        assertEquals(25.0, d2.riskScore, 0.001);

        // T + 29s: Vẫn trong Hold Time => điểm vẫn giữ nguyên 25
        RiskDecision d3 = engine.update(30000L, RiskEngine.ViolationType.NONE);
        assertEquals(25.0, d3.riskScore, 0.001);
    }

    /**
     * Kiểm tra hạ nhiệt sau khi hết Hold Time:
     * Vùng an toàn (R < 60): Giảm 4 điểm mỗi 10s (0.4đ/s).
     */
    @Test
    public void testCooldownAfterHoldTime() {
        engine.setSpeedPreset(RiskConfig.K_SPEED_OPEN);
        engine.setTimePreset(RiskConfig.K_TIME_DAY);
        engine.setDrivingDurationHours(1.0);

        // T0 = 1000L: Ngáp (+15)
        engine.update(1000L, RiskEngine.ViolationType.YAWN);

        // Hold time đến 31000L (30s)
        // Đến 41000L (đã qua 10s cooldown): giảm 4 điểm từ 15 => 11 điểm
        RiskDecision d = engine.update(41000L, RiskEngine.ViolationType.NONE);
        assertEquals(11.0, d.riskScore, 0.1);
    }

    /**
     * Kiểm tra cụm nguy cơ dồn dập (Fatigue Clustering):
     * Ngáp (+15) -> Lơ đãng (+25) -> Vi ngủ (+45) dồn dập trong 30s.
     */
    @Test
    public void testFatigueClusteringEscalation() {
        engine.setSpeedPreset(RiskConfig.K_SPEED_OPEN);
        engine.setTimePreset(RiskConfig.K_TIME_DAY);
        engine.setDrivingDurationHours(1.0);

        // Bước 1: Ngáp (+15) => R = 15 (Vùng Xanh)
        RiskDecision d1 = engine.update(1000L, RiskEngine.ViolationType.YAWN);
        assertEquals(15.0, d1.riskScore, 0.001);
        assertEquals(RiskDecision.Zone.GREEN, d1.zone);

        // Bước 2: 5s sau lơ đãng (+25) => R = 40 (Vùng Vàng - Chime)
        RiskDecision d2 = engine.update(6000L, RiskEngine.ViolationType.DISTRACTION);
        assertEquals(40.0, d2.riskScore, 0.001);
        assertEquals(RiskDecision.Zone.YELLOW, d2.zone);
        assertEquals(RiskDecision.AlertType.CHIME_GENTLE, d2.alertType);

        // Bước 3: 5s sau vi ngủ (+45) => R = 85 (Vùng Đỏ - Còi hú Max)
        RiskDecision d3 = engine.update(11000L, RiskEngine.ViolationType.MICROSLEEP);
        assertEquals(85.0, d3.riskScore, 0.001);
        assertEquals(RiskDecision.Zone.RED, d3.zone);
        assertEquals(RiskDecision.AlertType.SIREN_100DB, d3.alertType);
    }
}
