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

    // 채널 생성 (내부에서만 사용하므로 private)
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "아기 울음 감지 알림 (긴급)";
            String description = "화면이 꺼져있어도 울음소리를 즉시 알려줍니다.";

            // ⭐ IMPORTANCE_HIGH: 소리/진동 발생 및 헤드업 팝업 허용
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(description);

            // ⭐ 진동 및 잠금화면 표시 강제 설정
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500}); // 징~ 징~ 징~
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); // 잠금화면에서 내용 숨기지 않음

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    // 외부(MainActivity 등)에서 알림을 보낼 때 호출하는 메서드
    public void sendCryNotification(String label, float confidence) {
        // 1. 인텐트 설정
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // ⭐ 2. 까만 화면 강제로 켜기 (WakeLock)
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            PowerManager.WakeLock wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "CryAnalyzer::UrgentAlertWakeLock"
            );
            // 5초 동안만 화면을 켜고 스스로 꺼지게 합니다 (배터리 절약)
            wakeLock.acquire(5000);
        }

        // 3. 알림 디자인 및 설정
        String contentText = "분석 결과: " + label + " (" + (int)(confidence * 100) + "%)";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("🚨 아기가 울고 있어요!")
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_MAX) // 최우선 순위
                .setDefaults(NotificationCompat.DEFAULT_ALL)  // 시스템 기본 소리/진동 사용
                .setFullScreenIntent(pendingIntent, true)     // ⭐ 잠금화면을 뚫고 나오는 핵심 설정
                .setAutoCancel(true);

        // 4. 알림 전송 (권한 재확인)
        if (notificationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            // 알림이 씹히지 않도록 현재 시간을 ID로 사용
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}
