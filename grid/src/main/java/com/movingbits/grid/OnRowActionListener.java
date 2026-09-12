package com.movingbits.grid;

import java.util.Map;

/**
 * Hook that reports what the user wants to do with a row of a table.
 *
 * <p>It is called when the user presses and holds the number of a row - the first column. That
 * only reaches the application when the table carries a primary key, because without one the
 * row could not be addressed unambiguously.</p>
 *
 * <p>Nothing happens by itself. The application decides what to make of it - ask the user,
 * apply a rule of its own, both - and hands the matter back through
 * {@link DatabaseGrid#performRowAction(int, Map)} with the very same {@code type} and
 * {@code key} once it has made up its mind. That may take as long as it takes: an answer is
 * not expected here, so a dialog put to the user fits in.</p>
 *
 * <p>The row is addressed by its key, not by its place on the screen. Whatever happens in
 * between - paging, sorting, searching - the record that was pressed is the one it stays.</p>
 */
public interface OnRowActionListener {

    /** Type of the action: the row is to be deleted. */
    int DELETE = 1;

    /**
     * @param number  the running number the row carries in the number column, 1-based within
     *                the whole data set - what the user pressed and what a question should
     *                name back to them
     * @param type    what is being asked for; {@link #DELETE} for now
     * @param key     the row's primary key: one entry per field, name and value, in the order
     *                of the key itself
     * @param columns the rest of the row as it stands on the screen: every field outside the
     *                key, name and value as text, in display order and without the hidden
     *                ones. A key says little to whoever is asked; this is what a question is
     *                built from
     */
    void onRowAction(int number, int type, Map<String, String> key, Map<String, String> columns);
}
