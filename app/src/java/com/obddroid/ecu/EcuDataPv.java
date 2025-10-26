package com.obddroid.ecu;

import com.obddroid.common.ProcessVariables.IndexedProcessVar;

/**
 * Process variable which contains a single OBD data item
 *

 */
public class EcuDataPv extends IndexedProcessVar
{
	/** UID for serialisation */
	private static final long serialVersionUID = -7787217159439147214L;

	// Field IDs
	public static final int FID_PID = 0;
	public static final int FID_OFS = 1;
	public static final int FID_DESCRIPT = 2;
	public static final int FID_VALUE = 3;
	public static final int FID_UNITS = 4;
	// optional Field IDs which will be invisible for table display
	public static final String FID_FORMAT = "FMT";
	public static final String FID_CNVID = "CNV_ID";
	public static final String FID_MIN = "MIN";
	public static final String FID_MAX = "MAX";
	public static final String FID_BIT_OFS = "BIT_OFS";
	public static final String FID_MNEMONIC = "MNEMONIC";
	public static final String FID_COLOR = "COLOR";
	public static final String FID_UPDT_PERIOD = "PERIOD";

	public static final String[] FIELDS =
		{
			"PID",
			"OFS",
			"DESCRIPTION",
			"VALUE",
			"UNITS",
		};

	private transient Object renderingComponent;

	/**
	 * Creates a new instance of EcuDataPv
	 */
	public EcuDataPv()
	{
		super();
		this.setKeyAttribute(FIELDS[0]);
	}

	public String[] getFields()
	{
		return (FIELDS);
	}

	/**
	 * get physical measurement units of a data PV.
	 * Units may change because of the conversion system changed (metric/imperial)
	 *
	 * @return string of physical measurement units
	 */
	public String getUnits()
	{
		String result = "";
		try
		{
			// Try to get from assigned conversion
			Conversion[] cnv = (Conversion[]) get(FID_CNVID);
			if(cnv != null && cnv[EcuDataItem.cnvSystem] != null)
			{
				result = cnv[EcuDataItem.cnvSystem].getUnits();
			}
			else
			{
				// Attempt to get units from field
				result = get(FID_UNITS).toString();
			}
		}
		catch(Exception ex)
		{
			result="";
		}
		return result;
	}

	public Object getRenderingComponent()
	{
		return renderingComponent;
	}

	public void setRenderingComponent(Object renderingComponent)
	{
		this.renderingComponent = renderingComponent;
	}

	public String toString()
	{
		return (String.format("%02X.%d.%d", get(FID_PID), get(FID_OFS), get(FID_BIT_OFS)));
	}
}
