package com.obddroid.ecu.gui.application;

import java.io.File;

import javax.swing.filechooser.FileFilter;

/**
 * File filter for saving/loading OBD files
 *

 */
class ObdFileFilter extends FileFilter
{

	private static final String[] FLT_EXTENSIONS =
		{
			"obd",
		};
	private static final String FLT_DESCRIPTION = "OBD Files";

	/** Creates a new instance of ObdFileFilter */
	public ObdFileFilter()
	{
	}

	/**
	 * Return the extension portion of the file's name .
	 *
	 * @param f file to get extension for
	 * @return file extension
	 * @see #getExtension
	 * @see FileFilter#accept
	 */
	private String getExtension(File f)
	{
		if (f != null)
		{
			String filename = f.getName();
			int i = filename.lastIndexOf('.');
			if (i > 0 && i < filename.length() - 1)
			{
				return filename.substring(i + 1);
			}
		}
		return null;
	}

	/**
	 * Whether the given file is accepted by this filter.
	 */
	public boolean accept(java.io.File f)
	{
		boolean result = f.isDirectory();
		String ext = getExtension(f);
		for (int i = 0; !result && i < FLT_EXTENSIONS.length; i++)
			result |= FLT_EXTENSIONS[i].equalsIgnoreCase(ext);
		return (result);
	}

	/**
	 * The description of this filter. For example: "JPG and GIF Images"
	 *
	 * @return description of filter (to be used within file chooser ...)
	 */
	public String getDescription()
	{
		return (FLT_DESCRIPTION);
	}

}
