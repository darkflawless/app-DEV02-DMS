package com.example.dms_appdetect.tuning;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.example.dms_appdetect.FaceOverlayView;
import com.example.dms_appdetect.risk.OneMinuteTestSession;
import com.example.dms_appdetect.risk.RiskConfig;
import com.example.dms_appdetect.risk.RiskDecision;
import com.example.dms_appdetect.risk.RiskEngine;

import java.util.Locale;

/**
 * Controller for tester tuning controls:
 * - Speed presets (Jam, Urban, Open, Highway)
 * - Time of day presets (Day, Afternoon, Night)
 * - Continuous driving duration slider (0h - 6h)
 * - Custom Hold Time (Giữ nhiệt) slider (3s - 30s)
 * - Cooldown decay multiplier buttons (1x, 3x, 5x)
 * - Face mesh overlay toggle
 * - 1-Minute Test Mode with countdown and scorecard
 */
public final class TuningController {
    public interface TuningListener {
        void onTestSessionToggled(boolean started);
    }

    private final Context context;
    private final RiskEngine riskEngine;
    private final FaceOverlayView faceOverlayView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OneMinuteTestSession testSession;
    private TuningListener listener;

    // UI elements
    private Button btnSpeedJam;
    private Button btnSpeedUrban;
    private Button btnSpeedOpen;
    private Button btnSpeedHighway;

    private Button btnTimeDay;
    private Button btnTimeAfternoon;
    private Button btnTimeNight;

    private SeekBar seekDrivingHours;
    private TextView tvDrivingHours;

    private SeekBar seekHoldTime;
    private TextView tvHoldTime;

    private Button btnDecay1x;
    private Button btnDecay3x;
    private Button btnDecay5x;

    private SwitchCompat switchOverlay;
    private SwitchCompat switchFastDecay;
    private Button btnOneMinuteTest;
    private TextView tvTestCountdown;
    private View btnToggleBottomPanel;
    private TextView tvToggleBottomText;
    private View bottomScrollView;

    public TuningController(Context context, RiskEngine riskEngine, FaceOverlayView overlayView) {
        this.context = context;
        this.riskEngine = riskEngine;
        this.faceOverlayView = overlayView;
    }

    public void setListener(TuningListener listener) {
        this.listener = listener;
    }

    public void bindViews(
            View btnToggleBottomPanel, TextView tvToggleBottomText, View bottomScrollView,
            Button btnSpeedJam, Button btnSpeedUrban, Button btnSpeedOpen, Button btnSpeedHighway,
            Button btnTimeDay, Button btnTimeAfternoon, Button btnTimeNight,
            SeekBar seekDrivingHours, TextView tvDrivingHours,
            SeekBar seekHoldTime, TextView tvHoldTime,
            Button btnDecay1x, Button btnDecay3x, Button btnDecay5x,
            SwitchCompat switchOverlay, SwitchCompat switchFastDecay,
            Button btnOneMinuteTest, TextView tvTestCountdown) {

        this.btnToggleBottomPanel = btnToggleBottomPanel;
        this.tvToggleBottomText = tvToggleBottomText;
        this.bottomScrollView = bottomScrollView;
        this.btnSpeedJam = btnSpeedJam;
        this.btnSpeedUrban = btnSpeedUrban;
        this.btnSpeedOpen = btnSpeedOpen;
        this.btnSpeedHighway = btnSpeedHighway;
        this.btnTimeDay = btnTimeDay;
        this.btnTimeAfternoon = btnTimeAfternoon;
        this.btnTimeNight = btnTimeNight;
        this.seekDrivingHours = seekDrivingHours;
        this.tvDrivingHours = tvDrivingHours;
        this.seekHoldTime = seekHoldTime;
        this.tvHoldTime = tvHoldTime;
        this.btnDecay1x = btnDecay1x;
        this.btnDecay3x = btnDecay3x;
        this.btnDecay5x = btnDecay5x;
        this.switchOverlay = switchOverlay;
        this.switchFastDecay = switchFastDecay;
        this.btnOneMinuteTest = btnOneMinuteTest;
        this.tvTestCountdown = tvTestCountdown;

        if (btnToggleBottomPanel != null && bottomScrollView != null && tvToggleBottomText != null) {
            btnToggleBottomPanel.setOnClickListener(v -> {
                boolean isVisible = bottomScrollView.getVisibility() == View.VISIBLE;
                bottomScrollView.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                tvToggleBottomText.setText(isVisible
                        ? "▲ Hiện bảng điều khiển"
                        : "▼ Ẩn bảng điều khiển (Xem full mặt)");
            });
        }

        setupSpeedListeners();
        setupTimeListeners();
        setupDrivingHoursListener();
        setupHoldTimeListener();
        setupDecayListeners();
        setupOverlayToggle();
        setupFastDecayToggle();
        setupOneMinuteTestButton();
    }

