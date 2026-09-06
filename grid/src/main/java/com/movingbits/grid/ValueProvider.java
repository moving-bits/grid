package com.movingbits.grid;

/**
 * Supplies the text a cell shows for one record.
 *
 * @param <T> type of a row's record
 */
public interface ValueProvider<T> {

    /**
     * @param item the row's record
     * @return the text to display; may be {@code null}, which counts as empty
     */
    String valueOf(T item);
}
