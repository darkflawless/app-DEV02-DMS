package com.example.dms_appdetect;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.dms_appdetect.calibration.EyeCalibrator;
import com.example.dms_appdetect.camera.DmsCameraManager;
import com.example.dms_appdetect.risk.RiskConfig;
import com.example.dms_appdetect.risk.RiskDecision;
import com.example.dms_appdetect.risk.RiskEngine;
import com.example.dms_appdetect.tuning.TuningController;
import com.example.dms_appdetect.vision.FaceLandmarkDetector;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrator activity adhering to Single Responsibility and Clean Architecture.
 * Preserves the original clean camera & dashboard layout while cleanly orchestrating:
 * - DmsCameraManager (Camera lifecycle & preprocessing)
 * - FaceLandmarkDetector (MediaPipe face landmarks)
 * - EyeCalibrator (Personal 11-second eye calibration)
 * - RiskEngine (Realtime R(t) risk score & safety scorecard)
 * - TuningController (Collapsible tester panel, sliders & 1-minute test mode)
 * - FaceOverlayView (Face landmarks visualizer)
 * - AlertPlayer (Audio & vibration output)
 */
public final class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // Sub-modules
    private DmsCameraManager cameraManager;
    private FaceLandmarkDetector landmarkDetector;
    private EyeCalibrator eyeCalibrator;
    private final RiskEngine riskEngine = new RiskEngine();
    private TuningController tuningController;
    private AlertPlayer alertPlayer;
    private final DmsDecisionEngine legacyEngine = new DmsDecisionEngine();
    private final SessionLogger logger = new SessionLogger();

    // Concurrency & state tracking
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Long, Double> frameLumaMap = new ConcurrentHashMap<>();
    private final Deque<Long> resultTimes = new ArrayDeque<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // UI elements
    private PreviewView previewView;
    private FaceOverlayView faceOverlay;
    private View warmBorder;
    private TextView statusText;
    private TextView detailText;
    private TextView metricsText;
    private TextView qualityText;

    private TextView tvSafetyScore;
    private View layoutRiskBanner;
    private TextView tvRiskZone;
    private TextView tvContextMultiplier;

    private Button startButton;
    private Button calibrateButton;
    private Button muteButton;
    private Button sunglassesButton;
    private Button lightButton;
    private Button eyeTestButton;

    // Permissions & runtime flags
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private volatile boolean monitoring;
    private volatile boolean diagnosticMode;
    private boolean requestedDiagnostic;
    private volatile boolean sunglassesMode;
    private boolean softLight;
    private double activeEyeThreshold = Double.NaN;
    private long lastUiRenderMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        initViews();
        initModules();
        initPermissionLauncher();
        initButtonListeners();

        showIdle("Sẵn sàng");
    }

    private void initViews() {
        previewView = findViewById(R.id.previewView);
        faceOverlay = findViewById(R.id.faceOverlay);
        warmBorder = findViewById(R.id.warmBorder);
        statusText = findViewById(R.id.statusText);
        detailText = findViewById(R.id.detailText);
        metricsText = findViewById(R.id.metricsText);
        qualityText = findViewById(R.id.qualityText);

        tvSafetyScore = findViewById(R.id.tvSafetyScore);
        layoutRiskBanner = findViewById(R.id.layoutRiskBanner);
        tvRiskZone = findViewById(R.id.tvRiskZone);
        tvContextMultiplier = findViewById(R.id.tvContextMultiplier);

        startButton = findViewById(R.id.startButton);
        calibrateButton = findViewById(R.id.calibrateButton);
        muteButton = findViewById(R.id.muteButton);
        sunglassesButton = findViewById(R.id.sunglassesButton);
        lightButton = findViewById(R.id.lightButton);
        eyeTestButton = findViewById(R.id.eyeTestButton);
    }

    private void initModules() {
        cameraManager = new DmsCameraManager(this);
        alertPlayer = new AlertPlayer(this);
        eyeCalibrator = new EyeCalibrator(this);

        eyeCalibrator.setListener(new EyeCalibrator.Listener() {
            @Override
            public void onPhaseChanged(String phase, String title, String detail, boolean playCue) {
                runOnUiThread(() -> {
                    if (playCue) alertPlayer.playTestCue();
                    statusText.setText(title);
                    statusText.setTextColor(Color.WHITE);
                    detailText.setText(detail);
                });
            }

            @Override
            public void onCompleted(boolean success, double threshold, String summary, String detail) {
                runOnUiThread(() -> {
                    stopMonitoring();
                    statusText.setText(summary);
                    detailText.setText(detail);
                    qualityText.setText(success
                            ? String.format(Locale.US, "Ngưỡng cá nhân %.3f. Sẵn sàng giám sát!", threshold)
                            : "Chưa phân biệt rõ mắt. Hãy ngồi đủ sáng và thử lại.");
                });
            }
        });

        landmarkDetector = new FaceLandmarkDetector(this, new FaceLandmarkDetector.LandmarkListener() {
            @Override
            public void onLandmarks(FaceLandmarkerResult result, MPImage inputImage) {
                processLandmarkResult(result, inputImage);
            }

            @Override
            public void onError(Throwable error) {
                Log.e(TAG, "Landmark detector error", error);
            }
        });

        // Initialize Tuning Controller for collapsible tester controls
        tuningController = new TuningController(this, riskEngine, faceOverlay);
        tuningController.setListener(started -> {
            if (started && !monitoring) {
                requestedDiagnostic = false;
                requestCameraAndStart();
            }
        });
        tuningController.bindViews(
                findViewById(R.id.btnToggleBottomPanel),
                findViewById(R.id.tvToggleBottomText),
                findViewById(R.id.bottomScrollView),
                findViewById(R.id.btnSpeedJam),
                findViewById(R.id.btnSpeedUrban),
                findViewById(R.id.btnSpeedOpen),
                findViewById(R.id.btnSpeedHighway),
                findViewById(R.id.btnTimeDay),
                findViewById(R.id.btnTimeAfternoon),
                findViewById(R.id.btnTimeNight),
                findViewById(R.id.seekDrivingHours),
                findViewById(R.id.tvDrivingHours),
                findViewById(R.id.seekHoldTime),
                findViewById(R.id.tvHoldTime),
                findViewById(R.id.btnDecay1x),
                findViewById(R.id.btnDecay3x),
                findViewById(R.id.btnDecay5x),
                findViewById(R.id.switchOverlay),
                findViewById(R.id.switchFastDecay),
                findViewById(R.id.btnOneMinuteTest),
                findViewById(R.id.tvTestCountdown)
        );
    }

    private void initPermissionLauncher() {
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) startMonitoringInternal();
                    else showIdle("Cần quyền camera để giám sát");
                });
    }

    private void initButtonListeners() {
        startButton.setOnClickListener(v -> {
            if (monitoring) stopMonitoring();
            else {
                requestedDiagnostic = false;
                requestCameraAndStart();
            }
        });

        eyeTestButton.setOnClickListener(v -> {
            if (monitoring) stopMonitoring();
            requestedDiagnostic = true;
            requestCameraAndStart();
        });

        calibrateButton.setOnClickListener(v -> {
            legacyEngine.reset();
            statusText.setText("Đang hiệu chuẩn");
            detailText.setText("Ngồi thẳng và nhìn về phía trước");
        });

        muteButton.setOnClickListener(v -> {
            alertPlayer.setMuted(!alertPlayer.isMuted());
            muteButton.setText(alertPlayer.isMuted() ? R.string.unmute : R.string.mute);
        });

        sunglassesButton.setOnClickListener(v -> {
            sunglassesMode = !sunglassesMode;
            sunglassesButton.setText(sunglassesMode ? R.string.sunglasses_on : R.string.sunglasses_off);
            legacyEngine.reset();
        });

        lightButton.setOnClickListener(v -> {
            softLight = !softLight;
            warmBorder.setVisibility(softLight ? View.VISIBLE : View.GONE);
            lightButton.setText(softLight ? R.string.light_on : R.string.light_off);
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.screenBrightness = softLight ? 0.50f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getWindow().setAttributes(params);
        });
    }

    private void requestCameraAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startMonitoringInternal();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startMonitoringInternal() {
        if (monitoring) return;
        monitoring = true;
        diagnosticMode = requestedDiagnostic;
        lastUiRenderMs = 0;

        synchronized (resultTimes) {
            resultTimes.clear();
        }
        legacyEngine.reset();
        riskEngine.reset();

        double savedThreshold = eyeCalibrator.getSavedThreshold();
        activeEyeThreshold = diagnosticMode ? DmsConfig.EAR_CLOSED
                : (Double.isFinite(savedThreshold) ? savedThreshold : Double.NaN);

        legacyEngine.configureEyeDetection(Double.isFinite(activeEyeThreshold), activeEyeThreshold);
        logger.start(this);

        startButton.setText(R.string.stop_monitoring);
        eyeTestButton.setEnabled(!diagnosticMode);
        calibrateButton.setEnabled(!diagnosticMode);

        statusText.setText("Đang mở camera");
        detailText.setText("Đang nạp mô hình AI...");

        backgroundExecutor.execute(() -> {
            try {
                if (!landmarkDetector.isReady()) {
                    landmarkDetector.initialize();
                }
                runOnUiThread(() -> {
                    if (monitoring) bindCamera();
                });
            } catch (Exception e) {
                Log.e(TAG, "Cannot start FaceLandmarkDetector", e);
                runOnUiThread(() -> {
                    stopMonitoring();
                    showIdle("Không mở được mô hình: " + e.getMessage());
                });
            }
        });
    }

    private void bindCamera() {
        cameraManager.start(
                this,
                previewView.getSurfaceProvider(),
                (uprightBitmap, timestamp, luma) -> {
                    if (!monitoring) return;
                    if (frameLumaMap.size() > 90) frameLumaMap.clear();
                    frameLumaMap.put(timestamp, luma);
                    landmarkDetector.detectAsync(uprightBitmap, timestamp);
                },
                error -> runOnUiThread(() -> {
                    stopMonitoring();
                    showIdle("Lỗi camera: " + error.getMessage());
                })
        );

        if (diagnosticMode) {
            eyeCalibrator.start(SystemClock.uptimeMillis());
        } else {
            statusText.setText("Đang hiệu chuẩn");
            detailText.setText("Ngồi thẳng và nhìn về phía trước");
        }
    }

    private void processLandmarkResult(FaceLandmarkerResult result, MPImage inputImage) {
        if (!monitoring) return;
        long timestamp = result.timestampMs();
        Double lumaVal = frameLumaMap.remove(timestamp);
        double luma = lumaVal != null ? lumaVal : 0.0;

        FeatureSample sample = FaceFeatureExtractor.extract(
                result, timestamp, inputImage.getWidth(), inputImage.getHeight(), luma, sunglassesMode);

        DmsDecision decision = legacyEngine.process(sample);
        long processingMs = Math.max(0, SystemClock.uptimeMillis() - timestamp);
        double aiFps = calculateFps(timestamp);

        // Map violation ONLY when entering a new alert state (avoids spamming points every frame)
        RiskEngine.ViolationType violation = RiskEngine.ViolationType.NONE;
        if (decision.newAlert) {
            if (decision.state == DmsDecision.State.DROWSY) {
                long closureDuration = SystemClock.uptimeMillis() - decision.sinceMs;
                violation = closureDuration >= 2000
                        ? RiskEngine.ViolationType.EMERGENCY_SLEEP
                        : RiskEngine.ViolationType.MICROSLEEP;
            } else if (decision.state == DmsDecision.State.HEAD_AWAY) {
                violation = RiskEngine.ViolationType.DISTRACTION;
            } else if (decision.state == DmsDecision.State.YAWN || decision.state == DmsDecision.State.FATIGUE_WARNING) {
                violation = RiskEngine.ViolationType.YAWN;
            }
        }

        RiskDecision riskDecision = riskEngine.update(timestamp, violation);

        // Update 1-minute test session if active
        if (tuningController.getTestSession().isActive()) {
            if (violation != RiskEngine.ViolationType.NONE) {
                tuningController.getTestSession().onViolation(violation);
            }
            tuningController.getTestSession().onTick(timestamp, riskDecision.riskScore, riskDecision.zone);
        }

        if (diagnosticMode) {
            eyeCalibrator.onFrame(timestamp, sample.eyeValid, sample.ear());
        } else {
            // Audio alert: immediately stops as soon as driver looks straight / opens eyes
            alertPlayer.maybePlay(decision, riskDecision);
        }

        logger.write(sample, decision, processingMs, diagnosticMode ? eyeCalibrator.getCurrentPhase() : "LIVE", activeEyeThreshold);

        runOnUiThread(() -> {
            if (!monitoring) return;

            long now = SystemClock.uptimeMillis();
            if (now - lastUiRenderMs >= 150 || decision.newAlert || riskDecision.isNewAlert) {
                lastUiRenderMs = now;
                if (faceOverlay.isOverlayEnabled()) {
                    faceOverlay.setLandmarks(result.faceLandmarks().isEmpty()
                            ? null : result.faceLandmarks().get(0),
                            inputImage.getWidth(), inputImage.getHeight());
                } else {
                    faceOverlay.setLandmarks(null, 0, 0);
                }
                renderUi(sample, decision, riskDecision, processingMs, aiFps);
            }
        });
    }

    private void renderUi(FeatureSample sample, DmsDecision decision, RiskDecision riskDecision, long processingMs, double aiFps) {
        // Render Top Risk Banner
        if (layoutRiskBanner != null) {
            layoutRiskBanner.setBackgroundColor(riskDecision.zone.color);
        }
        if (tvRiskZone != null) {
            tvRiskZone.setText(String.format(Locale.US, "%s • R(t) = %.1f / 100",
                    riskDecision.zone.title, riskDecision.riskScore));
        }
        if (tvContextMultiplier != null) {
            tvContextMultiplier.setText(String.format(Locale.US, "K = %.2fx", riskDecision.contextMultiplier));
        }
        if (tvSafetyScore != null) {
            tvSafetyScore.setText(String.format(Locale.US, "An toàn: %d • %s",
                    riskDecision.safetyScore, riskDecision.rank));
        }

        // Render Status
        if (!diagnosticMode) {
            boolean eyeReady = DmsConfig.validPersonalEarThreshold(activeEyeThreshold);
            if (!eyeReady && decision.state == DmsDecision.State.MONITORING) {
                statusText.setText("Chưa hiệu chuẩn mắt");
                statusText.setTextColor(Color.WHITE);
            } else {
                statusText.setText(decision.message);
                if (decision.severity == DmsDecision.Severity.CRITICAL) {
                    statusText.setTextColor(0xFFFF4D4D);
                } else if (decision.severity == DmsDecision.Severity.WARNING) {
                    statusText.setTextColor(0xFFFF9933);
                } else {
                    statusText.setTextColor(Color.WHITE);
                }
            }
            detailText.setText(decision.state.name().replace('_', ' ')
                    + "  •  " + (alertPlayer.isMuted() ? "TẮT TIẾNG" : "CÓ ÂM THANH")
                    + "  •  " + riskDecision.cooldownStatus);
        }

        // Render Metrics (Original Monospace format)
        metricsText.setText(String.format(Locale.US,
                "EAR  %s / %s    MAR  %s\nPITCH  %s°   YAW  %s°   ROLL  %s°",
                metric(sample.earLeft, 3), metric(sample.earRight, 3),
                metric(sample.mar, 3), metric(sample.pitch, 1),
                metric(sample.yaw, 1), metric(sample.roll, 1)));

        qualityText.setText(String.format(Locale.US,
                "Mặt %s  •  Mắt %s  •  Pose %s  •  Sáng %.0f\n"
                        + "FPS AI %.1f  •  Xử lý %d ms  •  Hold: %ds  •  K=%.2fx",
                sample.facePresent ? "có" : "mất",
                sample.eyeValid ? "rõ" : "không rõ",
                sample.poseValid ? "rõ" : "không rõ",
                sample.luminance,
                aiFps, processingMs,
                riskDecision.holdTimeRemainingMs / 1000L,
                riskDecision.contextMultiplier));
    }

    private static String metric(double value, int decimals) {
        return Double.isFinite(value)
                ? String.format(Locale.US, "%." + decimals + "f", value) : "--";
    }

    private double calculateFps(long timestamp) {
        synchronized (resultTimes) {
            resultTimes.addLast(timestamp);
            while (!resultTimes.isEmpty() && timestamp - resultTimes.peekFirst() > 2000) {
                resultTimes.removeFirst();
            }
            if (resultTimes.size() < 2) return 0;
            long elapsed = timestamp - resultTimes.peekFirst();
            return elapsed > 0 ? (resultTimes.size() - 1) * 1000.0 / elapsed : 0;
        }
    }

    private void stopMonitoring() {
        monitoring = false;
        diagnosticMode = false;
        mainHandler.removeCallbacksAndMessages(null);
        cameraManager.stop();
        faceOverlay.setLandmarks(null, 0, 0);
        frameLumaMap.clear();
        legacyEngine.reset();
        eyeCalibrator.stop();
        logger.close();

        startButton.setText(R.string.start_monitoring);
        eyeTestButton.setEnabled(true);
        calibrateButton.setEnabled(false);
        showIdle("Đã dừng giám sát");
    }

    private void showIdle(String text) {
        statusText.setText(text);
        statusText.setTextColor(Color.WHITE);
        if (layoutRiskBanner != null) {
            layoutRiskBanner.setBackgroundColor(0xFF2E7D32);
        }
        if (tvRiskZone != null) {
            tvRiskZone.setText("🟢 VÙNG XANH • R(t) = 0.0 / 100");
        }
        if (tvContextMultiplier != null) {
            tvContextMultiplier.setText(String.format(Locale.US, "K = %.2fx", riskEngine.getContextMultiplier()));
        }
        if (tvSafetyScore != null) {
            tvSafetyScore.setText(String.format(Locale.US, "An toàn: %d • %s",
                    riskEngine.getSafetyScore(), RiskEngine.calculateRank(riskEngine.getSafetyScore())));
        }

        double savedThreshold = eyeCalibrator.getSavedThreshold();
        boolean eyeReady = Double.isFinite(savedThreshold);
        detailText.setText(eyeReady ? "Bắt đầu để mở camera giám sát"
                : "Bấm Kiểm tra mắt 11 giây trước khi bật cảnh báo mắt");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (monitoring) stopMonitoring();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        monitoring = false;
        cameraManager.release();
        landmarkDetector.close();
        alertPlayer.release();
        backgroundExecutor.shutdown();
        logger.close();
    }
}
