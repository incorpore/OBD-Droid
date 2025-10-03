package com.obddroid.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Screenshot
{
	private static final Logger log = Logger.getLogger(Screenshot.class.getSimpleName());
	
	/**
	 * Take a screenshot of selected view in selected context and save on external
	 * storage as filename <AppName>_<TimeStamp>.png
	 *
	 * @param context context of view
	 * @param view    view to be saved
	 */
	public static void takeScreenShot(Context context, View view)
	{
		// get Bitmap from the view
		Bitmap bitmap = loadBitmapFromView(view);
		String fileName = context.getPackageName() + "." + System.currentTimeMillis() + ".png";
		String savedPath = null;

		try
		{
			OutputStream fout;

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
			{
				// Android 10+ - Use MediaStore API for public Pictures directory
				ContentResolver resolver = context.getContentResolver();
				ContentValues contentValues = new ContentValues();
				contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
				contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
				contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + context.getPackageName());

				Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
				if (imageUri != null)
				{
					fout = resolver.openOutputStream(imageUri);
					savedPath = "Pictures/" + context.getPackageName() + "/" + fileName;
				}
				else
				{
					throw new IOException("Failed to create MediaStore entry");
				}
			}
			else
			{
				// Android 9 and below - Use app-specific external storage
				File picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
				if (picturesDir != null)
				{
					File imageFile = new File(picturesDir, fileName);
					fout = new FileOutputStream(imageFile);
					savedPath = imageFile.getAbsolutePath();
				}
				else
				{
					throw new IOException("External storage not available");
				}
			}

			// compress the bitmap to PNG file
			if (fout != null)
			{
				bitmap.compress(Bitmap.CompressFormat.PNG, 90, fout);
				fout.flush();
				fout.close();

				// show notification
				SnackbarHelper.showSuccess(context, "Screenshot saved: " + savedPath, SnackbarHelper.Duration.SHORT);
				log.info("Screenshot saved: " + savedPath);
			}
		}
		catch (FileNotFoundException e)
		{
			log.log(Level.SEVERE, "ScreenShot", e);
			SnackbarHelper.showError(context, "Failed to save screenshot: " + e.getMessage(), SnackbarHelper.Duration.SHORT);
		}
		catch (IOException e)
		{
			log.log(Level.SEVERE, "ScreenShot", e);
			SnackbarHelper.showError(context, "Failed to save screenshot: " + e.getMessage(), SnackbarHelper.Duration.SHORT);
		}
	}

	/**
	 * get a bitmap from selected view
	 *
	 * @param v       View to be taken
	 * @return Bitmap of selected view
	 */
	private static Bitmap loadBitmapFromView(View v)
	{
		Bitmap returnedBitmap = Bitmap.createBitmap(v.getWidth(),
			v.getHeight(),
			Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(returnedBitmap);
		v.draw(c);

		return returnedBitmap;
	}
}
