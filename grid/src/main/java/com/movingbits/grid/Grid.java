package com.movingbits.grid;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration of a grid: columns, display properties and where the data comes from.
 *
 * <p>The grid holds no records itself. What it shows it fetches page by page through a
 * {@link GridDataSource}; it only keeps the configuration object – columns, order, visibility,
 * boundary share, widths and sort order. Whoever keeps their data in memory anyway takes
 * {@link MemGrid} instead.</p>
 *
 * <p>All configuration methods return {@code this} and can be chained into a single call:</p>
 *
 * <pre>{@code
 * Grid grid = new Grid(dataSource)
 *         .column("Name")
 *         .column(new GridColumn("Amount").name("amount")
 *                 .type(ColumnType.FLOAT)
 *                 .widthDp(96f))
 *         .fixedColumns(2)
 *         .rowsPerPage(10)
 *         .onCellClick((cell, row) -> { ... });
 * }</pre>
 *
 * <p>The finished configuration is handed to a {@code GridView}. Changes made after that only
 * take effect once the grid is set again or the view is refreshed.</p>
 */
public class Grid {

    /** Default share of the total width that the fixed block occupies at most. */
    public static final float DEFAULT_FIXED_BOUNDARY_FRACTION = 0.4f;
    /** Smallest share the boundary can be moved to. */
    public static final float MIN_FIXED_BOUNDARY_FRACTION = 0.15f;
    /** Largest share the boundary can be moved to. */
    public static final float MAX_FIXED_BOUNDARY_FRACTION = 0.6f;
    /** Key the state is stored under in a bundle. */
    private static final String STATE_GRID = "com.movingbits.grid.state";

    /** All defined columns in the order they were handed over. */
    private final List<GridColumn> allColumns = new ArrayList<>();
    /**
     * All columns that are not excluded, in display order – including the hidden ones. That is
     * how those keep their position and their area while they are out of sight.
     */
    private final List<GridColumn> layout = new ArrayList<>();
    /** How many columns at the start of {@link #layout} belong to the fixed area. */
    private int layoutFixedCount;
    /** Displayed columns in display order; {@link #layout} without the hidden ones. */
    private final List<GridColumn> columns = new ArrayList<>();
    /**
     * Order of the fields within a data row. It is settled once and stays unchanged after
     * that, so that the data source can rely on it.
     */
    private List<GridColumn> dataColumns;
    /** Names of explicitly hidden columns; comes from the configuration object. */
    private final Set<String> hiddenNames = new LinkedHashSet<>();
    /** Names of entirely excluded columns; set while configuring in code. */
    private final Set<String> ignoredNames = new LinkedHashSet<>();
    private final GridSortState sortState = new GridSortState();
    /** The colors to paint with; without any of them set, the theme decides. */
    private GridColors colors = new GridColors();
    /** Search conditions in effect; transient, not part of the configuration object. */
    private final List<SearchRequest> search = new ArrayList<>();

    private GridDataSource dataSource;
    /** Total number of records as asked for most recently. */
    private int rowCount;
    private boolean sortable = true;

    private int fixedColumnCount;
    private int rowsPerPage;
    private float totalWidthDp = Float.NaN;
    private float fixedBoundaryFraction = DEFAULT_FIXED_BOUNDARY_FRACTION;
    private boolean fixedBoundaryAdjustable;
    private boolean alternatingRowColors;

    private OnCellClickListener cellClickListener;
    private OnCellClickListener cellLongClickListener;
    private OnHeaderClickListener headerClickListener;
    private OnHeaderClickListener headerLongClickListener;
    private OnFixedBoundaryChangedListener fixedBoundaryChangedListener;
    private OnSortChangedListener sortChangedListener;
    private OnSearchChangedListener searchChangedListener;
    private OnConfigurationChangedListener configurationChangedListener;

    /** Creates a grid without a data source; it is set through {@link #data(GridDataSource)}. */
    public Grid() {
    }

    /**
     * @param dataSource supplies the contents to display
     */
    public Grid(final GridDataSource dataSource) {
        data(dataSource);
    }

    // ----------------------------------------------------------------- Data

    /**
     * Sets the source of the contents to display. It is asked on every change of page and sort
     * order, not while scrolling.
     */
    public Grid data(final GridDataSource dataSource) {
        this.dataSource = dataSource;
        refreshRowCount();
        return this;
    }

