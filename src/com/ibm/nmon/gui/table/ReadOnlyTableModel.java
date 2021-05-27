package com.ibm.nmon.gui.table;

import javax.swing.table.DefaultTableModel;

/**
 * Subclass of {@link DefaultTableModel} that disallows editing. Note that just setting the table to
 * disabled also disallows selecting rows, so a different, read-only table model is needed.
 */
public final class ReadOnlyTableModel extends DefaultTableModel {
    private static final long serialVersionUID = 5233038727463081596L;

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
}
