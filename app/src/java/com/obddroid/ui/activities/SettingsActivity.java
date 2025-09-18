

package com.obddroid.ui.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.obddroid.core.ecu.EcuDataItem;
import com.obddroid.core.obd.ElmProt;
import com.obddroid.core.obd.ObdProt;
import com.obddroid.services.CommService;
import com.obddroid.R;

import java.util.HashSet;
import java.util.Objects;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SettingsActivity
	extends AppCompatActivity
{
	/** The logger object */
	private static final Logger log = Logger.getLogger(SettingsActivity.class.getName());
	
	/**
	 * app preferences
	 */
	private static SharedPreferences prefs;
	/**
	 * key ids for device network settings
	 */
	private static final String[] networkKeys =
	{
		"device_address",
		"device_port"
	};
	/**
	 * key ids for device network settings
	 */
	private static final String[] bluetoothKeys =
	{
		"bt_secure_connection"
	};
	/**
	 * key ids for device network settings
	 */
	private static final String[] usbKeys =
	{
			"comm_baudrate"
	};

	// Preference key for data items
	public static final String KEY_DATA_ITEMS = "data_items";
	static final String KEY_PROT_SELECT = "protocol";
	static final String KEY_COMM_MEDIUM = "comm_medium";
	static final String ELM_MIN_TIMEOUT = "elm_min_timeout";
	static final String ELM_CMD_DISABLE = "elm_cmd_disable";
    static final String ELM_TIMING_SELECT = "adaptive_timing_mode";

	/*
	 * (non-Javadoc)
	 *
	 * @see android.preference.PreferenceActivity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setTheme(R.style.AppTheme_NoActionBar);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		// Set the custom layout first so we can access the toolbar
		setContentView(R.layout.activity_settings);

		// Set up custom toolbar
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		// Apply full screen based on preference
		if(prefs.getBoolean(MainActivity.PREF_FULLSCREEN, false))
		{
			// Ultra-dark mode
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				getWindow().setNavigationBarColor(Color.BLACK);
				getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
					View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
					View.SYSTEM_UI_FLAG_FULLSCREEN
				);
			}
			// Make the toolbar black
			if (toolbar != null) {
				toolbar.setBackgroundColor(Color.BLACK);
			}
		}
		else
		{
			// Set status bar and navigation bar colors to match the toolbar
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				getWindow().setStatusBarColor(Color.parseColor("#212121"));
				getWindow().setNavigationBarColor(Color.parseColor("#212121"));
			}
			// Ensure toolbar is dark grey
			if (toolbar != null) {
				toolbar.setBackgroundColor(Color.parseColor("#212121"));
			}
		}

		// Configure ActionBar - hide default title since we have custom TextView
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null)
		{
			actionBar.setDisplayShowTitleEnabled(false);
			actionBar.setDisplayHomeAsUpEnabled(false);
		}

		// Set up the back button on the right
		ImageButton backButton = findViewById(R.id.back_button);
		backButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				finish();
			}
		});

		// Display the fragment in the container
		getSupportFragmentManager().beginTransaction().replace(R.id.settings_container,
		                                                new PrefsFragment()).commit();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == android.R.id.home)
		{
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public static class PrefsFragment
		extends PreferenceFragmentCompat
		implements Preference.OnPreferenceClickListener,
		SharedPreferences.OnSharedPreferenceChangeListener
	{
		Vector<EcuDataItem> items;

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
		{
			// Load the preferences from an XML resource
			setPreferencesFromResource(R.xml.settings, rootKey);
		}

		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			// set up communication media selection
			setupCommMediaSelection();
			// set up protocol selection
			setupProtoSelection();
			// set up ELM command selection
			setupElmCmdSelection();
            // set up ELM adaptive timing mode selection
			setupElmTimingSelection();
			// set up selectable PID list
			setupPidSelection();
			// update network selection fields
			updateNetworkSelections();
			// add handler for selection update
			prefs.registerOnSharedPreferenceChangeListener(this);
		}

		/**
		 * set up protocol selection
		 */
		void setupProtoSelection()
		{
			ListPreference pref = (ListPreference) findPreference(KEY_PROT_SELECT);
			ElmProt.PROT[] values = ElmProt.PROT.values();
			CharSequence[] titles = new CharSequence[values.length];
			CharSequence[] keys = new CharSequence[values.length];
			int i = 0;
			for (ElmProt.PROT proto : values)
			{
				titles[i] = proto.toString();
				keys[i] = String.valueOf(proto.ordinal());
				i++;
			}
			// set enries and keys
			pref.setEntries(titles);
			pref.setEntryValues(keys);
			pref.setDefaultValue(titles[0]);
			// show current selection
			pref.setSummary(pref.getEntry());
		}

        /**
         * set up protocol selection
         */
        void setupElmTimingSelection()
        {
            ListPreference pref = (ListPreference) findPreference(ELM_TIMING_SELECT);
            ElmProt.AdaptTimingMode[] values = ElmProt.AdaptTimingMode.values();
            CharSequence[] titles = new CharSequence[values.length];
            CharSequence[] keys = new CharSequence[values.length];
            int i = 0;
            for (ElmProt.AdaptTimingMode mode : values)
            {
				titles[i] = mode.toString();
				keys[i] = mode.toString();
				i++;
            }
            // set entries and keys
            pref.setEntries(titles);
            pref.setEntryValues(keys);
            pref.setDefaultValue(titles[0]);
            // show current selection
            pref.setSummary(pref.getEntry());
        }

		/**
		 * set up protocol selection
		 */
		void setupElmCmdSelection()
		{
			MultiSelectListPreference pref =
				(MultiSelectListPreference) findPreference(ELM_CMD_DISABLE);
			ElmProt.CMD[] values = ElmProt.CMD.values();
			HashSet<String> selections = new HashSet<>();
			CharSequence[] titles = new CharSequence[values.length];
			CharSequence[] keys = new CharSequence[values.length];
			int i = 0;
			for (ElmProt.CMD cmd : values)
			{
				titles[i] = cmd.toString();
				keys[i] = cmd.toString();
				if(!cmd.isEnabled()) selections.add(cmd.toString());
				i++;
			}
			// set enries and keys
			pref.setEntries(titles);
			pref.setEntryValues(keys);
			pref.setValues(selections);
		}

		/**
		 * set up protocol selection
		 */
		void setupCommMediaSelection()
		{
			ListPreference pref = (ListPreference) findPreference(KEY_COMM_MEDIUM);
			CommService.MEDIUM[] values = CommService.MEDIUM.values();
			CharSequence[] titles = new CharSequence[values.length];
			CharSequence[] keys = new CharSequence[values.length];
			int i = 0;
			for (CommService.MEDIUM proto : values)
			{
				titles[i] = proto.toString();
				keys[i] = String.valueOf(proto.ordinal());
				i++;
			}
			// set enries and keys
			pref.setEntries(titles);
			pref.setEntryValues(keys);
			pref.setDefaultValue(titles[0]);
			// show current selection
			pref.setSummary(pref.getEntry());
		}

		/**
		 * set up selection for PIDs
		 */
		void setupPidSelection()
		{
			MultiSelectListPreference itemList =
				(MultiSelectListPreference) findPreference(KEY_DATA_ITEMS);

			// collect data items for selection
			items = ObdProt.dataItems.getSvcDataItems(ObdProt.OBD_SVC_DATA);
			HashSet<String> selections = new HashSet<>();
			CharSequence[] titles = new CharSequence[items.size()];
			CharSequence[] keys = new CharSequence[items.size()];
			// loop through data items
			int i = 0;
			for (EcuDataItem currItem : items)
			{
				titles[i] = currItem.label;
				keys[i] = currItem.toString();
				selections.add(currItem.toString());
				i++;
			}
			// set enries and keys
			itemList.setEntries(titles);
			itemList.setEntryValues(keys);

			// if there is no item selected, mark all as selected
			if (itemList.getValues().size() == 0)
			{
				itemList.setValues(selections);
			}
		}

		/**
		 * set up preference text for extension files
		 *
		 * @param key preference key to be set up
		 */
		void setPrefsText(String key)
		{
			Preference prefComp = findPreference(key);
			prefComp.setOnPreferenceClickListener(this);
			String value = prefs.getString(key, null);
			if (value != null)
			{
				prefComp.setSummary(value);
			}
		}

		/**
		 * Update fields for network parameters
		 *
		 * enable/disable elements for network parameters
		 * based on selection of communication medium
		 */
		void updateNetworkSelections()
		{
			boolean networkSelected =
				String.valueOf(CommService.MEDIUM.NETWORK.ordinal())
					.equals(prefs.getString(KEY_COMM_MEDIUM,""));
			boolean bluetoothSelected =
				String.valueOf(CommService.MEDIUM.BLUETOOTH.ordinal())
					.equals(prefs.getString(KEY_COMM_MEDIUM,""));
			boolean usbSelected =
					String.valueOf(CommService.MEDIUM.USB.ordinal())
							.equals(prefs.getString(KEY_COMM_MEDIUM,""));

			// enable/disable network specific entries
			for(String key : networkKeys)
			{
				Preference pref = findPreference(key);
				pref.setEnabled(networkSelected);
			}

			// enable/disable bluetooth specific entries
			for(String key : bluetoothKeys)
			{
				Preference pref = findPreference(key);
				pref.setEnabled(bluetoothSelected);
			}

			// enable/disable usb specific entries
			for(String key : usbKeys)
			{
				Preference pref = findPreference(key);
				pref.setEnabled(usbSelected);
			}
		}

		@Override
		public boolean onPreferenceClick(Preference preference)
		{
			Intent intent = preference.getIntent();
			try
			{
				// OPEN intents require result handling
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				startActivityForResult(intent, preference.hashCode());
			}
			catch(Exception e)
			{
				log.log(Level.SEVERE, "Settings", e);
				Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
			}
			return true;
		}

		/**
		 * Handler for result messages from other activities
		 */
		@Override
		public void onActivityResult(int requestCode, int resultCode, Intent data)
		{
			// Extension files are no longer supported
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
		{
			Preference pref = findPreference(key);

			if (pref instanceof ListPreference)
			{
				ListPreference currPref = (ListPreference) pref;
				currPref.setSummary(currPref.getEntry());
			}
			else
			if (pref instanceof EditTextPreference)
			{
				EditTextPreference currPref = (EditTextPreference) pref;
				currPref.setSummary(currPref.getText());
			}

			// Apply full screen changes instantly
			if(MainActivity.PREF_FULLSCREEN.equals(key))
			{
				Toolbar toolbar = getActivity().findViewById(R.id.toolbar);
				if(sharedPreferences.getBoolean(MainActivity.PREF_FULLSCREEN, false))
				{
					// Ultra-dark mode
					getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						getActivity().getWindow().setNavigationBarColor(Color.BLACK);
						getActivity().getWindow().getDecorView().setSystemUiVisibility(
							View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
							View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
							View.SYSTEM_UI_FLAG_FULLSCREEN
						);
					}
					// Make the toolbar black
					if (toolbar != null) {
						toolbar.setBackgroundColor(Color.BLACK);
					}
				}
				else
				{
					getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						getActivity().getWindow().setNavigationBarColor(Color.parseColor("#212121"));
						getActivity().getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
					}
					// Restore toolbar color
					if (toolbar != null) {
						toolbar.setBackgroundColor(Color.parseColor("#212121"));
					}
				}
			}

			if(KEY_COMM_MEDIUM.equals(key))
				updateNetworkSelections();

			if(ELM_TIMING_SELECT.equals(key))
				//noinspection ConstantConditions
				findPreference(ELM_MIN_TIMEOUT)
					.setEnabled(ElmProt.AdaptTimingMode.SOFTWARE.toString()
						          .equals(((ListPreference)pref).getValue())
					           );
		}
	}
}