    /** The data source that was set, or {@code null}. */
    public GridDataSource getDataSource() {
        return dataSource;
    }

    /**
     * Fetches the contents of one page from the data source.
     *
     * <p>Missing rows and fields are made up for: the display copes with an answer that is too
     * short, and surplus values are ignored.</p>
     *
     * @param page 0-based page number
     */
    String[][] fetchPage(final int page) {
        refreshRowCount();
        if (dataSource == null) {
            return new String[0][];
        }
        freezeDataOrder();
        final int total = getRowCount();
        final int count = isPaged() ? GridLayoutMath.rowsOnPage(total, page, rowsPerPage) : total;
        if (count <= 0) {
            return new String[0][];
        }
        final String[][] rows = dataSource.getPage(page, count, getSortJson(), getSearchJson());
        return rows == null ? new String[0][] : rows;
    }

    /** Asks for the total number of records again. */
    void refreshRowCount() {
        rowCount = dataSource == null ? 0 : Math.max(0, dataSource.getRowCount(getSearchJson()));
    }

    /** The current sort order as an excerpt of the configuration object. */
    public String getSortJson() {
        return GridConfiguration.sortToJson(this);
    }

    // --------------------------------------------------------------- Search

    /**
     * Sets the search conditions. They apply together: only what meets all of them is shown.
     * Conditions without a value are ignored.
     *
     * <p>The search is transient – it goes to the data source with every page fetch, but it is
     * not part of the configuration object and is therefore not persisted.</p>
     *
     * @param requests the conditions, or {@code null} for no search
     */
    public Grid search(final List<SearchRequest> requests) {
        search.clear();
        if (requests != null) {
            for (SearchRequest request : requests) {
                if (request != null && !request.value().isEmpty()) {
                    search.add(request);
                }
            }
        }
        return this;
    }

    /** The search conditions in effect; empty when nothing is searched for. */
    public List<SearchRequest> getSearch() {
        return Collections.unmodifiableList(search);
    }

    /** {@code true} when at least one search condition is in effect. */
    public boolean isSearching() {
        return !search.isEmpty();
    }

    /** The current search, exactly as it goes to the data source. */
    public String getSearchJson() {
        return SearchRequest.toJson(search);
    }

    // -------------------------------------------------------------- Columns

    /**
     * Adds a prepared column. Columns added after a configuration object has been applied show
     * up at the end of the display.
     */
    public Grid column(final GridColumn column) {
        if (column == null) {
            throw new IllegalArgumentException("column must not be null");
        }
        allColumns.add(column);
        if (!ignoredNames.contains(column.getName())) {
            layout.add(column);
            // If the data order is settled already, the column joins at the back.
            if (dataColumns != null) {
                column.setDataIndex(dataColumns.size());
                dataColumns.add(column);
            }
            rebuildVisibleColumns();
        }
        return this;
    }

    /**
     * Drops every column – together with display order, boundary share, visibility, sort
     * order, search and data order.
     *
     * <p>Meant for a change of data set where the columns are different ones too, for instance
     * to another database table. Afterwards the new columns are added through
     * {@code column(...)}; a configuration object belongs at the end, just as when building the
     * grid for the first time. The view is then to be refreshed through
     * {@code GridView.refresh()}.</p>
     *
     * <p>Excluded column names ({@link #ignoreColumns(String...)}) stay in place: they belong
     * to the application, not to the data set.</p>
     */
    public Grid clearColumns() {
        allColumns.clear();
        layout.clear();
        layoutFixedCount = 0;
        columns.clear();
        fixedColumnCount = 0;
        // The data order of the old data set does not suit the new one; it is settled again on
        // the next fetch.
        dataColumns = null;
        hiddenNames.clear();
        sortState.clear();
        // The search names columns that no longer exist in this form.
        search.clear();
        refreshRowCount();
        return this;
    }

    /** Adds a column with a title and the defaults of its type. */
    public Grid column(final String title) {
        return column(new GridColumn(title));
    }

    /** Adds a column with a title, an alignment and a width. */
    public Grid column(final String title, final CellAlignment alignment, final ColumnWidth width) {
        return column(new GridColumn(title).align(alignment).width(width));
    }

