package com.obddroid.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.obddroid.R;

/**
 * Custom horizontal gauge for fuel flow display
 * Shows a scale from 0 to 4 gal/h with a filled progress bar and marker
 */
public class FuelFlowGauge extends View {

    private Paint trackPaint;        // Background track
    private Paint fillPaint;         // Filled portion
    private Paint tickPaint;         // Tick marks
    private Paint textPaint;         // Scale numbers
    private Paint markerPaint;       // Diamond marker
    private Paint arrowPaint;        // Arrow indicators

    private float currentValue = 2.3f;  // Current fuel flow value
    private static final float MAX_VALUE = 4f;
    private static final int TICK_COUNT = 5;  // 0, 1, 2, 3, 4

    public FuelFlowGauge(Context context) {
        super(context);
        init(context);
    }

    public FuelFlowGauge(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FuelFlowGauge(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Background track
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(0xFFE0E0E0);
        trackPaint.setStyle(Paint.Style.FILL);

        // Filled progress bar
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(context.getResources().getColor(R.color.colorPrimary));
        fillPaint.setStyle(Paint.Style.FILL);

        // Tick marks
        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setColor(context.getResources().getColor(R.color.text_secondary));
        tickPaint.setStrokeWidth(2f);

        // Scale text
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(context.getResources().getColor(R.color.text_secondary));
        textPaint.setTextSize(32f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Marker
        markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        markerPaint.setColor(0xFFFFFFFF);  // White marker
        markerPaint.setStyle(Paint.Style.FILL);

        // Arrows
        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(context.getResources().getColor(R.color.text_primary));
        arrowPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Set the current fuel flow value
     * @param value Fuel flow in gal/h
     */
    public void setValue(float value) {
        this.currentValue = Math.min(Math.max(value, 0), MAX_VALUE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        float leftPadding = 40f;
        float rightPadding = 40f;
        float topPadding = 5f;

        float gaugeWidth = width - leftPadding - rightPadding;
        float gaugeHeight = 12f;
        float gaugeTop = topPadding;

        // Draw background track with rounded corners
        RectF trackRect = new RectF(leftPadding, gaugeTop, leftPadding + gaugeWidth, gaugeTop + gaugeHeight);
        canvas.drawRoundRect(trackRect, gaugeHeight / 2f, gaugeHeight / 2f, trackPaint);

        // Draw filled portion
        float fillWidth = (currentValue / MAX_VALUE) * gaugeWidth;
        RectF fillRect = new RectF(leftPadding, gaugeTop, leftPadding + fillWidth, gaugeTop + gaugeHeight);
        canvas.drawRoundRect(fillRect, gaugeHeight / 2f, gaugeHeight / 2f, fillPaint);

        // Draw tick marks and scale numbers
        for (int i = 0; i < TICK_COUNT; i++) {
            float x = leftPadding + (gaugeWidth * i / (TICK_COUNT - 1));

            // Tick mark
            canvas.drawLine(x, gaugeTop + gaugeHeight, x, gaugeTop + gaugeHeight + 8f, tickPaint);

            // Scale number
            canvas.drawText(String.valueOf(i), x, gaugeTop + gaugeHeight + 28f, textPaint);
        }

        // Draw left arrow (▶)
        drawArrow(canvas, leftPadding - 20f, gaugeTop + gaugeHeight / 2f, true);

        // Draw right arrow (◀)
        drawArrow(canvas, leftPadding + gaugeWidth + 20f, gaugeTop + gaugeHeight / 2f, false);

        // Draw diamond marker at current value
        float markerX = leftPadding + fillWidth;
        float markerY = gaugeTop + gaugeHeight / 2f;
        drawDiamond(canvas, markerX, markerY, 8f);
    }

    private void drawArrow(Canvas canvas, float x, float y, boolean pointsRight) {
        Path arrow = new Path();
        float size = 8f;

        if (pointsRight) {
            // Right-pointing triangle
            arrow.moveTo(x - size, y - size);
            arrow.lineTo(x + size, y);
            arrow.lineTo(x - size, y + size);
        } else {
            // Left-pointing triangle
            arrow.moveTo(x + size, y - size);
            arrow.lineTo(x - size, y);
            arrow.lineTo(x + size, y + size);
        }
        arrow.close();
        canvas.drawPath(arrow, arrowPaint);
    }

    private void drawDiamond(Canvas canvas, float x, float y, float size) {
        Path diamond = new Path();
        diamond.moveTo(x, y - size);           // Top
        diamond.lineTo(x + size, y);           // Right
        diamond.lineTo(x, y + size);           // Bottom
        diamond.lineTo(x - size, y);           // Left
        diamond.close();
        canvas.drawPath(diamond, markerPaint);

        // Add border
        Paint borderPaint = new Paint(markerPaint);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        borderPaint.setColor(getResources().getColor(R.color.colorPrimary));
        canvas.drawPath(diamond, borderPaint);
    }
}
