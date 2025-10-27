package com.obddroid.ecu;

import android.util.Log;
import com.obddroid.common.ProcessVariables.PvLimits;
import com.obddroid.obd.Messages;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;


/**
 * Collection of all known OBD data conversions.
 * This collection implements conversions to metric and imperial system
 *
 *
 */
public class Conversions
{

	private static final int SYSTEM_METRIC = 0;
	public static final int SYSTEM_IMPERIAL = 1;
	public static final int SYSTEM_TYPES = 2;

	// current conversion system
	private static final int cnvSystem = SYSTEM_METRIC;
	/**
	 * ID's of Conversions
	 * These ID's are used as index into table below
	 * Please take care of the order
	 */
	public static final int CNV_ID_ONETOONE = 0;
	public static final int CNV_ID_PERCENT = 1;
	public static final int CNV_ID_PERCENT_REL = 2;
	public static final int CNV_ID_PERCENT7 = 3;
	public static final int CNV_ID_PERCENT7_REL = 4;
	public static final int CNV_ID_RPM = 5;
	public static final int CNV_ID_VEHSPEED = 6;
	public static final int CNV_ID_TEMPERATURE = 7;
	public static final int CNV_ID_TEMP_WIDERANGE = 8;
	public static final int CNV_ID_AIRFLOW = 9;
	public static final int CNV_ID_PRESS = 10;
	public static final int CNV_ID_PRESS_AIR = 11;
	public static final int CNV_ID_PRESS_REL = 12;
	public static final int CNV_ID_PRESS_WIDERANGE = 13;
	public static final int CNV_ID_PRESS_VAPOR = 14;
	public static final int CNV_ID_ANGLE = 15;
	public static final int CNV_ID_VOLTAGE = 16;
	public static final int CNV_ID_VOLTAGE_HIGHRES = 17;
	public static final int CNV_ID_RATIO = 18;
	public static final int CNV_ID_RATIO_WIDERANGE = 19;
	public static final int CNV_ID_DISTANCE = 20;
	public static final int CNV_ID_HOURS = 21;
	public static final int CNV_ID_SPEED_HIGHRES = 22;
	public static final int CNV_ID_TORQUE = 23;
	public static final int CNV_ID_RATIO_RELATIVE = 24;
	public static final int CNV_ID_OBD_TYPE = 25;
	public static final int CNV_ID_OBD_CODELIST = 26;
	public static final int CNV_ID_MAX = 27;// This needs to be last entry

	// Use getInstance() to get database-backed instance when available
	private static final ObdCodeList obdCodeList = ObdCodeList.getInstance();

	private static final Hash cnvObdType = new Hash(new String[]{
		"1=OBD II",
		"2=OBD Federal EPA",
		"3=OBD and OBD II",
		"4=OBD I",
		"5=Not OBD compliant",
		"6=EOBD",
		"7=EOBD and OBD II",
		"8=EOBD and OBD",
		"9=EOBD, OBD and OBD II",
		"10=JOBD",
		"11=JOBD and OBD II",
		"12=JOBD and EOBD",
		"13=JOBD, EOBD and OBD II"
	});

	/** limits for RPM display */
	private static final PvLimits rpmLimits = new PvLimits(0.0f, 6000.0f);

