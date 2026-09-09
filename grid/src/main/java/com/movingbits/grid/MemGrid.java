package com.movingbits.grid;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link Grid} for data that already sits in memory.
 *
 * <p>It is its own {@link GridDataSource}: it holds the list of records, applies search and
 * sort order to it and cuts out the page that was asked for. The application notices nothing
 * of the callback – it only describes how a column wins its text and, where needed, how it is
 * sorted.</p>
 *
 * <p>The search runs on the displayed text: for text columns ignoring case, for numeric
 * columns as a number. The "Global" condition checks every column of the data set, including
 * those currently hidden.</p>
 *
 * <pre>{@code
 * MemGrid<Person> grid = new MemGrid<>(people)
 *         .column("Name", Person::getName)
 *         .column(new MemColumn<Person>("Amount", Person::getAmount)
 *                 .name("amount")
 *                 .comparator((a, b) -> Double.compare(a.getValue(), b.getValue())))
 *         .fixedColumns(1)
 *         .rowsPerPage(10)
 *         .onItemClick((cell, person) -> { ... });
 * }</pre>
 *
 * <p>Columns that are not created as a {@link MemColumn} stay empty: they lack the statement
 * of where their text comes from.</p>
 *
 * @param <T> type of a row's record
 */
public class MemGrid<T> extends Grid implements GridDataSource {

    /** The records in the order handed over; never modified. */
    private List<T> rows = Collections.emptyList();
    /** The most recently determined display order: filtered and sorted. */
    private List<T> view;
    /** The sort order and search {@link #view} was built for. */
    private String viewFor;

    /** Creates a grid without data; it is handed over later through {@link #rows(List)}. */
    public MemGrid() {
        data(this);
    }

    /**
     * @param rows the records in display order
     */
    public MemGrid(final List<T> rows) {
        rows(rows);
        data(this);
    }

    // ----------------------------------------------------------------- Data

    /** Replaces the records. Sort order and search are kept. */
    public MemGrid<T> rows(final List<T> rows) {
        this.rows = rows == null ? Collections.<T>emptyList() : rows;
        this.view = null;
        return this;
    }

    /** The records in the order originally handed over, without the search. */
    public List<T> getUnsortedRows() {
        return rows;
    }

    /** The records in display order: what the search left over, sorted. */
    public List<T> getItems() {
        return Collections.unmodifiableList(view(getSortJson(), getSearchJson()));
    }

    /**
     * The record at one position of the display order.
     *
     * @param rowIndex 0-based index within the displayed data set, as a {@code CellRef} names
     *                 it
     * @return the record, or {@code null} when there is none at that position
     */
    public T getItem(final int rowIndex) {
        final List<T> items = getItems();
        return rowIndex < 0 || rowIndex >= items.size() ? null : items.get(rowIndex);
    }

    // ---------------------------------------------------------- Data source

    @Override
    public int getRowCount() {
        return getRowCount(getSearchJson());
    }

    @Override
    public int getRowCount(final String search) {
        return view(getSortJson(), search).size();
    }

