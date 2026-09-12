package com.movingbits.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A joined table together with the condition that ties it to the rest of the query.
 *
 * <p>The library gets by without table aliases: a table takes part in a query at most once, so
 * its own name qualifies its columns.</p>
 *
 * @param type  how the table is joined
 * @param table name of the table
 * @param on    the comparisons of the {@code ON} clause; they apply together
 */
public record SqlJoin(SqlJoinType type, String table, List<SqlComparison> on) {

    public SqlJoin(final SqlJoinType type, final String table, final List<SqlComparison> on) {
        this.type = type == null ? SqlJoinType.INNER : type;
        this.table = table == null ? "" : table;
        final List<SqlComparison> copy = new ArrayList<>();
        if (on != null) {
            for (SqlComparison comparison : on) {
                if (comparison != null) {
                    copy.add(comparison);
                }
            }
        }
        this.on = Collections.unmodifiableList(copy);
    }

    /** {@code true} when table and condition are in place. */
    public boolean isComplete() {
        if (table.isEmpty() || on.isEmpty()) {
            return false;
        }
        for (SqlComparison comparison : on) {
            if (!comparison.isComplete()) {
                return false;
            }
        }
        return true;
    }

    /** Short text for the chip in the editor. */
    public String label() {
        final StringBuilder label = new StringBuilder(type.getSql()).append(" ").append(table);
        for (int i = 0; i < on.size(); i++) {
            label.append(i == 0 ? " ON " : " AND ").append(on.get(i).label());
        }
        return label.toString();
    }
}
