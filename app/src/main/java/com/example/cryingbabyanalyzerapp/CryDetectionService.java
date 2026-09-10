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
import java.util.concurrent.atomic.AtomicBoolean;

public class CryDetectionService extends Service {

    public static String lastResultText = "";

    private YamnetMonitor yamnetMonitor;
    private CryApiService apiService;
    private CryNotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;

    private static final int ONGOING_NOTIFICATION_ID = 100;

    /*
     * 울음 결과를 보낸 후 30초 동안 새로운 감지를 무시합니다.
     */
    private static final long COOLDOWN_MS = 30_000L;

    /*
     * 서비스가 실행 중인지 여부
     */
    public static boolean isRunning = false;

    /*
     * 현재 서버 분석을 진행 중인지 여부
     *
     * true인 동안에는 새로운 울음 감지를 무시합니다.
     */
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    /*
     * 서버 분석 결과를 받은 후 30초 동안 true
     */
    private volatile boolean isCooldown = false;

    /*
     * Cooldown 종료를 위한 Runnable
     */
    private Runnable cooldownRunnable;


    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * 백그라운드에서 CPU가 잠들지 않도록 WakeLock 유지
         */
        PowerManager pm =
                (PowerManager) getSystemService(Context.POWER_SERVICE);

        if (pm != null) {
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "CryAnalyzer::BackgroundCpuLock"
            );

