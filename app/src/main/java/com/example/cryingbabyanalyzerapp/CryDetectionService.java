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

    @Override
    public void onCreate() {
        super.onCreate();
        // 초기화
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CryAnalyzer::BackgroundCpuLock");
            wakeLock.acquire(); // 잠들기 방지 시작!
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
                // 백그라운드에서 감지 시 서버 분석 시작
                requestPrediction();
            }

            @Override
            public void onError(String message) {}
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 1. 시스템에게 서비스 시작을 알리는 상시 알림 띄우기 (포그라운드 서비스 필수 조건)
        Notification notification = new NotificationCompat.Builder(this, "BabyCryAlertChannel")
                .setContentTitle("아기 울음 감지 중")
                .setContentText("백그라운드에서 소리를 감지하고 있습니다.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setForegroundServiceBehavior(androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();

        startForeground(ONGOING_NOTIFICATION_ID, notification);

        // 2. 감지 시작
        if (yamnetMonitor != null) yamnetMonitor.start();

        return START_STICKY; // 서비스가 강제 종료되어도 시스템이 다시 살려줌
    }

    private void requestPrediction() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File wavFile = WavRecorder.recordFiveSeconds(this);
                apiService.predict(wavFile, new CryApiService.PredictCallback() {
                    @Override
                    public void onSuccess(CryApiService.PredictResponse response) {
                        // ⭐ 1. 화면에 표시할 예쁜 텍스트 만들기
                        String resultStr = response.message + "\n" +
                                "label = " + response.prediction.label + "\n" +
                                "confidence = " + String.format(java.util.Locale.US, "%.3f", response.prediction.confidence);

                        // ⭐ 2. 변수에 기억해두기 (알림을 누르고 앱에 들어올 때를 대비)
                        lastResultText = resultStr;

                        // ⭐ 3. MainActivity로 결과가 나왔다고 방송(Broadcast) 쏘기
                        Intent broadcastIntent = new Intent("com.example.cryingbabyanalyzerapp.RESULT_UPDATE");
                        sendBroadcast(broadcastIntent);

                        if (response != null && response.prediction != null) {
                            // 실제 알림 발송 (폰 상단에 팝업 뜸)
                            notificationManager.sendCryNotification(
                                    response.prediction.label,
                                    response.prediction.confidence
                            );
                        }
                        yamnetMonitor.start(); // 다음 감지를 위해 재시작
                    }

                    @Override
                    public void onFailure(String message) {
                        yamnetMonitor.start();
                    }
                });
            } catch (Exception e) {
                yamnetMonitor.start();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (yamnetMonitor != null) yamnetMonitor.stop();

        // ⭐ [추가] 서비스가 종료되면 CPU 잠금을 풀어줍니다.
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        isRunning = false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}