package com.obddroid.pvs.gui;

import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;

/**
 * Cell Renderer for PvTable component
 *

 */
public class PvTableCellRenderer extends JLabel
	implements TableCellRenderer
{

	/**
	 *
	 */
	private static final long serialVersionUID = -7686049090382566048L;
	private static final EmptyBorder brdr = new EmptyBorder(0, 5, 0, 5);

	/** Creates a new instance of PvTableCellRenderer */
	public PvTableCellRenderer()
	{
		setOpaque(true);
		setBorder(brdr);
	}

	/** render a single pv attribute based on location within table */
	public Component getTableCellRendererComponent(JTable table,
	                                               Object value,
	                                               boolean isSelected,
	                                               boolean hasFocus,
	                                               int row,
	                                               int column)
	{
		setFont(table.getFont());
		setHorizontalAlignment(value instanceof Number ? RIGHT : LEFT);
		setText(String.valueOf(value));
		setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());

		return (this);
	}
}