            wakeLock.acquire();
        }

        /*
         * 서버 API
         */
        apiService = new CryApiService(BuildConfig.SERVER_IP);

        /*
         * 알림 관리자
         */
        notificationManager = new CryNotificationManager(this);

        /*
         * YAMNet 설정
         */
        setupYamnet();

        isRunning = true;
    }


    /**
     * YAMNet 감지기 설정
     */
    private void setupYamnet() {

        yamnetMonitor = new YamnetMonitor(
                this,
                new YamnetMonitor.Listener() {

                    @Override
                    public void onStatus(String message) {
                        // 필요하면 로그 출력
                    }


                    @Override
                    public void onCryDetected() {

                        /*
                         * =====================================================
                         * 1. 이미 서버 분석 중이면 무시
                         * =====================================================
                         */
                        if (isProcessing.get()) {
                            return;
                        }


                        /*
                         * =====================================================
                         * 2. Cooldown 중이면 무시
                         * =====================================================
                         */
                        if (isCooldown) {
                            return;
                        }


                        /*
                         * =====================================================
                         * 3. 분석 시작
                         *
                         * false -> true로 원자적으로 변경합니다.
                         *
                         * 여러 감지 이벤트가 동시에 들어와도
                         * 하나만 requestPrediction()을 실행합니다.
                         * =====================================================
                         */
                        if (!isProcessing.compareAndSet(false, true)) {
                            return;
                        }


                        requestPrediction();
                    }


                    @Override
                    public void onError(String message) {
                        // 필요하면 로그 출력
                    }
                }
        );
    }


    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        /*
         * Foreground Service 알림
         */
        Notification notification =
                new NotificationCompat.Builder(
                        this,
                        "BabyCryAlertChannel"
                )
                        .setContentTitle("아기 상태 감시 중")
                        .setContentText(
                                "백그라운드에서 소리를 상시 감지하고 있습니다."
                        )
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setOngoing(true)
                        .setForegroundServiceBehavior(
                                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                        )
                        .build();

        startForeground(
                ONGOING_NOTIFICATION_ID,
                notification
        );


        /*
         * YAMNet 시작
         */
        if (yamnetMonitor != null &&
                !isProcessing.get() &&
                !isCooldown) {

            yamnetMonitor.start();
        }

        return START_STICKY;
    }


    /**
     * 울음 감지 후 서버 분석 요청
     */
    private void requestPrediction() {

        /*
         * 혹시 다른 경로에서 직접 호출되는 경우에도
         * 중복 분석 방지
         */
        if (isCooldown) {
            isProcessing.set(false);
            return;
        }


        /*
         * Executor가 없으면 분석을 실행하지 않음
         */
        if (isRunning == false) {
            isProcessing.set(false);
            return;
        }


        /*
         * 기존에 YAMNet이 이미 stop()된 상태이므로
         * 여기서는 5초 녹음만 진행합니다.
         */
        Executors.newSingleThreadExecutor().execute(() -> {

            try {

                /*
                 * =====================================================
                 * 1. 울음 감지 후 5초간 녹음
                 * =====================================================
                 */
                File wavFile =
                        WavRecorder.recordFiveSeconds(this);


                /*
                 * =====================================================
                 * 2. 서버에 분석 요청
                 * =====================================================
                 */
                apiService.predict(
                        wavFile,
                        new CryApiService.PredictCallback() {

                            @Override
                            public void onSuccess(
                                    CryApiService.PredictResponse response
                            ) {

                                try {

                                    String label = "없음";
                                    float confidence = 0f;


                                    /*
                                     * 서버 결과 확인
                                     */
                                    if (response != null &&
                                            response.prediction != null) {

                                        label =
                                                response.prediction.label;

                                        confidence =
                                                response.prediction.confidence;
                                    }


                                    /*
                                     * =================================================
                                     * 3. 분석 결과 저장
                                     * =================================================
                                     */
                                    lastResultText = label;


                                    /*
                                     * =================================================
                                     * 4. MainActivity에 결과 전달
                                     * =================================================
                                     */
                                    Intent broadcastIntent =
                                            new Intent(
                                                    "com.example.cryingbabyanalyzerapp.RESULT_UPDATE"
                                            );

                                    broadcastIntent.setPackage(
                                            getPackageName()
                                    );

                                    sendBroadcast(
                                            broadcastIntent
                                    );


                                    /*
                                     * =================================================
                                     * 5. 알림은 딱 한 번만 전송
                                     * =================================================
                                     */
                                    if (response != null &&
                                            response.prediction != null &&
                                            notificationManager != null) {

                                        notificationManager
                                                .sendCryNotification(
                                                        label,
                                                        confidence
                                                );
                                    }


                                    /*
                                     * =================================================
                                     * 6. 분석 완료
                                     * =================================================
                                     */
                                    isProcessing.set(false);


                                    /*
                                     * =================================================
                                     * 7. 여기서부터 30초 Cooldown 시작
                                     * =================================================
                                     *
                                     * 서버 결과가 나온 순간부터 30초입니다.
                                     */
                                    startCooldown();


                                } catch (Exception e) {

                                    /*
                                     * 결과 처리 중 예외가 발생해도
                                     * 반드시 분석 상태를 초기화합니다.
                                     */
                                    isProcessing.set(false);

                                    /*
                                     * 예외가 발생한 경우에는
                                     * 감지를 다시 시작할 수 있도록 합니다.
                                     */
                                    if (isRunning) {
                                        startYamnet();
                                    }
                                }
                            }


                            @Override
                            public void onFailure(String message) {

                                /*
                                 * 서버 요청 실패
                                 */
                                isProcessing.set(false);


                                /*
                                 * 서버 분석에 실패했다면
                                 * 이번에는 Cooldown을 적용하지 않고
                                 * 다시 감지하도록 합니다.
                                 */
                                if (isRunning) {
                                    startYamnet();
                                }
                            }
                        }
                );

            } catch (Exception e) {

                /*
                 * 녹음 자체가 실패한 경우
                 */
                isProcessing.set(false);

                if (isRunning) {
                    startYamnet();
                }
            }
        });
    }


    /**
     * 30초 Cooldown 시작
     */
    private void startCooldown() {

        /*
         * Cooldown 상태로 변경
         */
        isCooldown = true;


        /*
         * 혹시 실행 중이라면 YAMNet 완전히 중지
         */
        if (yamnetMonitor != null) {
            yamnetMonitor.stop();
        }


        /*
         * 기존 Cooldown Runnable 제거
         */
        if (cooldownRunnable != null) {
            cooldownRunnable = null;
        }


        /*
         * 30초 후 다시 감지 시작
         */
        cooldownRunnable = new Runnable() {

            @Override
            public void run() {

                /*
                 * 서비스가 종료된 경우 아무것도 하지 않음
                 */
                if (!isRunning) {
                    return;
                }


                /*
                 * Cooldown 종료
                 */
                isCooldown = false;


                /*
                 * YAMNet 다시 시작
                 */
                startYamnet();
            }
        };


        /*
         * 30초 후 실행
         */
        android.os.Handler handler =
                new android.os.Handler(
                        android.os.Looper.getMainLooper()
                );

        handler.postDelayed(
                cooldownRunnable,
                COOLDOWN_MS
        );
    }


    /**
     * YAMNet 다시 시작
     */
    private void startYamnet() {

        /*
         * 분석 중이면 시작하지 않음
         */
        if (isProcessing.get()) {
            return;
        }


        /*
         * Cooldown 중이면 시작하지 않음
         */
        if (isCooldown) {
            return;
        }


        /*
         * 서비스가 종료됐으면 시작하지 않음
         */
        if (!isRunning) {
            return;
        }


        /*
         * YAMNet 시작
         */
        if (yamnetMonitor != null) {
            yamnetMonitor.start();
        }
    }


    @Override
    public void onDestroy() {

        /*
         * 서비스 종료
         */
        isRunning = false;


        /*
         * 분석 상태 초기화
         */
        isProcessing.set(false);


        /*
         * Cooldown 해제
         */
        isCooldown = false;


        /*
         * YAMNet 종료
         */
        if (yamnetMonitor != null) {
            yamnetMonitor.stop();
            yamnetMonitor = null;
        }


        /*
         * WakeLock 해제
         */
        if (wakeLock != null &&
                wakeLock.isHeld()) {

            wakeLock.release();
            wakeLock = null;
        }


        super.onDestroy();
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

