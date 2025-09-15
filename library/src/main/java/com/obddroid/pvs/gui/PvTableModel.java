package com.obddroid.pvs.gui;

import com.obddroid.pvs.IndexedProcessVar;
import com.obddroid.pvs.ProcessVar;
import com.obddroid.pvs.PvChangeEvent;
import com.obddroid.pvs.PvChangeListener;

import java.util.Arrays;

import javax.swing.table.AbstractTableModel;

/**
 * Data Model to represent a ProcessVar within a Table object
 *

 */
public class PvTableModel extends AbstractTableModel
	implements PvChangeListener
{

	/**
	 *
	 */
	private static final long serialVersionUID = -6777148395276846388L;
	protected ProcessVar pv;
	protected IndexedProcessVar currRow;
	protected Object[] keys;
	private String[] colHeaders = {};

	/** Creates a new instance of PvTableModel */
	public PvTableModel()
	{
	}

	/**
	 * Creates a new instance of PvTableModel
	 *
	 * @param pVar the process variable to represent
	 */
	public PvTableModel(ProcessVar pVar)
	{
		setProcessVar(pVar);
	}

	/**
	 * update th list of key items for correct location of coordinates
	 *
	 * @param pv Process variable
	 */
	private void updateKeys(ProcessVar pv)
	{
		keys = pv.keySet().toArray();
		Arrays.sort(keys);
	}

	/**
	 * set the process variable to be represented
	 *
	 * @param pVar the process variable to represent
	 */
	public synchronized void setProcessVar(ProcessVar pVar)
	{
		// set the pv
		pv = pVar;
		updateKeys(pVar);
		if (keys.length > 0)
		{
			currRow = (IndexedProcessVar) pv.get(keys[0]);
			colHeaders = currRow.getFields();
			fireTableStructureChanged();
		}
		pv.addPvChangeListener(this, PvChangeEvent.PV_ALLEVENTS);
	}

	/**
	 * get number of table rows
	 *
	 * @return number of rows to show
	 */
	public int getRowCount()
	{
		return (keys != null ? keys.length : 0);
	}

	/**
	 * return number of columns
	 *
	 * @return number of columns
	 */
	public int getColumnCount()
	{
		return (colHeaders.length);
	}

	/**
	 * get process var for row
	 * @param rowIndex row index
	 * @return process var
	 */
	public IndexedProcessVar getElementAt(int rowIndex)
	{
		if (rowIndex < getRowCount())
			currRow = (IndexedProcessVar) pv.get(keys[rowIndex]);

		return currRow;
	}
	/**
	 * get the value for table location (x,y)
	 *
	 * @param rowIndex    row number
	 * @param columnIndex column number
	 * @return value for table location
	 */
	public Object getValueAt(int rowIndex, int columnIndex)
	{
		Object result = null;
		// if the pv is set ...
		if (pv != null)
		{
			// get the column value
			if((currRow = getElementAt(rowIndex)) != null)
			{
				result = currRow.get(columnIndex);
			}
		}
		return (result);
	}

	/**
	 * handle a process variable change
	 *
	 * @param event the change event to be handled
	 */
	public synchronized void pvChanged(PvChangeEvent event)
	{
 		Object key;
 		int rowId;
		try
 		{
			if(event.isChildEvent())
	 		{
	 			key = String.valueOf(event.getValue());
	 		}
			else
			{
	 			key = event.getKey();
			}
 			rowId = key != null ? Arrays.binarySearch(keys, key) : 0;
	
			switch (event.getType())
			{
				case PvChangeEvent.PV_CONFIRMED:
				case PvChangeEvent.PV_MODIFIED:
				case PvChangeEvent.PV_MANUAL_MOD:
					fireTableRowsUpdated(rowId, rowId);
					break;
	
				case PvChangeEvent.PV_ADDED:
					if (pv.size() == 1)
					{
						setProcessVar(pv);
					} else
					{
						updateKeys(pv);
						fireTableRowsInserted(rowId, rowId);
					}
					break;
	
				case PvChangeEvent.PV_DELETED:
					updateKeys(pv);
					fireTableRowsDeleted(rowId, rowId);
					break;
	
				case PvChangeEvent.PV_CLEARED:
					if (keys.length > 0)
					{
						updateKeys(pv);
					}
					// w/o data there are NO columns available -> structure change
					fireTableStructureChanged();
					break;
			}
 		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}

	/**
	 * return column name of PID field
	 *
	 * @param column specified column number to get name for ...
	 */
	@Override
	public String getColumnName(int column)
	{
		return (colHeaders[column]);
	}
}
