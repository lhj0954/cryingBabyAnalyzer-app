package com.example.cryingbabyanalyzerapp;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.util.concurrent.Executors;

public class CryDetectionService extends Service {

    public static String lastResultText = "";
    private YamnetMonitor yamnetMonitor;
    private CryApiService apiService;
    private CryNotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;
    private static final int ONGOING_NOTIFICATION_ID = 100;

    public static boolean isRunning = false;

    // 💡 [핵심 추가] 중복 API 호출 및 녹음을 막기 위한 플래그 변수
    private boolean isProcessing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CryAnalyzer::BackgroundCpuLock");
            wakeLock.acquire();
        }

        apiService = new CryApiService(BuildConfig.SERVER_IP);
        notificationManager = new CryNotificationManager(this);
        setupYamnet();

        isRunning = true;
    }

    private void setupYamnet() {
        yamnetMonitor = new YamnetMonitor(this, new YamnetMonitor.Listener() {
            @Override
            public void onStatus(String message) {}

            @Override
            public void onCryDetected() {
                // 💡 [추가] 이미 서버 분석 프로세스가 진행 중이라면 새로운 감지 신호는 무시합니다.
                if (isProcessing) return;
                requestPrediction();
            }

            @Override
            public void onError(String message) {}
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, "BabyCryAlertChannel")
                .setContentTitle("아기 상태 감시 중")
                .setContentText("백그라운드에서 소리를 상시 감지하고 있습니다.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setForegroundServiceBehavior(androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();

        startForeground(ONGOING_NOTIFICATION_ID, notification);

        if (yamnetMonitor != null) yamnetMonitor.start();

        return START_STICKY;
    }

    private void requestPrediction() {
        // 💡 [추가] 메서드 진입 시 중복 진입 방지용 더블 체크 잠금
        if (isProcessing) return;
        isProcessing = true;

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File wavFile = WavRecorder.recordFiveSeconds(this);
                apiService.predict(wavFile, new CryApiService.PredictCallback() {
                    @Override
                    public void onSuccess(CryApiService.PredictResponse response) {
                        String label = "없음";
                        float confidence = 0f;

                        if (response != null && response.prediction != null) {
                            label = response.prediction.label;
                            confidence = response.prediction.confidence;

                            String resultText =
                                    "label = " + label + "\n" +
                                            "confidence = " + String.format(java.util.Locale.US, "%.3f", confidence);

                            notificationManager.sendCryNotification(label, confidence);

                            Intent alertIntent = new Intent(CryDetectionService.this, AlertActivity.class);
                            alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                            // AlertActivity에서 확실하게 받을 값들
                            alertIntent.putExtra("label", label);
                            alertIntent.putExtra("confidence", confidence);
                            alertIntent.putExtra("RESULT_TEXT", resultText);

                            startActivity(alertIntent);
                        }if (response != null && response.prediction != null) {
                            label = response.prediction.label;
                            confidence = response.prediction.confidence;

                            String resultText =
                                    "label = " + label + "\n" +
                                            "confidence = " + String.format(java.util.Locale.US, "%.3f", confidence);

                            notificationManager.sendCryNotification(label, confidence);

                            Intent alertIntent = new Intent(CryDetectionService.this, AlertActivity.class);
                            alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                            // AlertActivity에서 확실하게 받을 값들
                            alertIntent.putExtra("label", label);
                            alertIntent.putExtra("confidence", confidence);
                            alertIntent.putExtra("RESULT_TEXT", resultText);

                            startActivity(alertIntent);
                        }

                        // 1. 메인 화면의 한글 변환 가이드와 정상 연동되도록 깔끔한 영어 라벨만 변수에 기억
                        lastResultText = label;

                        // 2. MainActivity로 결과 브로드캐스트 전송 (실시간 화면 갱신)
                        Intent broadcastIntent = new Intent("com.example.cryingbabyanalyzerapp.RESULT_UPDATE");
                        broadcastIntent.setPackage(getPackageName());
                        sendBroadcast(broadcastIntent);

                        if (response != null && response.prediction != null) {
                            // 3. 실제 알림 발송 (폰 상단 팝업)
                            notificationManager.sendCryNotification(label, confidence);
                        }

                        // 💡 [핵심 변경] 모든 처리가 완전히 끝난 후 플래그를 풀고 YAMNet을 재시작합니다.
                        isProcessing = false;
                        if (isRunning && yamnetMonitor != null) {
                            yamnetMonitor.start();
                        }
                    }

                    @Override
                    public void onFailure(String message) {
                        // 💡 실패 시에도 안전하게 플래그를 해제하고 재가동합니다.
                        isProcessing = false;
                        if (isRunning && yamnetMonitor != null) {
                            yamnetMonitor.start();
                        }
                    }
                });
            } catch (Exception e) {
                // 💡 예외 발생 시에도 안전하게 플래그를 해제하고 재가동합니다.
                isProcessing = false;
                if (isRunning && yamnetMonitor != null) {
                    yamnetMonitor.start();
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (yamnetMonitor != null) yamnetMonitor.stop();

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        isRunning = false;
        isProcessing = false; // 서비스 종료 시 플래그 초기화
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}