package com.movingbits.grid;

/**
 * Hook for a short or long tap on a cell in the data area – with the record itself instead of
 * the plain row of texts.
 *
 * @param <T> type of a row's record
 */
public interface OnItemClickListener<T> {

    /**
     * @param cell the cell that was tapped
     * @param item record of the corresponding row; {@code null} if it no longer exists
     */
    void onItemClick(CellRef cell, T item);
}
