

package com.obddroid.ui.components;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import java.util.Timer;
import java.util.TimerTask;

import com.obddroid.activities.MainActivity;

/**
 * Automatically hide components after timeout and show again on touch action
 */
public class AutoHider
	extends TimerTask
	implements OnTouchListener
{
	/** activities message handler */
	private final Handler mHandler;
	/** timestamp when component was hidden */
	private long componentHideTime;
	/** message ID to be sent for hiding component */
	private final int mMessageId;
	/** current visibility state of component */
	private boolean visible = true;
	/** delay time[ms] before component gets hidden */
	private final long TB_HIDE_DELAY;

	/**
	 * Constructor
	 * @param activity parent activity
	 * @param handler activity's message handler
	 * @param hideDelayTime delay time[ms] before component gets hidden
	 */
	public AutoHider(Activity activity,
	          Handler handler,
	          long hideDelayTime)
	{
		TB_HIDE_DELAY = hideDelayTime;
		mMessageId = MainActivity.MESSAGE_TOOLBAR_VISIBLE;
		mHandler  = handler;
		activity.getWindow().getDecorView().setOnTouchListener(this);
	}

	@Override
	public boolean cancel()
	{
		// make component visible
		showComponent();
		return super.cancel();
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouch(View v, MotionEvent event)
	{
		showComponent();
		return false;
	}

	@Override
	public void run()
	{
		// update component visibility based on hide timer
		setComponentVisibility(System.currentTimeMillis() < componentHideTime);
	}

	/**
	 * Start AutoHider loop
	 * @param checkLoopInterval interval to update visibility
	 */
	public void start(int checkLoopInterval)
	{
		showComponent();
		new Timer().schedule(this, 0, checkLoopInterval);
	}

	/**
	 * set Visibility of component
	 * @param visible visible/invisible?
	 */
	private void setComponentVisibility(boolean visible)
	{
		// if visibility changed ...
		if(this.visible != visible)
		{
			/* forward message to update the view */
			Message msg = mHandler.obtainMessage(mMessageId);
			msg.obj = visible;
			mHandler.sendMessage(msg);
			// update visibility state
			this.visible = visible;
		}
	}

	/**
	 * Show tool bar again
	 */
	void showComponent()
	{
		// set next hiding time
		componentHideTime = System.currentTimeMillis() + TB_HIDE_DELAY;
		// now show the component
		setComponentVisibility(true);
	}
}
