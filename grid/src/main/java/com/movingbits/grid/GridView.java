package com.movingbits.grid;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Display of a {@link Grid}: the fixed header row on top, the data rows below it.
 *
 * <p>Without paging the data rows are as tall as their content and scroll vertically. With
 * paging, the header row and the data rows of one page fill the available height exactly: the
 * row height follows from (available height minus header row height) divided by the rows per
 * page. Controls for paging deliberately do not belong to the library; {@link #nextPage()},
 * {@link #previousPage()}, {@link #setCurrentPage(int)} and
 * {@link #setOnPageChangedListener(OnPageChangedListener)} are provided for that.</p>
 *
 * <p>When content is hidden at the boundary between the fixed and the freely scrolling area, a
 * vertical line in the theme's accent color appears there. It sits as an overlay above the
 * whole grid and therefore runs across the header row and all data rows without seams. In
 * addition the header row shows small arrows per area for columns outside the window.</p>
 *
 * <pre>{@code
 * GridView view = new GridView(context);
 * view.setGrid(new Grid(dataSource)
 *         .column("Name")
 *         .fixedColumns(1)
 *         .rowsPerPage(10));
 * }</pre>
 */
public class GridView extends FrameLayout {

    /** Shared scroll position of the fixed block (only relevant once it has been capped). */
    private final ScrollSync fixedSync = new ScrollSync();
    /** Shared scroll position of the freely scrolling column area. */
    private final ScrollSync bodySync = new ScrollSync();

    private LinearLayout content;
    private RecyclerView body;
    private PagingLayoutManager layoutManager;
    private HorizontalSeparatorView boundary;
    private TextView numberProbe;

    private static final String[][] NO_ROWS = new String[0][];

    /** Keys the view's state is stored under in a bundle. */
    private static final String STATE_PAGE = "com.movingbits.grid.view.page";
    private static final String STATE_SCROLL_FIXED = "com.movingbits.grid.view.scroll_fixed";
    private static final String STATE_SCROLL_BODY = "com.movingbits.grid.view.scroll_body";
    private static final String STATE_TOP_ROW = "com.movingbits.grid.view.top_row";
    private static final String STATE_TOP_OFFSET = "com.movingbits.grid.view.top_offset";

    private Grid grid;
    private GridRowView headerRow;
    private GridAdapter adapter;
    private GridMetrics metrics;
    /** Rows of the displayed page; they survive a rebuild caused by changed columns. */
    private String[][] pageRows = NO_ROWS;

    private OnPageChangedListener pageChangedListener;
    private int currentPage;
    private int lastBodyHeight;
    private int lastMeasuredWidth;

    /** Row the list is to be scrolled back to; {@code -1} when nothing is pending. */
    private int restoreTopRow = -1;
    private int restoreTopOffset;

    /** Current boundary share; the user may have moved it away from the configuration. */
    private float boundaryFraction = Grid.DEFAULT_FIXED_BOUNDARY_FRACTION;
    private final Runnable boundaryLongPress = this::startBoundaryDrag;
    private int boundaryTouchRadius;
    private int gutterWidth;
    private int touchSlop;

    private boolean boundaryCandidate;
    private boolean boundaryDragging;
    private float boundaryDownRawX;
    private float boundaryDownCenterX;

    /** Display of the column edge while the user drags it. */
    private HorizontalSeparatorView columnEdge;
    private final Runnable columnResizeLongPress = this::startColumnResize;
    private int columnEdgeTouchRadius;

    private boolean columnCandidate;
    private boolean columnResizing;
    /** Column whose width is currently being dragged; -1 when none is. */
    private int resizeColumn = -1;
    private float resizeDownRawX;
    private float resizeEdgeStartX;
    private float resizeStartWidth;

    public GridView(final Context context) {
        super(context);
        init();
    }

    public GridView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GridView(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Until a grid is set the theme decides; applyColors takes over from there.
        setBackgroundColor(GridStyle.themeSurface(this));

        numberProbe = GridStyle.createTextCell(getContext(), false);

        content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        layoutManager = new PagingLayoutManager(getContext());
        body = new RecyclerView(getContext());
        body.setLayoutManager(layoutManager);
        body.setClipToPadding(false);
        content.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        gutterWidth = GridStyle.dp(getContext(), GridStyle.BOUNDARY_GUTTER_DP);

        // The line is as wide as the gap and draws its stroke in the middle of it.
        boundary = new HorizontalSeparatorView(getContext());
        addView(boundary, new LayoutParams(gutterWidth, LayoutParams.MATCH_PARENT));

        // The same appearance as the area boundary: both are an edge the user drags into
        // place.
        columnEdge = new HorizontalSeparatorView(getContext());
        addView(columnEdge, new LayoutParams(gutterWidth, LayoutParams.MATCH_PARENT));

        boundaryTouchRadius = GridStyle.dp(getContext(), GridStyle.BOUNDARY_TOUCH_WIDTH_DP) / 2;
        columnEdgeTouchRadius = GridStyle.dp(getContext(), GridStyle.COLUMN_EDGE_TOUCH_WIDTH_DP) / 2;
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

        // Both positions influence the arrows as well as the visibility of the line: the fixed
        // block through its end, the free area through its start.
        final ScrollSync.Listener listener = scrollX -> {
            updateBoundaryVisibility();
            invalidateHeader();
        };
        fixedSync.setListener(listener);
        bodySync.setListener(listener);
    }

    /** Sets the configuration to display. A grid set before is replaced. */
    public void setGrid(final Grid grid) {
        if (grid == null) {
            throw new IllegalArgumentException("grid must not be null");
        }
        this.grid = grid;
        this.currentPage = 0;
        this.pageRows = NO_ROWS;
        this.restoreTopRow = -1;
        // Only when a grid is set, not on refresh(): a boundary the user has moved should
        // survive a data update.
        this.boundaryFraction = grid.getFixedBoundaryFraction();
        rebuild();
        reload();
    }

    /** The configuration currently displayed, or {@code null}. */
    public Grid getGrid() {
        return grid;
    }

    /**
     * Rebuilds the view and fetches the displayed page from the data source again. To be called
     * after later changes to the configuration or to the data.
     */
    public void refresh() {
        rebuild();
        reload();
    }

    // --------------------------------------------------------------- Paging

    /** Number of pages; always 1 without paging. */
    public int getPageCount() {
        return grid == null ? 1 : grid.getPageCount();
    }

    /** 0-based index of the displayed page. */
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * Changes the page. Values outside the valid range are clamped.
     *
     * @param page 0-based page index
     */
    public void setCurrentPage(final int page) {
        final int target = Math.max(0, Math.min(page, getPageCount() - 1));
        if (target == currentPage && adapter != null && adapter.getPage() == target) {
            return;
        }
        currentPage = target;
        reload();
        notifyPageChanged();
    }

    /**
     * Turns one page forward.
     *
     * @return {@code true} when the page changed
     */
    public boolean nextPage() {
        if (currentPage + 1 >= getPageCount()) {
            return false;
        }
        setCurrentPage(currentPage + 1);
        return true;
    }

    /**
     * Turns one page back.
     *
     * @return {@code true} when the page changed
     */
    public boolean previousPage() {
        if (currentPage == 0) {
            return false;
        }
        setCurrentPage(currentPage - 1);
        return true;
    }

    /** Hook for page changes; also fired once when a grid is set. */
    public void setOnPageChangedListener(final OnPageChangedListener listener) {
        this.pageChangedListener = listener;
        notifyPageChanged();
    }

    private void notifyPageChanged() {
        if (pageChangedListener != null) {
            pageChangedListener.onPageChanged(currentPage, getPageCount());
        }
    }

    // ---------------------------------------------------------------- State

    /**
     * Writes the view's state into the bundle handed over – meant for
     * {@code Activity.onSaveInstanceState}: the displayed page and the positions the areas are
     * scrolled to. {@link #readState(Bundle)} takes it back.
     *
     * <p>What belongs to the configuration is stored by {@link Grid#addState(Bundle)}, which is
     * to be called as well.</p>
     */
    public void addState(final Bundle outState) {
        if (outState == null || grid == null) {
            return;
        }
        outState.putInt(STATE_PAGE, currentPage);
        outState.putInt(STATE_SCROLL_FIXED, fixedSync.getScrollX());
        outState.putInt(STATE_SCROLL_BODY, bodySync.getScrollX());
        // With paging one page fills the height exactly; there is nothing to scroll to
        // vertically.
        if (!grid.isPaged()) {
            final int row = layoutManager.findFirstVisibleItemPosition();
            if (row != RecyclerView.NO_POSITION) {
                outState.putInt(STATE_TOP_ROW, row);
                outState.putInt(STATE_TOP_OFFSET, topOffsetOf(row));
            }
        }
    }

    /**
     * Takes the state back out of a bundle – the one handed to {@code Activity.onCreate} or to
     * {@code onRestoreInstanceState}. A bundle without a state, {@code null} included, has no
     * effect.
     *
     * <p>The call belongs directly after {@link #setGrid(Grid)}, whose grid has taken over its
     * own state through {@link Grid#readState(Bundle)} beforehand.</p>
     */
    public void readState(final Bundle state) {
        if (state == null || grid == null || !state.containsKey(STATE_PAGE)) {
            return;
        }
        final int page = Math.max(0, Math.min(state.getInt(STATE_PAGE), getPageCount() - 1));
        if (page != currentPage) {
            currentPage = page;
            reload();
            notifyPageChanged();
        }
        // A narrower screen cannot be scrolled as far; the areas cap the position themselves
        // on the next layout pass and report the capped one back.
        fixedSync.moveTo(state.getInt(STATE_SCROLL_FIXED, 0));
        bodySync.moveTo(state.getInt(STATE_SCROLL_BODY, 0));
        restoreTopRow = state.getInt(STATE_TOP_ROW, -1);
        restoreTopOffset = state.getInt(STATE_TOP_OFFSET, 0);
    }

    /** Distance of a row's upper edge from the upper edge of the list; negative above it. */
    private int topOffsetOf(final int row) {
        final View view = layoutManager.findViewByPosition(row);
        return view == null ? 0 : view.getTop();
    }

    /**
     * Brings the list back to the row it was scrolled to. That is only possible once the rows
     * exist, and they need the widths, which are settled in the first measuring pass – hence
     * the attempt with every layout pass until it works.
     */
    private void applyRestoredRow() {
        if (restoreTopRow < 0 || adapter == null || adapter.getItemCount() == 0) {
            return;
        }
        final int row = Math.min(restoreTopRow, adapter.getItemCount() - 1);
        final int offset = restoreTopOffset;
        restoreTopRow = -1;
        // Do not scroll from within the running layout pass.
        post(() -> layoutManager.scrollToPositionWithOffset(row, offset));
    }

    // -------------------------------------------------------------- Sorting

    /**
     * A short tap on a header cell: sorts by that column where that is possible, and then fires
     * the hook configured for the header row.
     */
    private void onHeaderTapped(final int columnIndex) {
        if (grid == null) {
            return;
        }
        if (grid.toggleSort(columnIndex)) {
            applySortChange();
        }
        if (grid.getHeaderClickListener() != null) {
            grid.getHeaderClickListener().onHeaderClick(columnIndex);
        }
    }

    /**
     * Takes over a changed sort order: the whole list is rebuilt and the display starts at the
     * first page, or at the top of the list, again.
     */
    private void applySortChange() {
        currentPage = 0;
        reload();
        body.scrollToPosition(0);
        headerRow.bindHeader();
        notifyPageChanged();

        if (grid.getSortChangedListener() != null) {
            grid.getSortChangedListener().onSortChanged(grid.getSortOrder());
        }
        notifyConfigurationChanged();
    }

    /**
     * Reports the new state as a configuration object. To be called after every change to the
     * user interface that is reflected in it.
     */
    private void notifyConfigurationChanged() {
        if (grid != null && grid.getConfigurationChangedListener() != null) {
            grid.getConfigurationChangedListener()
                    .onConfigurationChanged(grid.toConfigurationJson());
        }
    }

    /**
     * Advances a column's sorting from code – like a tap on its header cell, but without the
     * hook for the header row.
     */
    public void toggleSort(final int columnIndex) {
        if (grid != null && grid.toggleSort(columnIndex)) {
            applySortChange();
        }
    }

    /** Removes the sorting and shows the original order again. */
    public void clearSort() {
        if (grid != null && !grid.getSortOrder().isEmpty()) {
            grid.clearSort();
            applySortChange();
        }
    }

    // --------------------------------------------------------------- Build-up

    private void rebuild() {
        if (grid == null) {
            return;
        }
        grid.refreshRowCount();
        applyColors();
        fixedSync.clear();
        bodySync.clear();

        if (headerRow != null) {
            content.removeView(headerRow);
        }
        headerRow = new GridRowView(getContext(), grid, true, fixedSync, bodySync, this::onHeaderTapped);
        ViewCompat.setElevation(headerRow, GridStyle.dp(getContext(), GridStyle.HEADER_ELEVATION_DP));
        content.addView(headerRow, 0, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // With paging exactly one page fills the height; vertical scrolling falls away.
        layoutManager.setScrollEnabled(!grid.isPaged());

        currentPage = Math.max(0, Math.min(currentPage, getPageCount() - 1));
        adapter = new GridAdapter(grid, fixedSync, bodySync);
        // The rows fetched last stay valid: reordering and hiding columns only change the
        // display, not the data rows.
        adapter.setPage(currentPage, pageRows);
        body.setAdapter(adapter);

        metrics = null;
        lastBodyHeight = 0;
        lastMeasuredWidth = 0;
        requestLayout();
        notifyPageChanged();
    }

    /**
     * Takes over the grid's colors. The rows read them themselves when they are built; what
     * is left here are the parts that outlive a rebuild – the view's own background and the two
     * lines that lie as an overlay above it.
     */
    private void applyColors() {
        final GridColors colors = grid.getColors();
        setBackgroundColor(colors.surface(this));
        boundary.applyColors(colors);
        columnEdge.applyColors(colors);
    }

    /**
     * Fetches the contents of the displayed page from the data source. To be called when the
     * page, the sort order or the data set has changed – not when only the display of the
     * columns changes.
     */
    private void reload() {
        if (grid == null) {
            return;
        }
        pageRows = grid.fetchPage(currentPage);
        if (adapter != null) {
            adapter.setPage(currentPage, pageRows);
        }
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        // The widths have to be settled before the header row and the data rows are measured.
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        if (grid != null && width > 0 && width != lastMeasuredWidth) {
            lastMeasuredWidth = width;
            updateMetrics(width);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top, final int right, final int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateBoundaryPosition();
        updateRowHeights();
        applyRestoredRow();
    }

    /**
     * Computes the widths for one view width and passes them on to the header row and the data
     * rows.
     */
    private void updateMetrics(final int availableWidth) {
        if (grid == null || availableWidth <= 0) {
            return;
        }
        final float density = GridStyle.density(getContext());
        metrics = GridMetrics.compute(grid, availableWidth, density, measureNumberColumnWidth(),
                density * GridStyle.MIN_SHARED_COLUMN_WIDTH_DP, boundaryFraction, gutterWidth);

        headerRow.applyMetrics(metrics);
        headerRow.bindHeader();
        if (!metrics.fixedBlockScrollable) {
            fixedSync.reset();
        }
        // The adapter reports the change to the RecyclerView; that must not happen from within
        // the running measuring or layout pass.
        final GridMetrics current = metrics;
        post(() -> {
            if (adapter != null) {
                adapter.setMetrics(current);
            }
        });
    }

    private void invalidateHeader() {
        if (headerRow != null) {
            headerRow.invalidate();
        }
    }

    /** Puts the boundary line on the transition from the fixed to the scrolling area. */
    private void updateBoundaryPosition() {
        if (metrics == null) {
            return;
        }
        boundary.setCenterX(metrics.boundaryCenterX());
        updateBoundaryVisibility();
    }

    /**
     * The line appears as soon as content is hidden at the boundary – be it at the end of the
     * fixed columns or at the start of the free ones. It therefore stands exactly when the
     * header row shows an arrow there too.
     */
    private void updateBoundaryVisibility() {
        final boolean visible = metrics != null && headerRow != null && headerRow.hasHintsAtBoundary();
        if (!visible && boundaryDragging) {
            // While dragging the line stays put, even when the content runs back.
            return;
        }
        boundary.setVisibility(visible ? VISIBLE : INVISIBLE);
    }

    // ------------------------------------------------- Moving the boundary

    /** Share of the total width that the fixed area currently occupies at most. */
    public float getFixedBoundaryFraction() {
        return boundaryFraction;
    }

    /**
     * Sets the boundary share from code. The hook
     * {@link Grid#onFixedBoundaryChanged(OnFixedBoundaryChangedListener)} is not fired in the
     * process – it reports changes made by the user only.
     *
     * @param fraction the share; clamped to the permitted range
     */
    public void setFixedBoundaryFraction(final float fraction) {
        final float target = GridLayoutMath.clampBoundaryFraction(fraction);
        if (target == boundaryFraction) {
            return;
        }
        boundaryFraction = target;
        if (grid != null) {
            grid.setFixedBoundaryFraction(target);
        }
        applyWidths();
    }

    private boolean isBoundaryAdjustable() {
        return grid != null && grid.isFixedBoundaryAdjustable();
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // A new touch begins: gestures left open from an earlier sequence are over in
                // any case.
                if (boundaryDragging) {
                    boundaryDragging = false;
                    boundary.setActive(false);
                }
                if (columnResizing) {
                    cancelColumnResize();
                }
                // In the header row the column edge takes precedence: the area boundary can be
                // grabbed in the data rows, the column width only here.
                if (!trackColumnEdgeDown(event)) {
                    trackBoundaryDown(event);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                // Only clear dragging discards the gestures. The slight wandering of a resting
                // finger should not get in their way.
                if (boundaryCandidate && !boundaryDragging
                        && Math.abs(event.getRawX() - boundaryDownRawX) > 2 * touchSlop) {
                    cancelBoundaryCandidate();
                }
                if (columnCandidate && !columnResizing
                        && Math.abs(event.getRawX() - resizeDownRawX) > 2 * touchSlop) {
                    cancelColumnCandidate();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelBoundaryCandidate();
                cancelColumnCandidate();
                break;
            default:
                break;
        }
        return boundaryDragging || columnResizing || super.onInterceptTouchEvent(event);
    }

    /** Notes a touch on the boundary line. */
    private void trackBoundaryDown(final MotionEvent event) {
        if (!isBoundaryAdjustable()) {
            return;
        }
        boundaryCandidate = boundary.getVisibility() == VISIBLE && Math.abs(event.getX() - boundary.getCenterX()) <= boundaryTouchRadius;
        if (boundaryCandidate) {
            boundaryDownRawX = event.getRawX();
            boundaryDownCenterX = boundary.getCenterX();
            // A little ahead of the standard moment, so that a long press on a cell running at
            // the same time does not fire as well: as soon as we take over here, the cell
            // receives an ACTION_CANCEL.
            postDelayed(boundaryLongPress, Math.max(0, ViewConfiguration.getLongPressTimeout() - 60));
        }
    }

    /**
     * Notes a touch on a column edge in the header row.
     *
     * @return {@code true} when an edge lies there
     */
    private boolean trackColumnEdgeDown(final MotionEvent event) {
        if (grid == null || headerRow == null || metrics == null || !isInHeaderRow(event.getY())) {
            return false;
        }
        final int column = headerRow.columnAtEdge(event.getX() - headerLeft(), columnEdgeTouchRadius);
        if (column < 0 || column >= metrics.columnWidths.length) {
            return false;
        }
        final float edge = headerRow.columnEdgeX(column);
        if (Float.isNaN(edge)) {
            return false;
        }

        columnCandidate = true;
        resizeColumn = column;
        resizeDownRawX = event.getRawX();
        resizeEdgeStartX = headerLeft() + edge;
        resizeStartWidth = metrics.columnWidths[column];
        // As with the area boundary, a little ahead of the standard moment so that a long press
        // on the header cell running at the same time does not fire as well.
        postDelayed(columnResizeLongPress, Math.max(0, ViewConfiguration.getLongPressTimeout() - 60));
        return true;
    }

    /** Left edge of the header row within the grid; its coordinates are offset by it. */
    private float headerLeft() {
        return content.getLeft() + headerRow.getLeft();
    }

    private boolean isInHeaderRow(final float y) {
        final float top = content.getTop() + headerRow.getTop();
        return y >= top && y <= top + headerRow.getHeight();
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (columnResizing) {
            return switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE -> {
                    columnEdge.setCenterX(resizeEdgeStartX + (draggedWidth(event.getRawX()) - resizeStartWidth));
                    yield true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    finishColumnResize(event.getRawX());
                    yield true;
                }
                default -> true;
            };
        }
        if (!boundaryDragging) {
            return super.onTouchEvent(event);
        }
        return switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE -> {
                moveBoundaryTo(boundaryDownCenterX + (event.getRawX() - boundaryDownRawX));
                yield true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finishBoundaryDrag();
                yield true;
            }
            default -> true;
        };
    }

    private void cancelBoundaryCandidate() {
        boundaryCandidate = false;
        removeCallbacks(boundaryLongPress);
    }

    // ------------------------------------------------ Moving a column edge

    private void startColumnResize() {
        if (metrics == null || !columnCandidate) {
            return;
        }
        columnResizing = true;
        columnEdge.setCenterX(resizeEdgeStartX);
        columnEdge.setActive(true);
        columnEdge.setVisibility(VISIBLE);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        super.requestDisallowInterceptTouchEvent(false);
        getParent().requestDisallowInterceptTouchEvent(true);
        cancelChildTouch();
    }

    /**
     * Width the column would have at the current state of the gesture. It does not go below the
     * minimum width – the display of the edge stops there even when the finger wanders on to
     * the left.
     */
    private float draggedWidth(final float rawX) {
        final float minWidth = ColumnWidth.MIN_WIDTH_DP * GridStyle.density(getContext());
        return Math.max(minWidth, resizeStartWidth + (rawX - resizeDownRawX));
    }

    /**
     * Takes over the width that was dragged. Only now is the table measured again: while
     * dragging, nothing but the display of the edge moves.
     */
    private void finishColumnResize(final float rawX) {
        final int column = resizeColumn;
        final float widthDp = draggedWidth(rawX) / GridStyle.density(getContext());
        cancelColumnResize();

        if (grid == null || column < 0 || column >= grid.getColumnCount()) {
            return;
        }
        grid.getColumn(column).widthDp(widthDp);

        // As after moving the area boundary: distribute anew, pull the visible rows along at
        // once, and finally the recycled ones through the adapter.
        applyWidths();
        if (adapter != null && metrics != null) {
            adapter.setMetrics(metrics);
        }
        updateBoundaryVisibility();
        notifyConfigurationChanged();
    }

    private void cancelColumnResize() {
        columnResizing = false;
        cancelColumnCandidate();
        resizeColumn = -1;
        columnEdge.setActive(false);
        columnEdge.setVisibility(INVISIBLE);
    }

    private void cancelColumnCandidate() {
        columnCandidate = false;
        removeCallbacks(columnResizeLongPress);
    }

    /**
     * Opens the dialog for hiding, showing and reordering columns.
     *
     * <p>It is opened by the application, which provides a control of its own for it. Nothing
     * is taken over before OK; after that the view is up to date and the configuration object
     * has been reported. The data source is left alone – reordering and hiding columns does not
     * change the data rows.</p>
     */
    public void showColumnSettings() {
        if (grid == null) {
            return;
        }
        ColumnSettingsDialog.show(getContext(), grid, () -> {
            rebuild();
            notifyConfigurationChanged();
        });
    }

    /**
     * Opens the search dialog: an operator and a value field per column, and above them the
     * entry "Global" for the search across every column.
     *
     * <p>It is opened by the application, which provides a control of its own for it. Nothing
     * is taken over before OK; after that the view shows the first page of the narrowed-down
     * data set. The search is transient and is not part of the configuration object.</p>
     */
    public void showSearch() {
        if (grid == null) {
            return;
        }
        SearchDialog.show(getContext(), grid, this::applySearchChange);
    }

    /**
     * Sets the search from code.
     *
     * @param requests the conditions, or {@code null} for no search
     */
    public void setSearch(final List<SearchRequest> requests) {
        if (grid == null) {
            return;
        }
        grid.search(requests);
        applySearchChange();
    }

    /**
     * Takes over a changed search: the data set is a different one, so the display starts at
     * the first page again. The widths are computed anew, because the number column depends on
     * the number of records.
     */
    private void applySearchChange() {
        currentPage = 0;
        reload();
        body.scrollToPosition(0);
        lastMeasuredWidth = 0;
        requestLayout();
        notifyPageChanged();

        if (grid.getSearchChangedListener() != null) {
            grid.getSearchChangedListener().onSearchChanged(grid.getSearch());
        }
    }

    private void startBoundaryDrag() {
        if (metrics == null || !boundaryCandidate) {
            return;
        }
        boundaryDragging = true;
        boundary.setActive(true);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        // If a child has blocked interception – a scrolling area does so at the slightest
        // movement of a finger – the block is lifted here. Otherwise neither the dragging nor
        // the release would reach us.
        super.requestDisallowInterceptTouchEvent(false);
        // A surrounding scrolling element must not intercept the gesture from now on.
        getParent().requestDisallowInterceptTouchEvent(true);
        cancelChildTouch();
    }

    /**
     * Cancels the running touch in the child views. Without this cancellation the cell under
     * the finger would report a long press of its own, because an ACTION_CANCEL only reaches it
     * with the next touch event – which does not come while the finger stands still.
     */
    private void cancelChildTouch() {
        final long now = SystemClock.uptimeMillis();
        final MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        content.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    /** Moves the boundary to the dragged position of the line. */
    private void moveBoundaryTo(final float centerX) {
        // The line sits in the middle of the gap; what counts is the gap's left edge.
        final float fraction = GridLayoutMath.boundaryFractionFor(centerX - gutterWidth / 2f, metrics.numberColumnWidth, metrics.totalWidth);
        if (fraction == boundaryFraction) {
            return;
        }
        boundaryFraction = fraction;
        applyWidths();
    }

    private void finishBoundaryDrag() {
        boundaryDragging = false;
        boundaryCandidate = false;
        boundary.setActive(false);

        // Finally bring the whole table onto the new split: while dragging, only the visible
        // rows are pulled along for the sake of effort, and all the other (recycled) rows do
        // not know the new widths yet.
        if (adapter != null && metrics != null) {
            adapter.setMetrics(metrics);
        }
        requestLayout();
        updateBoundaryVisibility();

        if (grid != null) {
            // The moved share belongs to the grid's state and therefore into the configuration
            // object.
            grid.setFixedBoundaryFraction(boundaryFraction);
            if (grid.getFixedBoundaryChangedListener() != null) {
                grid.getFixedBoundaryChangedListener().onFixedBoundaryChanged(boundaryFraction);
            }
            notifyConfigurationChanged();
        }
    }

    /**
     * Distributes the widths anew and brings the header row and the visible data rows onto them
     * at once.
     *
     * <p>A plain {@code requestLayout()} on the view is not enough for that: it only marks the
     * view itself for measuring again, not the views below it. Their measuring pass is skipped
     * as long as their parameters do not change – and what changed here is nothing but widths
     * inside the rows.</p>
     */
    private void applyWidths() {
        if (grid == null || metrics == null || lastMeasuredWidth <= 0) {
            return;
        }
        final float density = GridStyle.density(getContext());
        metrics = GridMetrics.compute(grid, lastMeasuredWidth, density,
                measureNumberColumnWidth(), density * GridStyle.MIN_SHARED_COLUMN_WIDTH_DP,
                boundaryFraction, gutterWidth);

        headerRow.applyMetrics(metrics);
        headerRow.requestFullLayout();
        for (int i = 0; i < body.getChildCount(); i++) {
            final View child = body.getChildAt(i);
            if (child instanceof GridRowView) {
                ((GridRowView) child).applyMetrics(metrics);
                ((GridRowView) child).requestFullLayout();
            }
        }
        if (adapter != null) {
            adapter.setMetricsQuietly(metrics);
        }
        if (!metrics.fixedBlockScrollable) {
            fixedSync.reset();
        }
        boundary.setCenterX(metrics.boundaryCenterX());
        body.requestLayout();
    }

    /**
     * Derives the row height from the height left over after the header row. Evaluated after
     * the layout pass, because the header row's height is only settled then.
     */
    private void updateRowHeights() {
        if (grid == null || !grid.isPaged() || adapter == null) {
            return;
        }
        final int available = body.getHeight();
        if (available <= 0 || available == lastBodyHeight) {
            return;
        }
        lastBodyHeight = available;
        final int[] heights = GridLayoutMath.distributeRowHeights(available, grid.getRowsPerPage());
        // Do not report from within the running layout pass.
        post(() -> {
            if (adapter != null) {
                adapter.setRowHeights(heights);
            }
        });
    }

    /**
     * Width of the number column: as narrow as possible, but wide enough for the highest number
     * that occurs. The reference is deliberately the whole data set, so that the column does
     * not jump about while paging.
     */
    private float measureNumberColumnWidth() {
        final String widest = String.valueOf(Math.max(1, grid.getRowCount()));
        return numberProbe.getPaint().measureText(widest) + numberProbe.getPaddingLeft() + numberProbe.getPaddingRight();
    }

    /** Layout manager whose vertical scrolling can be switched off while paging. */
    private static final class PagingLayoutManager extends LinearLayoutManager {

        private boolean scrollEnabled = true;

        PagingLayoutManager(final Context context) {
            super(context);
        }

        void setScrollEnabled(final boolean enabled) {
            this.scrollEnabled = enabled;
        }

        @Override
        public boolean canScrollVertically() {
            return scrollEnabled && super.canScrollVertically();
        }
    }
}
