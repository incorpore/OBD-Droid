package com.obddroid.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.cardview.widget.CardView;

/**
 * CardView that measures itself to maintain a square aspect ratio (height == width).
 */
public class SquareCardView extends CardView {

    public SquareCardView(Context context) {
        super(context);
    }

    public SquareCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int size;
        if (widthMode == MeasureSpec.EXACTLY && widthSize > 0) {
            size = widthSize;
        } else if (heightMode == MeasureSpec.EXACTLY && heightSize > 0) {
            size = heightSize;
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int measured = Math.max(getMeasuredWidth(), getMeasuredHeight());
            size = measured == 0 ? View.resolveSize(0, widthMeasureSpec) : measured;
        }

        int resolvedSize = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        super.onMeasure(resolvedSize, resolvedSize);
    }
}
