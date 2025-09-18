package com.obddroid.activities;
import android.graphics.Color;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Paint.Align;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ListAdapter;

import ecu.EcuDataPv;
import prot.ObdProt;

import com.obddroid.ui.components.AutoHider;
import com.obddroid.utils.ExportTask;
import com.obddroid.utils.Screenshot;
import com.obddroid.adapters.ColorAdapter;
import com.obddroid.adapters.ObdItemAdapter;
import com.obddroid.R;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.BasicStroke;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

/**
 * <code>Activity</code> that displays the readout of one <code>Sensor</code>.
 * This <code>Activity</code> must be started with an <code>Intent</code> that
 * passes in the number of the <code>Sensor</code>(s) to display. If none is
 * passed, the first available <code>Sensor</code> is used.
 */
public class ChartActivity extends AppCompatActivity
{

	/**
	 * minimum time between screen updates
	 */
	public static final long MIN_UPDATE_TIME = 1000;

	/**
	 * For passing the index number of the <code>Sensor</code> in its
	 * <code>SensorManager</code>
	 */
	public static final String POSITIONS = "POSITIONS";

	/** Map to uniquely collect PID numbers */
	private final TreeSet<Integer> pidNumbers = new TreeSet<>();

	/**
	 * list of colors to be used for series
	 */
	private static final BasicStroke[] stroke =
		{
			BasicStroke.SOLID,
			BasicStroke.DASHED,
			BasicStroke.DOTTED,
		};

	/**
	 * The displaying component
	 */
	private GraphicalView chartView;

	/**
	 * Dataset of the graphing component
	 */
	private XYMultipleSeriesDataset sensorData;

	/**
	 * Renderer for actually drawing the graph
	 */
	private XYMultipleSeriesRenderer renderer;
	/** automatic hiding toolbar */
	private AutoHider toolBarHider;

	/**
	 * the wake lock to keep app communication alive
	 */
	private static WakeLock wakeLock;

	private static ListAdapter mAdapter = null;

	/** data adapter as source of display data */
	public static ListAdapter getAdapter()
	{
		return mAdapter;
	}

	public static void setAdapter(ListAdapter mAdapter)
	{
		ChartActivity.mAdapter = mAdapter;
	}

	/**
	 * get stroke for an ID number preferrably unique pid number
	 * this is to get persistent coloring/lining for each id
	 * @param id id to get color for
	 * @return color for given ID
	 */
	private static BasicStroke getStroke(int id)
	{
		return stroke[(id / ColorAdapter.colors.length) % stroke.length];
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.chart, menu);
		return true;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setTheme(R.style.AppTheme);