    @Override
    public String[][] getPage(final int page, final int count, final String sort, final String search) {
        final List<T> items = view(sort, search);
        final int rowsPerPage = getRowsPerPage();
        final int first = rowsPerPage > 0 ? Math.max(0, page) * rowsPerPage : 0;
        final int length = Math.max(0, Math.min(count, items.size() - first));

        final List<GridColumn> dataColumns = getDataColumns();
        final String[][] result = new String[length][];
        for (int i = 0; i < length; i++) {
            final T item = items.get(first + i);
            final String[] row = new String[dataColumns.size()];
            for (int c = 0; c < row.length; c++) {
                row[c] = textOf(dataColumns.get(c), item);
            }
            result[i] = row;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String textOf(final GridColumn column, final T item) {
        // Columns without a value provider stay empty; there is no other way to win a text
        // from the record.
        return column instanceof MemColumn ? ((MemColumn<T>) column).textOf(item) : "";
    }

    // --------------------------------------------------- Search and sorting

    /**
     * The records this search leaves over, in the order of this sort order. The result is
     * remembered so that paging does not filter and sort all over again every time.
     */
    private List<T> view(final String sort, final String search) {
        final String key = sort + ' ' + search;
        if (view != null && key.equals(viewFor)) {
            return view;
        }
        view = sort(filter(SearchRequest.parse(search)), sort);
        viewFor = key;
        return view;
    }

    private List<T> sort(final List<T> items, final String sort) {
        final List<MemColumn<T>> columns = new ArrayList<>();
        final List<SortDirection> directions = new ArrayList<>();
        for (SortRequest request : SortRequest.parse(sort)) {
            final MemColumn<T> column = memColumn(request.columnName());
            if (column != null) {
                columns.add(column);
                directions.add(request.direction());
            }
        }
        if (columns.isEmpty()) {
            return items;
        }
        // The list handed over stays untouched.
        final List<T> sorted = new ArrayList<>(items);
        sorted.sort(new RowComparator<>(columns, directions));
        return sorted;
    }

    /**
     * The records that meet every condition. Without conditions the data set stays as it is.
     */
    private List<T> filter(final List<SearchRequest> requests) {
        if (requests.isEmpty()) {
            return rows;
        }
        final List<T> result = new ArrayList<>();
        for (T item : rows) {
            if (matches(item, requests)) {
                result.add(item);
            }
        }
        return result;
    }

    private boolean matches(final T item, final List<SearchRequest> requests) {
        for (SearchRequest request : requests) {
            if (!matches(item, request)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks a single condition. The "Global" condition is met as soon as one of the data set's
     * columns matches.
     */
    private boolean matches(final T item, final SearchRequest request) {
        if (request.isAllColumns()) {
            for (GridColumn column : getDataColumns()) {
                final MemColumn<T> memColumn = asMemColumn(column);
                if (memColumn != null && matches(memColumn, item, request)) {
                    return true;
                }
            }
            return false;
        }
        final MemColumn<T> column = memColumn(request.columnName());
        return column != null && matches(column, item, request);
    }

    private boolean matches(final MemColumn<T> column, final T item, final SearchRequest request) {
        final SearchOperator operator = request.operator();
        // Numeric columns compare as a number – but only with an operator that suits. Through
        // "Global" a text operator can hit a numeric column as well.
        if (column.getType().isNumeric() && operator.fits(column.getType())) {
            return SearchMatch.matchesNumber(column.textOf(item), request.value(), operator);
        }
        return SearchMatch.matchesText(column.textOf(item), request.value(), operator);
    }

    @SuppressWarnings("unchecked")
    private MemColumn<T> asMemColumn(final GridColumn column) {
        return column instanceof MemColumn ? (MemColumn<T>) column : null;
    }

    @SuppressWarnings("unchecked")
    private MemColumn<T> memColumn(final String name) {
        final GridColumn column = findColumn(name);
        return column instanceof MemColumn ? (MemColumn<T>) column : null;
    }

    // -------------------------------------------------------------- Columns

    /** Adds a leading-aligned text column that shares the remaining width evenly. */
    public MemGrid<T> column(final String title, final ValueProvider<T> valueProvider) {
        return column(new MemColumn<>(title, valueProvider));
    }

    /** Adds a text column with an alignment and a width. */
    public MemGrid<T> column(final String title, final CellAlignment alignment, final ColumnWidth width, final ValueProvider<T> valueProvider) {
        return column(new MemColumn<>(title, valueProvider).align(alignment).width(width));
    }

    // ---------------------------------------------------------------- Hooks

    /** Hook for a short tap on a cell in the data area, with the record. */
    public MemGrid<T> onItemClick(final OnItemClickListener<T> listener) {
        return onCellClick(wrap(listener));
    }

    /** Hook for a long tap on a cell in the data area, with the record. */
    public MemGrid<T> onItemLongClick(final OnItemClickListener<T> listener) {
        return onCellLongClick(wrap(listener));
    }

    private OnCellClickListener wrap(final OnItemClickListener<T> listener) {
        if (listener == null) {
            return null;
        }
        return (cell, row) -> listener.onItemClick(cell, getItem(cell.rowIndex()));
    }

    // ----------------------------------------------------------------- Chain

    // The inherited configuration methods return a MemGrid again, so that the chain can carry
    // on without a detour.

    @Override
    public MemGrid<T> column(final GridColumn column) {
        super.column(column);
        return this;
    }

    @Override
    public MemGrid<T> ignoreColumns(final String... names) {
        super.ignoreColumns(names);
        return this;
    }

    @Override
    public MemGrid<T> fixedColumns(final int count) {
        super.fixedColumns(count);
        return this;
    }

    @Override
    public MemGrid<T> rowsPerPage(final int rowsPerPage) {
        super.rowsPerPage(rowsPerPage);
        return this;
    }

    @Override
    public MemGrid<T> totalWidth(final float dp) {
        super.totalWidth(dp);
        return this;
    }

    @Override
    public MemGrid<T> fixedBoundaryFraction(final float fraction) {
        super.fixedBoundaryFraction(fraction);
        return this;
    }

    @Override
    public MemGrid<T> adjustableFixedBoundary(final boolean adjustable) {
        super.adjustableFixedBoundary(adjustable);
        return this;
    }

    @Override
    public MemGrid<T> alternatingRowColors(final boolean alternating) {
        super.alternatingRowColors(alternating);
        return this;
    }

    @Override
    public MemGrid<T> sortable(final boolean sortable) {
        super.sortable(sortable);
        return this;
    }

    @Override
    public MemGrid<T> colors(final GridColors colors) {
        super.colors(colors);
        return this;
    }

    @Override
    public MemGrid<T> configuration(final String json) {
        super.configuration(json);
        return this;
    }

    @Override
    public MemGrid<T> state(final String json) {
        super.state(json);
        return this;
    }

    @Override
    public MemGrid<T> readState(final Bundle state) {
        super.readState(state);
        return this;
    }

    @Override
    public MemGrid<T> clearSort() {
        super.clearSort();
        return this;
    }

    @Override
    public MemGrid<T> onCellClick(final OnCellClickListener listener) {
        super.onCellClick(listener);
        return this;
    }

    @Override
    public MemGrid<T> onCellLongClick(final OnCellClickListener listener) {
        super.onCellLongClick(listener);
        return this;
    }

    @Override
    public MemGrid<T> onHeaderClick(final OnHeaderClickListener listener) {
        super.onHeaderClick(listener);
        return this;
    }

    @Override
    public MemGrid<T> onHeaderLongClick(final OnHeaderClickListener listener) {
        super.onHeaderLongClick(listener);
        return this;
    }

    @Override
    public MemGrid<T> onConfigurationChanged(final OnConfigurationChangedListener listener) {
        super.onConfigurationChanged(listener);
        return this;
    }

    @Override
    public MemGrid<T> onSortChanged(final OnSortChangedListener listener) {
        super.onSortChanged(listener);
        return this;
    }

    @Override
    public MemGrid<T> onFixedBoundaryChanged(final OnFixedBoundaryChangedListener listener) {
        super.onFixedBoundaryChanged(listener);
        return this;
    }

    @Override
    public MemGrid<T> onSearchChanged(final OnSearchChangedListener listener) {
        super.onSearchChanged(listener);
        return this;
    }

    @Override
    public MemGrid<T> search(final List<SearchRequest> requests) {
        super.search(requests);
        return this;
    }
}
