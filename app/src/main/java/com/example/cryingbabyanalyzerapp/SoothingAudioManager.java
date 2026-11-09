package com.example.cryingbabyanalyzerapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

public final class SoothingAudioManager {

    public static final String ACTION_PLAYBACK_STATE_CHANGED =
            "com.example.cryingbabyanalyzerapp.SOOTHING_PLAYBACK_STATE_CHANGED";

    private static final String PREFS_NAME = "soothing_settings";
    private static final String KEY_AUTO_PLAY = "auto_play";
    private static final String KEY_AUDIO_URI = "audio_uri";
    private static final String KEY_AUDIO_NAME = "audio_name";
    private static final String KEY_DURATION_SECONDS = "duration_seconds";

    private static final int DEFAULT_DURATION_SECONDS = 60;

    private static Context appContext;
    private static MediaPlayer mediaPlayer;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable stopRunnable;

    private SoothingAudioManager() {
    }

    public static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isAutoPlayEnabled(Context context) {
        return getPreferences(context).getBoolean(KEY_AUTO_PLAY, false);
    }

    public static void setAutoPlayEnabled(Context context, boolean enabled) {
        getPreferences(context)
                .edit()
                .putBoolean(KEY_AUTO_PLAY, enabled)
                .apply();
    }

    public static String getSelectedAudioUri(Context context) {
        return getPreferences(context).getString(KEY_AUDIO_URI, "");
    }

    public static String getSelectedAudioName(Context context) {
        return getPreferences(context).getString(KEY_AUDIO_NAME, "");
    }

    public static void setSelectedAudio(Context context, String uri, String name) {
        getPreferences(context)
                .edit()
                .putString(KEY_AUDIO_URI, uri == null ? "" : uri)
                .putString(KEY_AUDIO_NAME, name == null ? "" : name)
                .apply();
    }

    public static int getDurationSeconds(Context context) {
        return getPreferences(context)
                .getInt(KEY_DURATION_SECONDS, DEFAULT_DURATION_SECONDS);
    }

    public static void setDurationSeconds(Context context, int seconds) {
        getPreferences(context)
                .edit()
                .putInt(KEY_DURATION_SECONDS, Math.max(10, seconds))
                .apply();
    }

    public static boolean hasSelectedAudio(Context context) {
        String uri = getSelectedAudioUri(context);
        return uri != null && !uri.trim().isEmpty();
    }

    public static boolean playIfAutoEnabled(Context context) {
        if (!isAutoPlayEnabled(context) || !hasSelectedAudio(context)) {
            return false;
        }
        return play(context);
    }

    public static synchronized boolean play(Context context) {
        String uriText = getSelectedAudioUri(context);
        if (uriText == null || uriText.trim().isEmpty()) {
            return false;
        }

        appContext = context.getApplicationContext();
        stop();

        try {

            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
            );
            player.setDataSource(appContext, Uri.parse(uriText));
            player.setLooping(true);
            player.setOnErrorListener((mp, what, extra) -> {
                stop();
                return true;
            });

            mediaPlayer = player;
            player.prepare();
            player.start();
            notifyPlaybackStateChanged();

            int durationSeconds = getDurationSeconds(appContext);
            stopRunnable = SoothingAudioManager::stop;
            handler.postDelayed(stopRunnable, durationSeconds * 1000L);

            return true;
        } catch (Exception e) {
            stop();
            return false;
        }
    }

    public static synchronized void stop() {
        boolean hadPlayer = mediaPlayer != null;
        if (stopRunnable != null) {
            handler.removeCallbacks(stopRunnable);
            stopRunnable = null;
        }

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {
            }

            try {
                mediaPlayer.reset();
            } catch (Exception ignored) {
            }

            try {
                mediaPlayer.release();
            } catch (Exception ignored) {
            }

            mediaPlayer = null;
        }
        if (hadPlayer) {
            notifyPlaybackStateChanged();
        }
    }

    private static void notifyPlaybackStateChanged() {
        if (appContext == null) return;
        Intent intent = new Intent(ACTION_PLAYBACK_STATE_CHANGED);
        intent.setPackage(appContext.getPackageName());
        appContext.sendBroadcast(intent);
    }

    public static synchronized boolean isPlaying() {
        if (mediaPlayer == null) {
            return false;
        }

        try {
            return mediaPlayer.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }
}
