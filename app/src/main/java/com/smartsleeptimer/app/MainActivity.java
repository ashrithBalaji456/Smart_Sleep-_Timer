package com.smartsleeptimer.app;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.smartsleeptimer.app.model.TimerState;
import com.smartsleeptimer.app.ui.CircularTimerView;
import com.smartsleeptimer.app.ui.CustomDurationDialog;
import com.smartsleeptimer.app.util.TimeFormatter;
import com.smartsleeptimer.app.viewmodel.SleepTimerViewModel;

import java.util.concurrent.TimeUnit;

/**
 * Main Activity of Smart Sleep Timer (Phase 1).
 * Displays night-themed UI, handles user interactions, and coordinates with SleepTimerViewModel.
 */
public class MainActivity extends AppCompatActivity {

    private SleepTimerViewModel viewModel;

    // UI Components
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
        setupViewModel();
        setupListeners();
    }

    private void initViews() {
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
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(SleepTimerViewModel.class);

        // Observe remaining countdown milliseconds
        viewModel.getRemainingMillis().observe(this, remainingMillis -> {
            if (remainingMillis != null) {
                tvCountdownDisplay.setText(TimeFormatter.formatTime(remainingMillis));
            }
        });

        // Observe progress percentage for circular arc
        viewModel.getProgressFraction().observe(this, progressFraction -> {
            if (progressFraction != null) {
                circularTimerView.setProgress(progressFraction, true);
            }
        });

        // Observe selected total duration
        viewModel.getSelectedDuration().observe(this, duration -> {
            if (duration != null) {
                currentSelectedDuration = duration;
                tvTargetDurationLabel.setText(getString(R.string.preset_header) + ": " + TimeFormatter.formatDurationLabel(duration));
                updatePresetButtonStyles(duration);
            }
        });

        // Observe timer state
        viewModel.getTimerState().observe(this, this::renderTimerState);
    }

    private void setupListeners() {
        // Controls
        btnStart.setOnClickListener(v -> viewModel.startTimer());
        btnPause.setOnClickListener(v -> viewModel.pauseTimer());
        btnResume.setOnClickListener(v -> viewModel.resumeTimer());
        btnCancel.setOnClickListener(v -> viewModel.cancelTimer());

        // Presets
        btnPreset15m.setOnClickListener(v -> viewModel.setDuration(DURATION_15M));
        btnPreset30m.setOnClickListener(v -> viewModel.setDuration(DURATION_30M));
        btnPreset45m.setOnClickListener(v -> viewModel.setDuration(DURATION_45M));
        btnPreset1h.setOnClickListener(v -> viewModel.setDuration(DURATION_1H));
        btnPreset2h.setOnClickListener(v -> viewModel.setDuration(DURATION_2H));

        // Quick test preset
        btnPreset30sTest.setOnClickListener(v -> {
            viewModel.setDuration(DURATION_30S);
            Toast.makeText(this, "30-Second verification timer selected", Toast.LENGTH_SHORT).show();
        });

        // Custom duration dialog
        btnPresetCustom.setOnClickListener(v -> showCustomDurationDialog());
    }

    private void showCustomDurationDialog() {
        CustomDurationDialog dialog = new CustomDurationDialog(this, durationMillis -> {
            viewModel.setDuration(durationMillis);
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
        int textColor = ContextCompat.getColor(this, textColorRes);
        int bgColor = ContextCompat.getColor(this, bgColorRes);
        tvStatusBadge.setTextColor(textColor);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(100f);
        drawable.setColor(bgColor);
        drawable.setStroke(3, textColor);
        tvStatusBadge.setBackground(drawable);
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
    protected void onResume() {
        super.onResume();
        // Immediately sync countdown display upon returning to the foreground
        if (viewModel != null) {
            viewModel.refreshTimer();
        }
    }
}