		// Set status bar and navigation bar colors to match our theme
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			getWindow().setStatusBarColor(Color.parseColor("#212121"));
			getWindow().setNavigationBarColor(Color.parseColor("#212121"));
		}

		// Apply full screen based on preference
		if(MainActivity.prefs.getBoolean(MainActivity.PREF_FULLSCREEN, false))
		{
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
				getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
					View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
				);
			}
		}

		// Always keep main display on for vehicle diagnostics
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// prevent activity from falling asleep
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		wakeLock = Objects.requireNonNull(powerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
			getString(R.string.app_name));
		wakeLock.acquire();

		// Hide the action bar completely
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null)
		{
			actionBar.hide();
		}

		setTitle(R.string.chart);

		/* get PIDs to be shown */
		int[] positions = getIntent().getIntArrayExtra(POSITIONS);

		// set up overall chart properties
		sensorData = new XYMultipleSeriesDataset();
		renderer = new XYMultipleSeriesRenderer(positions.length);
		chartView = ChartFactory.getTimeChartView(this, sensorData, renderer, "H:mm:ss");
		// set up global renderer
		float textSize = new EditText(this).getTextSize();
		renderer.setLabelsTextSize(textSize/2);
		renderer.setXTitle(getString(R.string.time));
		renderer.setXLabels(5);
		renderer.setYLabels(5);
		renderer.setGridColor(Color.DKGRAY);
		renderer.setShowGrid(true);
		renderer.setFitLegend(true);
		renderer.setClickEnabled(false);
		// set up chart data
		setUpChartData(positions);
		// make chart visible
		setContentView(chartView);
		// limit selected PIDs to selection
		MainActivity.setFixedPids(pidNumbers);
		// if auto hiding selected ...
		if(MainActivity.prefs.getBoolean(MainActivity.PREF_AUTOHIDE,false))
		{
			// get autohide timeout [s]
			int timeout = Integer.parseInt(
				MainActivity.prefs.getString(MainActivity.PREF_AUTOHIDE_DELAY,"15") );
			// auto hide toolbar
			toolBarHider = new AutoHider( this,
			                              mHandler,
			                              timeout * 1000L);
			toolBarHider.start(1000);
			// wake up on touch
			chartView.setOnTouchListener(toolBarHider);
		}
	}

	/**
	 * Handle message requests
	 */
	private transient final Handler mHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{

			switch (msg.what)
			{
				case MainActivity.MESSAGE_UPDATE_VIEW:
					/* update chart */
					chartView.invalidate();
					break;

				// set toolbar visibility
				case MainActivity.MESSAGE_TOOLBAR_VISIBLE:
					// Always keep action bar hidden
					ActionBar ab = getSupportActionBar();
					if(ab != null)
					{
						ab.hide();
					}
					break;
			}
		}
	};

	/**
	 * Handle menu selections
	 *
	 * @param item selected menu item
	 * @return result of super call
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.share:
				new ExportTask(this).execute(sensorData);
				break;

			case R.id.snapshot:
				Screenshot.takeScreenShot(this, getWindow().peekDecorView());
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Handle destroy of the Activity
	 */
	@Override
	protected void onDestroy()
	{
		if(toolBarHider != null)
		{
			// cancel hiding thread
			toolBarHider.cancel();
			// forget about it
			toolBarHider = null;
		}
		ObdProt.resetFixedPid();
		// allow sleeping again
		wakeLock.release();
		super.onDestroy();
	}

	private final Timer refreshTimer = new Timer();

	/**
	 * Timer Task to cyclically update data screen
	 */
	private final TimerTask updateTask = new TimerTask()
	{
		@Override
		public void run()
		{
			/* forward message to update the view */
			Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_UPDATE_VIEW);
			mHandler.sendMessage(msg);
		}
	};


	/* (non-Javadoc)
	 * @see android.app.Activity#onStart()
	 */
	@Override
	protected void onStart()
	{
		super.onStart();
		// start display update task
		try
		{
			refreshTimer.schedule(updateTask, 0, 1000);
		} catch (Exception e)
		{
			// exception ignored here ...
		}
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onStop()
	 */
	@Override
	protected void onStop()
	{
		refreshTimer.purge();
		super.onStop();
	}

	/**
	 * Set up all the charting data series
	 *
	 * @param positions Positions of PIDs withn adapter
	 */
	private void setUpChartData(int[] positions)
	{
		long startTime = System.currentTimeMillis();
		int i = 0;
		EcuDataPv currPv;
		XYSeries currSeries;

		pidNumbers.clear();

		// loop through all PIDs
		for (int position : positions)
		{
			// get corresponding Process variable
			currPv = (EcuDataPv) mAdapter.getItem(position);
			if (currPv == null) continue;
			int pid = currPv.getAsInt(EcuDataPv.FID_PID);
			// add PID to unique list of PIDs
			pidNumbers.add(pid);

			// Get display color ...
			int pidColor = ColorAdapter.getItemColor(currPv);

			// get contained data series
			currSeries = (XYSeries) currPv.get(ObdItemAdapter.FID_DATA_SERIES);
			if (currSeries == null) continue;
			// add initial measurement to series data to ensure
			// at least one measurement is available
			if (currSeries.getItemCount() < 1)
				currSeries.add(startTime, Float.parseFloat(currPv.get(EcuDataPv.FID_VALUE).toString()));

			// set scale to display series
			currSeries.setScaleNumber(i);
			// register series to graph
			sensorData.addSeries(i, currSeries);
			/* set up series visual parameters */
			renderer.setYTitle(String.valueOf(currPv.get(EcuDataPv.FID_UNITS)), i);
			renderer.setYAxisAlign(((i % 2) == 0) ? Align.LEFT : Align.RIGHT, i);
			renderer.setYLabelsAlign(((i % 2) == 0) ? Align.LEFT : Align.RIGHT, i);
			renderer.setYLabelsColor(i, pidColor);
			/* set up new line renderer */
			XYSeriesRenderer r = new XYSeriesRenderer();
			r.setColor(pidColor);
			r.setStroke(getStroke(pid!=0?pid:position));
			// register line renderer
			renderer.addSeriesRenderer(i, r);
			i++;
		}
	}
}
