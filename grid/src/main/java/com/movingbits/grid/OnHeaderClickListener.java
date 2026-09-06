package com.movingbits.grid;

/**
 * Hook for a short or long tap on a cell of the header row.
 */
public interface OnHeaderClickListener {

    /**
     * @param columnIndex 0-based index of the column, not counting the number column
     */
    void onHeaderClick(int columnIndex);
}
