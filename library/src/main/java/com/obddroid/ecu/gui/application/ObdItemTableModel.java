package com.obddroid.ecu.gui.application;

import com.obddroid.pvs.IndexedProcessVar;
import com.obddroid.pvs.gui.PvTableModel;

/**
 * TableModel for OBD data items
 *

 */
public class ObdItemTableModel
	extends PvTableModel
{

	/** used for caching current row */
	private int currRowIndex = -1;
	/**
	 *
	 */
	private static final long serialVersionUID = -1162610644870557702L;

	/** Creates a new instance of ObdItemTableModel */
	public ObdItemTableModel()
	{
	}

	/**
	 * get the value for table location (x,y)
	 * This implementation returns the complete OBD-Item which represents the complete row
	 * the field handling shall be done by the renderer, since the OBD-item
	 * also includes formatting information which is required for rendering.
	 *
	 * @param rowIndex    row number
	 * @param columnIndex column number
	 * @return value for table location
	 */
	@Override
	public Object getValueAt(int rowIndex, int columnIndex)
	{
		// if the pv is set ...
		if (pv != null)
		{
			// get the column value
			if (rowIndex != currRowIndex && rowIndex < getRowCount())
			{
				currRowIndex = rowIndex;
				currRow = (IndexedProcessVar) pv.get(keys[rowIndex]);
			}
		}
		return (currRow);
	}

	/**
	 * fire update events for specified column on all rows
	 *
	 */
	public synchronized void updateAllRows()
	{
		fireTableRowsUpdated(0, getRowCount());
	}
}
