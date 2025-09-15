# OBD-Droid Plugin Development Guide

## Overview

OBD-Droid plugins are Android apps that extend the functionality of OBD-Droid without modifying the core application. Plugins can log data, provide visualizations, sync to cloud services, or add any custom functionality.

## Quick Start

### Minimum Requirements

1. Android Studio
2. Android SDK (same minimum version as OBD-Droid)
3. Basic knowledge of Android development

### Step 1: Create a New Android Project

Create a new Android application with:
- Minimum SDK: API 17 (same as OBD-Droid)
- No launcher activity needed (plugins don't run standalone)

### Step 2: Configure AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.obdplugin">

    <application
        android:allowBackup="true"
        android:label="@string/app_name">

        <!-- Plugin identification activity -->
        <activity
            android:name=".PluginActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="com.obddroid.androbd.plugin.IDENTIFY" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <!-- Data receiver (if plugin receives OBD data) -->
        <receiver
            android:name=".OBDDataReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="com.obddroid.androbd.plugin.DATA" />
            </intent-filter>
        </receiver>

        <!-- Configuration activity (optional) -->
        <activity
            android:name=".ConfigActivity"
            android:label="Plugin Settings">
            <intent-filter>
                <action android:name="com.obddroid.androbd.plugin.CONFIG" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

### Step 3: Implement Plugin Identification

```java
package com.example.obdplugin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class PluginActivity extends Activity {

    // Feature flags
    private static final int FEATURE_CONFIG = 0x01;  // Has settings
    private static final int FEATURE_ACTION = 0x02;  // Manual actions
    private static final int FEATURE_DATA = 0x04;    // Receives data
    private static final int FEATURE_PROVIDER = 0x08; // Provides data

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Respond to OBD-Droid's query
        Intent response = new Intent();

        // Basic plugin information
        response.putExtra("NAME", "My OBD Plugin");
        response.putExtra("VERSION", "1.0.0");
        response.putExtra("CLASS", getPackageName());

        // Declare plugin features (CONFIG + DATA in this example)
        response.putExtra("FEATURES", FEATURE_CONFIG | FEATURE_DATA);

        // Additional information
        response.putExtra("DESCRIPTION", "Logs OBD data to CSV files");
        response.putExtra("COPYRIGHT", "© 2025 Your Name");
        response.putExtra("LICENSE", "MIT");

        setResult(RESULT_OK, response);
        finish();
    }
}
```

### Step 4: Handle OBD Data (Optional)

```java
package com.example.obdplugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class OBDDataReceiver extends BroadcastReceiver {
    private static final String TAG = "OBDPlugin";

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.obddroid.androbd.plugin.DATA".equals(intent.getAction())) {
            Bundle data = intent.getExtras();

            if (data != null) {
                // Extract OBD data
                String pidName = data.getString("name");
                String pidValue = data.getString("value");
                String pidUnit = data.getString("unit");
                long timestamp = data.getLong("timestamp");

                // Process the data (example: log to file)
                Log.d(TAG, String.format("OBD Data: %s = %s %s at %d",
                    pidName, pidValue, pidUnit, timestamp));

                // Your custom processing here
                saveToCSV(pidName, pidValue, pidUnit, timestamp);
            }
        }
    }

    private void saveToCSV(String name, String value, String unit, long timestamp) {
        // Implement CSV logging
        // Remember to request WRITE_EXTERNAL_STORAGE permission
    }
}
```

### Step 5: Add Configuration UI (Optional)

```java
package com.example.obdplugin;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class ConfigActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load preferences from XML
        addPreferencesFromResource(R.xml.preferences);
    }
}
```

## Plugin Features in Detail

### CONFIG Feature (Bit 0)

Allows users to configure plugin settings:
- Implement a configuration Activity
- Handle `com.obddroid.androbd.plugin.CONFIG` intent
- Store settings using SharedPreferences

### ACTION Feature (Bit 1)

Enables manual trigger actions:
- Start/stop logging
- Export data
- Clear stored data
- Handle `com.obddroid.androbd.plugin.ACTION` intent

### DATA Feature (Bit 2)

Receives real-time OBD data:
- Register BroadcastReceiver for data updates
- Process PIDs as they're read
- Data includes: name, value, unit, timestamp

### DATA_PROVIDER Feature (Bit 3)

Provides custom data to OBD-Droid:
- Inject calculated values
- Add GPS data
- Provide sensor readings
- Send via `com.obddroid.androbd.plugin.PROVIDE_DATA` intent

## Testing Your Plugin

1. **Install OBD-Droid** on your device/emulator
2. **Install your plugin** APK
3. **Open OBD-Droid** and go to Plugin Manager
4. **Verify your plugin appears** in the list
5. **Test features**:
   - Tap plugin for details
   - Test configuration (if implemented)
   - Connect to OBD adapter to test data flow

## Best Practices

### Performance
- Don't block the main thread
- Use background services for heavy processing
- Batch file I/O operations
- Respect device battery life

### Permissions
- Request only necessary permissions
- Handle permission denials gracefully
- Use runtime permissions for Android 6.0+

### Data Storage
- Use app-specific directories
- Implement data export functionality
- Provide data cleanup options
- Respect user privacy

### User Experience
- Provide clear plugin description
- Include configuration options
- Show status notifications when active
- Handle errors gracefully

## Example Plugin Ideas

### Simple Examples
1. **CSV Logger** - Log all PIDs to CSV file
2. **Speed Alert** - Beep when exceeding speed limit
3. **Trip Recorder** - Track journey statistics

### Advanced Examples
1. **Cloud Sync** - Upload to Google Drive/Dropbox
2. **MQTT Bridge** - Stream to Home Assistant
3. **Performance Meter** - 0-60, quarter mile times
4. **Eco Coach** - Real-time driving efficiency tips
5. **Maintenance Tracker** - Service interval reminders

## Troubleshooting

### Plugin doesn't appear in Plugin Manager
- Check intent filter in AndroidManifest.xml
- Ensure activity is exported (`android:exported="true"`)
- Verify package is installed

### Not receiving OBD data
- Check DATA feature flag is set
- Verify BroadcastReceiver registration
- Ensure OBD-Droid has active connection

### Configuration not working
- Set CONFIG feature flag
- Implement CONFIG intent handler
- Check activity launches correctly

## Plugin Communication Protocol

### Intent Actions

| Action | Direction | Description |
|--------|-----------|-------------|
| `com.obddroid.androbd.plugin.IDENTIFY` | OBD→Plugin | Request plugin information |
| `com.obddroid.androbd.plugin.CONFIG` | OBD→Plugin | Open configuration |
| `com.obddroid.androbd.plugin.ACTION` | OBD→Plugin | Trigger manual action |
| `com.obddroid.androbd.plugin.DATA` | OBD→Plugin | Send OBD data |
| `com.obddroid.androbd.plugin.PROVIDE_DATA` | Plugin→OBD | Provide custom data |

### Data Bundle Keys

| Key | Type | Description |
|-----|------|-------------|
| `name` | String | PID name/identifier |
| `value` | String | Current value |
| `unit` | String | Unit of measurement |
| `timestamp` | Long | Unix timestamp |
| `min` | Float | Minimum value |
| `max` | Float | Maximum value |

## Resources

- [OBD-Droid GitHub](https://github.com/Wal33D/OBD-Droid)
- [Android Developer Documentation](https://developer.android.com)
- [OBD-II PID Reference](https://en.wikipedia.org/wiki/OBD-II_PIDs)

## Support

For plugin development questions:
1. Check this guide first
2. Review example plugins on GitHub
3. Open an issue with the `plugin` tag

Happy plugin development!