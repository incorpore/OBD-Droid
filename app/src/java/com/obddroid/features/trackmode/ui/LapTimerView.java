package com.obddroid.features.trackmode.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.obddroid.features.trackmode.data.LapTime;

/**
 * Custom view for displaying lap timer with predictive timing
 */
public class LapTimerView extends View {

    // Paints
    private Paint backgroundPaint;
    private Paint arcPaint;
    private Paint textPaint;
    private Paint deltaTextPaint;
    private Paint labelPaint;

    // Values
    private long currentLapTime = 0;
    private long bestLapTime = 0;
    private long deltaTime = 0;
    private float lapProgress = 0; // 0-100%

    // Display settings
    private RectF arcRect;
    private float centerX, centerY;
    private float radius;

    // Colors
    private final int colorBackground = Color.parseColor("#1A1A1A");
    private final int colorArc = Color.parseColor("#2196F3");
    private final int colorGreen = Color.parseColor("#4CAF50");
    private final int colorRed = Color.parseColor("#F44336");
    private final int colorPurple = Color.parseColor("#9B59B6");
    private final int colorText = Color.WHITE;

    public LapTimerView(Context context) {
        super(context);
        init();
    }

    public LapTimerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LapTimerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Background paint
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(colorBackground);

        // Arc paint for progress
        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(20);
        arcPaint.setColor(colorArc);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        // Main time text paint
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(colorText);
        textPaint.setTextSize(72);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        // Delta text paint
        deltaTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        deltaTextPaint.setColor(colorGreen);
        deltaTextPaint.setTextSize(48);
        deltaTextPaint.setTextAlign(Paint.Align.CENTER);
        deltaTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));

        // Label text paint
        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.GRAY);
        labelPaint.setTextSize(24);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        arcRect = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(w, h) / 2f - 30;

        // Setup arc rectangle
        arcRect.set(centerX - radius, centerY - radius,
                    centerX + radius, centerY + radius);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background circle
        canvas.drawCircle(centerX, centerY, radius + 20, backgroundPaint);

        // Draw progress arc
        if (lapProgress > 0) {
            float sweepAngle = (lapProgress / 100f) * 360f;

            // Color based on delta
            if (deltaTime > 0) {
                arcPaint.setColor(colorRed);
            } else if (deltaTime < 0) {
                arcPaint.setColor(colorGreen);
            } else {
                arcPaint.setColor(colorArc);
            }

            canvas.drawArc(arcRect, -90, sweepAngle, false, arcPaint);
        }

        // Draw current lap time
        String timeText = LapTime.formatTime(currentLapTime);
        canvas.drawText(timeText, centerX, centerY, textPaint);

        // Draw delta time
        if (deltaTime != 0 && bestLapTime > 0) {
            String deltaText;
            if (deltaTime > 0) {
                deltaText = "+" + formatDeltaTime(deltaTime);
                deltaTextPaint.setColor(colorRed);
            } else {
                deltaText = "-" + formatDeltaTime(Math.abs(deltaTime));
                deltaTextPaint.setColor(colorGreen);
            }
            canvas.drawText(deltaText, centerX, centerY + 60, deltaTextPaint);
        }

        // Draw labels
        canvas.drawText("LAP TIME", centerX, centerY - 80, labelPaint);

        // Draw lap progress percentage
        if (lapProgress > 0) {
            String progressText = String.format("%.0f%%", lapProgress);
            canvas.drawText(progressText, centerX, centerY + 110, labelPaint);
        }

        // Draw best lap time if available
        if (bestLapTime > 0) {
            labelPaint.setColor(colorPurple);
            String bestText = "BEST: " + LapTime.formatTime(bestLapTime);
            canvas.drawText(bestText, centerX, centerY + 140, labelPaint);
            labelPaint.setColor(Color.GRAY);
        }
    }

    /**
     * Update lap timer display
     */
    public void updateLapTime(long lapTime, long delta) {
        this.currentLapTime = lapTime;
        this.deltaTime = delta;
        invalidate();
    }

    /**
     * Set best lap time for reference
     */
    public void setBestLapTime(long bestTime) {
        this.bestLapTime = bestTime;
        invalidate();
    }

    /**
     * Update lap progress
     */
    public void setLapProgress(float progress) {
        this.lapProgress = Math.max(0, Math.min(100, progress));
        invalidate();
    }

    /**
     * Reset the timer
     */
    public void reset() {
        currentLapTime = 0;
        deltaTime = 0;
        lapProgress = 0;
        invalidate();
    }

    /**
     * Start new lap
     */
    public void startNewLap() {
        reset();
    }

    /**
     * Format delta time (shorter format)
     */
    private String formatDeltaTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long millis = milliseconds % 1000;

        if (seconds > 0) {
            return String.format("%d.%03d", seconds, millis);
        } else {
            return String.format("0.%03d", millis);
        }
    }

    // Getters
    public long getCurrentLapTime() {
        return currentLapTime;
    }

    public long getDeltaTime() {
        return deltaTime;
    }

    public float getLapProgress() {
        return lapProgress;
    }
}