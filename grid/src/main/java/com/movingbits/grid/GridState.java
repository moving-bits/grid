package com.movingbits.grid;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Reads and writes a grid's state as a JSON string: everything that has to survive the
 * activity being built anew – on a change of screen orientation, for instance.
 *
 * <p>Structure:</p>
 * <pre>{@code
 * {
 *   "configuration": { ... },
 *   "search": { "search": [ ... ] },
 *   "data_order": [ "<name>" ],
 *   "table": "<name>",
 *   "statement": { ... }, "query": { ... }
 * }
 * }</pre>
 *
 * <p>The state holds more than the configuration object: the search as well, which is
 * transient and therefore no part of it, and the data order, which the display order does not
 * tell once columns have been moved. {@code table} only appears for a {@link DatabaseGrid},
 * {@code statement} and {@code query} only for a {@link SqlGrid}: the statement the editor is
 * working on, and the query whose result is on display.</p>
 *
 * <p>Reading is as forgiving as it is for the configuration object: whatever is missing leaves
 * the grid as it is, and a string that cannot be read has no effect at all.</p>
 */
final class GridState {

    private static final String FIELD_CONFIGURATION = "configuration";
    private static final String FIELD_SEARCH = "search";
    private static final String FIELD_DATA_ORDER = "data_order";
    private static final String FIELD_TABLE = "table";
    private static final String FIELD_STATEMENT = "statement";
    private static final String FIELD_QUERY = "query";

    private GridState() {
            // utility class
    }

    /** Builds the state object out of a grid's current state. */
    static String toJson(final Grid grid) {
        final JSONObject root = new JSONObject();
        try {
            root.put(FIELD_CONFIGURATION, new JSONObject(grid.toConfigurationJson()));
            root.put(FIELD_SEARCH, new JSONObject(grid.getSearchJson()));
            // Only once the order is settled: before that there is none to remember, and
            // asking for it would settle it ahead of time.
            final List<GridColumn> dataColumns = grid.getFrozenDataColumns();
            if (dataColumns != null) {
                final JSONArray order = new JSONArray();
                for (GridColumn column : dataColumns) {
                    order.put(column.getName());
                }
                root.put(FIELD_DATA_ORDER, order);
            }
        } catch (JSONException ignore) {
            return "{}";
        }
        return root.toString();
    }

    /** Applies a state object to a grid. A string that cannot be read has no effect. */
    static void apply(final Grid grid, final String json) {
        final JSONObject root = parse(json);
        if (root == null) {
            return;
        }
        final JSONObject configuration = root.optJSONObject(FIELD_CONFIGURATION);
        if (configuration != null) {
            GridConfiguration.apply(grid, configuration.toString());
        }
        // After the configuration object, because that one settles the data order too – on the
        // display order, which is not what the data rows were built for.
        grid.applyDataOrder(names(root.optJSONArray(FIELD_DATA_ORDER)));
        final JSONObject search = root.optJSONObject(FIELD_SEARCH);
        if (search != null) {
            grid.search(SearchRequest.parse(search.toString()));
        }
        grid.refreshRowCount();
    }

    /** Adds the current table to a state object; only a {@link DatabaseGrid} has one. */
    static String withTable(final String json, final String table) {
        final JSONObject root = parse(json);
        if (root == null) {
            return json;
        }
        try {
            root.put(FIELD_TABLE, table == null ? "" : table);
        } catch (JSONException ignore) {
            return json;
        }
        return root.toString();
    }

    /** The table a state object names; empty when it names none. */
    static String tableOf(final String json) {
        final JSONObject root = parse(json);
        return root == null ? "" : root.optString(FIELD_TABLE, "");
    }

    /**
     * Adds the statements of a {@link SqlGrid} to a state object: the one the editor is
     * working on, and the query whose result is on display.
     *
     * @param statement the working statement as JSON, or {@code null}
     * @param query     the displayed query as JSON, or {@code null}
     */
    static String withStatements(final String json, final String statement, final String query) {
        final JSONObject root = parse(json);
        if (root == null) {
            return json;
        }
        try {
            putObject(root, FIELD_STATEMENT, statement);
            putObject(root, FIELD_QUERY, query);
        } catch (JSONException ignore) {
            return json;
        }
        return root.toString();
    }

    /** The working statement a state object carries, as JSON; empty when it carries none. */
    static String statementOf(final String json) {
        return objectOf(json, FIELD_STATEMENT);
    }

    /** The displayed query a state object carries, as JSON; empty when it carries none. */
    static String queryOf(final String json) {
        return objectOf(json, FIELD_QUERY);
    }

    private static void putObject(final JSONObject root, final String field, final String json) throws JSONException {
        final JSONObject value = parse(json);
        if (value != null) {
            root.put(field, value);
        }
    }

    private static String objectOf(final String json, final String field) {
        final JSONObject root = parse(json);
        final JSONObject value = root == null ? null : root.optJSONObject(field);
        return value == null ? "" : value.toString();
    }

    private static JSONObject parse(final String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(json);
        } catch (JSONException ignore) {
            return null;
        }
    }

    /** The strings of an array in their order; empty when there is no array. */
    private static List<String> names(final JSONArray array) {
        final List<String> names = new ArrayList<>();
        if (array == null) {
            return names;
        }
        for (int i = 0; i < array.length(); i++) {
            final String name = array.optString(i, null);
            if (name != null && !name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }
}