    /**
     * Excludes columns from the display entirely.
     *
     * <p>An ignored column behaves as if it were not listed in the {@code order} field of the
     * configuration object and no {@code "*"} were given: it does not appear, it cannot be
     * brought back through {@code order} or {@code columns_fixed}, it turns up in no field of
     * the configuration object that is written out – not even under {@code columns_hidden} –
     * and it occupies no field in the data rows.</p>
     *
     * <p>The order of the call makes no difference: columns already added are excluded
     * afterwards, columns added later are not taken in to begin with.</p>
     *
     * @param names names of the columns; without a name of its own that is the title
     */
    public Grid ignoreColumns(final String... names) {
        if (names == null) {
            return this;
        }
        for (String name : names) {
            if (name != null && !name.isEmpty()) {
                ignoredNames.add(name);
            }
        }
        layout.removeIf(gridColumn -> ignoredNames.contains(gridColumn.getName()));
        layoutFixedCount = Math.min(layoutFixedCount, layout.size());
        rebuildVisibleColumns();
        return this;
    }

    /**
     * Derives the displayed columns, and how many of them are fixed, from the layout order.
     * Hidden columns stay put in there and thereby keep their position and their area.
     */
    private void rebuildVisibleColumns() {
        columns.clear();
        fixedColumnCount = 0;
        final int fixedLimit = Math.min(layoutFixedCount, layout.size());
        for (int i = 0; i < layout.size(); i++) {
            final GridColumn column = layout.get(i);
            if (hiddenNames.contains(column.getName())) {
                continue;
            }
            columns.add(column);
            if (i < fixedLimit) {
                fixedColumnCount++;
            }
        }
    }

    // ----------------------------------------------------------- Data order

    /**
     * The columns in the order their fields sit in a data row.
     *
     * <p>It follows from the configuration object handed in at the start, or, without one,
     * from the order of the {@code column(...)} calls. As soon as the grid fetches data for the
     * first time it is settled: reordering and hiding columns only change the display, not the
     * data rows. Columns added later join at the back.</p>
     */
    public List<GridColumn> getDataColumns() {
        freezeDataOrder();
        return Collections.unmodifiableList(dataColumns);
    }

    /** Settles the data order unless that has happened already. */
    void freezeDataOrder() {
        if (dataColumns != null) {
            return;
        }
        dataColumns = new ArrayList<>(layout);
        for (int i = 0; i < dataColumns.size(); i++) {
            dataColumns.get(i).setDataIndex(i);
        }
    }

    /** The settled data order, or {@code null} while it is not settled yet. */
    List<GridColumn> getFrozenDataColumns() {
        return dataColumns;
    }

    /**
     * Takes over a data order out of a stored state.
     *
     * <p>Unlike {@link #freezeDataOrder()} it overrides an order settled already: what the
     * fields of a data row mean has to stay as it was, and the display order does not tell –
     * moving and hiding columns leaves the data rows untouched. Names that no longer belong to
     * any column fall away; columns the order does not know join at the back, just as columns
     * added later do.</p>
     *
     * @param names names of the columns in data order; nothing happens without any
     */
    void applyDataOrder(final List<String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        final List<GridColumn> ordered = new ArrayList<>();
        for (String name : names) {
            final GridColumn column = findColumn(name);
            if (column != null && layout.contains(column) && !ordered.contains(column)) {
                ordered.add(column);
            }
        }
        for (GridColumn column : layout) {
            if (!ordered.contains(column)) {
                ordered.add(column);
            }
        }
        dataColumns = ordered;
        for (int i = 0; i < dataColumns.size(); i++) {
            dataColumns.get(i).setDataIndex(i);
        }
    }

    // -------------------------------------------------- Optional appearance

    /**
     * States how many columns are fixed, counted from the left. The default is 0.
     *
     * @param count number of fixed columns, {@code >= 0}
     */
    public Grid fixedColumns(final int count) {
        if (count < 0) {
            throw new IllegalArgumentException("fixedColumns must be >= 0, was: " + count);
        }
        this.layoutFixedCount = count;
        rebuildVisibleColumns();
        return this;
    }

    /**
     * Switches on paging with a fixed number of rows per page. Without it all records form a
     * single, vertically scrolling page.
     *
     * @param rowsPerPage rows per page, {@code > 0}; {@code 0} switches paging off
     */
    public Grid rowsPerPage(final int rowsPerPage) {
        if (rowsPerPage < 0) {
            throw new IllegalArgumentException("rowsPerPage must be >= 0, was: " + rowsPerPage);
        }
        this.rowsPerPage = rowsPerPage;
        return this;
    }

