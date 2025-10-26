package com.obddroid.obd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Messages and labels for OBD data items
 * Loads from CSV file: standard/messages.csv
 * Format: key, label, description (tab-separated)
 */
public class Messages
{
	private static final String CSV_FILE = "/standard/messages.csv";
	private static final Logger log = Logger.getLogger("messages");

	// Maps key -> label
	private static HashMap<String, String> labels = new HashMap<>();
	// Maps key -> description
	private static HashMap<String, String> descriptions = new HashMap<>();

	public Messages()
	{
		init(CSV_FILE);
	}

	/**
     * Initialize messages from a CSV file
     *
     * @param csvResource Path to CSV resource file
     */
	public static void init(String csvResource)
	{
		labels.clear();
		descriptions.clear();

		try
		{
			InputStream inStr = Messages.class.getResourceAsStream(csvResource);
			if (inStr == null)
			{
				log.severe("Could not find messages CSV file: " + csvResource);
				return;
			}

			BufferedReader rdr = new BufferedReader(new InputStreamReader(inStr));
			String currLine;
			int line = 0;

			// Read CSV file line by line
			while ((currLine = rdr.readLine()) != null)
			{
				line++;
				// Skip header line
				if (line == 1 || currLine.trim().isEmpty() || currLine.startsWith("#"))
				{
					continue;
				}

				// Split by tab
				String[] parts = currLine.split("\t", -1); // -1 keeps empty trailing fields

				if (parts.length >= 2)
				{
					String key = parts[0].trim();
					String label = parts[1].trim();
					String description = parts.length >= 3 ? parts[2].trim() : "";

					labels.put(key, label);
					if (!description.isEmpty())
					{
						descriptions.put(key, description);
					}
				}
			}

			rdr.close();
			log.info("Loaded " + labels.size() + " messages from " + csvResource);
		}
		catch (IOException e)
		{
			log.severe("Error loading messages CSV: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Get the label for a given key
	 *
	 * @param key Key to lookup
	 * @param defaultString Default value if key not found
	 * @return Label string or default
	 */
	public static String getString(String key, String defaultString)
	{
		String value = labels.get(key);
		return (value != null) ? value : defaultString;
	}

	/**
	 * Get the label for a given key
	 *
	 * @param key Key to lookup
	 * @return Label string or !key! if not found
	 */
	public static String getString(String key)
	{
		return getString(key, '!' + key + '!');
	}

	/**
	 * Get the description for a given key
	 *
	 * @param key Key to lookup
	 * @param defaultString Default value if no description found
	 * @return Description string or default
	 */
	public static String getDescription(String key, String defaultString)
	{
		String value = descriptions.get(key);
		return (value != null) ? value : defaultString;
	}

	/**
	 * Get the description for a given key
	 *
	 * @param key Key to lookup
	 * @return Description string or empty string if not found
	 */
	public static String getDescription(String key)
	{
		return getDescription(key, "");
	}
}
