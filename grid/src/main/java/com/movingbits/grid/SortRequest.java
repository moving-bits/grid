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
 * One sort criterion as {@link GridDataSource#getPage(int, int, String, String)} receives it:
 * column name and direction.
 *
 * <p>Unlike {@link SortCriterion} it refers to the column by name rather than by position –
 * the display order is none of the data source's business.</p>
 */
public record SortRequest(String columnName, SortDirection direction) {

    private static final String FIELD_SORT = "sort";

    /**
     * Name of the column that is sorted by.
     */
    @Override
    public String columnName() {
        return columnName;
    }

    /**
     * Reads the sort order out of the excerpt of the configuration object handed over.
     *
     * <p>The evaluation is forgiving: whatever cannot be read yields an empty list; unusable
     * entries are skipped. The order of the list is the order in which the criteria take
     * effect – the first one weighs the most.</p>
     *
     * @param json a string of the form {@code {"sort":[{"<name>":"a|d"}]}}, or {@code null}
     */
    public static List<SortRequest> parse(final String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        final JSONArray sort;
        try {
            sort = new JSONObject(json).optJSONArray(FIELD_SORT);
        } catch (JSONException unreadable) {
            return Collections.emptyList();
        }
        if (sort == null) {
            return Collections.emptyList();
        }

        final List<SortRequest> requests = new ArrayList<>();
        for (int i = 0; i < sort.length(); i++) {
            final JSONObject entry = sort.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            for (Iterator<String> names = entry.keys(); names.hasNext(); ) {
                final String name = names.next();
                final boolean descending = "d".equalsIgnoreCase(entry.optString(name, "a").trim());
                requests.add(new SortRequest(name, descending
                        ? SortDirection.DESCENDING
                        : SortDirection.ASCENDING));
            }
        }
        return requests;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SortRequest that)) {
            return false;
        }
        return columnName.equals(that.columnName) && direction == that.direction;
    }

    @NonNull
    @Override
    public String toString() {
        return "SortRequest(" + columnName + ", " + direction + ")";
    }
}
