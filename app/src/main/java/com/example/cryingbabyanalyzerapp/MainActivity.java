package com.example.cryingbabyanalyzerapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

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
    private TextView txtStatus;
    private TextView txtResult;
    private Switch switchBackground;

    private YamnetMonitor yamnetMonitor;
    private CryApiService apiService;
    private CryNotificationManager notificationManager;

    private boolean detectMode = false;

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateResultUI(); // 방송을 들으면 화면을 업데이트!
        }
    };
    private void updateResultUI() {
        if (!CryDetectionService.lastResultText.isEmpty()) {
            txtResult.setText(CryDetectionService.lastResultText);

            // 만약 수동 감지 모드가 아니라면 상태도 업데이트
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
        txtStatus = findViewById(R.id.txtStatus);
        txtResult = findViewById(R.id.txtResult);

        switchBackground = findViewById(R.id.switchBackground);

        apiService = new CryApiService(BuildConfig.SERVER_IP);

        notificationManager = new CryNotificationManager(this);

        yamnetMonitor = new YamnetMonitor(this, new YamnetMonitor.Listener() {
            @Override
            public void onStatus(final String message) {
                txtStatus.post(new Runnable() {
                    @Override
                    public void run() {
                        txtStatus.setText(message);
                    }
                });
            }

            @Override
            public void onCryDetected() {
                txtStatus.post(new Runnable() {
                    @Override
                    public void run() {
                        txtStatus.setText("울음 후보 감지됨. 5초 녹음 후 서버 분석 중...");
                        txtResult.setText("");
                    }
                });
                requestPrediction();
            }

            @Override
            public void onError(final String message) {
                txtStatus.post(new Runnable() {
                    @Override
                    public void run() {
                        txtStatus.setText(message);
                    }
                });
            }
        });

        btnDetect.setOnClickListener(v -> {
            if (!hasRequiredPermissions()) { // ⭐ 여기를 수정
                requestAudioPermission();
                return;
            }

            if (switchBackground.isChecked()) {
                txtStatus.setText("먼저 백그라운드 감지를 꺼주세요!");
                return;
            }

            if (detectMode) {
                stopDetectMode();
            } else {
                startDetectMode();
            }
        });

        switchBackground.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!hasRequiredPermissions()) { // ⭐ 여기를 수정
                requestAudioPermission();
                switchBackground.setChecked(false); // 권한 없으면 스위치 다시 끄기
                return;
            }

            if (isChecked) {
                startBackgroundService();
            } else {
                stopBackgroundService();
            }
        });
    }

    private void startDetectMode() {
        detectMode = true;
        btnDetect.setText("감지 중지");
        txtStatus.setText("감지 모드 ON");
        yamnetMonitor.start();
    }

    private void stopDetectMode() {
        detectMode = false;
        btnDetect.setText("감지 시작");
        txtStatus.setText("감지 모드 OFF");
        yamnetMonitor.stop();
    }

    private void requestPrediction() {
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File wavFile = WavRecorder.recordFiveSeconds(MainActivity.this);

                    apiService.predict(wavFile, new CryApiService.PredictCallback() {
                        @Override
                        public void onSuccess(final CryApiService.PredictResponse response) {
                            txtStatus.post(new Runnable() {
                                @Override
                                public void run() {
                                    txtStatus.setText("서버 분석 완료");
                                    String label = "없음";
                                    float confidence = 0f;

                                    if (response != null && response.prediction != null) {
                                        label = response.prediction.label;
                                        confidence = response.prediction.confidence;
                                    }

                                    txtResult.setText(
                                            response.message + "\n" +
                                                    "label = " + label + "\n" +
                                                    "confidence = " + String.format(Locale.US, "%.3f", confidence)
                                    );

                                    notificationManager.sendCryNotification(label, confidence);

                                    if (detectMode) {
                                        yamnetMonitor.start();
                                    }
                                }
                            });
                        }

                        @Override
                        public void onFailure(final String message) {
                            txtStatus.post(new Runnable() {
                                @Override
                                public void run() {
                                    txtStatus.setText(message);
                                    if (detectMode) {
                                        yamnetMonitor.start();
                                    }
                                }
                            });
                        }
                    });

                } catch (final Exception e) {
                    txtStatus.post(new Runnable() {
                        @Override
                        public void run() {
                            txtStatus.setText("녹음 실패: " + e.getMessage());
                            if (detectMode) {
                                yamnetMonitor.start();
                            }
                        }
                    });
                }
            }
        });
    }

    // 교체할 부분: 기존 hasAudioPermission() 삭제 후 아래 코드로 교체
    private boolean hasRequiredPermissions() {
        // 1. 마이크 권한 확인
        boolean audioGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        // 2. 안드로이드 13(API 33) 이상인 경우 알림 권한도 함께 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean notifyGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            return audioGranted && notifyGranted; // 둘 다 있어야 true 반환
        } else {
            return audioGranted; // 하위 버전은 마이크만 확인
        }
    }

    // MainActivity.java의 권한 요청 부분 수정
    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.POST_NOTIFICATIONS // 알림 권한 추가
                    },
                    REQ_RECORD_AUDIO
            );
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_RECORD_AUDIO
            );
        }
    }

    // 교체할 부분: 기존 onRequestPermissionsResult 삭제 후 아래 코드로 교체
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_RECORD_AUDIO) {
            boolean allGranted = true;
            // 요청한 모든 권한(마이크, 알림)이 승인되었는지 확인
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                txtStatus.setText("권한이 승인되었습니다. 버튼이나 스위치를 다시 눌러주세요.");
            } else {
                txtStatus.setText("앱을 사용하려면 마이크 및 알림 권한이 필요합니다.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (yamnetMonitor != null) {
            yamnetMonitor.stop();
        }
    }

    // ⭐ [새로 추가할 함수] 화면이 다시 사용자에게 보여질 때마다 실행됨
    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter("com.example.cryingbabyanalyzerapp.RESULT_UPDATE");

        // ⭐ ContextCompat을 사용하면 복잡한 버전 체크 없이 한 방에 안전하게 해결됩니다!
        androidx.core.content.ContextCompat.registerReceiver(
                this,
                resultReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        );

        // ⭐ 2. 알림을 클릭하고 들어온 경우를 위해 화면 켜질 때 즉시 최신 결과 반영
        updateResultUI();

        // 백그라운드 서비스가 실행 중이라면?
        if (CryDetectionService.isRunning) {
            // 스위치 리스너가 작동해서 서비스가 두 번 켜지는 걸 막기 위해 잠시 리스너를 끕니다.
            switchBackground.setOnCheckedChangeListener(null);

            // 스위치를 ON으로 변경하고 텍스트도 업데이트
            switchBackground.setChecked(true);
            txtStatus.setText("백그라운드 상시 감지 작동 중...");

            // 리스너를 다시 달아줍니다. (onCreate에 있던 내용과 동일)
            switchBackground.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!hasRequiredPermissions()) {
                    requestAudioPermission();
                    switchBackground.setChecked(false);
                    return;
                }
                if (isChecked) {
                    startBackgroundService();
                } else {
                    stopBackgroundService();
                }
            });
        }
    }

    // ⭐ [추가 3] 화면이 안 보일 때는 수신기를 잠시 끕니다.
    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(resultReceiver);
        } catch (IllegalArgumentException e) {
            // 무시 (이미 해제된 경우)
        }
    }

    // 백그라운드 서비스 시작
    private void startBackgroundService() {
        Intent serviceIntent = new Intent(this, CryDetectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        txtStatus.setText("백그라운드 상시 감지 ON");
    }

    // 백그라운드 서비스 종료
    private void stopBackgroundService() {
        Intent serviceIntent = new Intent(this, CryDetectionService.class);
        stopService(serviceIntent);
        txtStatus.setText("백그라운드 상시 감지 OFF");
    }
}