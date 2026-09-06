package com.movingbits.grid;

import java.util.List;

/**
 * Hook for the table names read from a database.
 *
 * <p>It reports as soon as {@link DatabaseGrid} knows the names – together with the database,
 * or immediately when set if the hook is registered afterwards. The application fills its
 * table selection from it and calls {@link DatabaseGrid#setCurrentTable(String)} for whatever
 * the user picks.</p>
 */
public interface OnTablesLoadedListener {

    /**
     * @param tables names of the available tables, alphabetically; never empty
     */
    void onTablesLoaded(List<String> tables);
}