    /**
     * States the total width of the grid. Without one the available width is used.
     *
     * @param dp total width in dp, {@code > 0}
     */
    public Grid totalWidth(final float dp) {
        if (dp <= 0f) {
            throw new IllegalArgumentException("totalWidth must be > 0, was: " + dp);
        }
        this.totalWidthDp = dp;
        return this;
    }

    /**
     * Share of the total width that the fixed block occupies at most. If it is exceeded, the
     * view caps the block at that share and makes it scroll horizontally on its own. The
     * default is {@link #DEFAULT_FIXED_BOUNDARY_FRACTION}.
     *
     * @param fraction share between {@link #MIN_FIXED_BOUNDARY_FRACTION} and
     *                 {@link #MAX_FIXED_BOUNDARY_FRACTION}
     */
    public Grid fixedBoundaryFraction(final float fraction) {
        if (fraction < MIN_FIXED_BOUNDARY_FRACTION || fraction > MAX_FIXED_BOUNDARY_FRACTION) {
            throw new IllegalArgumentException("fixedBoundaryFraction must lie between "
                    + MIN_FIXED_BOUNDARY_FRACTION + " and " + MAX_FIXED_BOUNDARY_FRACTION
                    + ", was: " + fraction);
        }
        this.fixedBoundaryFraction = fraction;
        return this;
    }

    /**
     * Allows the boundary of the fixed area to be moved by long-pressing the boundary line and
     * dragging. The default is {@code false}.
     *
     * <p>The boundary line only appears while content is hidden at the boundary – and only
     * then can the boundary be grabbed.</p>
     */
    public Grid adjustableFixedBoundary(final boolean adjustable) {
        this.fixedBoundaryAdjustable = adjustable;
        return this;
    }

    /**
     * Tints every other data row slightly, so that long rows are easier to follow. The default
     * is {@code false}.
     *
     * <p>What counts is the running number within the whole data set, not the position on the
     * page: a record therefore keeps its tint even when sorting or paging puts it elsewhere.
     * The header row stays untouched.</p>
     */
    public Grid alternatingRowColors(final boolean alternating) {
        this.alternatingRowColors = alternating;
        return this;
    }

    /**
     * States whether a short tap on the header row sorts by that column. The default is
     * {@code true}. Individual columns can be excluded through
     * {@link GridColumn#sortable(boolean)}.
     */
    public Grid sortable(final boolean sortable) {
        this.sortable = sortable;
        return this;
    }

    /**
     * Sets the colors to paint with. Without this call – and for every color left unset – the
     * grid follows the Material 2 theme of the embedding application.
     *
     * <p>The colors are read while the view is built. Handing over a different palette
     * afterwards takes effect on {@code GridView.refresh()}.</p>
     *
     * @param colors the palette, or {@code null} to go back to the theme
     */
    public Grid colors(final GridColors colors) {
        this.colors = colors == null ? new GridColors() : colors;
        return this;
    }

    /** The colors in effect; never {@code null}. */
    public GridColors getColors() {
        return colors;
    }

    // ------------------------------------------------------- Configuration

    /**
     * Takes over a configuration object as a JSON string: column properties, order, fixed and
     * hidden columns, widths and the sort order.
     *
     * <p>The evaluation is forgiving: unknown fields, unknown column names and invalid values
     * are skipped, missing values keep their default. Even a string that cannot be read has no
     * effect.</p>
     *
     * <p>The call belongs at the end of the chain, after all {@code column(...)} calls: it acts
     * on the columns known at that point and settles the data order at the same time.</p>
     *
     * @param json the configuration object, or {@code null}
     */
    public Grid configuration(final String json) {
        GridConfiguration.apply(this, json);
        return this;
    }

    /**
     * The current state as a configuration object – suitable for storing and setting again
     * later through {@link #configuration(String)}.
     */
    public String toConfigurationJson() {
        return GridConfiguration.toJson(this);
    }

    // ---------------------------------------------------------------- State