	private static final Conversion[][] cnvFactors =
		{
			//                     METRIC                            ,                      IMPERIAL
			//                     FACT,  DIV, OFFS, PhOf,  UNIT     ,                      FACT,  DIV, OFFS, PhOf,  UNIT
			{new Linear(1, 1, 0, 0, "-"), new Linear(1, 1, 0, 0, "-")}, // OneToOne
			{new Linear(100, 255, 0, 0, "%"), new Linear(100, 255, 0, 0, "%")}, // Percent
			{new Linear(100, 255, -128, 0, "%"), new Linear(100, 255, -128, 0, "%")}, // Percent relative
			{new Linear(100, 128, 0, 0, "%"), new Linear(100, 128, 0, 0, "%")}, // Percent 7Bit (Fuel Trim)
			{new Linear(100, 128, -128, 0, "%"), new Linear(100, 128, -128, 0, "%")}, // Percent 7Bit relative)
			{new Linear(1, 4, 0, 0, "/min", rpmLimits), new Linear(1, 4, 0, 0, "/min", rpmLimits)}, // RPM
			{new Linear(1, 1, 0, 0, "km/h"), new Linear(1000, 1609, 0, 0, "mph")}, // vehicle speed
			{new Linear(1, 1, -40, 0, "°C"), new Linear(9, 5, -40, 32, "°F")}, // Temperature
			{new Linear(1, 10, -40, 0, "°C"), new Linear(9, 50, -40, 32, "°F")}, // Temperature (wide range)
			{new Linear(1, 100, 0, 0, "g/s"), new Linear(1, 756, 0, 0, "lb/min")}, // Air Flow
			{new Linear(3, 1, 0, 0, "kPa"), new Linear(4351, 10000, 0, 0, "PSI")}, // Pressure
			{new Linear(1, 1, 0, 0, "kPa"), new Linear(2953, 10000, 0, 0, "inHg")}, // Pressure (intake)
			{new Linear(79, 1000, 0, 0, "kPa"), new Linear(100, 8727, 0, 0, "PSI")}, // Pressure (relative)
			{new Linear(10, 1, 0, 0, "kPa"), new Linear(14504, 10000, 0, 0, "PSI")}, // Pressure (wide range)
			{new Linear(1, 4, 0, 0, "Pa"), new Linear(100, 99635, 0, 0, "in H2O")}, // Pressure (Vapor)
			{new Linear(1, 2, -128, 0, "°"), new Linear(1, 2, -128, 0, "°")}, // Angle (Timing adv)
			{new Linear(1, 1000, 0, 0, "V"), new Linear(1, 1000, 0, 0, "V")}, // Voltage
			{new Linear(10, 81967, 0, 0, "V"), new Linear(10, 81967, 0, 0, "V")}, // Voltage (high resolution)
			{new Linear(100, 32768, 0, 0, "%"), new Linear(100, 32768, 0, 0, "%")}, // Ratio
			{new Linear(100, 256, -32768, 0, "%"), new Linear(100, 256, -32768, 0, "%")}, // Ratio (wide range)
			{new Linear(1, 1, 0, 0, "km"), new Linear(1000, 1609, 0, 0, "miles")}, // Distance
			{new Linear(1, 3600, 0, 0, "h"), new Linear(1, 3600, 0, 0, "h")}, // Time (hours)
			{new Linear(1, 128, 0, 0, "km/h"), new Linear(100, 20595, 0, 0, "mph")}, // vehicle speed
			{new Linear(1, 1, 0, 0, "Nm"), new Linear(1, 1, 0, 0, "Nm")}, // Torque
			{new Linear(100, 65535, 0, 0, "%"), new Linear(100, 65535, 0, 0, "%")}, // Ratio relative
			{cnvObdType, cnvObdType},
			{obdCodeList, obdCodeList},
		};

	/**
	 * Creates a new instance of Conversions
	 */
	public Conversions()
	{
	}

	/**
	 * convert measurement item from storage format to physical value
	 */
	public static float memToPhys(long value, int cnvID)
	{
		return (cnvFactors[cnvID][cnvSystem].memToPhys(value).floatValue());
	}

	/**
	 * convert measurement item from storage format to physical value
	 */
	public static long physToMem(float value, int cnvID)
	{
		return (cnvFactors[cnvID][cnvSystem].physToMem(value).longValue());
	}

	/**
	 * convert measurement item from storage format to physical value
	 */
	public static String getUnits(int cnvID)
	{
		return (cnvFactors[cnvID][cnvSystem].getUnits());
	}

	/**
	 * convert measurement item from storage format to physical value
	 */
	private static String memToString(long value, int cnvID, int decimals)
	{
		return (cnvFactors[cnvID][cnvSystem].memToString(value, decimals));
	}

	/** object to be used for parsing and formatting decimal numbers */
	private static DecimalFormat decimalFormat;
	private static final DecimalFormat[] formats =
		{
			new DecimalFormat("0;-#"),
			new DecimalFormat("0.0;-#"),
			new DecimalFormat("0.00;-#"),
			new DecimalFormat("0.000;-#"),
			new DecimalFormat("0.0000;-#")
		};