    private void setupSpeedListeners() {
        View.OnClickListener click = v -> {
            resetSpeedButtonHighlights();
            if (v == btnSpeedJam) {
                riskEngine.setSpeedPreset(RiskConfig.K_SPEED_JAM);
                highlightButton(btnSpeedJam);
            } else if (v == btnSpeedUrban) {
                riskEngine.setSpeedPreset(RiskConfig.K_SPEED_URBAN);
                highlightButton(btnSpeedUrban);
            } else if (v == btnSpeedOpen) {
                riskEngine.setSpeedPreset(RiskConfig.K_SPEED_OPEN);
                highlightButton(btnSpeedOpen);
            } else if (v == btnSpeedHighway) {
                riskEngine.setSpeedPreset(RiskConfig.K_SPEED_HIGHWAY);
                highlightButton(btnSpeedHighway);
            }
        };
        btnSpeedJam.setOnClickListener(click);
        btnSpeedUrban.setOnClickListener(click);
        btnSpeedOpen.setOnClickListener(click);
        btnSpeedHighway.setOnClickListener(click);
        highlightButton(btnSpeedOpen); // Default: Open road (1.0x)
    }

    private void setupTimeListeners() {
        View.OnClickListener click = v -> {
            resetTimeButtonHighlights();
            if (v == btnTimeDay) {
                riskEngine.setTimePreset(RiskConfig.K_TIME_DAY);
                highlightButton(btnTimeDay);
            } else if (v == btnTimeAfternoon) {
                riskEngine.setTimePreset(RiskConfig.K_TIME_AFTERNOON);
                highlightButton(btnTimeAfternoon);
            } else if (v == btnTimeNight) {
                riskEngine.setTimePreset(RiskConfig.K_TIME_NIGHT);
                highlightButton(btnTimeNight);
            }
        };
        btnTimeDay.setOnClickListener(click);
        btnTimeAfternoon.setOnClickListener(click);
        btnTimeNight.setOnClickListener(click);
        highlightButton(btnTimeDay); // Default: Day (1.0x)
    }

    private void setupDrivingHoursListener() {
        seekDrivingHours.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double hours = progress / 2.0; // 0 to 12 steps -> 0.0 to 6.0 hours
                riskEngine.setDrivingDurationHours(hours);
                double k = hours < 2.0 ? 1.0 : (hours <= 4.0 ? 1.2 : 1.5);
                tvDrivingHours.setText(String.format(Locale.US, "Thời gian lái: %.1fh (K=%.1fx)", hours, k));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupHoldTimeListener() {
        if (seekHoldTime == null || tvHoldTime == null) return;
        // Default progress 7 -> 7 + 3 = 10s (or 30s)
        int defaultHoldSec = 10;
        riskEngine.setCustomHoldDurationMs(defaultHoldSec * 1000L);
        tvHoldTime.setText(String.format(Locale.US, "Thời gian giữ nhiệt: %ds (chờ trước khi hạ)", defaultHoldSec));

        seekHoldTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int sec = progress + 3; // 3 to 30 seconds
                riskEngine.setCustomHoldDurationMs(sec * 1000L);
                tvHoldTime.setText(String.format(Locale.US, "Thời gian giữ nhiệt: %ds (chờ trước khi hạ)", sec));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupDecayListeners() {
        if (btnDecay1x == null || btnDecay3x == null || btnDecay5x == null) return;
        View.OnClickListener click = v -> {
            resetDecayButtonHighlights();
            if (v == btnDecay1x) {
                riskEngine.setCustomDecayMultiplier(1.0);
                highlightButton(btnDecay1x);
            } else if (v == btnDecay3x) {
                riskEngine.setCustomDecayMultiplier(3.0);
                highlightButton(btnDecay3x);
            } else if (v == btnDecay5x) {
                riskEngine.setCustomDecayMultiplier(5.0);
                highlightButton(btnDecay5x);
            }
        };
        btnDecay1x.setOnClickListener(click);
        btnDecay3x.setOnClickListener(click);
        btnDecay5x.setOnClickListener(click);
        highlightButton(btnDecay1x); // Default 1x
    }

    private void setupOverlayToggle() {
        switchOverlay.setChecked(faceOverlayView.isOverlayEnabled());
        switchOverlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            faceOverlayView.setOverlayEnabled(isChecked);
        });
    }

