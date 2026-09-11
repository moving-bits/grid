package com.movingbits.grid;

import java.util.Map;

/**
 * Hook for an action the application has handed back through
 * {@link DatabaseGrid#performRowAction(int, Map)}, so that it can log it.
 *
 * <p>It reports every such call, whether it came to anything or not; {@code success} tells
 * which. What the application turned down beforehand never gets here - the grid hears nothing
 * of it.</p>
 */
public interface OnRowActionPerformedListener {

    /**
     * @param type    what was asked for; {@link OnRowActionListener#DELETE} for now
     * @param key     the row's primary key, as the reporting hook handed it over
     * @param success {@code true} when the row was deleted; {@code false} when the parameters
     *                did not fit the table or the database refused
     */
    void onRowActionPerformed(int type, Map<String, String> key, boolean success);
}
