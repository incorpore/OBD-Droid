

package com.obddroid.ui.activities;

import com.obddroid.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Shows a {@link ListView} of available USB devices.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public final class UsbDeviceListActivity extends AppCompatActivity
{
	private static final String TAG = UsbDeviceListActivity.class.getSimpleName();
	private static final Logger log = Logger.getLogger(TAG);
	
	/** selected USB port */
	public static UsbSerialPort selectedPort = null;

	private UsbManager mUsbManager;
	private static final int MESSAGE_REFRESH = 101;
	private static final long REFRESH_TIMEOUT_MILLIS = 5000;

	@SuppressLint("HandlerLeak")
	private final Handler mHandler = new Handler(Looper.getMainLooper())
	{
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
				case MESSAGE_REFRESH:
					refreshDeviceList();
					mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
					break;
				default:
					super.handleMessage(msg);
					break;
			}
		}

	};

	private final List<UsbSerialPort> mEntries = new ArrayList<>();
	private ArrayAdapter<UsbSerialPort> mAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Apply full screen based on preference using modern WindowInsetsController
		if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean(MainActivity.PREF_FULLSCREEN, false))
		{
			WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
			if (windowInsetsController != null) {
				windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
				windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
			}
		}

		setContentView(R.layout.usb_list);

		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		ListView mListView = findViewById(R.id.deviceList);
		mAdapter = new ArrayAdapter<UsbSerialPort>(this,
		                                           android.R.layout.simple_expandable_list_item_2,
		                                           mEntries)
		{
			@Override
			public View getView(int position, View convertView, ViewGroup parent)
			{
				View row = convertView;
				if (row == null)
				{
					final LayoutInflater inflater =
						(LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					if (inflater != null) {
						row = inflater.inflate(android.R.layout.simple_list_item_2, parent, false);
					}
				}

				if (row != null)
				{
					final UsbSerialPort port = mEntries.get(position);
					final UsbSerialDriver driver = port.getDriver();
					final UsbDevice device = driver.getDevice();

					final String title = String.format("USB: 0x%04x/0x%04x",
					                                   device.getVendorId(),
					                                   device.getProductId());
					final String subtitle = driver.getClass().getSimpleName();

					TextView text1 = row.findViewById(android.R.id.text1);
					TextView text2 = row.findViewById(android.R.id.text2);

					if (text1 != null) text1.setText(title);
					if (text2 != null) text2.setText(subtitle);
				}

				return row;
			}

		};
		mListView.setAdapter(mAdapter);

		mListView.setOnItemClickListener(new ListView.OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id)
			{
				log.fine("Pressed item " + position);
				if (position >= mEntries.size())
				{
					log.warning("Illegal position.");
					return;
				}

				selectedPort = mEntries.get(position);

				// Create the result Intent and include the MAC address
				Intent intent = new Intent();
				// Set result and finish this Activity
				setResult(Activity.RESULT_OK, intent);
				log.fine("Sending Result...");
				finish();
			}
		});
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		mHandler.sendEmptyMessage(MESSAGE_REFRESH);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		mHandler.removeMessages(MESSAGE_REFRESH);
	}

	@SuppressLint("StaticFieldLeak")
	private void refreshDeviceList()
	{
		new AsyncTask<Void, Void, List<UsbSerialPort>>()
		{
			@Override
			protected List<UsbSerialPort> doInBackground(Void... params)
			{
				log.fine("Refreshing device list ...");
				final List<UsbSerialDriver> drivers =
					UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
				final List<UsbSerialPort> result = new ArrayList<>();

				for (final UsbSerialDriver driver : drivers)
				{
					final List<UsbSerialPort> ports = driver.getPorts();
					log.fine(String.format("+ %s: %s selectedPort%s",
					                         driver, ports.size(),
					                         ports.size() == 1 ? "" : "s"));
					result.addAll(ports);
				}

				return result;
			}

			@SuppressLint("StringFormatInvalid")
			@Override
			protected void onPostExecute(List<UsbSerialPort> result)
			{
				mEntries.clear();
				mEntries.addAll(result);
				TextView numFound = findViewById(R.id.num_found);
				numFound.setText(getString(R.string.devices_found, result.size()));
				mAdapter.notifyDataSetChanged();
				log.fine("Done refreshing, " + mEntries.size() + " entries found.");
			}

		}.execute();
	}
}