    private void setupFastDecayToggle() {
        switchFastDecay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            riskEngine.setTestModeCompressed(isChecked);
        });
    }

    private void setupOneMinuteTestButton() {
        testSession = new OneMinuteTestSession(riskEngine, new OneMinuteTestSession.SessionListener() {
            @Override
            public void onTick(int secondsRemaining, double currentRisk, RiskDecision.Zone zone) {
                mainHandler.post(() -> {
                    if (tvTestCountdown != null) {
                        tvTestCountdown.setText(String.format(Locale.US, "00:%02d", secondsRemaining));
                        tvTestCountdown.setTextColor(zone.color);
                    }
                });
            }

            @Override
            public void onCompleted(OneMinuteTestSession.Summary summary) {
                mainHandler.post(() -> {
                    if (btnOneMinuteTest != null) {
                        btnOneMinuteTest.setText("⏱️ Bắt đầu Test 1 Phút");
                    }
                    if (tvTestCountdown != null) {
                        tvTestCountdown.setText("Hoàn thành!");
                    }
                    if (listener != null) {
                        listener.onTestSessionToggled(false);
                    }
                    showScorecardDialog(summary);
                });
            }
        });

        btnOneMinuteTest.setOnClickListener(v -> {
            if (testSession.isActive()) {
                testSession.stop();
                btnOneMinuteTest.setText("⏱️ Bắt đầu Test 1 Phút");
                tvTestCountdown.setText("--:--");
                if (listener != null) listener.onTestSessionToggled(false);
            } else {
                testSession.start(SystemClock.uptimeMillis());
                btnOneMinuteTest.setText("Dừng Test 1 Phút");
                tvTestCountdown.setText("01:00");
                if (listener != null) listener.onTestSessionToggled(true);
            }
        });
    }

    private void showScorecardDialog(OneMinuteTestSession.Summary summary) {
        if (context instanceof Activity && ((Activity) context).isFinishing()) {
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle("🏆 BẢNG ĐIỂM TEST 1 PHÚT")
                .setMessage(summary.getFormattedReport())
                .setPositiveButton("Đã hiểu", (dialog, which) -> dialog.dismiss())
                .setNeutralButton("Thiết lập lại (Reset)", (dialog, which) -> {
                    riskEngine.reset();
                    dialog.dismiss();
                })
                .show();
    }

    public OneMinuteTestSession getTestSession() {
        return testSession;
    }

    private void resetSpeedButtonHighlights() {
        int defaultColor = 0xFF37474F;
        btnSpeedJam.setBackgroundColor(defaultColor);
        btnSpeedUrban.setBackgroundColor(defaultColor);
        btnSpeedOpen.setBackgroundColor(defaultColor);
        btnSpeedHighway.setBackgroundColor(defaultColor);
    }

    private void resetTimeButtonHighlights() {
        int defaultColor = 0xFF37474F;
        btnTimeDay.setBackgroundColor(defaultColor);
        btnTimeAfternoon.setBackgroundColor(defaultColor);
        btnTimeNight.setBackgroundColor(defaultColor);
    }

    private void resetDecayButtonHighlights() {
        int defaultColor = 0xFF37474F;
        if (btnDecay1x != null) btnDecay1x.setBackgroundColor(defaultColor);
        if (btnDecay3x != null) btnDecay3x.setBackgroundColor(defaultColor);
        if (btnDecay5x != null) btnDecay5x.setBackgroundColor(defaultColor);
    }

    private void highlightButton(Button btn) {
        if (btn != null) btn.setBackgroundColor(0xFF00897B); // Highlight teal color
    }
}
