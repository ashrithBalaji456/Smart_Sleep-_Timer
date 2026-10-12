package com.smartsleeptimer.app.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.smartsleeptimer.app.R;
import com.smartsleeptimer.app.util.TimeFormatter;

/**
 * Custom duration picker dialog with input validation and friendly error messages.
 */
public class CustomDurationDialog extends Dialog {

    public interface OnDurationSelectedListener {
        void onDurationSelected(long durationMillis);
    }

    private final OnDurationSelectedListener listener;
    private EditText etHours;
    private EditText etMinutes;
    private TextView tvErrorMessage;

    public CustomDurationDialog(@NonNull Context context, OnDurationSelectedListener listener) {
        super(context, R.style.CustomDialogTheme);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_custom_duration);

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        etHours = findViewById(R.id.etHours);
        etMinutes = findViewById(R.id.etMinutes);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);

        Button btnCancel = findViewById(R.id.btnDialogCancel);
        Button btnApply = findViewById(R.id.btnDialogApply);

        btnCancel.setOnClickListener(v -> dismiss());

        btnApply.setOnClickListener(v -> validateAndApply());
    }

    private void validateAndApply() {
        tvErrorMessage.setVisibility(View.GONE);

        String hoursStr = etHours.getText() != null ? etHours.getText().toString().trim() : "";
        String minutesStr = etMinutes.getText() != null ? etMinutes.getText().toString().trim() : "";

        if (hoursStr.isEmpty() && minutesStr.isEmpty()) {
            showError(getContext().getString(R.string.error_invalid_number));
            return;
        }

        int hours = 0;
        int minutes = 0;

        try {
            if (!hoursStr.isEmpty()) {
                hours = Integer.parseInt(hoursStr);
            }
            if (!minutesStr.isEmpty()) {
                minutes = Integer.parseInt(minutesStr);
            }
        } catch (NumberFormatException e) {
            showError(getContext().getString(R.string.error_invalid_number));
            return;
        }

        if (hours < 0 || minutes < 0) {
            showError(getContext().getString(R.string.error_invalid_number));
            return;
        }

        if (hours == 0 && minutes == 0) {
            showError(getContext().getString(R.string.error_zero_duration));
            return;
        }

        if (hours > 24 || (hours == 24 && minutes > 0)) {
            showError(getContext().getString(R.string.error_exceeds_max));
            return;
        }

        long durationMillis = TimeFormatter.toMillis(hours, minutes);
        if (durationMillis <= 0) {
            showError(getContext().getString(R.string.error_zero_duration));
            return;
        }

        if (listener != null) {
            listener.onDurationSelected(durationMillis);
        }
        dismiss();
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        tvErrorMessage.setVisibility(View.VISIBLE);
    }
}
