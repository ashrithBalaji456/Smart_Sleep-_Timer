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

        Button btnQuickAdd5m = findViewById(R.id.btnQuickAdd5m);
        Button btnQuickAdd15m = findViewById(R.id.btnQuickAdd15m);
        Button btnQuickAdd30m = findViewById(R.id.btnQuickAdd30m);
        Button btnQuickAdd1h = findViewById(R.id.btnQuickAdd1h);
        Button btnQuickClear = findViewById(R.id.btnQuickClear);

        if (btnQuickAdd5m != null) btnQuickAdd5m.setOnClickListener(v -> addMinutes(5));
        if (btnQuickAdd15m != null) btnQuickAdd15m.setOnClickListener(v -> addMinutes(15));
        if (btnQuickAdd30m != null) btnQuickAdd30m.setOnClickListener(v -> addMinutes(30));
        if (btnQuickAdd1h != null) btnQuickAdd1h.setOnClickListener(v -> addMinutes(60));
        if (btnQuickClear != null) btnQuickClear.setOnClickListener(v -> clearInputs());

        etMinutes.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                validateAndApply();
                return true;
            }
            return false;
        });

        btnCancel.setOnClickListener(v -> dismiss());
        btnApply.setOnClickListener(v -> validateAndApply());
    }

    private void addMinutes(int addedMins) {
        tvErrorMessage.setVisibility(View.GONE);
        int currentHours = parseField(etHours);
        int currentMinutes = parseField(etMinutes);

        int totalMinutes = (currentHours * 60) + currentMinutes + addedMins;
        if (totalMinutes > 24 * 60) {
            totalMinutes = 24 * 60;
        }

        int newHours = totalMinutes / 60;
        int newMinutes = totalMinutes % 60;

        etHours.setText(newHours > 0 ? String.valueOf(newHours) : "");
        etMinutes.setText(newMinutes > 0 ? String.valueOf(newMinutes) : (newHours > 0 ? "0" : ""));
        etMinutes.setSelection(etMinutes.getText().length());
    }

    private void clearInputs() {
        tvErrorMessage.setVisibility(View.GONE);
        etHours.setText("");
        etMinutes.setText("");
    }

    private int parseField(EditText editText) {
        if (editText == null || editText.getText() == null) return 0;
        String val = editText.getText().toString().trim();
        if (val.isEmpty()) return 0;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
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
