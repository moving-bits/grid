package com.movingbits.grid;

/**
 * Hook for the SQL editor being closed, however it went: a statement was run, the dialog was
 * cancelled, or a tap landed beside it.
 *
 * <p>It reports once per opening and after the fact, with the display already up to date.
 * Whether a table or the result of a statement is on screen by then can be asked of the grid:
 * {@code hasCurrentTable()} for the one, {@link SqlGrid#getQuery()} for the other.</p>
 */
public interface OnEditorClosedListener {

    /**
     * @param executed {@code true} when a statement was carried out before the editor closed;
     *                 {@code false} when nothing came of it - cancelled, or refused by the
     *                 database
     */
    void onEditorClosed(boolean executed);
}
