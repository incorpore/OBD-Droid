package com.obddroid.core.ecu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Diagnostic data conversions
 *

 */
public class EcuConversions extends HashMap<String, Conversion[]>
{
	/** SerialVersion UID */
	private static final long serialVersionUID = 273813879102783740L;

	/** conversion type IDs from CSV file */
	private static final String CNV_TYPE_LINEAR      = "LINEAR";
	private static final String CNV_TYPE_HASH        = "HASH";
	private static final String CNV_TYPE_BITMAP      = "BITMAP";
	private static final String CNV_TYPE_CODELIST    = "CODELIST";
	private static final String CNV_TYPE_PCODELIST   = "PCODELIST";
	private static final String CNV_TYPE_VAG         = "VAG";
	private static final String CNV_TYPE_INT         = "INTEGER";
	private static final String CNV_TYPE_ASCII       = "ASCII";

	/** CSV field positions */
	private static final int FLD_NAME = 0;
	private static final int FLD_TYPE = 1;
	private static final int FLD_VARIANT = 2;
	private static final int FLD_SYSTEM = 3;
	private static final int FLD_FACTOR = 4;
	private static final int FLD_DIVIDER = 5;
	private static final int FLD_OFFSET = 6;
	private static final int FLD_PHOFFSET = 7;
	private static final int FLD_UNITS = 8;
	static final int FLD_DESCRIPTION = 9;
	private static final int FLD_PARAMETERS = 10;

	// the data logger
	private static final Logger log = Logger.getLogger("data.cnv");

	/** DEFAULT type conversion */
	public static final NumericConversion dfltCnv = new IntConversion();

	/** code list conversion */
	public static EcuCodeList codeList = null;
	/**
	 * Create conversion list from default resource file (tab delimited csv)
	 * (protocol/conversions.csv)
	 */
	public EcuConversions()
	{
		// add dynamic entris from csv file(s)
		this("/protocol/conversions.csv");
	}

	/**
	 * Create conversion list from resource file (tab delimited csv)
	 *
	 * @param resource name of resource to be loaded
	 */
	public EcuConversions(String resource)
	{
		// add static conversions
		put("DEFAULT", new Conversion[]{dfltCnv, dfltCnv});
		// add dynamic entris from csv file(s)
		loadFromResource(resource);
	}

