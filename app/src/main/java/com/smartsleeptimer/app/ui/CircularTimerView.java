package com.smartsleeptimer.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.smartsleeptimer.app.R;

/**
 * Custom circular countdown progress view with a calming night-themed glow and gradient arc.
 */
public class CircularTimerView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ovalRect = new RectF();

    private float progress = 1.0f; // 1.0 = 100%, 0.0 = 0%
    private float animatedProgress = 1.0f;
    private ValueAnimator progressAnimator;

    private float strokeWidthPx = 18f;
    private int trackColor;
    private int startColor;
    private int endColor;

    public CircularTimerView(Context context) {
        super(context);
        init(context);
    }

    public CircularTimerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CircularTimerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        strokeWidthPx = 14f * density;

        trackColor = ContextCompat.getColor(context, R.color.circle_track);
        startColor = ContextCompat.getColor(context, R.color.circle_progress_start);
        endColor = ContextCompat.getColor(context, R.color.circle_progress_end);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidthPx);
        trackPaint.setColor(trackColor);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidthPx);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float halfStroke = strokeWidthPx / 2f;
        float padding = Math.max(getPaddingLeft(), Math.max(getPaddingTop(), Math.max(getPaddingRight(), getPaddingBottom())));
        float inset = halfStroke + padding + 4f;

        ovalRect.set(inset, inset, w - inset, h - inset);

        if (w > 0 && h > 0) {
            float cx = w / 2f;
            float cy = h / 2f;
            // Subtle 2-color gradient across the ring aligned with 12 o'clock (-90 degrees)
            int[] colors = new int[]{startColor, endColor, startColor};
            float[] positions = new float[]{0.0f, 0.5f, 1.0f};
            SweepGradient sweepGradient = new SweepGradient(cx, cy, colors, positions);
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(-90f, cx, cy);
            sweepGradient.setLocalMatrix(matrix);
            progressPaint.setShader(sweepGradient);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // Draw background circular track
        canvas.drawOval(ovalRect, trackPaint);

        // Draw remaining countdown progress arc (starts at -90 degrees, top)
        if (animatedProgress > 0f) {
            float sweepAngle = 360f * Math.min(1.0f, Math.max(0.0f, animatedProgress));
            canvas.drawArc(ovalRect, -90f, sweepAngle, false, progressPaint);
        }
    }

    /**
     * Updates progress value (between 0.0f and 1.0f) with smooth animation.
     */
    public void setProgress(float targetProgress, boolean animate) {
        float clamped = Math.max(0.0f, Math.min(1.0f, targetProgress));
        this.progress = clamped;

        if (progressAnimator != null && progressAnimator.isRunning()) {
            progressAnimator.cancel();
        }

        if (animate) {
            progressAnimator = ValueAnimator.ofFloat(animatedProgress, clamped);
            progressAnimator.setDuration(350);
            progressAnimator.setInterpolator(new DecelerateInterpolator());
            progressAnimator.addUpdateListener(anim -> {
                animatedProgress = (float) anim.getAnimatedValue();
                invalidate();
            });
            progressAnimator.start();
        } else {
            animatedProgress = clamped;
            invalidate();
        }
    }

    public float getProgress() {
        return progress;
    }
}
