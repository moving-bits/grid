package com.movingbits.grid;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Reads and writes a grid's configuration object as a JSON string.
 *
 * <p>Structure:</p>
 * <pre>{@code
 * {
 *   "columns": { "<name>": { "t": "s|i|f|u", "a": "l|r|c", "r": "y|n", "w": 130 } },
 *   "order": [ "<name>", "*" ],
 *   "columns_fixed": [ "<name>" ],
 *   "columns_hidden": [ "<name>" ],
 *   "width_fixed": 60,
 *   "sort": [ { "<name>": "a|d" } ]
 * }
 * }</pre>
 *
 * <p>{@code width_fixed} is the share of the total width, in percent, that the fixed area
 * occupies at most – deliberately not an absolute value, which would no longer suit another
 * device or another orientation.</p>
 *
 * <p>Reading is forgiving: whatever is missing keeps its default; whatever is unknown or
 * invalid is skipped. A missing {@code columns_fixed} therefore leaves the boundary share set
 * in code alone – unlike an empty one, which explicitly removes the fixing. Writing, by
 * contrast, is complete, so that the stored state does not depend on defaults in the code the
 * next time it is loaded.</p>
 *
 * <p>Columns excluded through {@link Grid#ignoreColumns(String...)} appear in no field at all –
 * neither when reading nor when writing.</p>
 */
public final class GridConfiguration {

    private static final String FIELD_COLUMNS = "columns";
    private static final String FIELD_ORDER = "order";
    private static final String FIELD_FIXED = "columns_fixed";
    private static final String FIELD_HIDDEN = "columns_hidden";
    private static final String FIELD_SORT = "sort";
    private static final String FIELD_WIDTH_FIXED = "width_fixed";

    private static final String OPTION_TYPE = "t";
    private static final String OPTION_ALIGNMENT = "a";
    private static final String OPTION_READ_ONLY = "r";
    private static final String OPTION_WIDTH = "w";

    private static final String ALL_REMAINING = "*";

    private GridConfiguration() {
            // utility class
    }

    // --------------------------------------------------------------- Reading

    /**
     * Applies a configuration object to a grid. A string that cannot be read has no effect.
     */
    static void apply(final Grid grid, final String json) {
        final JSONObject root = parse(json);
        if (root == null) {
            return;
        }
        applyColumnOptions(grid, root.optJSONObject(FIELD_COLUMNS));

        final Set<String> hidden = readHidden(grid, root.optJSONArray(FIELD_HIDDEN));

        // Order and area apply to all columns, the hidden ones included: only that way do they
        // keep their place instead of slipping to the end when they are hidden.
        final JSONArray fixedNames = root.optJSONArray(FIELD_FIXED);
        final List<GridColumn> fixed = readFixed(grid, fixedNames);
        final List<GridColumn> layout = new ArrayList<>(fixed);
        layout.addAll(readOrder(grid, root.optJSONArray(FIELD_ORDER), fixed));

        // A column named only under columns_hidden belongs to the layout – otherwise the entry
        // would have no effect. It joins the free area at the back.
        for (String name : hidden) {
            final GridColumn column = grid.findColumn(name);
            if (column != null && !layout.contains(column)) {
                layout.add(column);
            }
        }

        // If the field is missing, the boundary share set in code stays in place – an empty
        // field, by contrast, explicitly removes the fixing.
        final int fixedCount = fixedNames == null
                ? Math.min(grid.getLayoutFixedCount(), layout.size())
                : fixed.size();
        grid.applyColumnLayout(layout, hidden, fixedCount);

        // A percentage; values outside the permitted range are clamped.
        final int widthFixed = root.optInt(FIELD_WIDTH_FIXED, 0);
        if (widthFixed > 0) {
            grid.setFixedBoundaryFraction(widthFixed / 100f);
        }

        applySort(grid, root.optJSONArray(FIELD_SORT));

        // The configuration object handed in at the start also settles the data order.
        grid.freezeDataOrder();
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

    /** Takes over type, alignment, write protection and width for each column. */
    private static void applyColumnOptions(final Grid grid, final JSONObject columns) {
        if (columns == null) {
            return;
        }
        for (Iterator<String> names = columns.keys(); names.hasNext(); ) {
            final String name = names.next();
            final GridColumn column = grid.findColumn(name);
            if (column == null || grid.isIgnored(column)) {
                continue;
            }
            final JSONObject options = columns.optJSONObject(name);
            if (options == null) {
                continue;
            }

            // The type first: it supplies the defaults for alignment and write protection.
            final ColumnType type = ColumnType.fromCode(options.optString(OPTION_TYPE, null));
            if (type != null) {
                column.type(type);
            }
            final CellAlignment alignment = alignmentOf(options.optString(OPTION_ALIGNMENT, null));
            if (alignment != null) {
                column.align(alignment);
            }
            final Boolean readOnly = readOnlyOf(options.optString(OPTION_READ_ONLY, null));
            if (readOnly != null) {
                column.readOnly(readOnly);
            }
            // Without a value the width set in code stays; ColumnWidth.dp raises values that
            // are too narrow to the minimum width, which therefore takes precedence here too.
            final int widthDp = options.optInt(OPTION_WIDTH, 0);
            if (widthDp > 0) {
                column.widthDp(widthDp);
            }
        }
    }

    private static CellAlignment alignmentOf(final String code) {
        if (code == null) {
            return null;
        }
        return switch (code.trim().toLowerCase(Locale.ROOT)) {
            case "l" -> CellAlignment.START;
            case "r" -> CellAlignment.END;
            case "c" -> CellAlignment.CENTER;
            default -> null;
        };
    }

    private static Boolean readOnlyOf(final String code) {
        if (code == null) {
            return null;
        }
        return switch (code.trim().toLowerCase(Locale.ROOT)) {
            case "y" -> Boolean.TRUE;
            case "n" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static Set<String> readHidden(final Grid grid, final JSONArray hidden) {
        final Set<String> names = new LinkedHashSet<>();
        if (hidden == null) {
            return names;
        }
        for (int i = 0; i < hidden.length(); i++) {
            final String name = hidden.optString(i, null);
            final GridColumn column = grid.findColumn(name);
            if (column != null && !grid.isIgnored(column)) {
                names.add(column.getName());
            }
        }
        return names;
    }

    /** The columns of the fixed area in the order of their array; they come first. */
    private static List<GridColumn> readFixed(final Grid grid, final JSONArray fixed) {
        final List<GridColumn> result = new ArrayList<>();
        if (fixed == null) {
            return result;
        }
        for (int i = 0; i < fixed.length(); i++) {
            final GridColumn column = grid.findColumn(fixed.optString(i, null));
            if (usable(grid, column) && !result.contains(column)) {
                result.add(column);
            }
        }
        return result;
    }

    /**
     * The columns of the freely scrolling area. Without a value all remaining columns follow in
     * the order they were handed over; with one only those named, where {@code "*"} inserts all
     * columns not yet listed at its own position.
     */
    private static List<GridColumn> readOrder(final Grid grid, final JSONArray order, final List<GridColumn> alreadyPlaced) {
        final List<GridColumn> result = new ArrayList<>();
        if (order == null) {
            for (GridColumn column : grid.getAllColumns()) {
                if (usable(grid, column) && !alreadyPlaced.contains(column)) {
                    result.add(column);
                }
            }
            return result;
        }

        // Columns named explicitly stay out of the star's reach even when they only appear
        // behind it - otherwise they would land in the list twice, the first time in the wrong
        // place.
        final Set<String> named = new LinkedHashSet<>();
        for (int i = 0; i < order.length(); i++) {
            final String entry = order.optString(i, null);
            if (entry != null && !ALL_REMAINING.equals(entry.trim())) {
                final GridColumn column = grid.findColumn(entry);
                if (column != null) {
                    named.add(column.getName());
                }
            }
        }

        boolean starUsed = false;
        for (int i = 0; i < order.length(); i++) {
            final String entry = order.optString(i, null);
            if (entry == null) {
                continue;
            }
            if (ALL_REMAINING.equals(entry.trim())) {
                if (!starUsed) {
                    starUsed = true;
                    for (GridColumn column : grid.getAllColumns()) {
                        if (usable(grid, column)
                                && !named.contains(column.getName())
                                && !alreadyPlaced.contains(column)
                                && !result.contains(column)) {
                            result.add(column);
                        }
                    }
                }
                continue;
            }
            final GridColumn column = grid.findColumn(entry);
            if (usable(grid, column)
                    && !alreadyPlaced.contains(column)
                    && !result.contains(column)) {
                result.add(column);
            }
        }
        return result;
    }

    private static boolean usable(final Grid grid, final GridColumn column) {
        return column != null && !grid.isIgnored(column);
    }

    /** Takes over the sort order; the order within the array determines the rank. */
    private static void applySort(final Grid grid, final JSONArray sort) {
        grid.resetSortState();
        if (sort != null) {
            for (int i = 0; i < sort.length(); i++) {
                final JSONObject entry = sort.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                for (Iterator<String> names = entry.keys(); names.hasNext(); ) {
                    final String name = names.next();
                    final int index = visibleIndexOf(grid, name);
                    if (index < 0) {
                        continue;
                    }
                    final boolean descending = "d".equalsIgnoreCase(entry.optString(name, "a").trim());
                    grid.appendSortCriterion(index, descending ? SortDirection.DESCENDING : SortDirection.ASCENDING);
                }
            }
        }
    }

    private static int visibleIndexOf(final Grid grid, final String name) {
        for (int i = 0; i < grid.getColumnCount(); i++) {
            if (grid.getColumn(i).getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    // --------------------------------------------------------------- Writing

    /** Builds the configuration object out of a grid's current state. */
    public static String toJson(final Grid grid) {
        final JSONObject root = new JSONObject();
        try {
            final JSONObject columns = new JSONObject();
            for (GridColumn column : grid.getAllColumns()) {
                if (grid.isIgnored(column)) {
                    continue;
                }
                final JSONObject options = new JSONObject();
                options.put(OPTION_TYPE, column.getType().getCode());
                options.put(OPTION_ALIGNMENT, alignmentCode(column.getAlignment()));
                options.put(OPTION_READ_ONLY, column.isReadOnly() ? "y" : "n");
                // Only fixed widths go in here: a percentage share and the split remaining
                // width should keep adapting to the device instead of freezing on the value
                // just computed the first time the state is stored.
                if (column.getWidth().getType() == ColumnWidth.Type.FIXED) {
                    options.put(OPTION_WIDTH, Math.round(column.getWidth().getValue()));
                }
                columns.put(column.getName(), options);
            }
            root.put(FIELD_COLUMNS, columns);

            // Order and area are kept across all columns, the hidden ones included; their
            // visibility lives in columns_hidden alone.
            final List<GridColumn> layout = grid.getLayoutColumns();
            final int fixedCount = grid.getLayoutFixedCount();
            final JSONArray fixed = new JSONArray();
            final JSONArray order = new JSONArray();
            final JSONArray hidden = new JSONArray();
            for (int i = 0; i < layout.size(); i++) {
                final GridColumn column = layout.get(i);
                if (i < fixedCount) {
                    fixed.put(column.getName());
                } else {
                    order.put(column.getName());
                }
                if (grid.isHidden(column)) {
                    hidden.put(column.getName());
                }
            }
            root.put(FIELD_FIXED, fixed);
            root.put(FIELD_ORDER, order);
            root.put(FIELD_HIDDEN, hidden);
            root.put(FIELD_WIDTH_FIXED, Math.round(grid.getFixedBoundaryFraction() * 100f));

            root.put(FIELD_SORT, sortArray(grid));
        } catch (JSONException ignore) {
            return "{}";
        }
        return root.toString();
    }

    /**
     * The sort order on its own, as a standalone object – the excerpt the data source receives
     * with every page.
     */
    static String sortToJson(final Grid grid) {
        final JSONObject root = new JSONObject();
        try {
            root.put(FIELD_SORT, sortArray(grid));
        } catch (JSONException ignore) {
            return "{\"" + FIELD_SORT + "\":[]}";
        }
        return root.toString();
    }

    /** The sort criteria as an array of name/direction pairs, in the order they take effect. */
    private static JSONArray sortArray(final Grid grid) throws JSONException {
        final JSONArray sort = new JSONArray();
        for (SortCriterion criterion : grid.getSortOrder()) {
            final int index = criterion.columnIndex();
            if (index < 0 || index >= grid.getColumnCount()) {
                continue;
            }
            final JSONObject entry = new JSONObject();
            entry.put(grid.getColumn(index).getName(), criterion.direction() == SortDirection.DESCENDING ? "d" : "a");
            sort.put(entry);
        }
        return sort;
    }

    private static String alignmentCode(final CellAlignment alignment) {
        return switch (alignment) {
            case END -> "r";
            case CENTER -> "c";
            default -> "l";
        };
    }
}