    /**
     * Writes the grid's state into the bundle handed over – meant for
     * {@code Activity.onSaveInstanceState}, so that a change of screen orientation does not
     * throw the display back to the start. {@link #readState(Bundle)} takes it back.
     *
     * <p>The state holds more than the configuration object: the search as well, and the data
     * order. What comes out of the application's own code – data source, columns, colors,
     * hooks – is no part of it: that is built anew anyway. The view's own state, page and
     * scroll position, is stored by {@code GridView.addState(Bundle)}.</p>
     *
     * <p>Several grids in one activity each need a bundle of their own, because they all store
     * under the same key:</p>
     *
     * <pre>{@code
     * Bundle state = new Bundle();
     * grid.addState(state);
     * outState.putBundle("left", state);
     * }</pre>
     */
    public void addState(final Bundle outState) {
        if (outState != null) {
            outState.putString(STATE_GRID, toStateJson());
        }
    }

    /**
     * Takes the state back out of a bundle – the one handed to {@code Activity.onCreate} or to
     * {@code onRestoreInstanceState}. A bundle without a state, {@code null} included, has no
     * effect.
     *
     * <p>The call belongs at the end of the chain, after all {@code column(...)} calls and
     * before the grid is handed to the view: it acts on the columns known at that point and
     * settles the data order. A configuration object set beforehand is thereby replaced by the
     * state, which is the more recent one.</p>
     */
    public Grid readState(final Bundle state) {
        return state == null ? this : state(state.getString(STATE_GRID));
    }

    /**
     * The current state as a JSON string – suitable for storing anywhere and setting again
     * later through {@link #state(String)}. For the usual case, a change of screen
     * orientation, {@link #addState(Bundle)} does the same with a bundle.
     */
    public String toStateJson() {
        return GridState.toJson(this);
    }

    /**
     * Takes over a state as a JSON string. As with the configuration object the evaluation is
     * forgiving: a string that cannot be read has no effect.
     */
    public Grid state(final String json) {
        GridState.apply(this, json);
        return this;
    }

    /** All defined columns in the order handed over, including those not displayed. */
    List<GridColumn> getAllColumns() {
        return allColumns;
    }

    /** Looks a column up by its name; {@code null} when there is none. */
    GridColumn findColumn(final String name) {
        if (name == null) {
            return null;
        }
        for (int i = 0; i < allColumns.size(); i++) {
            if (allColumns.get(i).getName().equals(name)) {
                return allColumns.get(i);
            }
        }
        return null;
    }

    boolean isIgnored(final GridColumn column) {
        return ignoredNames.contains(column.getName());
    }

    public boolean isHidden(final GridColumn column) {
        return hiddenNames.contains(column.getName());
    }

    /** {@code true} when at least one column is hidden. */
    public boolean hasHiddenColumns() {
        return !hiddenNames.isEmpty();
    }

    Set<String> getHiddenNames() {
        return hiddenNames;
    }

    /** All columns that are not excluded, in display order, including the hidden ones. */
    List<GridColumn> getLayoutColumns() {
        return layout;
    }

    /** How many columns at the start of the layout order belong to the fixed area. */
    int getLayoutFixedCount() {
        return Math.min(layoutFixedCount, layout.size());
    }

    /** Sets the hidden columns. */
    void setHiddenNames(final Collection<String> names) {
        hiddenNames.clear();
        hiddenNames.addAll(names);
    }

    /** Appends a sort criterion at the end of the order. */
    void appendSortCriterion(final int columnIndex, final SortDirection direction) {
        if (canSortBy(columnIndex)) {
            sortState.append(columnIndex, direction);
        }
    }

    /** Drops the sort order. */
    void resetSortState() {
        sortState.clear();
    }

    // ---------------------------------------------------- Reordering columns

    /**
     * Moves a column within the display order.
     *
     * <p>Fixed and freely scrolling columns form separate areas: a column can only be moved
     * within its own area, not across the boundary. A sort order in effect is kept and follows
     * the column. The data order stays untouched.</p>
     *
     * @param fromIndex previous position in the display order
     * @param toIndex   desired position
     * @return {@code true} when something was moved
     */
    public boolean moveColumn(final int fromIndex, final int toIndex) {
        if (!canMoveColumn(fromIndex, toIndex)) {
            return false;
        }
        // Move within the layout order, so that hidden columns in between keep their position;
        // the display is derived from it.
        final GridColumn moved = columns.get(fromIndex);
        final GridColumn target = columns.get(toIndex);
        layout.add(layout.indexOf(target), layout.remove(layout.indexOf(moved)));
        sortState.moveColumn(fromIndex, toIndex);
        rebuildVisibleColumns();
        return true;
    }

