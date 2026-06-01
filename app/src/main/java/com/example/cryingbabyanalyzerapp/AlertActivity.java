package com.example.cryingbabyanalyzerapp;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class AlertActivity extends AppCompatActivity {

    private TextView txtAlertCause;
    private TextView txtAlertConfidence;
    private Button btnOpenFeedback;
    private Button btnDismissAlert;

    private Vibrator vibrator;
    private CameraManager cameraManager;
    private String flashCameraId;
    private boolean flashOn = false;

    private String label;
    private float confidence;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_alert);

        txtAlertCause = findViewById(R.id.txtAlertCause);
        txtAlertConfidence = findViewById(R.id.txtAlertConfidence);
        btnOpenFeedback = findViewById(R.id.btnOpenFeedback);
        btnDismissAlert = findViewById(R.id.btnDismissAlert);

        label = getIntent().getStringExtra("label");
        confidence = getIntent().getFloatExtra("confidence", 0f);

        txtAlertCause.setText(convertLabelToKorean(label));
        txtAlertConfidence.setText(
                String.format(Locale.KOREA, "신뢰도 %.1f%%", confidence * 100)
        );

        startVibration();
        startFlashBlink();

        btnDismissAlert.setOnClickListener(v -> {
            stopAlertEffects();
            finish();
        });

        btnOpenFeedback.setOnClickListener(v -> {
            stopAlertEffects();

            Intent intent = new Intent(AlertActivity.this, FeedbackActivity.class);
            intent.putExtra(
                    "RESULT_TEXT",
                    convertLabelToKorean(label) + "\n신뢰도 "
                            + String.format(Locale.KOREA, "%.1f%%", confidence * 100)
            );
            startActivity(intent);
            finish();
        });
    }

    private String convertLabelToKorean(String input) {
        if (input == null || input.isEmpty()) return "아기가 울고 있어요.";

        String lower = input.toLowerCase(Locale.US);

        if (lower.contains("uncomfortable")) return "아기가 불편함을 느끼고 있어요.";
        if (lower.contains("awake")) return "아기가 깼어요.";
        if (lower.contains("diaper")) return "아기 기저귀를 확인해주세요.";
        if (lower.contains("hug")) return "아기가 안아달라고 보채고 있어요.";
        if (lower.contains("hungry")) return "아기가 배고파요.";
        if (lower.contains("sleepy")) return "아기가 졸려요.";

        return input;
    }

    private void startVibration() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        if (vibrator == null) return;

        long[] pattern = new long[]{0, 500, 200, 500, 200, 500, 500, 800};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } else {
            vibrator.vibrate(pattern, 0);
        }
    }

    private void startFlashBlink() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        if (cameraManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);

                if (hasFlash != null && hasFlash) {
                    flashCameraId = id;
                    break;
                }
            }

            if (flashCameraId == null) return;

            blinkFlashLoop();

        } catch (Exception ignored) {
        }
    }

    private void blinkFlashLoop() {
        if (flashCameraId == null) return;

        txtAlertCause.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || flashCameraId == null) return;

                try {
                    flashOn = !flashOn;
                    cameraManager.setTorchMode(flashCameraId, flashOn);
                } catch (CameraAccessException ignored) {
                }

                txtAlertCause.postDelayed(this, 500);
            }
        }, 300);
    }

    private void stopAlertEffects() {
        try {
            if (vibrator != null) {
                vibrator.cancel();
            }

            if (cameraManager != null
                    && flashCameraId != null
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(flashCameraId, false);
            }

            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (manager != null) {
                manager.cancel(CryNotificationManager.ALERT_NOTIFICATION_ID);
            }

        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAlertEffects();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAlertEffects();
    }
}