	public void loadFromStream(InputStream inStr)
	{
		BufferedReader rdr;
		String currLine;
		String[] params;
		Conversion[] currCnvSet;
		Conversion newCnv;
		int line = 0;

		try
		{
			rdr = new BufferedReader(new InputStreamReader(inStr));
			// loop through all lines of the file ...
			while ((currLine = rdr.readLine()) != null)
			{
				// ignore line 1
				if (++line == 1)
				{
					continue;
				}

				// replace all optional quotes from CSV code list
				currLine = currLine.replaceAll("\"", "");
				// split CSV line into parameters
				params = currLine.split("\t");
				if (params[FLD_TYPE].equals(CNV_TYPE_LINEAR))
				{
					if(params.length > FLD_PARAMETERS)
					{
						// create linear conversion (w/ dynamic parameters)
						newCnv = new LinearConversion(Integer.parseInt(params[FLD_FACTOR]),
													  Integer.parseInt(params[FLD_DIVIDER]),
													  Integer.parseInt(params[FLD_OFFSET]),
													  Integer.parseInt(params[FLD_PHOFFSET]),
													  params[FLD_UNITS],
													  params[FLD_PARAMETERS]);
					}
					else
					{
						// create linear conversion (w/o dynamic parameter)
						newCnv = new LinearConversion(Integer.parseInt(params[FLD_FACTOR]),
													  Integer.parseInt(params[FLD_DIVIDER]),
													  Integer.parseInt(params[FLD_OFFSET]),
													  Integer.parseInt(params[FLD_PHOFFSET]),
													  params[FLD_UNITS]);
					}
				}
				else if (params[FLD_TYPE].equals(CNV_TYPE_HASH))
				{
					// create HashConversion based on CSV data
					newCnv = new HashConversion( String.valueOf(params[FLD_PARAMETERS]).split(";") );
				}
				else if (params[FLD_TYPE].equals(CNV_TYPE_BITMAP))
				{
					// Check if this is a special test status conversion by name
					String conversionName = params[FLD_NAME];
					log.info("BITMAP conversion: name='" + conversionName + "', type='" + params[FLD_TYPE] + "'");

					if ("TEST_STATUS_4".equals(conversionName)) {
						// create TestStatusConversion for 4-bit offset test status
						log.info("Creating TestStatusConversion(4, false) for TEST_STATUS_4");
						newCnv = new TestStatusConversion(4, false);
					} else if ("TEST_STATUS_8".equals(conversionName)) {
						// create TestStatusConversion for 8-bit offset test status
						log.info("Creating TestStatusConversion(8, true) for TEST_STATUS_8");
						newCnv = new TestStatusConversion(8, true);
					} else {
						// create normal BitmapConversion based on CSV parameters
						log.fine("Creating BitmapConversion for " + conversionName);
						newCnv = new BitmapConversion( String.valueOf(params[FLD_PARAMETERS]).split(";") );
					}
				}
				else if (params[FLD_TYPE].equals(CNV_TYPE_CODELIST))
				{
					// create ECU code list based on ResourceBundle
					codeList = new EcuCodeList( String.valueOf(params[FLD_PARAMETERS]));
					newCnv = codeList;
				}
				else if (params[FLD_TYPE].equals(CNV_TYPE_PCODELIST))
				{
					// create OBD code list based on ResourceBundle
					// Use getInstance to preserve any database-backed instance that was set
					codeList = ObdCodeList.getInstance();
					newCnv = codeList;
				}
				else if (params[FLD_TYPE].equals(CNV_TYPE_VAG))
				{
					// create VAG conversion
					newCnv = new VagConversion(Integer.parseInt(params[FLD_VARIANT]),
						Double.parseDouble(params[FLD_FACTOR]) / Integer.parseInt(params[FLD_DIVIDER]),
						Double.parseDouble(params[FLD_OFFSET]),
						params[FLD_UNITS]);
				}
				else if (params[FLD_TYPE].equals(CNV_TYPE_ASCII))
				{
					newCnv = null;
				}
				else if (params[FLD_TYPE].equals(CNV_TYPE_INT))
				{
					newCnv = dfltCnv;
				}
				else
				{
					newCnv = dfltCnv;
				}

				// insert fault code element
				currCnvSet = get(params[FLD_NAME]);
				// if this conversion does not exist yet ...
				if (currCnvSet == null)
				{
					// create new set for metric and imperial
					currCnvSet = new Conversion[EcuDataItem.SYSTEM_TYPES];
					// and initialize both systems with this data
					for (int i = 0; i < EcuDataItem.SYSTEM_TYPES; i++)
					{
						currCnvSet[i] = newCnv;
						log.finer("+" + params[FLD_NAME] + "/" + params[FLD_SYSTEM] + " - " + String.valueOf(newCnv));
					}
				} else
				{
					// if it is known already, then only update the matching system
					for (int i = 0; i < EcuDataItem.SYSTEM_TYPES; i++)
					{
						if (EcuDataItem.cnvSystems[i].equals(params[FLD_SYSTEM]))
						{
							currCnvSet[i] = newCnv;
							log.finer("+" + params[FLD_NAME] + "/" + params[FLD_SYSTEM] + " - " + newCnv.toString());
						}
					}
				}
				// (re-)enter the updated conversion set into map
				put(params[FLD_NAME], currCnvSet);
			}
			rdr.close();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * load conversion list from resource file (tab delimited)
	 *
	 * @param resource name of resource to be loaded
	 */
	private void loadFromResource(String resource)
	{
		try
		{
			loadFromStream(getClass().getResource(resource).openStream());
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
	}

}
