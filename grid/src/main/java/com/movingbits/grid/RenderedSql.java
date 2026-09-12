package com.movingbits.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A statement ready to be handed to the database: the text with a {@code ?} for every value,
 * and the values themselves in the order they occur.
 *
 * <p>The parameters are texts, because that is the only way Android binds them; an entry of
 * {@code null} stands for {@code NULL}. What has to be compared as a number says so in the
 * statement itself, through a {@code CAST}.</p>
 *
 * @param sql        the statement
 * @param parameters its values in order
 */
public record RenderedSql(String sql, List<String> parameters) {

    public RenderedSql(final String sql, final List<String> parameters) {
        this.sql = sql == null ? "" : sql;
        this.parameters = parameters == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    /** The parameters as the database expects them. */
    public String[] args() {
        return parameters.toArray(new String[0]);
    }
}
