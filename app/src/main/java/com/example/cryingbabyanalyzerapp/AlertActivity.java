package com.example.cryingbabyanalyzerapp;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public class AlertActivity extends AppCompatActivity {

    private TextView txtAlertCause;
    private Button btnOpenFeedback;
    private Button btnDismissAlert;

    private Vibrator vibrator;
    private CameraManager cameraManager;
    private String flashCameraId;
    private boolean flashOn = false;

    private String label;

    /*
     * 플래시 반복 Runnable
     */
    private Runnable flashRunnable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * 잠금화면에서도 AlertActivity 표시
         */
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );


        setContentView(R.layout.activity_alert);


        txtAlertCause =
                findViewById(R.id.txtAlertCause);

        btnOpenFeedback =
                findViewById(R.id.btnOpenFeedback);

        btnDismissAlert =
                findViewById(R.id.btnDismissAlert);


        btnOpenFeedback.setText(
                "결과 확인 및 피드백 남기기"
        );

        btnDismissAlert.setText(
                "알림 끄기"
        );


        /*
         * 서버 결과 받기
         */
        Intent receivedIntent = getIntent();

        label =
                receivedIntent.getStringExtra("label");


        /*
         * 기존 RESULT_TEXT 호환
         */
        String resultText =
                receivedIntent.getStringExtra(
                        "RESULT_TEXT"
                );


        if ((label == null || label.isEmpty())
                && resultText != null) {

            label = resultText;
        }


        /*
         * 신뢰도는 표시하지 않음
         */
        txtAlertCause.setText(
                convertLabelToKorean(label)
        );


        /*
         * 진동 시작
         */
        startVibration();


        /*
         * 플래시 시작
         */
        startFlashBlink();


        /*
         * 알림 끄기 버튼
         */
        btnDismissAlert.setOnClickListener(v -> {

            stopAlertEffects();

            finish();
        });


        /*
         * 결과 확인 및 피드백
         */
        btnOpenFeedback.setOnClickListener(v -> {

            stopAlertEffects();


            Intent intent =
                    new Intent(
                            AlertActivity.this,
                            FeedbackActivity.class
                    );


            intent.putExtra(
                    "RESULT_TEXT",
                    convertLabelToKorean(label)
            );


            startActivity(intent);

            finish();
        });
    }


    /**
     * 서버에서 받은 영어 label을
     * 사용자에게 보여줄 한글 문장으로 변환
     */
    private String convertLabelToKorean(String input) {

        if (input == null || input.isEmpty()) {
            return "아기가 울고 있어요.";
        }


        String lower =
                input.toLowerCase(Locale.US);


        if (lower.contains("uncomfortable")) {
            return "아기가 불편함을 느끼고 있어요.";
        }


        if (lower.contains("awake")) {
            return "아기가 깼어요.";
        }


        if (lower.contains("diaper")) {
            return "아기 기저귀를 확인해주세요.";
        }


        if (lower.contains("hug")) {
            return "아기가 안아달라고 보채고 있어요.";
        }


        if (lower.contains("hungry")) {
            return "아기가 배고파요.";
        }


        if (lower.contains("sleepy")) {
            return "아기가 졸려요.";
        }


        return input;
    }


    /**
     * 진동 시작
     */
    private void startVibration() {

        vibrator =
                (Vibrator) getSystemService(
                        Context.VIBRATOR_SERVICE
                );


        if (vibrator == null) {
            return;
        }


        /*
         * Android 13 이상에서도 진동 권한이 있는지 확인
         */
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.VIBRATE
            ) != PackageManager.PERMISSION_GRANTED) {

                return;
            }
        }


        /*
         * 반복 진동 패턴
         *
         * 0ms 대기
         * 500ms 진동
         * 200ms 정지
         * 500ms 진동
         * 200ms 정지
         * 500ms 진동
         * 500ms 정지
         * 800ms 진동
         */
        long[] pattern =
                new long[]{
                        0,
                        500,
                        200,
                        500,
                        200,
                        500,
                        500,
                        800
                };


        try {

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O) {

                VibrationEffect effect =
                        VibrationEffect.createWaveform(
                                pattern,
                                0
                        );

                vibrator.vibrate(effect);

            } else {

                vibrator.vibrate(
                        pattern,
                        0
                );
            }

        } catch (Exception ignored) {
        }
    }


    /**
     * 카메라 플래시 깜빡임 시작
     */
    private void startFlashBlink() {

        cameraManager =
                (CameraManager) getSystemService(
                        Context.CAMERA_SERVICE
                );


        if (cameraManager == null
                || Build.VERSION.SDK_INT <
                Build.VERSION_CODES.M) {

            return;
        }


        try {

            for (String id :
                    cameraManager.getCameraIdList()) {

                CameraCharacteristics characteristics =
                        cameraManager.getCameraCharacteristics(id);


                Boolean hasFlash =
                        characteristics.get(
                                CameraCharacteristics
                                        .FLASH_INFO_AVAILABLE
                        );


                if (hasFlash != null && hasFlash) {

                    flashCameraId = id;

                    break;
                }
            }


            if (flashCameraId == null) {
                return;
            }


            blinkFlashLoop();

        } catch (Exception ignored) {
        }
    }


    /**
     * 플래시 깜빡임 반복
     */
    private void blinkFlashLoop() {

        if (flashCameraId == null) {
            return;
        }


        flashRunnable =
                new Runnable() {

                    @Override
                    public void run() {

                        if (isFinishing()
                                || isDestroyed()
                                || flashCameraId == null) {

                            return;
                        }


                        try {

                            flashOn = !flashOn;


                            cameraManager.setTorchMode(
                                    flashCameraId,
                                    flashOn
                            );

                        } catch (CameraAccessException ignored) {
                        }


                        txtAlertCause.postDelayed(
                                this,
                                500
                        );
                    }
                };


        txtAlertCause.postDelayed(
                flashRunnable,
                300
        );
    }


    /**
     * 진동 / 플래시 / 알림 효과 종료
     *
     * 사용자가 실제로 "알림 끄기"를 눌렀을 때 호출합니다.
     */
    private void stopAlertEffects() {

        try {

            /*
             * 진동 종료
             */
            if (vibrator != null) {
                vibrator.cancel();
            }


            /*
             * 플래시 반복 종료
             */
            if (flashRunnable != null) {

                txtAlertCause.removeCallbacks(
                        flashRunnable
                );

                flashRunnable = null;
            }


            /*
             * 플래시 끄기
             */
            if (cameraManager != null
                    && flashCameraId != null
                    && Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.M) {

                cameraManager.setTorchMode(
                        flashCameraId,
                        false
                );

                flashOn = false;
            }


            /*
             * 상단 알림 제거
             */
            NotificationManager manager =
                    (NotificationManager)
                            getSystemService(
                                    Context.NOTIFICATION_SERVICE
                            );


            if (manager != null) {

                manager.cancel(
                        CryNotificationManager
                                .ALERT_NOTIFICATION_ID
                );
            }


        } catch (Exception ignored) {
        }
    }


    /*
     * 중요:
     *
     * 기존에는 onPause()에서
     * stopAlertEffects()를 호출했습니다.
     *
     * 그러면 Activity 상태 변화만으로
     * 진동과 플래시가 바로 꺼질 수 있습니다.
     *
     * 따라서 onPause()에서는 아무것도
     * 종료하지 않습니다.
     */
    @Override
    protected void onPause() {

        super.onPause();
    }


    @Override
    protected void onDestroy() {

        /*
         * Activity가 실제로 종료될 때만
         * 효과를 정리합니다.
         */
        stopAlertEffects();

        super.onDestroy();
    }
}

