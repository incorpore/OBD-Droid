package com.obddroid.ecu.gui.androbd;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/**
 * Mirrored relative layout for HUD displays
 *

 */
public class MirrorRelativeLayout extends RelativeLayout
{
	public MirrorRelativeLayout(Context context)
	{
		super(context);
	}
	
	public MirrorRelativeLayout(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}
	
	public MirrorRelativeLayout(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
	}
	
	@Override
	protected void dispatchDraw(Canvas canvas)
	{
		// Scale the canvas in reverse in the x-direction, pivoting on
		// the center of the view
		canvas.scale(-1f, 1f, getWidth() / 2f, getHeight() / 2f);
		super.dispatchDraw(canvas);
	}
}
