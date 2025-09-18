package com.obddroid.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;

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
	 * storage as filename <AppName>_<TimeStamp>.jpeg
	 *
	 * @param context context of view
	 * @param view    view to be saved
	 */
	public static void takeScreenShot(Context context, View view)
	{
		// get Bitmap from the view
		Bitmap bitmap = loadBitmapFromView(view);
		// generate file name
		String mPath = Environment.getExternalStorageDirectory()
			+ File.separator
			+ context.getPackageName() + "."
			+ System.currentTimeMillis()
			+ ".png";
		File imageFile = new File(mPath);

		try
		{
			// compress the bitmap to PNG file
			OutputStream fout = new FileOutputStream(imageFile);
			bitmap.compress(Bitmap.CompressFormat.PNG, 90, fout);
			// show notification
			Toast.makeText(context, "Screenshot saved: " + mPath, Toast.LENGTH_SHORT).show();
			log.info("Screenshot saved: " + mPath);

			fout.flush();
			fout.close();
		} catch (FileNotFoundException e)
		{
			log.log(Level.SEVERE, "ScreenShot", e);
		} catch (IOException e)
		{
			log.log(Level.SEVERE, "ScreenShot", e);
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
