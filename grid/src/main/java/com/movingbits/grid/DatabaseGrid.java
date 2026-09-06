package com.movingbits.grid;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * A {@link Grid} for the tables of an SQLite database.
 *
 * <p>It is its own {@link GridDataSource}: all it is handed is the database, everything else it
 * gathers itself. From {@code sqlite_master} it reads the available tables and reports them
 * through {@link #onTablesLoaded(OnTablesLoadedListener)} to the application, which builds its
 * table selection from them. Columns and data only come into being with
 * {@link #setCurrentTable(String)} – before that the display stays empty.</p>
 *
 * <p>The columns follow from the table's own declarations: the declared type becomes a
 * {@link ColumnType}, the primary key columns become write-protected, move to the front and
 * form the fixed area. Whatever the application sets afterwards through a configuration object
 * wins – including a write protection that is lifted.</p>
 *
 * <pre>{@code
 * DatabaseGrid grid = new DatabaseGrid()
 *         .setDatabase(database)
 *         .onTablesLoaded(tables -> { ... show the selection ... })
 *         .rowsPerPage(10);
 * // after the application's choice:
 * grid.setCurrentTable("cities").configuration(storedObject);
 * view.refresh();
 * }</pre>
 *
 * <p>Sort order and search go into the query as {@code ORDER BY} and {@code WHERE}. The search
 * behaves as it does in memory: text columns ignore case, numeric columns compare as numbers;
 * "Global" checks every column of the table.</p>
 */
public class DatabaseGrid extends Grid implements GridDataSource {

    private static final String LOGTAG = "DatabaseGrid";

    private static final String SQL_TABLES = "SELECT name FROM sqlite_master WHERE TYPE IN ('table') AND name NOT LIKE 'sqlite_%' ORDER BY name";
    private static final String SQL_COLUMNS = "SELECT *, typeof(type) storageclass FROM pragma_table_info(?)";

    /** Width that text columns of a table receive. */
    private static final float DEFAULT_TEXT_WIDTH_DP = 150f;
    /** Width that numeric columns of a table receive. */
    private static final float DEFAULT_NUMBER_WIDTH_DP = 100f;

    /** Stand-in text for content that cannot be displayed as text. */
    private static final String BLOB_TEXT = "[BLOB]";

    protected SQLiteDatabase database;
    protected String currentTable = "";
    protected TableInfo tableInfo;

    private final List<String> tables = new ArrayList<>();
    private OnTablesLoadedListener tablesLoadedListener;

    private float textWidthDp = DEFAULT_TEXT_WIDTH_DP;
    private float numberWidthDp = DEFAULT_NUMBER_WIDTH_DP;

    /**
     * Open cursor across the whole data set of the current table. As long as the application
     * only pages, it serves every page; only another sort order or search makes a new query
     * necessary.
     */
    private Cursor cursor;
    /** The sort order and search {@link #cursor} was opened for. */
    private String cursorFor;

    private int knownRowCount;
    /** The search {@link #knownRowCount} holds for; {@code null} while nothing was counted. */
    private String rowCountFor;

    /** Storage form of a value, derived from the type declared in the table. */
    protected enum StorageClass {
        STORAGE_NULL(new String[]{"", "NULL"}),
        STORAGE_INTEGER(new String[]{"INTEGER", "INT", "BIGINT", "SMALLINT", "TINYINT", "LONG"}),
        STORAGE_REAL(new String[]{"REAL", "FLOAT", "DOUBLE", "NUMERIC", "DECIMAL"}),
        STORAGE_TEXT(new String[]{"TEXT", "VARCHAR", "NVARCHAR", "CHAR", "NCHAR", "CHARACTER", "CLOB"}),
        STORAGE_BLOB(new String[]{"BLOB"});

        final String[] hints;

        static StorageClass getStorageClass(final String hint) {
            // Length statements are not part of the type: VARCHAR(255) becomes VARCHAR.
            String bare = hint == null ? "" : hint.trim();
            final int parenthesis = bare.indexOf('(');
            if (parenthesis >= 0) {
                bare = bare.substring(0, parenthesis).trim();
            }
            for (StorageClass storageClass : values()) {
                if (Strings.CI.equalsAny(bare, storageClass.hints)) {
                    return storageClass;
                }
            }
            return STORAGE_NULL;
        }

        StorageClass(final String[] hints) {
            this.hints = hints;
        }
    }

    protected static class ColumnInfo {
        public final int position;
        public final String name;
        public final String type;
        public final boolean notNull;
        public final String defaultValue;
        public final int primaryKeyPosition;
        public final StorageClass storageClass;

        ColumnInfo(final int position, final String name, final String type, final boolean notNull, final String defaultValue, final int primaryKeyPosition, final StorageClass storageClass) {
            this.position = position;
            this.name = name;
            this.type = type;
            this.notNull = notNull;
            this.defaultValue = defaultValue;
            this.primaryKeyPosition = primaryKeyPosition;
            this.storageClass = storageClass;
        }

        /** {@code true} when this column belongs to the primary key. */
        boolean isPrimaryKey() {
            return primaryKeyPosition > 0;
        }
    }

    protected static class TableInfo {
        final String name;
        final List<ColumnInfo> columns;
        boolean hasPrimaryKey;

        TableInfo(final String name) {
            this.name = name;
            this.columns = new ArrayList<>();
            this.hasPrimaryKey = false;
        }

        /** The primary key's columns in the order the key itself lists them. */
        List<ColumnInfo> primaryKey() {
            final List<ColumnInfo> keys = new ArrayList<>();
            for (ColumnInfo column : columns) {
                if (column.isPrimaryKey()) {
                    keys.add(column);
                }
            }
            keys.sort(Comparator.comparingInt(a -> a.primaryKeyPosition));
            return keys;
        }
    }

    public DatabaseGrid() {
        data(this);
    }

    /**
     * @param database an opened database
     */
    public DatabaseGrid(final SQLiteDatabase database) {
        this();
        setDatabase(database);
    }

    // --------------------------------------------------------------- Tables

    /**
     * Sets the database and reads its table names. A table chosen before is lost in the
     * process.
     */
    public DatabaseGrid setDatabase(final SQLiteDatabase database) {
        this.database = database;
        this.currentTable = "";
        this.tableInfo = null;
        closeCursor();
        clearColumns();
        loadTables();
        return this;
    }

    /**
     * Hook for the table names that were read. If they are known already – the usual case,
     * because {@link #setDatabase(SQLiteDatabase)} comes first in the chain – it reports at
     * once.
     */
    public DatabaseGrid onTablesLoaded(final OnTablesLoadedListener listener) {
        this.tablesLoadedListener = listener;
        notifyTablesLoaded();
        return this;
    }

    /** Names of the available tables, alphabetically. */
    public List<String> getTables() {
        return Collections.unmodifiableList(tables);
    }

    /** Name of the current table; empty while none is chosen. */
    public String getCurrentTable() {
        return currentTable;
    }

    /** {@code true} as soon as a table is chosen and delivers data. */
    public boolean hasCurrentTable() {
        return database != null && tableInfo != null && !currentTable.isEmpty();
    }

    /**
     * Chooses the table to display. The previous columns are dropped and built anew from the
     * table's own declarations; the previous table's sort order and search fall away.
     *
     * <p>A configuration object belongs immediately afterwards, and the view is then to be
     * refreshed through {@code GridView.refresh()}.</p>
     *
     * @param table name of a table out of {@link #getTables()}
     * @return {@code true} when the table was chosen
     */
    public boolean setCurrentTable(final String table) {
        if (database == null || table == null || !tables.contains(table)) {
            return false;
        }
        final TableInfo info = readTableInfo(table);
        if (info == null || info.columns.isEmpty()) {
            return false;
        }

        currentTable = table;
        tableInfo = info;
        closeCursor();
        clearColumns();
        buildColumns(info);
        refreshRowCount();
        return true;
    }

    /**
     * Widths that a table's columns receive. The database knows no widths and the configuration
     * object does not carry them; without a value all columns would share the available width
     * and stay unreadably narrow.
     *
     * @param textDp   width of text and unknown columns, in dp
     * @param numberDp width of numeric columns, in dp
     */
    public DatabaseGrid columnWidthDp(final float textDp, final float numberDp) {
        if (textDp <= 0f || numberDp <= 0f) {
            throw new IllegalArgumentException("widths must be > 0");
        }
        this.textWidthDp = textDp;
        this.numberWidthDp = numberDp;
        return this;
    }

    /** Releases the open cursor. The database itself belongs to the application. */
    public void close() {
        closeCursor();
    }

    private void loadTables() {
        tables.clear();
        if (database != null) {
            try (Cursor names = database.rawQuery(SQL_TABLES, null)) {
                while (names.moveToNext()) {
                    tables.add(names.getString(0));
                }
            } catch (SQLiteException unreadable) {
                Log.w(LOGTAG, "table names not readable: " + unreadable.getMessage());
            }
        }
        notifyTablesLoaded();
    }

    private void notifyTablesLoaded() {
        if (tablesLoadedListener != null && !tables.isEmpty()) {
            tablesLoadedListener.onTablesLoaded(getTables());
        }
    }

    /** Reads name, type, write protection and primary key of a table's columns. */
    private TableInfo readTableInfo(final String table) {
        Cursor info;
        try {
            info = database.rawQuery(SQL_COLUMNS, new String[]{table});
        } catch (SQLiteException unavailable) {
            // pragma_table_info as a table-valued function only exists from SQLite 3.16
            // (Android 8) onwards; before that the pragma itself does the job, but it takes no
            // parameters.
            try {
                info = database.rawQuery("PRAGMA table_info(" + quote(table) + ")", null);
            } catch (SQLiteException unreadable) {
                Log.w(LOGTAG, "columns of " + table + " not readable: " + unreadable.getMessage());
                return null;
            }
        }

        final TableInfo result = new TableInfo(table);
        try (Cursor columns = info) {
            final int position = columns.getColumnIndex("cid");
            final int name = columns.getColumnIndex("name");
            final int type = columns.getColumnIndex("type");
            final int notNull = columns.getColumnIndex("notnull");
            final int defaultValue = columns.getColumnIndex("dflt_value");
            final int primaryKey = columns.getColumnIndex("pk");
            if (name < 0) {
                return null;
            }
            while (columns.moveToNext()) {
                // typeof(type) reports the storage form of the type name and is therefore
                // always "text"; what counts is the type declared in the table itself.
                final String declared = type < 0 ? "" : columns.getString(type);
                result.columns.add(new ColumnInfo(
                        position < 0 ? result.columns.size() : columns.getInt(position),
                        columns.getString(name),
                        declared,
                        notNull >= 0 && columns.getInt(notNull) != 0,
                        defaultValue < 0 ? null : columns.getString(defaultValue),
                        primaryKey < 0 ? 0 : columns.getInt(primaryKey),
                        StorageClass.getStorageClass(declared)));
            }
        } catch (SQLiteException unreadable) {
            Log.w(LOGTAG, "columns of " + table + " not readable: " + unreadable.getMessage());
            return null;
        }

        result.hasPrimaryKey = !result.primaryKey().isEmpty();
        return result;
    }

    /**
     * Creates the columns: the primary key's first, so that they form the fixed area, then the
     * rest in the order of the table.
     */
    private void buildColumns(final TableInfo info) {
        final List<ColumnInfo> keys = info.primaryKey();
        for (ColumnInfo column : keys) {
            column(toGridColumn(column));
        }
        for (ColumnInfo column : info.columns) {
            if (!column.isPrimaryKey()) {
                column(toGridColumn(column));
            }
        }
        fixedColumns(keys.size());
    }

    private GridColumn toGridColumn(final ColumnInfo column) {
        final ColumnType type = typeOf(column.storageClass);
        return new GridColumn(column.name)
                .name(column.name)
                .type(type)
                // The key must not change, otherwise the row would lose its identity.
                .readOnly(column.isPrimaryKey() || type == ColumnType.UNKNOWN)
                .widthDp(type.isNumeric() ? numberWidthDp : textWidthDp);
    }

    /** The grid's column type for a storage form. */
    protected static ColumnType typeOf(final StorageClass storageClass) {
        return switch (storageClass) {
            case STORAGE_INTEGER -> ColumnType.INTEGER;
            case STORAGE_REAL -> ColumnType.FLOAT;
            case STORAGE_TEXT -> ColumnType.STRING;
            default -> ColumnType.UNKNOWN;
        };
    }

    private ColumnInfo infoOf(final String columnName) {
        if (tableInfo == null || columnName == null) {
            return null;
        }
        for (ColumnInfo column : tableInfo.columns) {
            if (column.name.equals(columnName)) {
                return column;
            }
        }
        return null;
    }

    // ---------------------------------------------------------- Data source

    @Override
    public int getRowCount(final String search) {
        if (!hasCurrentTable()) {
            return 0;
        }
        final String key = String.valueOf(search);
        if (key.equals(rowCountFor)) {
            return knownRowCount;
        }

        final Condition where = whereOf(SearchRequest.parse(search));
        int count = 0;
        try (Cursor number = database.rawQuery(
                "SELECT COUNT(*) AS number FROM " + quote(currentTable) + where.sql, where.args())) {
            if (number.moveToFirst()) {
                count = number.getInt(0);
            }
        } catch (SQLiteException unreadable) {
            Log.w(LOGTAG, "count not obtainable: " + unreadable.getMessage());
        }

        knownRowCount = count;
        rowCountFor = key;
        return count;
    }

    @Override
    public String[][] getPage(final int page, final int count, final String sort, final String search) {
        if (!hasCurrentTable() || count <= 0) {
            return new String[0][];
        }
        final Cursor rows = cursorFor(sort, search);
        if (rows == null) {
            return new String[0][];
        }

        // The grid's data order and the query's column order are two different things.
        final List<GridColumn> dataColumns = getDataColumns();
        final int[] index = new int[dataColumns.size()];
        for (int i = 0; i < index.length; i++) {
            index[i] = rows.getColumnIndex(dataColumns.get(i).getName());
        }

        final int rowsPerPage = getRowsPerPage();
        final int first = rowsPerPage > 0 ? Math.max(0, page) * rowsPerPage : 0;
        final int length = Math.max(0, Math.min(count, rows.getCount() - first));
        final String[][] result = new String[length][];
        for (int i = 0; i < length; i++) {
            final String[] row = new String[index.length];
            final boolean present = rows.moveToPosition(first + i);
            for (int column = 0; column < index.length; column++) {
                row[column] = present && index[column] >= 0 ? textOf(rows, index[column]) : "";
            }
            result[i] = row;
        }
        return result;
    }

    /** The content of a cell as text; what cannot be displayed is named instead. */
    private static String textOf(final Cursor row, final int column) {
        return switch (row.getType(column)) {
            case Cursor.FIELD_TYPE_NULL -> "";
            case Cursor.FIELD_TYPE_BLOB -> BLOB_TEXT;
            default -> row.getString(column);
        };
    }

    /**
     * The cursor across the whole data set for this sort order and search. Paging gets by
     * without a new query; only another sort order or search issues one.
     */
    private Cursor cursorFor(final String sort, final String search) {
        final String key = sort + ' ' + search;
        if (cursor != null && key.equals(cursorFor)) {
            return cursor;
        }
        closeCursor();

        final Condition where = whereOf(SearchRequest.parse(search));
        final String sql = "SELECT * FROM " + quote(currentTable) + where.sql + orderBy(sort);
        try {
            cursor = database.rawQuery(sql, where.args());
            cursorFor = key;
        } catch (SQLiteException unreadable) {
            Log.w(LOGTAG, "query failed: " + unreadable.getMessage() + " - " + sql);
            cursor = null;
            cursorFor = null;
        }
        return cursor;
    }

    private void closeCursor() {
        if (cursor != null) {
            cursor.close();
        }
        cursor = null;
        cursorFor = null;
        rowCountFor = null;
    }

    // -------------------------------------------------------------- Writing

    /**
     * Writes the content of a cell back into the database.
     *
     * <p>That is only accepted when the table carries a primary key – without one the row could
     * not be addressed unambiguously – and the column is not write-protected. For numeric
     * columns the text is stored as a number, an empty value as {@code NULL} as far as the
     * column allows it.</p>
     *
     * @param row        0-based index of the row within the displayed data set, as a
     *                   {@link CellRef} names it
     * @param columnName name of the column
     * @param newValue   the new content
     * @return {@code true} when something was written
     */
    public boolean persistData(final int row, final String columnName, final String newValue) {
        if (!hasCurrentTable() || !tableInfo.hasPrimaryKey) {
            return false;
        }
        final ColumnInfo target = infoOf(columnName);
        final GridColumn column = findColumn(columnName);
        if (target == null || column == null || column.isReadOnly()) {
            return false;
        }

        final Cursor rows = cursorFor(getSortJson(), getSearchJson());
        if (rows == null || !rows.moveToPosition(row)) {
            return false;
        }

        final List<Object> args = new ArrayList<>();
        args.add(valueOf(target, newValue));
        final StringBuilder where = new StringBuilder();
        for (ColumnInfo key : tableInfo.primaryKey()) {
            final int index = rows.getColumnIndex(key.name);
            if (index < 0) {
                return false;
            }
            where.append(where.length() == 0 ? "" : " AND ").append(quote(key.name)).append(" = ?");
            args.add(rows.getString(index));
        }

        final String sql = "UPDATE " + quote(currentTable) + " SET " + quote(target.name) + " = ? WHERE " + where;
        try {
            database.execSQL(sql, args.toArray());
        } catch (SQLiteException notWritable) {
            Log.w(LOGTAG, "writing failed: " + notWritable.getMessage() + " - " + sql);
            return false;
        }

        // The open cursor still carries the old content; the next page is queried anew.
        closeCursor();
        return true;
    }

    /** The value to store, matching the column's storage form. */
    private static Object valueOf(final ColumnInfo column, final String text) {
        if (text == null || text.isEmpty()) {
            if (!column.notNull) {
                return null;
            }
            return column.storageClass == StorageClass.STORAGE_INTEGER
                    || column.storageClass == StorageClass.STORAGE_REAL ? (Object) 0L : "";
        }
        if (column.storageClass == StorageClass.STORAGE_INTEGER) {
            final double number = NumericValues.parse(text);
            return Double.isNaN(number) ? text : (Object) Math.round(number);
        }
        if (column.storageClass == StorageClass.STORAGE_REAL) {
            final double number = NumericValues.parse(text);
            return Double.isNaN(number) ? text : (Object) number;
        }
        return text;
    }

    // ----------------------------------------------------- Sorting and search

    /** The sort order as {@code ORDER BY}; an empty string when nothing is sorted. */
    private String orderBy(final String sort) {
        final StringBuilder order = new StringBuilder();
        for (SortRequest request : SortRequest.parse(sort)) {
            final GridColumn column = findColumn(request.columnName());
            if (column == null) {
                continue;
            }
            order.append(order.length() == 0 ? " ORDER BY " : ", ")
                    .append(quote(column.getName()))
                    // Text is ordered ignoring case, just as it is in memory; the SQLite
                    // default is a different one.
                    .append(column.getType().isNumeric() ? "" : " COLLATE NOCASE")
                    .append(request.direction() == SortDirection.DESCENDING ? " DESC" : " ASC");
        }
        return order.toString();
    }

    /** A {@code WHERE} clause together with its parameters. */
    private static final class Condition {
        String sql = "";
        final List<String> args = new ArrayList<>();

        String[] args() {
            return args.toArray(new String[0]);
        }
    }

    /** The search conditions as {@code WHERE}; they apply together. */
    private Condition whereOf(final List<SearchRequest> requests) {
        final Condition condition = new Condition();
        final StringBuilder sql = new StringBuilder();
        for (SearchRequest request : requests) {
            final String term = request.isAllColumns()
                    ? globalTerm(request, condition.args)
                    : columnTerm(request, condition.args);
            if (term != null) {
                sql.append(sql.length() == 0 ? " WHERE " : " AND ").append(term);
            }
        }
        condition.sql = sql.toString();
        return condition;
    }

    private String columnTerm(final SearchRequest request, final List<String> args) {
        final GridColumn column = findColumn(request.columnName());
        if (column == null) {
            return null;
        }
        return term(column.getName(), column.getType(), request.operator(), request.value(), args);
    }

    /** "Global": met as soon as one of the columns matches – as the search in memory has it. */
    private String globalTerm(final SearchRequest request, final List<String> args) {
        final StringBuilder any = new StringBuilder();
        for (GridColumn column : getDataColumns()) {
            final String term = term(column.getName(), ColumnType.STRING, request.operator(), request.value(), args);
            if (StringUtils.isNotBlank(term)) {
                any.append(any.length() == 0 ? "(" : " OR ").append(term);
            }
        }
        return any.length() == 0 ? null : any.append(")").toString();
    }

    /**
     * A single condition. Numeric columns compare magnitudes, everything else compares text –
     * ignoring case and with {@code NULL} as empty text, so that the search finds the same as
     * it does in memory.
     */
    private static String term(final String columnName, final ColumnType type, final SearchOperator operator, final String value, final List<String> args) {
        final String column = quote(columnName);
        if (type.isNumeric() && operator.fits(type)) {
            final double number = NumericValues.parse(value);
            if (Double.isNaN(number)) {
                // Without a recognisable number nothing matches - the same as the search in
                // memory has it.
                return "0";
            }
            args.add(numberText(number));
            return column + " " + comparison(operator) + " CAST(? AS NUMERIC)";
        }

        final String text = "IFNULL(CAST(" + column + " AS TEXT), '')";
        switch (operator) {
            case EQUALS:
                args.add(value);
                return text + " = ? COLLATE NOCASE";
            case NOT_EQUALS:
                args.add(value);
                return text + " <> ? COLLATE NOCASE";
            case STARTS_WITH:
            case NOT_STARTS_WITH:
                args.add(escapeLike(value) + "%");
                break;
            case ENDS_WITH:
            case NOT_ENDS_WITH:
                args.add("%" + escapeLike(value));
                break;
            default:
                args.add("%" + escapeLike(value) + "%");
                break;
        }
        final boolean negated = operator == SearchOperator.NOT_STARTS_WITH
                || operator == SearchOperator.NOT_ENDS_WITH
                || operator == SearchOperator.NOT_CONTAINS;
        return text + (negated ? " NOT LIKE ?" : " LIKE ?") + " ESCAPE '\\'";
    }

    private static String comparison(final SearchOperator operator) {
        return switch (operator) {
            case NOT_EQUALS -> "<>";
            case LESS -> "<";
            case LESS_OR_EQUAL -> "<=";
            case GREATER -> ">";
            case GREATER_OR_EQUAL -> ">=";
            default -> "=";
        };
    }

    /** Whole numbers without a decimal place, so that the comparison does not hinge on it. */
    private static String numberText(final double number) {
        return number == Math.rint(number) && !Double.isInfinite(number)
                ? String.valueOf((long) number)
                : String.valueOf(number);
    }

    /** Turns the placeholders of {@code LIKE} into ordinary characters. */
    private static String escapeLike(final String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Puts a name in quotation marks, so that keywords work as names too. */
    private static String quote(final String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    // ----------------------------------------------------------------- Chain

    // The inherited configuration methods return a DatabaseGrid again, so that the chain can
    // carry on without a detour.

    @Override
    public DatabaseGrid clearColumns() {
        super.clearColumns();
        return this;
    }

    @Override
    public DatabaseGrid column(final GridColumn column) {
        super.column(column);
        return this;
    }

    @Override
    public DatabaseGrid ignoreColumns(final String... names) {
        super.ignoreColumns(names);
        return this;
    }

    @Override
    public DatabaseGrid fixedColumns(final int count) {
        super.fixedColumns(count);
        return this;
    }

    @Override
    public DatabaseGrid rowsPerPage(final int rowsPerPage) {
        super.rowsPerPage(rowsPerPage);
        return this;
    }

    @Override
    public DatabaseGrid totalWidth(final float dp) {
        super.totalWidth(dp);
        return this;
    }

    @Override
    public DatabaseGrid fixedBoundaryFraction(final float fraction) {
        super.fixedBoundaryFraction(fraction);
        return this;
    }

    @Override
    public DatabaseGrid adjustableFixedBoundary(final boolean adjustable) {
        super.adjustableFixedBoundary(adjustable);
        return this;
    }

    @Override
    public DatabaseGrid alternatingRowColors(final boolean alternating) {
        super.alternatingRowColors(alternating);
        return this;
    }

    @Override
    public DatabaseGrid sortable(final boolean sortable) {
        super.sortable(sortable);
        return this;
    }

    @Override
    public DatabaseGrid colors(final GridColors colors) {
        super.colors(colors);
        return this;
    }

    @Override
    public DatabaseGrid configuration(final String json) {
        super.configuration(json);
        return this;
    }

    @Override
    public DatabaseGrid clearSort() {
        super.clearSort();
        return this;
    }

    @Override
    public DatabaseGrid search(final List<SearchRequest> requests) {
        super.search(requests);
        return this;
    }

    @Override
    public DatabaseGrid onCellClick(final OnCellClickListener listener) {
        super.onCellClick(listener);
        return this;
    }

    @Override
    public DatabaseGrid onCellLongClick(final OnCellClickListener listener) {
        super.onCellLongClick(listener);
        return this;
    }

    @Override
    public DatabaseGrid onHeaderClick(final OnHeaderClickListener listener) {
        super.onHeaderClick(listener);
        return this;
    }

    @Override
    public DatabaseGrid onHeaderLongClick(final OnHeaderClickListener listener) {
        super.onHeaderLongClick(listener);
        return this;
    }

    @Override
    public DatabaseGrid onConfigurationChanged(final OnConfigurationChangedListener listener) {
        super.onConfigurationChanged(listener);
        return this;
    }

    @Override
    public DatabaseGrid onSortChanged(final OnSortChangedListener listener) {
        super.onSortChanged(listener);
        return this;
    }

    @Override
    public DatabaseGrid onFixedBoundaryChanged(final OnFixedBoundaryChangedListener listener) {
        super.onFixedBoundaryChanged(listener);
        return this;
    }

    @Override
    public DatabaseGrid onSearchChanged(final OnSearchChangedListener listener) {
        super.onSearchChanged(listener);
        return this;
    }
}
