package com.mubasher.dfix.license;

import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import java.awt.event.MouseEvent;

public class ToolTipHeader extends JTableHeader {
    String[] toolTips;
    public ToolTipHeader(TableColumnModel model) {
        super(model);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int col = columnAtPoint(e.getPoint());
        int modelCol = getTable().convertColumnIndexToModel(col);
        String retStr;
        try {
            retStr = toolTips[modelCol];
        } catch (NullPointerException | ArrayIndexOutOfBoundsException ex) {
            retStr = "";
        }
        if (retStr.length() < 1) {
            retStr = super.getToolTipText(e);
        }
        return retStr;
    }
    public void setToolTipStrings(String[] toolTips) {
        this.toolTips = toolTips;
    }
}
