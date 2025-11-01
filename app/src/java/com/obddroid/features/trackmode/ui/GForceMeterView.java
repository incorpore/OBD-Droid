package com.obddroid.features.trackmode.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Custom view for displaying G-forces in a circular meter
 */
public class GForceMeterView extends View {

    private static final int MAX_HISTORY_POINTS = 50;
    private static final float MAX_G_FORCE = 2.0f; // Maximum displayable G-force

    // Paints
    private Paint circlePaint;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint dotPaint;
    private Paint trailPaint;
    private Paint maxPaint;

    // G-force values
    private float gForceLateral = 0; // Left/Right
    private float gForceLongitudinal = 0; // Forward/Backward
    private float maxGForce = 0;

    // Display values
    private float centerX, centerY;
    private float radius;
    private float dotX, dotY;
    private float maxDotX, maxDotY;

    // Trail history
    private Queue<GForcePoint> trailPoints = new LinkedList<>();

    // Colors
    private final int colorNormal = Color.GREEN;
    private final int colorMedium = Color.YELLOW;
    private final int colorHigh = Color.RED;
    private final int colorMax = Color.parseColor("#9B59B6"); // Purple

    private static class GForcePoint {
        float x, y;
        long timestamp;

        GForcePoint(float x, float y) {
            this.x = x;
            this.y = y;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public GForceMeterView(Context context) {
        super(context);
        init();
    }

    public GForceMeterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GForceMeterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Circle paint
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(4);
        circlePaint.setColor(Color.WHITE);

        // Grid paint
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1);
        gridPaint.setColor(Color.GRAY);
        gridPaint.setAlpha(128);

        // Text paint
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Current position dot
        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(colorNormal);

        // Trail paint
        trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeWidth(2);
        trailPaint.setColor(colorNormal);
        trailPaint.setAlpha(128);

        // Max G-force marker
        maxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maxPaint.setStyle(Paint.Style.FILL);
        maxPaint.setColor(colorMax);
        maxPaint.setAlpha(200);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(w, h) / 2f - 50; // Leave margin for text
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background
        canvas.drawColor(Color.BLACK);

        // Draw grid circles (0.5G, 1.0G, 1.5G, 2.0G)
        for (float g = 0.5f; g <= MAX_G_FORCE; g += 0.5f) {
            float r = radius * (g / MAX_G_FORCE);
            canvas.drawCircle(centerX, centerY, r, gridPaint);

            // Draw G-force labels
            String label = String.format("%.1fG", g);
            canvas.drawText(label, centerX, centerY - r - 5, textPaint);
        }

        // Draw crosshair
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, gridPaint);
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, gridPaint);

        // Draw outer circle
        canvas.drawCircle(centerX, centerY, radius, circlePaint);

        // Draw direction labels
        textPaint.setTextSize(24);
        canvas.drawText("FWD", centerX, centerY - radius - 25, textPaint);
        canvas.drawText("REV", centerX, centerY + radius + 35, textPaint);
        canvas.drawText("L", centerX - radius - 30, centerY + 5, textPaint);
        canvas.drawText("R", centerX + radius + 30, centerY + 5, textPaint);

        // Draw trail
        if (!trailPoints.isEmpty()) {
            Path trailPath = new Path();
            boolean first = true;
            for (GForcePoint point : trailPoints) {
                if (first) {
                    trailPath.moveTo(point.x, point.y);
                    first = false;
                } else {
                    trailPath.lineTo(point.x, point.y);
                }
            }
            canvas.drawPath(trailPath, trailPaint);
        }

        // Draw max G-force marker
        if (maxGForce > 0.1f) {
            canvas.drawCircle(maxDotX, maxDotY, 8, maxPaint);
        }

        // Calculate current dot position
        float normalizedLat = Math.min(Math.abs(gForceLateral) / MAX_G_FORCE, 1.0f);
        float normalizedLong = Math.min(Math.abs(gForceLongitudinal) / MAX_G_FORCE, 1.0f);

        dotX = centerX + (gForceLateral / MAX_G_FORCE) * radius;
        dotY = centerY - (gForceLongitudinal / MAX_G_FORCE) * radius; // Negative for forward

        // Constrain to circle
        float distance = (float) Math.sqrt(
            Math.pow(dotX - centerX, 2) + Math.pow(dotY - centerY, 2)
        );
        if (distance > radius) {
            float scale = radius / distance;
            dotX = centerX + (dotX - centerX) * scale;
            dotY = centerY + (dotY - centerY) * scale;
        }

        // Set dot color based on G-force magnitude
        float magnitude = (float) Math.sqrt(
            gForceLateral * gForceLateral + gForceLongitudinal * gForceLongitudinal
        );

        if (magnitude < 0.5f) {
            dotPaint.setColor(colorNormal);
        } else if (magnitude < 1.0f) {
            dotPaint.setColor(colorMedium);
        } else {
            dotPaint.setColor(colorHigh);
        }

        // Draw current position dot
        canvas.drawCircle(dotX, dotY, 12, dotPaint);

        // Draw current G-force text
        textPaint.setTextSize(36);
        textPaint.setColor(dotPaint.getColor());
        String gText = String.format("%.2fG", magnitude);
        canvas.drawText(gText, centerX, centerY + radius + 70, textPaint);

        // Draw max G-force text
        if (maxGForce > 0) {
            textPaint.setTextSize(24);
            textPaint.setColor(colorMax);
            String maxText = String.format("Max: %.2fG", maxGForce);
            canvas.drawText(maxText, centerX, centerY + radius + 100, textPaint);
        }
    }

    /**
     * Update G-force values
     */
    public void updateGForce(float lateral, float longitudinal) {
        this.gForceLateral = lateral;
        this.gForceLongitudinal = longitudinal;

        // Add to trail
        trailPoints.offer(new GForcePoint(dotX, dotY));
        if (trailPoints.size() > MAX_HISTORY_POINTS) {
            trailPoints.poll();
        }

        // Update max G-force
        float magnitude = (float) Math.sqrt(lateral * lateral + longitudinal * longitudinal);
        if (magnitude > maxGForce) {
            maxGForce = magnitude;
            maxDotX = dotX;
            maxDotY = dotY;
        }

        invalidate();
    }

    /**
     * Reset the meter
     */
    public void reset() {
        gForceLateral = 0;
        gForceLongitudinal = 0;
        maxGForce = 0;
        trailPoints.clear();
        invalidate();
    }

    /**
     * Get current G-force magnitude
     */
    public float getCurrentGForce() {
        return (float) Math.sqrt(
            gForceLateral * gForceLateral + gForceLongitudinal * gForceLongitudinal
        );
    }

    public float getMaxGForce() {
        return maxGForce;
    }
}