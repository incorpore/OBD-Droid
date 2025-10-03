package com.obddroid.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;

import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.SortedMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.obddroid.R;

/**
 * Builds the CSV dump for sharing.

 */
public class ExportTask
{
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private final Activity activity;
	@SuppressLint("SimpleDateFormat")
	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

	private static final String OPT_FIELD_DELIM		= "csv_field_delimiter";
	private static final String OPT_RECORD_DELIM	= "csv_record_delimiter";
	private static final String OPT_TEXT_QUOTED 	= "csv_text_quoted";
	private static final String OPT_SEND_EXPORT 	= "send_after_export";

	private static String CSV_FIELD_DELIMITER = ",";
	private static String CSV_LINE_DELIMITER = "\n";
	private static boolean CSV_TEXT_QUOTED = false;

	private static final String TAG = ExportTask.class.getSimpleName();
	private static final Logger log = Logger.getLogger(TAG);
	
	private final SharedPreferences prefs;

	// file name to be saved
    private final String path;
	private final String fileName;

	public ExportTask(Activity activity)
	{
		this.activity = activity;
        path = FileHelper.getPath(activity).concat(File.separator+"csv");
		fileName = path.concat(File.separator+FileHelper.getFileName()
                       .concat(".csv"));

		// get preferences
		prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		CSV_FIELD_DELIMITER = prefs.getString(OPT_FIELD_DELIM,",");
		CSV_LINE_DELIMITER  = prefs.getString(OPT_RECORD_DELIM,"\n");
		CSV_TEXT_QUOTED     = prefs.getBoolean(OPT_TEXT_QUOTED,false);
	}

	private static String quoteStringIfNeeded(String string)
	{
		return String.format( CSV_TEXT_QUOTED ? "\"%s\"" : "%s", string);
	}

	public void execute(XYMultipleSeriesDataset... datasets)
	{
		// Execute background task
		executorService.execute(() -> {
			String result = performExport(datasets);

			// Run onPostExecute on UI thread
			mainHandler.post(() -> onPostExecute(result));
		});
	}

	private String performExport(XYMultipleSeriesDataset... params)
	{
		double currX;
		double currY;
		int maxCounts = 0;
		int highestResChannel = 0;	/* channel id with highest x-resolution */

		XYSeries series[] = params[0].getSeries();

		// find channel with highest x-resolution
		for (int i = 0; i < series.length; i++)
		{
			if (maxCounts < series[i].getItemCount())
			{
				maxCounts = series[i].getItemCount();
				highestResChannel = i;
			}
		}
		
		//noinspection ResultOfMethodCallIgnored
		new File(path).mkdirs();
		FileWriter writer;
		try
		{
			writer = new FileWriter(new File(fileName));

			// create header line
			writer.append(quoteStringIfNeeded(activity.getString(R.string.time)));
			writer.append(CSV_FIELD_DELIMITER);
			for (XYSeries sery : series)
			{
				writer.append(quoteStringIfNeeded(sery.getTitle()));
				writer.append(CSV_FIELD_DELIMITER);
			}
			writer.append(CSV_LINE_DELIMITER);

			// generate data
			for (int i = 0; i < maxCounts; i++)
			{
				currX = series[highestResChannel].getX(i);
				writer.append(dateFormat.format(new Date((long)currX)));
				writer.append(CSV_FIELD_DELIMITER);
				for (XYSeries sery : series)
				{
					try
					{
						SortedMap<Double, Double> map = sery.getRange(currX, currX, true);
						currY = map.get(map.firstKey());
						writer.append(String.valueOf(currY));
						writer.append(CSV_FIELD_DELIMITER);
					} catch (Exception ex)
					{
						// do nothing, just catch the error
					}
				}
				writer.append(CSV_LINE_DELIMITER);
				// Note: Progress tracking removed as Window.FEATURE_PROGRESS is deprecated
			}
			writer.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return fileName;
	}

	private void onPostExecute(String result)
	{
		// show saved message
		String msg = String.format("CSV %s to %s",
								   activity.getString(R.string.saved),
								   fileName);
		log.log(Level.INFO, msg);
		SnackbarHelper.showSuccess(activity, msg, SnackbarHelper.Duration.SHORT);

		// if export file should be sent immediately ...
		if(prefs.getBoolean(OPT_SEND_EXPORT, false))
		{
			// allow sending the generated file ...
			Intent sendIntent = new Intent(Intent.ACTION_SEND);
			sendIntent.setType("*/*");
			sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(fileName)));
			activity.startActivity(
					Intent.createChooser(sendIntent,
										 activity.getResources().getText(R.string.send_to)));
		}

		// Shutdown executor after task completion
		executorService.shutdown();
	}
}
