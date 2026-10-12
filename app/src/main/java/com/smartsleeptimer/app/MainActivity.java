package com.smartsleeptimer.app;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.smartsleeptimer.app.media.MediaAccessHelper;
import com.smartsleeptimer.app.media.MediaControlResult;
import com.smartsleeptimer.app.media.MediaSessionInfo;
import com.smartsleeptimer.app.model.TimerState;
import com.smartsleeptimer.app.ui.CircularTimerView;
import com.smartsleeptimer.app.ui.CustomDurationDialog;
import com.smartsleeptimer.app.util.TimeFormatter;
import com.smartsleeptimer.app.viewmodel.MediaControlViewModel;
import com.smartsleeptimer.app.viewmodel.SleepTimerViewModel;

import java.util.concurrent.TimeUnit;

/**
 * Main Activity of Smart Sleep Timer.
 * Coordinates the sleep timer (Phase 1) and external media playback control inspection (Phase 2).
 */
public class MainActivity extends AppCompatActivity {

    // ViewModels
    private SleepTimerViewModel timerViewModel;
    private MediaControlViewModel mediaViewModel;

    // Phase 1: Timer UI Components
    private CircularTimerView circularTimerView;
    private TextView tvCountdownDisplay;
    private TextView tvTargetDurationLabel;
    private TextView tvStatusBadge;

    // Action Buttons
    private Button btnStart;
    private LinearLayout layoutActiveControls;
    private Button btnPause;
    private Button btnResume;
    private Button btnCancel;

    // Preset Buttons
    private Button btnPreset15m;
    private Button btnPreset30m;
    private Button btnPreset45m;
    private Button btnPreset1h;
    private Button btnPreset2h;
    private Button btnPresetCustom;
    private Button btnPreset30sTest;

    // Phase 2: Media Control Section Views
    private TextView tvMediaAccessBadge;
    private LinearLayout layoutPermissionPrompt;
    private Button btnGrantAccess;
    private TextView tvDetectedAppName;
    private TextView tvDetectedTrackInfo;
    private Button btnRefreshSessions;
    private Button btnTestPauseAudio;
    private TextView tvMediaResultMessage;

    private static final long DURATION_15M = TimeUnit.MINUTES.toMillis(15);
    private static final long DURATION_30M = TimeUnit.MINUTES.toMillis(30);
    private static final long DURATION_45M = TimeUnit.MINUTES.toMillis(45);
    private static final long DURATION_1H = TimeUnit.HOURS.toMillis(1);
    private static final long DURATION_2H = TimeUnit.HOURS.toMillis(2);
    private static final long DURATION_30S = TimeUnit.SECONDS.toMillis(30);

