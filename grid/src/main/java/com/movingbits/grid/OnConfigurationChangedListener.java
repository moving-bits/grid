package com.movingbits.grid;

/**
 * Notified after every change made through the user interface that is reflected in the
 * configuration object – sort order, column layout, boundary share and column widths.
 *
 * <p>The hook is meant for persisting state: the application stores the string and hands it
 * back through {@link Grid#configuration(String)} the next time the grid is built.</p>
 */
public interface OnConfigurationChangedListener {

    /**
     * @param json the complete configuration object holding the new state
     */
    void onConfigurationChanged(String json);
}
