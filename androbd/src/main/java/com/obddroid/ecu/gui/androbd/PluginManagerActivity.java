package com.obddroid.ecu.gui.androbd;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Plugin Manager Activity
 *
 * Discovers and displays installed OBD-Droid plugins.
 * Plugins are separate APKs that respond to the
 * com.obddroid.androbd.plugin.IDENTIFY intent
 */
public class PluginManagerActivity extends Activity {

    private ListView pluginListView;
    private ArrayAdapter<String> adapter;
    private List<ResolveInfo> plugins;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set theme
        setTheme(R.style.AppTheme);

        // Apply full screen based on preference
        if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean(MainActivity.PREF_FULLSCREEN, false))
        {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        // Set status bar and navigation bar to black
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
        }

        // Create a simple ListView
        pluginListView = new ListView(this);
        setContentView(pluginListView);

        // Set title
        setTitle(R.string.plugin_manager);

        // Load plugins
        loadPlugins();
    }

    private void loadPlugins() {
        // Query for installed plugins
        Intent pluginIntent = new Intent("com.obddroid.androbd.plugin.IDENTIFY");
        PackageManager pm = getPackageManager();

        plugins = pm.queryIntentActivities(pluginIntent, 0);

        List<String> pluginNames = new ArrayList<>();

        if (plugins.isEmpty()) {
            pluginNames.add("No OBD-Droid plugins installed");
            pluginNames.add("");
            pluginNames.add("Plugins extend OBD-Droid with:");
            pluginNames.add("• Data logging (CSV, cloud sync)");
            pluginNames.add("• Real-time streaming (MQTT, APIs)");
            pluginNames.add("• Performance analysis");
            pluginNames.add("• Custom visualizations");
            pluginNames.add("• Maintenance tracking");
            pluginNames.add("");
            pluginNames.add("Developers: See PLUGIN_DEVELOPMENT.md");
            pluginNames.add("on GitHub for creating plugins");
        } else {
            // List installed plugins
            for (ResolveInfo info : plugins) {
                String appName = info.loadLabel(pm).toString();
                String packageName = info.activityInfo.packageName;
                pluginNames.add(appName + "\n" + packageName);
            }
        }

        // Create adapter and set to ListView
        adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_list_item_1,
            pluginNames);
        pluginListView.setAdapter(adapter);

        // Set click listener for plugin items
        pluginListView.setOnItemClickListener((parent, view, position, id) -> {
            if (!plugins.isEmpty() && position < plugins.size()) {
                showPluginDetails(plugins.get(position));
            }
        });
    }

    private void showPluginDetails(ResolveInfo plugin) {
        PackageManager pm = getPackageManager();
        String appName = plugin.loadLabel(pm).toString();
        String packageName = plugin.activityInfo.packageName;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(appName);
        builder.setMessage("Package: " + packageName + "\n\n" +
                          "This plugin extends OBD-Droid functionality.\n" +
                          "Check plugin's own settings for configuration.");
        builder.setPositiveButton("OK", null);

        // Add option to open plugin settings if available
        try {
            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                builder.setNeutralButton("Open", (dialog, which) -> {
                    startActivity(launchIntent);
                });
            }
        } catch (Exception e) {
            // Ignore if can't launch
        }

        builder.show();
    }
}