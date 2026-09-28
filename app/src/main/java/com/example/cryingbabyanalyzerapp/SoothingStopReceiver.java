package com.example.cryingbabyanalyzerapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SoothingStopReceiver extends BroadcastReceiver {

    public static final String ACTION_STOP_SOOTHING =
            "com.example.cryingbabyanalyzerapp.ACTION_STOP_SOOTHING";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null
                && ACTION_STOP_SOOTHING.equals(intent.getAction())) {
            SoothingAudioManager.stop();
        }
    }
}