	/**
	 * Format physical value to physical value string
	 *
	 * @param physVal  physical value
	 * @param cnvId    ID of conversion to be used
	 * @param decimals number of decimals for formatting
	 * @return physical value as formatted string
	 */
	public static String physToPhysFmtString(Float physVal, int cnvId, int decimals)
	{
		String result;
		if (decimals >= 0)
		{
			decimalFormat = formats[decimals];
			result = decimalFormat.format(physVal);
		} else
		{
			result = Conversions.memToString(physVal.longValue(), cnvId, decimals);
		}
		return (result);
	}

	// ========== NESTED CONVERSION IMPLEMENTATION CLASSES ==========

	/**
	 * Definition of a single OBD data conversion
	 */
	public static class Linear extends NumericConversion
	{
		private static final long serialVersionUID = 7409621816599441879L;
		private int factor = 1;
		private int divider = 1;
		private int offset = 0;
		private int offsetPhys = 0;
		private PvLimits limits = null;
		// mnemonic of dynamic factor
		private String factMnemonic = null;

		public Linear()
		{
		}

		public Linear(int factor, int divider, int offset, int offsetPhys, String units)
		{
			this.offset = offset;
			this.factor = factor;
			this.divider = divider;
			this.offsetPhys = offsetPhys;
			this.units = units;
		}

		public Linear(int factor, int divider, int offset, int offsetPhys, String units, String factMnemonic)
		{
			this(factor, divider, offset, offsetPhys, units);
			if (factMnemonic != null && !factMnemonic.isEmpty())
			{
				this.factMnemonic = factMnemonic;
			}
		}

		public Linear(int factor, int divider, int offset, int offsetPhys, String units, PvLimits limits)
		{
			this(factor, divider, offset, offsetPhys, units);
			this.limits = limits;
		}

		private void updateCnvFromDynamicFactor()
		{
			if (factMnemonic != null)
			{
				EcuDataItem newFactItm = EcuDataItems.byMnemonic.get(factMnemonic);
				if (newFactItm != null)
				{
					Number factVal = (Number)newFactItm.pv.get(EcuDataPv.FID_VALUE);
					if (    factVal != null
					     && factVal.intValue() > 0
					     && factVal.intValue() != factor
					   )
					{
						factor = factVal.intValue();
						EcuDataItems.notifyConversionChange(this);
					}
				}
			}
		}

		public Number memToPhys(long value)
		{
			updateCnvFromDynamicFactor();
			float result = ((float) (value + offset) * factor / divider + offsetPhys);
			if (limits != null)
			{
				result = (Float) limits.limitedValue(result);
			}
			return result;
		}

		public Number physToMem(Number value)
		{
			return ((long) java.lang.Math.round((value.floatValue() - offsetPhys)
				* divider / factor - offset));
		}
	}

	/**
	 * Internal int-based conversion for hex display
	 */
	public static class Int extends NumericConversion
	{
		private static final long serialVersionUID = -2551205550381391025L;

		@Override
		public Number memToPhys(long value)
		{
			return value;
		}

		@Override
		public Number physToMem(Number value)
		{
			return value;
		}

		@Override
		public String physToPhysFmtString(Number physVal, String format)
		{
			long val = physVal.longValue();
			return String.format(format, val);
		}
	}

	/**
	 * conversion of numeric values based on a hash map
	 */
	public static class Hash extends NumericConversion
	{
		private static final long serialVersionUID = -1077047688974749271L;
		private final HashMap<Long, String> hashData = new HashMap<Long, String>();

		@SuppressWarnings({"rawtypes", "unchecked"})
		public Hash(Map data)
		{
			hashData.putAll(data);
		}

		public Hash(String[] initData)
		{
			initFromStrings(initData);
		}

		private void initFromStrings(String[] initData)
		{
			Long key;
			String value;
			String[] data;
			hashData.clear();

			for (String anInitData : initData)
			{
				data = anInitData.split(";");
				for (String aData : data)
				{
					String[] words = aData.split("=");
					key = Long.valueOf(words[0]);
					value = words[1];

					String xlatKey = value;
					xlatKey = xlatKey.replaceAll("[ -]", "_").toLowerCase();
					value = Messages.getString(xlatKey, value);
					log.finer(String.format("%s=%s", xlatKey, value));

					hashData.put(key, value);
				}
			}
		}

