package com.obddroid.core.obd;

/**
 * Protocol utility tool box
 *

 */
public class ProtUtils
{

	private static String hexFmt;
	private static String asciiFmt;

	/**
	 * return HEX:ASCII dump of buffer
	 * in ASCII section all NON ASCII chars are '.'
	 * @param buffer buffer to be dumped
	 * @return String containing HEX:ASCII data of buffer
	 */
	public static String hexDumpBuffer(char[] buffer)
	{
		hexFmt = "";
		asciiFmt = "";

		for (int i = 0; i < buffer.length; i++)
		{
			hexFmt += String.format("%02X ", (byte) buffer[i]);
			asciiFmt += String.format("%1s", buffer[i] < 32 || buffer[i] > 127 ? '.' : buffer[i]);
		}
		return (hexFmt + " : " + asciiFmt);
	}

}
