# Smart Sleep Timer ProGuard / R8 Rules

# Preserve custom views accessed via XML layout inflation
-keep class com.smartsleeptimer.app.ui.CircularTimerView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Preserve Foreground Service, Notification Listener Service, and Broadcast Receiver
-keep class com.smartsleeptimer.app.service.SleepTimerService { *; }
-keep class com.smartsleeptimer.app.service.TimerCompletionReceiver { *; }
-keep class com.smartsleeptimer.app.media.MediaNotificationListenerService { *; }

# Preserve Models, Enums, and Result types
-keep class com.smartsleeptimer.app.model.** { *; }
-keep class com.smartsleeptimer.app.media.MediaControlResult* { *; }
-keep class com.smartsleeptimer.app.media.MediaSessionInfo { *; }

# Preserve ViewModels
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(...);
}