		public Number memToPhys(long value)
		{
			return value;
		}

		public Number physToMem(Number value)
		{
			return value;
		}

		@Override
		public String physToPhysFmtString(Number physVal, String format)
		{
			String result = hashData.get(physVal.longValue());
			if (result == null)
				result = "Unknown state: "+super.physToPhysFmtString(physVal, format);

			return (result);
		}
	}

	/**
	 * conversion of numeric values based on a Bitmap
	 */
	public static class Bitmap extends NumericConversion
	{
		private static final long serialVersionUID = -8498739122873083420L;
		private final TreeMap<Long,String> hashData = new TreeMap<Long,String>();

		@SuppressWarnings({"unchecked", "rawtypes"})
		public Bitmap(Map data)
		{
			hashData.putAll(data);
		}

		public Bitmap(String[] initData)
		{
			initFromStrings(initData);
		}

		private void initFromStrings(String[] initData)
		{
			Long key;
			String value;
			String[] data;
			hashData.clear();

			for (String anInitData : initData)
			{
				data = anInitData.split(";");
				for (String aData : data)
				{
					String[] words = aData.split("=");
					key = (long) (1 << Long.valueOf(words[0]));
					value = words[1];

					String xlatKey = value;
					xlatKey = xlatKey.replaceAll("[ -]", "_").toLowerCase();
					value = Messages.getString(xlatKey, value);
					log.finer(String.format("%s=%s", xlatKey, value));

					hashData.put(key, value);
				}
			}
		}

		public Number memToPhys(long value)
		{
			return value;
		}

		public Number physToMem(Number value)
		{
			return value;
		}

		@Override
		public String physToPhysFmtString(Number physVal, String format)
		{
			StringBuilder result = null;
			long val = physVal.longValue();

			for(Map.Entry<Long,String> item : hashData.entrySet())
			{
				if (result == null)
					result = new StringBuilder();
				else
					result.append(System.lineSeparator());
				result.append(String.format("%s  %s",
					((val & item.getKey()) != 0) ? "(*)" : "(  )",
					item.getValue()));
			}
			if (result == null) result = new StringBuilder(super.physToPhysFmtString(physVal, format));
			return (result.toString());
		}
	}

	/**
	 * VAG data conversions (used by Kw1281 ...)
	 */
	public static class Vag extends NumericConversion
	{
		private static final long serialVersionUID = 9130358043909319282L;

		public static final int CNV_ID_TBL = 0;

		private int cnvId = 10;
		private double factor = 1.0;
		private double offset = 0.0;
		private char metaNw = 0;
		private char[] metaTblValues = {0, 255};

		public Vag()
		{
		}

		public Vag(int cnvId, double factor, double offset, String units)
		{
			this.cnvId = cnvId;
			this.factor = factor;
			this.offset = offset;
			this.units = units;
		}

		public void setMetaNw(char metaNw)
		{
			this.metaNw = metaNw;
		}

		public void setMetaTblValues(char[] metaTblValues)
		{
			this.metaTblValues = metaTblValues;
		}

		private double tableValue(int mw, int nw)
		{
			int internalVal = mw & 0xFF;
			double stepwidth = (double) 0xFF / (metaTblValues.length - 1);
			int pos = (int) (internalVal / stepwidth);
			int ofs = (int) (internalVal % stepwidth);
			int valBefore = metaTblValues[pos];
			int valAfter = pos < metaTblValues.length - 1 ? metaTblValues[pos + 1] : valBefore;
			return ((valBefore + (valAfter - valBefore) * ofs / stepwidth) + offset - nw) * factor;
		}

		private double formula10Value(int mw, int nw)
		{
			return ((mw + offset) * nw * factor);
		}

		private double formula11Value(int mw, int nw)
		{
			return ((Double.parseDouble(String.format("%d.%d", mw, nw)) + offset) * factor);
		}

		private double formula12Value(int mw, int nw)
		{
			return ((Double.parseDouble(String.format("%d.%d", nw, mw)) + offset) * factor);
		}

		private double formula13Value(int mw, int nw)
		{
			return ((nw * 255 + mw + offset) * factor);
		}

		private double formula14Value(int mw, int nw)
		{
			return ((mw + offset) / nw * factor);
		}

