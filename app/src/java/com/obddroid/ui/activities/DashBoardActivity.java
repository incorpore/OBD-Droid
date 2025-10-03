package com.obddroid.ui.activities;
import android.content.Intent;
import android.graphics.Color;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ListAdapter;

import com.obddroid.core.ecu.EcuDataItem;
import com.obddroid.core.ecu.EcuDataItems;
import com.obddroid.core.ecu.EcuDataPv;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.core.pvs.PvChangeEvent;
import com.obddroid.core.pvs.PvChangeListener;
import com.github.anastr.speedviewlib.Gauge;

import com.obddroid.ui.adapters.ObdGaugeAdapter;
import com.obddroid.R;

import java.util.HashSet;
import java.util.Objects;


/**
 * Display selected data items as dashboard
 */
public class DashBoardActivity extends AppCompatActivity
		implements PvChangeListener, AdapterView.OnItemLongClickListener
{
	/**
	 * For passing the index number of the <code>Sensor</code> in its
	 * <code>SensorManager</code>
	 */
	public static final String POSITIONS = "POSITIONS";
	/**
	 * For passing the resource id of the <code>dashboard display</code>
	 */
	public static final String RES_ID = "RES_ID";

	/**
	 * Minimum size for gauges to be displayed
	 */
	private static int MIN_GAUGE_SIZE = 300; /* dp */

	/**
	 * the wake lock to keep app communication alive
	 */
	private static PowerManager.WakeLock wakeLock;
	private transient ObdGaugeAdapter adapter;
	private transient GridView grid;

	/** Map to uniquely collect PID numbers */
	private final HashSet<Integer> pidNumbers = new HashSet<>();

	protected static final int MESSAGE_UPDATE_VIEW = 1;

	private static ListAdapter mAdapter = null;
	/** display metrics */
	private static final DisplayMetrics metrics = new DisplayMetrics();

	/** record positions to be charted */
	private transient int[] positions;

	/** data adapter as source of display data */
	public static ListAdapter getAdapter()
	{
		return mAdapter;
	}

	// screen distribution matrix
	private static final int[][] rowCols=
	{
		{1,1},{1,1},{2,1},{2,2},{2,2},{3,2},{3,2},{4,2},{4,2},{3,3},
		{4,3},{4,3},{4,3},{4,4},{4,4},{4,4},{4,4},{5,4},{5,4},{5,4},{5,4}
	};


	/**
	 * Set list adapter as data source of display
	 * @param Adapter List adapter
	 */
	public static void setAdapter(ListAdapter Adapter)
	{
		mAdapter = Adapter;
	}

	/**
	 * Handle message requests
	 */
	protected transient final Handler mHandler = new Handler(Looper.getMainLooper())
	{
		@Override
		public void handleMessage(Message msg)
		{

			switch (msg.what)
			{
				case MESSAGE_UPDATE_VIEW:
					EcuDataPv currPv = (EcuDataPv)msg.obj;
					View itemView = grid.getChildAt(msg.arg1);
					if(itemView != null)
					{
						Gauge gauge = itemView.findViewById(R.id.chart);
						if(gauge != null)
						{
							Number val = (Number)currPv.get(EcuDataPv.FID_VALUE);
							gauge.speedTo(val.floatValue());
						}
					}
					break;
			}
		}
	};

	/**
	 * Update scaling of dashboard items
	 *
	 * Used for:
	 * - start/resume activity
	 * - screen size / orientation change
	 */
	void updateDashboardScaling()
	{
		// calculate minimum gauge size (1.6 inch) based on screen density
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			WindowMetrics windowMetrics = getWindowManager().getCurrentWindowMetrics();
			metrics.widthPixels = windowMetrics.getBounds().width();
			metrics.heightPixels = windowMetrics.getBounds().height();
			metrics.densityDpi = getResources().getConfiguration().densityDpi;
		} else {
			// Suppress deprecation warning - required for backward compatibility with API < 30
			@SuppressWarnings("deprecation")
			android.view.Display display = getWindowManager().getDefaultDisplay();
			display.getMetrics(metrics);
		}
		MIN_GAUGE_SIZE = Math.min( metrics.densityDpi * 15 / 10,
								   Math.min(metrics.widthPixels, metrics.heightPixels));

		int height = metrics.heightPixels;
		int width = metrics.widthPixels;
		int numColumns = Math.max(1, Math.min(positions.length, width / MIN_GAUGE_SIZE));
		int numRows = Math.max(1, Math.min(positions.length, height / MIN_GAUGE_SIZE));

		// distribute gauges on screen
		if(positions.length < numColumns*numRows)
		{
			// read for corresponding number of gauges & orientation
			numColumns = rowCols[positions.length][(width>height)?0:1];
		}
		/* get grid object */
		grid.setNumColumns(numColumns);

		adapter.clear();
		pidNumbers.clear();
		for (int position : positions)
		{
			// get corresponding Process variable
			EcuDataPv currPv = (EcuDataPv) mAdapter.getItem(position);
			if (currPv != null)
			{
				pidNumbers.add(currPv.getAsInt(EcuDataPv.FID_PID));
				adapter.add(currPv);
				currPv.addPvChangeListener(this, PvChangeEvent.PV_MODIFIED);
			}
		}
		grid.setAdapter(adapter);
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

		// Apply full screen based on preference using modern WindowInsetsController
		if(MainActivity.prefs.getBoolean(MainActivity.PREF_FULLSCREEN, false))
		{
			WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
			if (windowInsetsController != null) {
				windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
				windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
			}
		}

		// Always keep main display on for vehicle diagnostics
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		// hide the action bar
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) actionBar.hide();

		// prevent activity from falling asleep
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		wakeLock = Objects.requireNonNull(powerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
			getString(R.string.app_name));
		wakeLock.acquire();

		// set the desired content screen
		int resId = getIntent().getIntExtra(RES_ID, R.layout.dashboard);
		setContentView(resId);
		grid = findViewById(android.R.id.list);
		grid.setOnItemLongClickListener(this);

		// create data adapter
		adapter = new ObdGaugeAdapter( this,
									   R.layout.obd_gauge);

		/* get PIDs to be shown */
		positions = getIntent().getIntArrayExtra(POSITIONS);
	}

	/**
	 * Handle destroy of the Activity
	 */
	@Override
	protected void onDestroy()
	{
		// reset PID limiting
		ObdProt.resetFixedPid();
		adapter.clear();
		// allow sleeping again
		wakeLock.release();
		super.onDestroy();
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume()
	{
		super.onResume();
		// set scaling of dashboard items
		updateDashboardScaling();
		// limit selected PIDs to selection
		MainActivity.setFixedPids(pidNumbers);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		updateDashboardScaling();
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause()
	{
		adapter.clear();
		super.onPause();
	}

	@Override
	public void pvChanged(PvChangeEvent event)
	{
		if(event.getKey().equals(EcuDataPv.FIELDS[EcuDataPv.FID_VALUE])
				&& event.getValue() instanceof Number)
		{
			int pos = adapter.getPosition((EcuDataPv)event.getSource());
			Message msg = mHandler.obtainMessage(MESSAGE_UPDATE_VIEW, pos,0, event.getSource());
			mHandler.sendMessage(msg);
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
	{
		// Set data item to be customized
		EcuDataPv pv = adapter.getItem(position);
		EcuDataItem item = EcuDataItems.byMnemonic.get(pv.get(EcuDataPv.FID_MNEMONIC));
		CustomPidActivity.item = item;

		// start customization ...
		Intent intent = new Intent(this, CustomPidActivity.class);
		startActivity(intent);

		return true;
	}
}
