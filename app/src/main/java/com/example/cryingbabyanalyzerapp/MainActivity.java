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
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_RECORD_AUDIO = 1001;

    // 홈 화면의 수동 울음 감지 시간: 10초
    private static final long MANUAL_DETECTION_DURATION_MS = 10_000L;

    private Button btnDetect;
    private Button btnFeedback;
    private Button btnSoothingSettings;
    private Button btnProfileQuick;
    private TextView txtStatus;
    private TextView txtResult;
    private TextView tvHomeGreeting;
    private TextView tvRecentMeta;
    private TextView tvTodayCount;
    private TextView tvTopCause;
    private Switch switchBackground;
    private BottomNavigationView bottomNavigationView;
    private CircularProgressIndicator detectionProgress;

    private View viewRipple1;
    private View viewRipple2;
    private AnimatorSet rippleAnimatorSet1;
    private AnimatorSet rippleAnimatorSet2;

    private YamnetMonitor yamnetMonitor;
    private CryApiService apiService;
    private LocalCryRecordStore localRecordStore;
    private CryNotificationManager notificationManager;

    private boolean detectMode = false;
    private int currentRecordId = -1;
    private CountDownTimer detectionTimer;

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
            currentRecordId = CryDetectionService.lastRecordId;
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
        btnSoothingSettings = findViewById(R.id.btnSoothingSettings);
        btnProfileQuick = findViewById(R.id.btnProfileQuick);
        txtStatus = findViewById(R.id.txtStatus);
        txtResult = findViewById(R.id.txtResult);
        tvHomeGreeting = findViewById(R.id.tvHomeGreeting);
        tvRecentMeta = findViewById(R.id.tvRecentMeta);
        tvTodayCount = findViewById(R.id.tvTodayCount);
        tvTopCause = findViewById(R.id.tvTopCause);
        switchBackground = findViewById(R.id.switchBackground);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        detectionProgress = findViewById(R.id.detectionProgress);

        viewRipple1 = findViewById(R.id.viewRipple1);
        viewRipple2 = findViewById(R.id.viewRipple2);

        apiService = new CryApiService(BuildConfig.SERVER_IP);
        localRecordStore = new LocalCryRecordStore(this);
        notificationManager = new CryNotificationManager(this);

        setupBottomNavigation();

        yamnetMonitor = new YamnetMonitor(this, new YamnetMonitor.Listener() {
            @Override
            public void onStatus(final String message) {
                // YAMNet 동작 중 잠깐씩 바뀌는 임시 문구가 찍히지 않도록 비워둡니다.
            }

            @Override
            public void onCryDetected() {
                // 10초 제한이 이미 끝난 뒤 들어온 늦은 콜백은 완전히 무시합니다.
                if (!detectMode) return;

                txtStatus.post(() -> {
                    // 메인 스레드에서 실행되기 전 10초 제한이 끝났다면 분석 요청도 하지 않습니다.
                    if (!detectMode) return;

                    // 울음을 찾은 순간부터는 더 이상 실시간 감지를 하지 않고 서버 분석만 진행합니다.
                    stopDetectMode();
                    txtStatus.setText("아기 울음소리 확인 완료. 사유 분석 중...");
                    txtResult.setText("");

                    requestPrediction();
                });
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
            intent.putExtra("RECORD_ID", currentRecordId);
            startActivity(intent);
        });

        btnSoothingSettings.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SoothingSettingsActivity.class))
        );

        btnProfileQuick.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ProfileActivity.class))
        );

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

    private void setupBottomNavigation() {
        if (bottomNavigationView == null) return;

        bottomNavigationView.setSelectedItemId(R.id.nav_home);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                return true;
            }

            if (itemId == R.id.nav_medical_record) {
                startActivity(new Intent(MainActivity.this, MedicalRecordActivity.class));
                return true;
            }

            if (itemId == R.id.nav_profile) {
                startActivity(new Intent(MainActivity.this, ProfileActivity.class));
                return true;
            }

            return false;
        });
    }

    private void startDetectMode() {
        // 중복 타이머 방지
        if (detectionTimer != null) {
            detectionTimer.cancel();
            detectionTimer = null;
        }

        detectMode = true;
        btnDetect.setText("분석 중지");

        yamnetMonitor.start();

        // YAMNet 시작 실패 시 감지 효과도 시작하지 않습니다.
        if (!yamnetMonitor.isRunning()) {
            detectMode = false;
            btnDetect.setText("◉  울음 분석 시작하기");
            stopDetectionChargingEffect();
            return;
        }

        startDetectionChargingEffect();

        // 버튼을 누른 순간부터 10초 동안만 수동 감지를 유지합니다.
        detectionTimer = new CountDownTimer(
                MANUAL_DETECTION_DURATION_MS,
                100L
        ) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!detectMode) return;

                // 10초 동안 0 -> 100%로 원형 게이지를 채웁니다.
                int progress = (int) (
                        100f * (MANUAL_DETECTION_DURATION_MS - millisUntilFinished)
                                / MANUAL_DETECTION_DURATION_MS
                );
                detectionProgress.setProgress(Math.min(progress, 100));
            }

            @Override
            public void onFinish() {
                detectionTimer = null;

                if (!detectMode) return;

                // 정확히 10초가 되면 감지를 종료하고 게이지를 초기화합니다.
                detectionProgress.setProgress(100);
                stopDetectMode();
                txtStatus.setText("10초 감지가 끝났습니다. 다시 감지하려면 마이크를 눌러주세요.");
            }
        }.start();
    }

    private void stopDetectMode() {
        detectMode = false;

        if (detectionTimer != null) {
            detectionTimer.cancel();
            detectionTimer = null;
        }

        btnDetect.setText("◉  울음 분석 시작하기");
        txtStatus.setText("편안한 상태예요");
        yamnetMonitor.stop();
        stopDetectionChargingEffect();
    }

    private void startDetectionChargingEffect() {
        if (detectionProgress == null) return;

        detectionProgress.setVisibility(View.VISIBLE);
        detectionProgress.setIndeterminate(false);
        detectionProgress.setProgress(0);
    }

    private void stopDetectionChargingEffect() {
        if (detectionProgress == null) return;

        detectionProgress.setVisibility(View.INVISIBLE);
        detectionProgress.setProgress(0);
    }

    // 기존 ripple 효과는 원형 충전 게이지가 감지 시간을 표현하므로 사용하지 않습니다.
    private void startRippleAnimation() {
        startDetectionChargingEffect();
    }

    private void stopRippleAnimation() {
        stopDetectionChargingEffect();
    }

    private void requestPrediction() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File wavFile = WavRecorder.recordFiveSeconds(MainActivity.this);

                apiService.predict(wavFile, new CryApiService.PredictCallback() {
                    @Override
                    public void onSuccess(final CryApiService.PredictResponse response) {
                        String label = "없음";
                        float confidence = 0f;

                        if (response != null && response.prediction != null) {
                            label = response.prediction.label;
                            confidence = response.prediction.confidence;
                        }

                        int savedRecordId = -1;
                        try {
                            savedRecordId = localRecordStore.insertRecord(
                                    wavFile,
                                    label,
                                    confidence,
                                    response != null ? response.duration_sec : 5f,
                                    response != null ? response.message : null
                            );
                        } catch (Exception ignored) {
                        }

                        final String finalLabel = label;
                        final float finalConfidence = confidence;
                        final int finalRecordId = savedRecordId;

                        txtStatus.post(() -> {
                            // 서버는 분석만 담당하고, 기록은 태블릿 로컬 DB에 저장합니다.
                            stopDetectMode();

                            currentRecordId = finalRecordId;

                            txtResult.setText(convertLabelToKorean(finalLabel));
                            tvRecentMeta.setText(
                                    String.format(
                                            Locale.KOREA,
                                            "방금 분석 · 신뢰도 %.0f%%",
                                            finalConfidence * 100f
                                    )
                            );

                            btnFeedback.setVisibility(
                                    currentRecordId > 0 ? View.VISIBLE : View.GONE
                            );

                            loadHomeSummary();

                            if (response != null && response.prediction != null) {
                                SoothingAudioManager.playIfAutoEnabled(MainActivity.this);
                                notificationManager.sendCryNotification(
                                        finalLabel,
                                        finalConfidence,
                                        currentRecordId
                                );
                            }
                        });
                    }

                    @Override
                    public void onFailure(final String message) {
                        txtStatus.post(() -> {
                            // 실패 시에도 모드와 이펙트를 자동 종료한 후 에러 메시지를 노출합니다.
                            stopDetectMode();
                            txtStatus.setText(message);
                        });
                    }
                });

            } catch (final Exception e) {
                txtStatus.post(() -> {
                    // 예외 발생 시에도 모드와 이펙트를 자동 종료한 후 에러 메시지를 노출합니다.
                    stopDetectMode();
                    txtStatus.setText("녹음 실패: " + e.getMessage());
                });
            }
        });
    }

    private boolean hasRequiredPermissions() {
        boolean audioGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;

        boolean cameraGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean notifyGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;

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
                txtStatus.setText("편안한 상태예요");
            } else {
                txtStatus.setText("앱을 사용하려면 마이크, 알림, 플래시 권한이 필요합니다.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (detectionTimer != null) {
            detectionTimer.cancel();
            detectionTimer = null;
        }

        super.onDestroy();
        if (yamnetMonitor != null) {
            yamnetMonitor.stop();
        }
        stopRippleAnimation();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }

        IntentFilter filter = new IntentFilter("com.example.cryingbabyanalyzerapp.RESULT_UPDATE");

        ContextCompat.registerReceiver(
                this,
                resultReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );

        updateResultUI();
        updateProfileGreeting();
        loadHomeSummary();

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

    private void updateProfileGreeting() {
        if (tvHomeGreeting == null) return;
        tvHomeGreeting.setText(BabyProfilePreferences.getHomeGreeting(this));
    }

    private void loadHomeSummary() {
        if (localRecordStore == null) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            List<CryApiService.CryRecord> records = localRecordStore.listRecords();

            runOnUiThread(() -> applyHomeSummary(records));
        });
    }

    private void applyHomeSummary(List<CryApiService.CryRecord> records) {
        if (records == null || records.isEmpty()) {
            tvTodayCount.setText("0회");
            tvTopCause.setText("없음");
            if (currentRecordId <= 0) {
                txtResult.setText("아직 분석 기록이 없어요");
                tvRecentMeta.setText("울음을 분석하면 이곳에 최근 결과가 표시됩니다.");
            }
            return;
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA);
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        int todayCount = 0;
        Map<String, Integer> labelCounts = new HashMap<>();

        for (CryApiService.CryRecord record : records) {
            Date created = parseRecordDate(format, record.created_at);
            if (created != null && created.getTime() >= start.getTimeInMillis()) {
                todayCount++;
                if (record.label != null && !record.label.trim().isEmpty()) {
                    labelCounts.put(
                            record.label,
                            labelCounts.getOrDefault(record.label, 0) + 1
                    );
                }
            }
        }

        tvTodayCount.setText(todayCount + "회");

        String topLabel = "없음";
        int topCount = 0;
        for (Map.Entry<String, Integer> entry : labelCounts.entrySet()) {
            if (entry.getValue() > topCount) {
                topCount = entry.getValue();
                topLabel = convertLabelShort(entry.getKey());
            }
        }
        tvTopCause.setText(topLabel);

        CryApiService.CryRecord recent = records.get(0);
        if (currentRecordId <= 0) {
            txtResult.setText(convertLabelToKorean(recent.label));
            currentRecordId = recent.id;
        }

        String meta = recent.created_at != null ? recent.created_at : "최근 분석";
        if (recent.confidence != null) {
            meta += String.format(Locale.KOREA, " · 신뢰도 %.0f%%", recent.confidence * 100f);
        }
        tvRecentMeta.setText(meta);
        btnFeedback.setVisibility(View.VISIBLE);
    }

    private String convertLabelShort(String input) {
        if (input == null || input.isEmpty()) return "없음";

        String lower = input.toLowerCase(Locale.US);
        if (lower.contains("uncomfortable")) return "불편함";
        if (lower.contains("awake")) return "깸";
        if (lower.contains("diaper")) return "기저귀";
        if (lower.contains("hug")) return "안아달람";
        if (lower.contains("hungry")) return "배고픔";
        if (lower.contains("sleepy")) return "졸림";

        return input;
    }

    private Date parseRecordDate(SimpleDateFormat format, String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return format.parse(value);
        } catch (ParseException e) {
            return null;
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