		private double formula15Value(int mw, int nw)
		{
			return (mw * factor + nw * offset);
		}

		private double formula16Value(int mw)
		{
			return ((mw + offset) * factor);
		}

		private double formula17Value(int mw, int nw)
		{
			return (1 + (mw + offset) * nw * factor);
		}

		private double formula18Value(int mw, int nw)
		{
			return (mw * nw * factor + offset);
		}

		public Number memToPhys(long value)
		{
			double result = 0;
			int mw = (int) (value % 0x100);
			int nw = metaNw != 0 ? metaNw : (int) (value / 0x100);
			switch (cnvId)
			{
				case 0:
					result = tableValue(mw, nw);
					break;
				case 10:
					result = formula10Value(mw, nw);
					break;
				case 11:
					result = formula11Value(mw, nw);
					break;
				case 12:
					result = formula12Value(mw, nw);
					break;
				case 13:
					result = formula13Value(mw, nw);
					break;
				case 14:
					result = formula14Value(mw, nw);
					break;
				case 15:
					result = formula15Value(mw, nw);
					break;
				case 16:
					result = formula16Value(mw);
					break;
				case 17:
					result = formula17Value(mw, nw);
					break;
				case 18:
					result = formula18Value(mw, nw);
					break;
				case 20:
					result = mw & nw;
					break;
				case 21:
				case 22:
				case 23:
					result = nw << 8 | mw;
					break;

				default:
					log.info(String.format("Unsupported Formula: ID=%d [%s]", cnvId, units));
			}
			return (float) result;
		}

		public Number physToMem(Number value)
		{
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public String physToPhysFmtString(Number physValue, String format)
		{
			String result;
			switch (cnvId)
			{
				case 20:
					result = Integer.toBinaryString(physValue.intValue());
					break;

				case 21:
				case 22:
					result = String.format("%c%c",
						physValue.intValue() / 0x100,
						physValue.intValue() % 0x100);
					break;

				case 23:
					result = String.format("%02d:%02d",
						physValue.intValue() / 0x100,
						physValue.intValue() % 0x100);
					break;

				case 24:
					result = String.format("%04X", physValue.intValue());
					break;

				default:
					result = super.physToPhysFmtString(physValue, format);
			}
			return (result);
		}
	}

	/**
	 * Specialized conversion for OBD-II test/monitor status fields per SAE J1979
	 */
	public static class TestStatus extends NumericConversion {

		private static final long serialVersionUID = -7498739122873083421L;
		private static final Logger testLog = Logger.getLogger("data.cnv.test");

		private final int bitOffset;
		private final boolean inverted;

		public TestStatus(int bitOffset, boolean inverted) {
			this.bitOffset = bitOffset;
			this.inverted = inverted;
		}

		public TestStatus() {
			this(4, false);
		}

		@Override
		public Number memToPhys(long value) {
			return value;
		}

		@Override
		public Number physToMem(Number value) {
			return value;
		}

		@Override
		public String physToPhysFmtString(Number physVal, String format) {
			long val = physVal.longValue();

			boolean bit0 = (val & 0x01) != 0;
			boolean bitOffsetValue = (val & (1 << bitOffset)) != 0;

			String convType = inverted ? "TEST_STATUS_8" : "TEST_STATUS_4";
			Log.d("TestStatus", String.format(
				"%s: value=0x%02X, bit0=%b, bit%d=%b",
				convType, val, bit0, bitOffset, bitOffsetValue));

			boolean isAvailable;
			boolean isComplete;

			if (inverted) {
				isComplete = !bit0;
				isAvailable = bitOffsetValue;
			} else {
				isAvailable = bit0;
				isComplete = !bitOffsetValue;
			}

			StringBuilder result = new StringBuilder();

			if (isAvailable) {
				result.append("(*) Available");
			} else {
				result.append("(  ) Not available");
			}
			result.append(System.lineSeparator());

			if (!isAvailable) {
				result.append("(  ) N/A");
			} else if (isComplete) {
				result.append("(*) Complete");
			} else {
				result.append("(  ) Incomplete");
			}

			Log.d("TestStatus", String.format(
				"  -> Result: available=%b, complete=%b", isAvailable, isComplete));

			return result.toString();
		}
	}
}
