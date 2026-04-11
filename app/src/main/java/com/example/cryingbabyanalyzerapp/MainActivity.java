package com.example.cryingbabyanalyzerapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
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

    private YamnetMonitor yamnetMonitor;
    private CryApiService apiService;

    private boolean detectMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnDetect = findViewById(R.id.btnDetect);
        txtStatus = findViewById(R.id.txtStatus);
        txtResult = findViewById(R.id.txtResult);

        //추가
        apiService = new CryApiService(BuildConfig.SERVER_IP);

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
            if (!hasAudioPermission()) {
                requestAudioPermission();
                return;
            }

            if (detectMode) {
                stopDetectMode();
            } else {
                startDetectMode();
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

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQ_RECORD_AUDIO
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDetectMode();
            } else {
                txtStatus.setText("마이크 권한이 필요합니다.");
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
}