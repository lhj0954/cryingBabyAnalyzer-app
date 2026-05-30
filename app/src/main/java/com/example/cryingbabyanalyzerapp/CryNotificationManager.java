package com.example.cryingbabyanalyzerapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import java.util.Locale;

public class CryNotificationManager {

    private static final String CHANNEL_ID = "BabyCryAlertChannel";
    private final Context context;
    private final NotificationManager notificationManager;

    // 생성자: 객체가 만들어질 때 자동으로 채널을 생성합니다.
    public CryNotificationManager(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    // 💡 [추가] 영문 라벨 명을 메인 화면과 똑같은 한국어 가이드 문구로 변경하는 함수
    private String convertLabelToKorean(String input) {
        if (input == null || input.isEmpty()) return "아기가 울고 있어요.";

        String lower = input.toLowerCase(Locale.US);
        if (lower.contains("uncomfortable")) return "아기가 불편함을 느끼고 있어요.";
        if (lower.contains("awake")) return "아기가 깼어요.";
        if (lower.contains("diaper")) return "아기 기저귀를 확인해주세요.";
        if (lower.contains("hug")) return "아기가 안아달라고 보채고 있어요.";
        if (lower.contains("hungry")) return "아기가 배고파요.";
        if (lower.contains("sleepy")) return "아기가 졸려요.";

        return input; // 매칭되지 않는 기본 텍스트 보호
    }

    // 채널 생성
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "아기 울음 감지 알림 (긴급)";
            String description = "화면이 꺼져있어도 울음소리를 즉시 알려줍니다.";

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(description);

            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500}); // 징~ 징~ 징~
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    // 외부(MainActivity 및 CryDetectionService)에서 알림을 보낼 때 호출하는 메서드
    public void sendCryNotification(String label, float confidence) {
        // 1. 인텐트 설정
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // 2. 까만 화면 강제로 켜기 (WakeLock)
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            PowerManager.WakeLock wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "CryAnalyzer::UrgentAlertWakeLock"
            );
            wakeLock.acquire(5000); // 5초 동안 화면 켬
        }

        // 💡 [수정] 복잡한 수치(Confidence) 및 영어 라벨을 없애고 직관적인 한국어 문구만 표시되도록 수정
        String contentText = convertLabelToKorean(label);

        // 3. 알림 디자인 및 설정
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("🚨 아기 울음 감지!")
                .setContentText(contentText) // 💡 변환된 한국어 문구가 들어갑니다.
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true);

        // 4. 알림 전송 (권한 재확인)
        if (notificationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}