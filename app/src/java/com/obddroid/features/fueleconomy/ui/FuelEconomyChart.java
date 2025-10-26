package com.obddroid.features.fueleconomy.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.obddroid.R;

/**
 * Custom view that displays a fuel economy bar chart with three time periods:
 * - 0-5 minutes (recent, accent blue)
 * - 0-30 minutes (medium-term, primary blue)
 * - 0-3 hours (long-term, primary dark blue)
 */
public class FuelEconomyChart extends View {

    private Paint gridPaint;
    private Paint axisPaint;
    private Paint textPaint;
    private Paint labelPaint;
    private Paint recentBarPaint;      // 0-5 min (accent blue)
    private Paint mediumBarPaint;      // 0-30 min (primary blue)
    private Paint longBarPaint;        // 0-3 hours (primary dark)
    private Paint baselinePaint;       // Reference line at y=20

    // Chart data (MPG values for each bar)
    private float[] recentData = {20f, 23f, 12f, 18f, 45f};     // 0-5 min
    private float[] mediumData = {15f, 35f, 30f, 25f, 20f, 25f}; // 0-30 min
    private float[] longData = {18f};                            // 0-3 hours

    private static final float MAX_VALUE = 50f;  // Max MPG on Y-axis
    private static final int GRID_LINES = 5;     // Number of horizontal grid lines
    private static final float BASELINE_VALUE = 20f;  // Reference line

    public FuelEconomyChart(Context context) {
        super(context);
        init(context);
    }

    public FuelEconomyChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FuelEconomyChart(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Grid lines paint
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(0xFFE0E0E0);
        gridPaint.setStrokeWidth(1f);

        // Axis lines paint
        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(0xFF9E9E9E);
        axisPaint.setStrokeWidth(2f);

        // Y-axis text paint
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(context.getResources().getColor(R.color.text_secondary));
        textPaint.setTextSize(36f);
        textPaint.setTextAlign(Paint.Align.RIGHT);

        // Label text paint (for time periods)
        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(context.getResources().getColor(R.color.text_secondary));
        labelPaint.setTextSize(30f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        // Recent bars (0-5 min) - Accent Blue
        recentBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        recentBarPaint.setColor(context.getResources().getColor(R.color.colorAccent));
        recentBarPaint.setStyle(Paint.Style.FILL);

        // Medium bars (0-30 min) - Primary Blue
        mediumBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mediumBarPaint.setColor(context.getResources().getColor(R.color.colorPrimary));
        mediumBarPaint.setStyle(Paint.Style.FILL);

        // Long bars (0-3 hours) - Primary Dark
        longBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        longBarPaint.setColor(context.getResources().getColor(R.color.colorPrimaryDark));
        longBarPaint.setStyle(Paint.Style.FILL);

        // Baseline reference line
        baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        baselinePaint.setColor(context.getResources().getColor(R.color.text_primary));
        baselinePaint.setStrokeWidth(2f);
    }

    /**
     * Update chart data
     * @param recent 0-5 minute data
     * @param medium 0-30 minute data
     * @param longTerm 0-3 hour data
     */
    public void setData(float[] recent, float[] medium, float[] longTerm) {
        this.recentData = recent != null ? recent : new float[0];
        this.mediumData = medium != null ? medium : new float[0];
        this.longData = longTerm != null ? longTerm : new float[0];
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Padding
        float leftPadding = 60f;
        float rightPadding = 40f;
        float topPadding = 20f;
        float bottomPadding = 40f;

        float chartWidth = width - leftPadding - rightPadding;
        float chartHeight = height - topPadding - bottomPadding;

        // Draw grid lines and Y-axis labels
        for (int i = 0; i <= GRID_LINES; i++) {
            float y = topPadding + (chartHeight * i / GRID_LINES);
            float value = MAX_VALUE - (MAX_VALUE * i / GRID_LINES);

            // Grid line
            canvas.drawLine(leftPadding, y, width - rightPadding, y, gridPaint);

            // Y-axis label
            canvas.drawText(String.format("%.0f", value), leftPadding - 10f, y + 8f, textPaint);
        }

        // Draw baseline reference line at y=20
        float baselineY = topPadding + (chartHeight * (MAX_VALUE - BASELINE_VALUE) / MAX_VALUE);
        canvas.drawLine(leftPadding, baselineY, width - rightPadding, baselineY, baselinePaint);

        // Draw Y-axis
        canvas.drawLine(leftPadding, topPadding, leftPadding, height - bottomPadding, axisPaint);

        // Draw X-axis
        canvas.drawLine(leftPadding, height - bottomPadding, width - rightPadding, height - bottomPadding, axisPaint);

        // Calculate section widths
        float sectionWidth = chartWidth / 3f;
        float barSpacing = 4f;

        // Draw recent data (0-5 min)
        drawBars(canvas, recentData, leftPadding, sectionWidth, chartHeight, topPadding, bottomPadding, barSpacing, recentBarPaint);
        canvas.drawText("0-5 min.", leftPadding + sectionWidth / 2f, height - 10f, labelPaint);

        // Draw medium data (0-30 min)
        drawBars(canvas, mediumData, leftPadding + sectionWidth, sectionWidth, chartHeight, topPadding, bottomPadding, barSpacing, mediumBarPaint);
        canvas.drawText("0-30 min.", leftPadding + sectionWidth * 1.5f, height - 10f, labelPaint);

        // Draw long data (0-3 hours)
        drawBars(canvas, longData, leftPadding + sectionWidth * 2f, sectionWidth, chartHeight, topPadding, bottomPadding, barSpacing, longBarPaint);
        canvas.drawText("0-3 hours", leftPadding + sectionWidth * 2.5f, height - 10f, labelPaint);
    }

    private void drawBars(Canvas canvas, float[] data, float sectionStartX, float sectionWidth,
                         float chartHeight, float topPadding, float bottomPadding,
                         float barSpacing, Paint barPaint) {
        if (data == null || data.length == 0) return;

        float barWidth = (sectionWidth - (barSpacing * (data.length + 1))) / data.length;

        for (int i = 0; i < data.length; i++) {
            float value = Math.min(data[i], MAX_VALUE);
            float barHeight = (value / MAX_VALUE) * chartHeight;

            float x = sectionStartX + barSpacing + (i * (barWidth + barSpacing));
            float top = topPadding + chartHeight - barHeight;
            float bottom = getHeight() - bottomPadding;

            RectF rect = new RectF(x, top, x + barWidth, bottom);
            canvas.drawRect(rect, barPaint);
        }
    }
}