    private long currentSelectedDuration = DURATION_30M;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupViewModels();
        setupListeners();
    }

    private void initViews() {
        // Phase 1 views
        circularTimerView = findViewById(R.id.circularTimerView);
        tvCountdownDisplay = findViewById(R.id.tvCountdownDisplay);
        tvTargetDurationLabel = findViewById(R.id.tvTargetDurationLabel);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);

        btnStart = findViewById(R.id.btnStart);
        layoutActiveControls = findViewById(R.id.layoutActiveControls);
        btnPause = findViewById(R.id.btnPause);
        btnResume = findViewById(R.id.btnResume);
        btnCancel = findViewById(R.id.btnCancel);

        btnPreset15m = findViewById(R.id.btnPreset15m);
        btnPreset30m = findViewById(R.id.btnPreset30m);
        btnPreset45m = findViewById(R.id.btnPreset45m);
        btnPreset1h = findViewById(R.id.btnPreset1h);
        btnPreset2h = findViewById(R.id.btnPreset2h);
        btnPresetCustom = findViewById(R.id.btnPresetCustom);
        btnPreset30sTest = findViewById(R.id.btnPreset30sTest);

        // Phase 2 views
        tvMediaAccessBadge = findViewById(R.id.tvMediaAccessBadge);
        layoutPermissionPrompt = findViewById(R.id.layoutPermissionPrompt);
        btnGrantAccess = findViewById(R.id.btnGrantAccess);
        tvDetectedAppName = findViewById(R.id.tvDetectedAppName);
        tvDetectedTrackInfo = findViewById(R.id.tvDetectedTrackInfo);
        btnRefreshSessions = findViewById(R.id.btnRefreshSessions);
        btnTestPauseAudio = findViewById(R.id.btnTestPauseAudio);
        tvMediaResultMessage = findViewById(R.id.tvMediaResultMessage);
    }

    private void setupViewModels() {
        // Phase 1: Sleep Timer ViewModel
        timerViewModel = new ViewModelProvider(this).get(SleepTimerViewModel.class);

        timerViewModel.getRemainingMillis().observe(this, remainingMillis -> {
            if (remainingMillis != null) {
                tvCountdownDisplay.setText(TimeFormatter.formatTime(remainingMillis));
            }
        });

        timerViewModel.getProgressFraction().observe(this, progressFraction -> {
            if (progressFraction != null) {
                circularTimerView.setProgress(progressFraction, true);
            }
        });

        timerViewModel.getSelectedDuration().observe(this, duration -> {
            if (duration != null) {
                currentSelectedDuration = duration;
                tvTargetDurationLabel.setText(getString(R.string.preset_header) + ": " + TimeFormatter.formatDurationLabel(duration));
                updatePresetButtonStyles(duration);
            }
        });

        timerViewModel.getTimerState().observe(this, this::renderTimerState);

        // Phase 2: Media Control ViewModel
        mediaViewModel = new ViewModelProvider(this).get(MediaControlViewModel.class);

        mediaViewModel.isPermissionGranted().observe(this, this::renderPermissionStatus);

        mediaViewModel.getDetectedSession().observe(this, this::renderDetectedSession);

        mediaViewModel.getControlResult().observe(this, this::renderControlResult);
    }

    private void setupListeners() {
        // Phase 1 timer controls
        btnStart.setOnClickListener(v -> timerViewModel.startTimer());
        btnPause.setOnClickListener(v -> timerViewModel.pauseTimer());
        btnResume.setOnClickListener(v -> timerViewModel.resumeTimer());
        btnCancel.setOnClickListener(v -> timerViewModel.cancelTimer());

        // Presets
        btnPreset15m.setOnClickListener(v -> timerViewModel.setDuration(DURATION_15M));
        btnPreset30m.setOnClickListener(v -> timerViewModel.setDuration(DURATION_30M));
        btnPreset45m.setOnClickListener(v -> timerViewModel.setDuration(DURATION_45M));
        btnPreset1h.setOnClickListener(v -> timerViewModel.setDuration(DURATION_1H));
        btnPreset2h.setOnClickListener(v -> timerViewModel.setDuration(DURATION_2H));

        btnPreset30sTest.setOnClickListener(v -> {
            timerViewModel.setDuration(DURATION_30S);
            Toast.makeText(this, "30-Second verification timer selected", Toast.LENGTH_SHORT).show();
        });

        btnPresetCustom.setOnClickListener(v -> showCustomDurationDialog());

        // Phase 2 media controls
        btnGrantAccess.setOnClickListener(v -> {
            Intent settingsIntent = MediaAccessHelper.createNotificationListenerSettingsIntent();
            try {
                startActivity(settingsIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open notification settings directly.", Toast.LENGTH_SHORT).show();
            }
        });

        btnRefreshSessions.setOnClickListener(v -> mediaViewModel.refreshSessions());

        btnTestPauseAudio.setOnClickListener(v -> mediaViewModel.pauseActiveMedia());
    }

    private void renderPermissionStatus(Boolean isGranted) {
        boolean granted = Boolean.TRUE.equals(isGranted);
        if (granted) {
            tvMediaAccessBadge.setText(R.string.media_access_granted);
            setBadgeStyle(tvMediaAccessBadge, R.color.status_completed, R.color.status_completed_bg);
            layoutPermissionPrompt.setVisibility(View.GONE);
        } else {
            tvMediaAccessBadge.setText(R.string.media_access_required);
            setBadgeStyle(tvMediaAccessBadge, R.color.status_paused, R.color.status_paused_bg);
            layoutPermissionPrompt.setVisibility(View.VISIBLE);
        }
    }

    private void renderDetectedSession(MediaSessionInfo sessionInfo) {
        if (sessionInfo != null) {
            tvDetectedAppName.setText(sessionInfo.getAppName());
            String details = sessionInfo.getDisplayTitle() + " (" + sessionInfo.getPlaybackStateText() + ")";
            tvDetectedTrackInfo.setText(details);
        } else {
            tvDetectedAppName.setText(R.string.no_media_detected);
            tvDetectedTrackInfo.setText("No active session");
        }
    }

    private void renderControlResult(MediaControlResult result) {
        if (result == null) return;

        tvMediaResultMessage.setText(result.getDisplayMessage());

        int textColor;
        switch (result.getStatus()) {
            case PAUSE_COMMAND_SENT:
                textColor = ContextCompat.getColor(this, R.color.primary_accent);
                break;
            case ACTIVE_SESSION_DETECTED:
                textColor = ContextCompat.getColor(this, R.color.status_completed);
                break;
            case MEDIA_ACCESS_REQUIRED:
            case PAUSE_COMMAND_UNAVAILABLE:
                textColor = ContextCompat.getColor(this, R.color.status_paused);
                break;
            case UNABLE_TO_CONTROL_SESSION:
            case NO_ACTIVE_SESSION_FOUND:
            default:
                textColor = ContextCompat.getColor(this, R.color.text_secondary);
                break;
        }
        tvMediaResultMessage.setTextColor(textColor);
    }

    private void showCustomDurationDialog() {
        CustomDurationDialog dialog = new CustomDurationDialog(this, durationMillis -> {
            timerViewModel.setDuration(durationMillis);
            Toast.makeText(MainActivity.this,
                    "Custom timer set to " + TimeFormatter.formatDurationLabel(durationMillis),
                    Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    private void renderTimerState(TimerState state) {
        if (state == null) return;

        switch (state) {
            case READY:
                updateStatusBadge(getString(R.string.status_ready), R.color.status_ready, R.color.status_ready_bg);
                btnStart.setVisibility(View.VISIBLE);
                btnStart.setText(R.string.action_start);
                layoutActiveControls.setVisibility(View.GONE);
                break;

            case RUNNING:
                updateStatusBadge(getString(R.string.status_running), R.color.status_running, R.color.status_running_bg);
                btnStart.setVisibility(View.GONE);
                layoutActiveControls.setVisibility(View.VISIBLE);
                btnPause.setVisibility(View.VISIBLE);
                btnResume.setVisibility(View.GONE);
                btnCancel.setVisibility(View.VISIBLE);
                break;

            case PAUSED:
                updateStatusBadge(getString(R.string.status_paused), R.color.status_paused, R.color.status_paused_bg);
                btnStart.setVisibility(View.GONE);
                layoutActiveControls.setVisibility(View.VISIBLE);
                btnPause.setVisibility(View.GONE);
                btnResume.setVisibility(View.VISIBLE);
                btnCancel.setVisibility(View.VISIBLE);
                break;

            case COMPLETED:
                updateStatusBadge(getString(R.string.status_completed), R.color.status_completed, R.color.status_completed_bg);
                btnStart.setVisibility(View.VISIBLE);
                btnStart.setText(R.string.action_start);
                layoutActiveControls.setVisibility(View.GONE);
                Toast.makeText(this, R.string.timer_completed_toast, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void updateStatusBadge(String text, int textColorRes, int bgColorRes) {
        tvStatusBadge.setText(text);
        setBadgeStyle(tvStatusBadge, textColorRes, bgColorRes);
    }

    private void setBadgeStyle(TextView badgeView, int textColorRes, int bgColorRes) {
        int textColor = ContextCompat.getColor(this, textColorRes);
        int bgColor = ContextCompat.getColor(this, bgColorRes);
        badgeView.setTextColor(textColor);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(100f);
        drawable.setColor(bgColor);
        drawable.setStroke(3, textColor);
        badgeView.setBackground(drawable);
    }

    private void updatePresetButtonStyles(long selectedMillis) {
        setPresetSelected(btnPreset15m, selectedMillis == DURATION_15M);
        setPresetSelected(btnPreset30m, selectedMillis == DURATION_30M);
        setPresetSelected(btnPreset45m, selectedMillis == DURATION_45M);
        setPresetSelected(btnPreset1h, selectedMillis == DURATION_1H);
        setPresetSelected(btnPreset2h, selectedMillis == DURATION_2H);

        boolean isKnownPreset = (selectedMillis == DURATION_15M || selectedMillis == DURATION_30M ||
                selectedMillis == DURATION_45M || selectedMillis == DURATION_1H ||
                selectedMillis == DURATION_2H || selectedMillis == DURATION_30S);
        setPresetSelected(btnPresetCustom, !isKnownPreset);

        // Highlight 30s test chip if selected
        if (selectedMillis == DURATION_30S) {
            btnPreset30sTest.setTextColor(ContextCompat.getColor(this, R.color.chip_text_selected));
            btnPreset30sTest.setBackgroundResource(R.drawable.bg_preset_chip_selected);
        } else {
            btnPreset30sTest.setTextColor(ContextCompat.getColor(this, R.color.primary_accent));
            btnPreset30sTest.setBackgroundResource(R.drawable.bg_card_rounded);
        }
    }

    private void setPresetSelected(Button button, boolean isSelected) {
        if (isSelected) {
            button.setBackgroundResource(R.drawable.bg_preset_chip_selected);
            button.setTextColor(ContextCompat.getColor(this, R.color.chip_text_selected));
        } else {
            button.setBackgroundResource(R.drawable.bg_preset_chip_unselected);
            button.setTextColor(ContextCompat.getColor(this, R.color.chip_text_unselected));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mediaViewModel != null) {
            mediaViewModel.onStart();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (timerViewModel != null) {
            timerViewModel.refreshTimer();
        }
        if (mediaViewModel != null) {
            mediaViewModel.checkAndRefresh();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaViewModel != null) {
            mediaViewModel.onStop();
        }
    }
}
