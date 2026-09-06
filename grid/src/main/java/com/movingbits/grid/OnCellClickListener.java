package com.movingbits.grid;

/**
 * Hook for a short or long tap on a cell in the data area.
 */
public interface OnCellClickListener {

    /**
     * @param cell the cell that was tapped; {@link CellRef#dataIndex()} names the position
     *             of the field within the row
     * @param row  the data row exactly as the data source delivered it
     */
    void onCellClick(CellRef cell, String[] row);
}
