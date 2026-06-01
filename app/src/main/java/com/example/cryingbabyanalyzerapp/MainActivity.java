package com.example.cryingbabyanalyzerapp;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_RECORD_AUDIO = 1001;

    private Button btnDetect;
    private Button btnFeedback;
    private TextView txtStatus;
    private TextView txtResult;
    private Switch switchBackground;

    private View viewRipple1;
    private View viewRipple2;
    private AnimatorSet rippleAnimatorSet1;
    private AnimatorSet rippleAnimatorSet2;

    private YamnetMonitor yamnetMonitor;
    private CryApiService apiService;
    private CryNotificationManager notificationManager;

    private boolean detectMode = false;

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateResultUI();
        }
    };

    private String convertLabelToKorean(String input) {
        if (input == null || input.isEmpty()) return "아직 감지된 울음이 없습니다.";

        String lower = input.toLowerCase(Locale.US);
        if (lower.contains("uncomfortable")) return "아기가 불편함을 느끼고 있어요.";
        if (lower.contains("awake")) return "아기가 깼어요.";
        if (lower.contains("diaper")) return "아기 기저귀를 확인해주세요.";
        if (lower.contains("hug")) return "아기가 안아달라고 보채고 있어요.";
        if (lower.contains("hungry")) return "아기가 배고파요.";
        if (lower.contains("sleepy")) return "아기가 졸려요.";

        return input;
    }

    private void updateResultUI() {
        if (!CryDetectionService.lastResultText.isEmpty()) {
            txtResult.setText(convertLabelToKorean(CryDetectionService.lastResultText));
            btnFeedback.setVisibility(View.VISIBLE);

            if (switchBackground.isChecked()) {
                txtStatus.setText("백그라운드에서 감지됨!");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnDetect = findViewById(R.id.btnDetect);
        btnFeedback = findViewById(R.id.btnFeedback);
        txtStatus = findViewById(R.id.txtStatus);
        txtResult = findViewById(R.id.txtResult);
        switchBackground = findViewById(R.id.switchBackground);

        viewRipple1 = findViewById(R.id.viewRipple1);
        viewRipple2 = findViewById(R.id.viewRipple2);

        apiService = new CryApiService(BuildConfig.SERVER_IP);
        notificationManager = new CryNotificationManager(this);

        yamnetMonitor = new YamnetMonitor(this, new YamnetMonitor.Listener() {
            @Override
            public void onStatus(final String message) {
                // YAMNet 동작 중 잠깐씩 바뀌는 임시 문구가 찍히지 않도록 비워둡니다.
            }

            @Override
            public void onCryDetected() {
                txtStatus.post(() -> {
                    txtStatus.setText("아기 울음소리 확인 완료. 사유 분석 중...");
                    txtResult.setText("");
                });
                requestPrediction();
            }

            @Override
            public void onError(final String message) {
                txtStatus.post(() -> txtStatus.setText(message));
            }
        });

        btnDetect.setOnClickListener(v -> {
            if (!hasRequiredPermissions()) {
                requestAudioPermission();
                return;
            }

            if (switchBackground.isChecked()) {
                Toast.makeText(MainActivity.this,
                        "감시 모드가 켜져 있을 때는 수동 감지를 시작할 수 없습니다.\n먼저 감시 모드를 꺼주세요.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (detectMode) {
                stopDetectMode();
            } else {
                startDetectMode();
            }
        });

        btnFeedback.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, FeedbackActivity.class);
            intent.putExtra("RESULT_TEXT", txtResult.getText().toString());
            startActivity(intent);
        });

        switchBackground.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!hasRequiredPermissions()) {
                requestAudioPermission();
                switchBackground.setChecked(false);
                return;
            }

            if (isChecked) {
                if (detectMode) {
                    Toast.makeText(MainActivity.this,
                            "수동 감지 중일 때는 감시 모드를 켤 수 없습니다.\n먼저 마이크를 눌러 감지를 중지해주세요.",
                            Toast.LENGTH_SHORT).show();
                    switchBackground.setChecked(false);
                    return;
                }
                startBackgroundService();
            } else {
                stopBackgroundService();
            }
        });
    }

    private void startDetectMode() {
        detectMode = true;
        txtStatus.setText("아기 울음소리 확인 중...");
        yamnetMonitor.start();
        startRippleAnimation();
    }

    private void stopDetectMode() {
        detectMode = false;
        txtStatus.setText("마이크 버튼을 눌러보세요!");
        yamnetMonitor.stop();
        stopRippleAnimation();
    }

    private void startRippleAnimation() {
        if (viewRipple1 == null || viewRipple2 == null) return;

        viewRipple1.setVisibility(View.VISIBLE);
        viewRipple2.setVisibility(View.VISIBLE);

        ObjectAnimator scaleX1 = ObjectAnimator.ofFloat(viewRipple1, "scaleX", 1.0f, 1.4f);
        ObjectAnimator scaleY1 = ObjectAnimator.ofFloat(viewRipple1, "scaleY", 1.0f, 1.4f);
        ObjectAnimator alpha1 = ObjectAnimator.ofFloat(viewRipple1, "alpha", 1.0f, 0.0f);

        scaleX1.setRepeatCount(ValueAnimator.INFINITE);
        scaleY1.setRepeatCount(ValueAnimator.INFINITE);
        alpha1.setRepeatCount(ValueAnimator.INFINITE);

        rippleAnimatorSet1 = new AnimatorSet();
        rippleAnimatorSet1.playTogether(scaleX1, scaleY1, alpha1);
        rippleAnimatorSet1.setDuration(1800);
        rippleAnimatorSet1.start();

        ObjectAnimator scaleX2 = ObjectAnimator.ofFloat(viewRipple2, "scaleX", 1.0f, 1.4f);
        ObjectAnimator scaleY2 = ObjectAnimator.ofFloat(viewRipple2, "scaleY", 1.0f, 1.4f);
        ObjectAnimator alpha2 = ObjectAnimator.ofFloat(viewRipple2, "alpha", 1.0f, 0.0f);

        scaleX2.setRepeatCount(ValueAnimator.INFINITE);
        scaleY2.setRepeatCount(ValueAnimator.INFINITE);
        alpha2.setRepeatCount(ValueAnimator.INFINITE);

        rippleAnimatorSet2 = new AnimatorSet();
        rippleAnimatorSet2.playTogether(scaleX2, scaleY2, alpha2);
        rippleAnimatorSet2.setDuration(1800);
        rippleAnimatorSet2.setStartDelay(900);
        rippleAnimatorSet2.start();
    }

    private void stopRippleAnimation() {
        if (rippleAnimatorSet1 != null) rippleAnimatorSet1.cancel();
        if (rippleAnimatorSet2 != null) rippleAnimatorSet2.cancel();

        if (viewRipple1 != null) {
            viewRipple1.setVisibility(View.INVISIBLE);
            viewRipple1.setScaleX(1.0f);
            viewRipple1.setScaleY(1.0f);
            viewRipple1.setAlpha(1.0f);
        }
        if (viewRipple2 != null) {
            viewRipple2.setVisibility(View.INVISIBLE);
            viewRipple2.setScaleX(1.0f);
            viewRipple2.setScaleY(1.0f);
            viewRipple2.setAlpha(1.0f);
        }
    }

    private void requestPrediction() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File wavFile = WavRecorder.recordFiveSeconds(MainActivity.this);

                apiService.predict(wavFile, new CryApiService.PredictCallback() {
                    @Override
                    public void onSuccess(final CryApiService.PredictResponse response) {
                        txtStatus.post(() -> {
                            // 💡 [핵심 변경] 서버 분석 성공 시 감지 모드를 자동으로 끕니다. (이펙트 스톱 및 상태 초기화)
                            stopDetectMode();

                            String label = "없음";
                            float confidence = 0f;

                            if (response != null && response.prediction != null) {
                                label = response.prediction.label;
                                confidence = response.prediction.confidence;
                            }

                            txtResult.setText(convertLabelToKorean(label));
                            btnFeedback.setVisibility(View.VISIBLE);

                            if (response != null && response.prediction != null) {
                                notificationManager.sendCryNotification(label, confidence);
                            }
                        });
                    }

                    @Override
                    public void onFailure(final String message) {
                        txtStatus.post(() -> {
                            // 💡 실패 시에도 모드와 이펙트를 자동 종료한 후 에러 메시지를 노출합니다.
                            stopDetectMode();
                            txtStatus.setText(message);
                        });
                    }
                });

            } catch (final Exception e) {
                txtStatus.post(() -> {
                    // 💡 예외 발생 시에도 모드와 이펙트를 자동 종료한 후 에러 메시지를 노출합니다.
                    stopDetectMode();
                    txtStatus.setText("녹음 실패: " + e.getMessage());
                });
            }
        });
    }

    private boolean hasRequiredPermissions() {
        boolean audioGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        boolean cameraGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean notifyGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

            return audioGranted && cameraGranted && notifyGranted;
        } else {
            return audioGranted && cameraGranted;
        }
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.CAMERA,
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    REQ_RECORD_AUDIO
            );
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.CAMERA
                    },
                    REQ_RECORD_AUDIO
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_RECORD_AUDIO) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                txtStatus.setText("앱을 사용하려면 마이크 및 알림 권한이 필요합니다.");
            } else {
                txtStatus.setText("앱을 사용하려면 마이크, 알림, 플래시 권한이 필요합니다.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (yamnetMonitor != null) {
            yamnetMonitor.stop();
        }
        stopRippleAnimation();
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter("com.example.cryingbabyanalyzerapp.RESULT_UPDATE");

        ContextCompat.registerReceiver(
                this,
                resultReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );

        updateResultUI();

        if (CryDetectionService.isRunning) {
            switchBackground.setOnCheckedChangeListener(null);
            switchBackground.setChecked(true);
            txtStatus.setText("백그라운드 상시 감지 작동 중...");

            switchBackground.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!hasRequiredPermissions()) {
                    requestAudioPermission();
                    switchBackground.setChecked(false);
                    return;
                }
                if (isChecked) {
                    if (detectMode) {
                        Toast.makeText(MainActivity.this,
                                "수동 감지 중일 때는 감시 모드를 켤 수 없습니다.\n먼저 마이크를 눌러 감지를 중지해주세요.",
                                Toast.LENGTH_SHORT).show();
                        switchBackground.setChecked(false);
                        return;
                    }
                    startBackgroundService();
                } else {
                    stopBackgroundService();
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(resultReceiver);
        } catch (IllegalArgumentException e) {
            // 무시
        }
    }

    private void startBackgroundService() {
        Intent serviceIntent = new Intent(this, CryDetectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        txtStatus.setText("백그라운드 상시 감지 ON");
    }

    private void stopBackgroundService() {
        Intent serviceIntent = new Intent(this, CryDetectionService.class);
        stopService(serviceIntent);
        txtStatus.setText("백그라운드 상시 감지 OFF");
    }
}