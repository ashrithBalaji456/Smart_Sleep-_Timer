package com.smartsleeptimer.app.media;

import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.util.Log;

/**
 * Android NotificationListenerService required by MediaSessionManager to query and control
 * active media sessions across external applications.
 */
public class MediaNotificationListenerService extends NotificationListenerService {

    private static final String TAG = "MediaNotifListener";
    private static volatile boolean isServiceConnected = false;

    public static boolean isConnected() {
        return isServiceConnected;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        isServiceConnected = true;
        Log.i(TAG, "Notification listener connected - Media session access is active.");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        isServiceConnected = false;
        Log.i(TAG, "Notification listener disconnected.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
