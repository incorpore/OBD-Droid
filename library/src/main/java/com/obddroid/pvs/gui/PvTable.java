package com.obddroid.pvs.gui;

import com.obddroid.pvs.ProcessVar;

import javax.swing.JTable;

/**
 * Table GUI object for process variables
 *

 */
public class PvTable extends JTable
{

	/**
	 *
	 */
	private static final long serialVersionUID = 6339359674123198139L;
	private ProcessVar pv;
	private PvTableModel pvModel = new PvTableModel();
	private final PvTableCellRenderer renderer = new PvTableCellRenderer();

	/** Creates a new instance of PvTable */
	public PvTable()
	{
		setRowSelectionAllowed(true);
		setDefaultRenderer(Object.class, renderer);
		setModel(new javax.swing.table.DefaultTableModel(
			new Object[][]{
				{null, null, null, null},
				{null, null, null, null},
				{null, null, null, null},
				{null, null, null, null}},
			new String[]{
				"Title 1", "Title 2", "Title 3", "Title 4"
			}));
	}

	/**
	 * Creates a new instance of PvTable
	 *
	 * @param list The process var to display in table
	 */
	public PvTable(ProcessVar list)
	{
		setProcessVar(list);
		setRowSelectionAllowed(true);
	}

	/**
	 * set the process var
	 *
	 * @param list The process var to display in table
	 */
	public void setProcessVar(ProcessVar list)
	{
		pv = list;
		pvModel.setProcessVar(pv);
		setModel(pvModel);
	}

	public PvTableModel getPvModel()
	{
		return pvModel;
	}

	public void setPvModel(PvTableModel pvModel)
	{
		this.pvModel = pvModel;
	}
}
