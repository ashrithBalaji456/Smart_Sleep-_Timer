package com.smartsleeptimer.app.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import java.util.Set;

/**
 * Helper utility to inspect and request Android Notification Listener access,
 * which is required by Android's MediaSessionManager to detect and control active media sessions.
 */
public final class MediaAccessHelper {

    private MediaAccessHelper() {
        // Utility class
    }

    /**
     * Checks whether the user has enabled notification listener access for this app in Android Settings.
     */
    public static boolean isNotificationListenerAccessGranted(@NonNull Context context) {
        Set<String> enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context);
        return enabledPackages.contains(context.getPackageName());
    }

    /**
     * Creates an intent directed to the system settings page where the user can grant
     * notification listener access.
     */
    @NonNull
    public static Intent createNotificationListenerSettingsIntent() {
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    }

    /**
     * Returns the ComponentName for the app's MediaNotificationListenerService.
     */
    @NonNull
    public static ComponentName getNotificationListenerComponentName(@NonNull Context context) {
        return new ComponentName(context, MediaNotificationListenerService.class);
    }
}
