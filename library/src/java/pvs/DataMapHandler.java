package com.obddroid.pvs;

import java.util.Map;

/**
 * generic Handler interface for mapped Data
 *
 
 */
interface DataMapHandler
{
	/**
	 * handle a set/map of data attributes
	 *
	 * @param data Map of new data attributes to handle
	 * @return previous value of corresponding data item
	 */
	@SuppressWarnings("rawtypes")
	Object handleData(Map data);
}
