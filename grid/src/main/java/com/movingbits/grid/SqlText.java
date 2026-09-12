package com.movingbits.grid;

import java.util.ArrayList;
import java.util.List;

/**
 * The statement being written: text on one side, the values as parameters on the other.
 *
 * <p>Identifiers are put in quotation marks, values never end up in the text. That is what
 * makes a clicked-together statement safe: whatever the user typed is either a parameter or a
 * name that was checked.</p>
 */
final class SqlText {

    private final StringBuilder sql = new StringBuilder();
    private final List<String> parameters = new ArrayList<>();

    /** Appends text as it stands - keywords and operators, nothing that came from outside. */
    SqlText append(final String raw) {
        sql.append(raw);
        return this;
    }

    /** Appends a name in quotation marks. */
    SqlText identifier(final String name) {
        sql.append('"').append(name == null ? "" : name.replace("\"", "\"\"")).append('"');
        return this;
    }

    /** Appends a column with its table in front of it. */
    SqlText qualified(final String table, final String column) {
        if (table != null && !table.isEmpty()) {
            identifier(table).append(".");
        }
        return identifier(column);
    }

    /**
     * Appends a value as the parameter of a prepared statement.
     *
     * <p>Android binds parameters as text only. Where a column decides the comparison that
     * does no harm - SQLite converts the text according to the column. Where no column has a
     * say, {@code COUNT(*) > ?} for instance, a text would never compare as a number; a number
     * therefore says in the statement that it is one.</p>
     */
    SqlText parameter(final ColumnType type, final String text) {
        switch (type == null ? ColumnType.STRING : type) {
            case INTEGER:
                sql.append("CAST(? AS INTEGER)");
                break;
            case FLOAT:
                sql.append("CAST(? AS REAL)");
                break;
            default:
                sql.append('?');
                break;
        }
        parameters.add(text == null || text.isEmpty() ? null : text);
        return this;
    }

    String sql() {
        return sql.toString();
    }

    List<String> parameters() {
        return parameters;
    }
}
