package com.movingbits.grid;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A statement that has been run, as the hook for the log receives it.
 *
 * @param origin     where the statement came from
 * @param kind       whether it read or changed
 * @param sql        the statement, with a {@code ?} for every value
 * @param parameters the values in the order they occur; an entry of {@code null} is
 *                   {@code NULL}
 * @param rowCount   rows changed for a change, rows found for a query; {@code -1} when it did
 *                   not come to that
 * @param successful {@code true} when the database carried it out
 * @param error      what the database complained about, or {@code null}
 */
public record SqlExecution(SqlExecution.Origin origin, SqlKind kind, String sql,
                           List<String> parameters, int rowCount, boolean successful, String error) {

    /** Where a statement came from. */
    public enum Origin {
        /** Clicked together in the SQL editor. */
        EDITOR,
        /** Written back after a cell of the grid was edited. */
        CELL
    }

    public SqlExecution(final Origin origin, final SqlKind kind, final String sql,
                        final List<String> parameters, final int rowCount,
                        final boolean successful, final String error) {
        this.origin = origin == null ? Origin.EDITOR : origin;
        this.kind = kind == null ? SqlKind.SELECT : kind;
        this.sql = sql == null ? "" : sql;
        this.parameters = parameters == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(parameters));
        this.rowCount = rowCount;
        this.successful = successful;
        this.error = error;
    }

    /** The statement with the values put in place - for a log that is to be read by people. */
    @NonNull
    @Override
    public String toString() {
        final StringBuilder text = new StringBuilder();
        int parameter = 0;
        for (int i = 0; i < sql.length(); i++) {
            final char c = sql.charAt(i);
            if (c == '?' && parameter < parameters.size()) {
                final String value = parameters.get(parameter++);
                text.append(value == null ? "NULL" : "'" + value + "'");
            } else {
                text.append(c);
            }
        }
        return text.toString();
    }
}