    /**
     * Takes over order, boundary share and visibility in one go.
     *
     * <p>The order covers the hidden columns as well, so that those keep their position and
     * their area. The sort order follows the columns; criteria of columns that are no longer
     * displayed fall away. The data order stays untouched.</p>
     *
     * @param layoutInOrder all columns that are not excluded, in display order
     * @param hidden        names of the hidden columns
     * @param fixedCount    how many columns at the start belong to the fixed area
     */
    void applyColumnLayout(final List<GridColumn> layoutInOrder, final Collection<String> hidden, final int fixedCount) {
        // The criteria refer to indices; held as the columns themselves they survive
        // reordering and hiding.
        final List<GridColumn> sortedColumns = new ArrayList<>();
        final List<SortDirection> sortedDirections = new ArrayList<>();
        for (SortCriterion criterion : sortState.getCriteria()) {
            final int index = criterion.columnIndex();
            if (index >= 0 && index < columns.size()) {
                sortedColumns.add(columns.get(index));
                sortedDirections.add(criterion.direction());
            }
        }

        hiddenNames.clear();
        hiddenNames.addAll(hidden);
        layout.clear();
        layout.addAll(layoutInOrder);
        this.layoutFixedCount = fixedCount;
        rebuildVisibleColumns();

        sortState.clear();
        for (int i = 0; i < sortedColumns.size(); i++) {
            final int index = columns.indexOf(sortedColumns.get(i));
            if (index >= 0) {
                sortState.append(index, sortedDirections.get(i));
            }
        }
    }

    /** {@code true} when {@link #moveColumn(int, int)} would do something with these positions. */
    public boolean canMoveColumn(final int fromIndex, final int toIndex) {
        if (fromIndex == toIndex || fromIndex < 0 || fromIndex >= columns.size() || toIndex < 0 || toIndex >= columns.size()) {
            return false;
        }
        // Both positions have to lie within the same area.
        final int fixed = getFixedColumnCount();
        return (fromIndex < fixed) == (toIndex < fixed);
    }

    // -------------------------------------------------------------- Sorting

    /**
     * Advances a column's sorting: none -> ascending -> descending -> none.
     *
     * <p>A column not yet involved is appended as a subordinate criterion, the existing ones
     * keep their order and direction. The whole data set is sorted, not just the page on
     * screen – that is done by the data source, which receives the order with every page.</p>
     *
     * @param columnIndex 0-based index of the column in the display, without the number column
     * @return {@code true} when the sort order changed
     */
    public boolean toggleSort(final int columnIndex) {
        if (!canSortBy(columnIndex)) {
            return false;
        }
        sortState.toggle(columnIndex);
        return true;
    }

    /** Removes the sorting entirely. */
    public Grid clearSort() {
        sortState.clear();
        return this;
    }

    /** {@code true} when this column can be sorted by. */
    public boolean canSortBy(final int columnIndex) {
        return sortable && columnIndex >= 0 && columnIndex < columns.size() && columns.get(columnIndex).isSortable();
    }

    /** Criteria in the order they take effect; empty when nothing is sorted. */
    public List<SortCriterion> getSortOrder() {
        return sortState.getCriteria();
    }

    /** Direction for a column, or {@code null} when it is not sorted by. */
    public SortDirection getSortDirection(final int columnIndex) {
        return sortState.directionOf(columnIndex);
    }

    /**
     * Rank of a column within the sort order.
     *
     * @return 1 for the strongest criterion, 0 when the column is not sorted by
     */
    public int getSortRank(final int columnIndex) {
        return sortState.rankOf(columnIndex);
    }

    /** Number of columns that are sorted by. */
    public int getSortCriteriaCount() {
        return sortState.size();
    }

    public boolean isSortable() {
        return sortable;
    }

    // ---------------------------------------------------------------- Hooks

    /** Hook for a short tap on a cell in the data area. */
    public Grid onCellClick(final OnCellClickListener listener) {
        this.cellClickListener = listener;
        return this;
    }

    /** Hook for a long tap on a cell in the data area. */
    public Grid onCellLongClick(final OnCellClickListener listener) {
        this.cellLongClickListener = listener;
        return this;
    }

