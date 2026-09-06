package com.movingbits.grid;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One search condition, as {@link GridDataSource#getPage(int, int, String, String)} and
 * {@link GridDataSource#getRowCount(String)} receive it: column, operator and value.
 *
 * <p>Several conditions apply together – a row is only shown when it meets all of them. The
 * condition carrying the column name {@link #ALL_COLUMNS} – "Global" in the dialog – refers to
 * every column and is met as soon as one of them matches.</p>
 *
 * <p>The search is transient: it is not part of the configuration object and is therefore not
 * persisted either.</p>
 */
public record SearchRequest(String columnName, SearchOperator operator, String value) {

    /**
     * Column name standing for the search across all columns.
     */
    public static final String ALL_COLUMNS = "*";

    private static final String FIELD_SEARCH = "search";
    private static final String OPTION_OPERATOR = "o";
    private static final String OPTION_VALUE = "v";

    /**
     * @param columnName name of the column, or {@link #ALL_COLUMNS}
     * @param operator   the comparison
     * @param value      the value searched for; empty means no condition
     */
    public SearchRequest(final String columnName, final SearchOperator operator, final String value) {
        if (columnName == null || operator == null) {
            throw new IllegalArgumentException("columnName and operator must not be null");
        }
        this.columnName = columnName;
        this.operator = operator;
        this.value = value == null ? "" : value;
    }

    /**
     * Name of the column; {@link #ALL_COLUMNS} for the search across all columns.
     */
    @Override
    public String columnName() {
        return columnName;
    }

    /**
     * The value searched for, never {@code null}.
     */
    @Override
    public String value() {
        return value;
    }

    /**
     * {@code true} when the condition applies to all columns.
     */
    public boolean isAllColumns() {
        return ALL_COLUMNS.equals(columnName);
    }

    /**
     * Reads the search out of the string handed over.
     *
     * <p>The evaluation is forgiving: whatever cannot be read yields an empty list; entries
     * without a usable operator or without a value are skipped.</p>
     *
     * @param json a string of the form {@code {"search":[{"<name>":{"o":"ct","v":"text"}}]}},
     *             or {@code null}
     */
    public static List<SearchRequest> parse(final String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        final JSONArray search;
        try {
            search = new JSONObject(json).optJSONArray(FIELD_SEARCH);
        } catch (JSONException unreadable) {
            return Collections.emptyList();
        }
        if (search == null) {
            return Collections.emptyList();
        }

        final List<SearchRequest> requests = new ArrayList<>();
        for (int i = 0; i < search.length(); i++) {
            final JSONObject entry = search.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            for (Iterator<String> names = entry.keys(); names.hasNext(); ) {
                final String name = names.next();
                final JSONObject condition = entry.optJSONObject(name);
                if (condition == null) {
                    continue;
                }
                final SearchOperator operator = SearchOperator.fromCode(condition.optString(OPTION_OPERATOR, null));
                final String value = condition.optString(OPTION_VALUE, "");
                // Without a value the column is not taken into the search.
                if (operator != null && !value.isEmpty()) {
                    requests.add(new SearchRequest(name, operator, value));
                }
            }
        }
        return requests;
    }

    /**
     * The search as a string, exactly as it goes to the data source.
     */
    static String toJson(final List<SearchRequest> requests) {
        final JSONArray search = new JSONArray();
        try {
            for (SearchRequest request : requests) {
                if (request.value.isEmpty()) {
                    continue;
                }
                final JSONObject condition = new JSONObject();
                condition.put(OPTION_OPERATOR, request.operator.getCode());
                condition.put(OPTION_VALUE, request.value);
                final JSONObject entry = new JSONObject();
                entry.put(request.columnName, condition);
                search.put(entry);
            }
            return new JSONObject().put(FIELD_SEARCH, search).toString();
        } catch (JSONException cannotHappen) {
            // Not possible for the values used here.
            return "{\"" + FIELD_SEARCH + "\":[]}";
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SearchRequest that)) {
            return false;
        }
        return columnName.equals(that.columnName)
                && operator == that.operator
                && value.equals(that.value);
    }

    @NonNull
    @Override
    public String toString() {
        return "SearchRequest(" + columnName + " " + operator + " \"" + value + "\")";
    }
}