    /** Hook for a short tap on a cell of the header row. */
    public Grid onHeaderClick(final OnHeaderClickListener listener) {
        this.headerClickListener = listener;
        return this;
    }

    /** Hook for a long tap on a cell of the header row. */
    public Grid onHeaderLongClick(final OnHeaderClickListener listener) {
        this.headerLongClickListener = listener;
        return this;
    }

    /**
     * Hook for changes that are reflected in the configuration object. It receives the complete
     * object as a JSON string, so that the application can persist the state.
     */
    public Grid onConfigurationChanged(final OnConfigurationChangedListener listener) {
        this.configurationChangedListener = listener;
        return this;
    }

    /**
     * Hook for a changed sort order. Fired after a header cell has been tapped, as soon as the
     * new page is at hand.
     */
    public Grid onSortChanged(final OnSortChangedListener listener) {
        this.sortChangedListener = listener;
        return this;
    }

    /**
     * Hook for a boundary of the fixed area moved by the user. Whether or not a hook is set,
     * moving has to be allowed through {@link #adjustableFixedBoundary(boolean)}.
     */
    public Grid onFixedBoundaryChanged(final OnFixedBoundaryChangedListener listener) {
        this.fixedBoundaryChangedListener = listener;
        return this;
    }

    /**
     * Hook for a changed search. Fired as soon as the narrowed-down page is at hand. Since the
     * search is not part of the configuration object, this is the only place it reports.
     */
    public Grid onSearchChanged(final OnSearchChangedListener listener) {
        this.searchChangedListener = listener;
        return this;
    }

    // -------------------------------------------------------------- Queries

    /** Total number of records as the data source reported it most recently. */
    public int getRowCount() {
        return rowCount;
    }

    public List<GridColumn> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public int getColumnCount() {
        return columns.size();
    }

    public GridColumn getColumn(final int index) {
        return columns.get(index);
    }

    /** Number of fixed columns among the displayed ones. */
    public int getFixedColumnCount() {
        return fixedColumnCount;
    }

    /** Rows per page, {@code 0} when paging is switched off. */
    public int getRowsPerPage() {
        return rowsPerPage;
    }

    /** {@code true} when paging is configured. */
    public boolean isPaged() {
        return rowsPerPage > 0;
    }

    /** The given total width in dp, or {@link Float#NaN} when none is set. */
    public float getTotalWidthDp() {
        return totalWidthDp;
    }

    public boolean hasTotalWidth() {
        return !Float.isNaN(totalWidthDp);
    }

    /** Share of the total width that the fixed block occupies at most. */
    public float getFixedBoundaryFraction() {
        return fixedBoundaryFraction;
    }

    /**
     * Takes over a moved boundary share. Unlike {@link #fixedBoundaryFraction(float)} the value
     * is clamped rather than rejected – it comes from the user or from a configuration object.
     */
    void setFixedBoundaryFraction(final float fraction) {
        this.fixedBoundaryFraction = GridLayoutMath.clampBoundaryFraction(fraction);
    }

    /** {@code true} when the user may move the boundary. */
    public boolean isFixedBoundaryAdjustable() {
        return fixedBoundaryAdjustable;
    }

    /** {@code true} when every other data row is tinted slightly. */
    public boolean hasAlternatingRowColors() {
        return alternatingRowColors;
    }

    /** Number of pages; always 1 without paging. */
    public int getPageCount() {
        return GridLayoutMath.pageCount(getRowCount(), rowsPerPage);
    }

    public OnCellClickListener getCellClickListener() {
        return cellClickListener;
    }

    public OnCellClickListener getCellLongClickListener() {
        return cellLongClickListener;
    }

    public OnHeaderClickListener getHeaderClickListener() {
        return headerClickListener;
    }

    public OnHeaderClickListener getHeaderLongClickListener() {
        return headerLongClickListener;
    }

    public OnFixedBoundaryChangedListener getFixedBoundaryChangedListener() {
        return fixedBoundaryChangedListener;
    }

    public OnSortChangedListener getSortChangedListener() {
        return sortChangedListener;
    }

    public OnSearchChangedListener getSearchChangedListener() {
        return searchChangedListener;
    }

    public OnConfigurationChangedListener getConfigurationChangedListener() {
        return configurationChangedListener;
    }
}
