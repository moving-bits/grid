package com.movingbits.grid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * One row of the grid – the header row as well as a data row.
 *
 * <p>Structure from left to right: the fixed number column, the block of fixed columns, the
 * horizontally scrolling block of the remaining columns. The scrolling block shares its
 * position through a {@link ScrollSync} with all other rows, so that the header row and the
 * data always line up.</p>
 */
final class GridRowView extends LinearLayout {

    /**
     * Reports a short tap on a header cell. Acting on it is the GridView's job, because that is
     * where sorting, page changes and the public hook come together.
     */
    interface HeaderTapListener {
        void onHeaderTapped(int columnIndex);
    }

    /**
     * Supplies the index of the record this row view currently shows.
     *
     * <p>What counts is the place the view actually holds in the list – not the value it
     * remembered when it was last bound. The two drift apart as soon as the list reuses or
     * reorders a view without binding it again; a tap would then report a record other than the
     * one that was touched.</p>
     */
    interface RowIndexProvider {
        int currentRowIndex();
    }

    /**
     * Reports a long tap on the number of a data row. Acting on it is the GridView's job: what
     * follows may change the data, and the display then has to be built anew.
     */
    interface NumberTapListener {
        void onNumberLongTapped(int rowIndex);
    }

    private final Grid grid;
    private final boolean header;
    private final HeaderTapListener headerTapListener;

    private final TextView numberCell;
    private final SyncedHorizontalScrollView fixedArea;
    private final LinearLayout fixedContent;
    private final SyncedHorizontalScrollView scrollArea;
    private final LinearLayout scrollContent;
    private final List<View> cells = new ArrayList<>();

    private final Paint dividerPaint = new Paint();
    private final int dividerHeight;

    /** Stroke width of the vertical lines: one screen pixel, regardless of the density. */
    private static final float COLUMN_DIVIDER_WIDTH_PX = 1f;
    private final Paint columnDividerPaint = new Paint();

    /** Arrows in the header row pointing at content outside the visible window. */
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintBackgroundPaint = new Paint();
    private final Path hintPath = new Path();
    private final float hintSize;
    private final float hintInset;

    /** Desired padding above and below a cell's content. */
    private final int cellPadding;

    private static final String[] NO_VALUES = new String[0];

    /** Background of the row; every other one takes the deviating tone on request. */
    private final int surfaceColor;
    private final int alternateColor;
    /** Background of the header cell of a write-protected column. */
    private final int readOnlyHeaderColor;
    /** Color for the cell text, or {@code null} to leave the text appearance's own alone. */
    private final Integer textColor;

    /** Index of the record bound last; -1 in the header row and before binding. */
    private int boundRowIndex = -1;
    /** The data row bound last; it also goes to the hooks for data cells. */
    private String[] boundRow = NO_VALUES;
    /** Supplies the index of the row this view actually stands at. */
    private RowIndexProvider rowIndexProvider;

    GridRowView(final Context context, final Grid grid, final boolean header,
                final ScrollSync fixedSync, final ScrollSync bodySync, final HeaderTapListener headerTapListener) {
        super(context);
        this.grid = grid;
        this.header = header;
        this.headerTapListener = headerTapListener;

        setOrientation(HORIZONTAL);
        setGravity(Gravity.TOP);
        setWillNotDraw(false);
        final GridColors colors = grid.getColors();
        surfaceColor = colors.surface(this);
        alternateColor = colors.alternateRow(this);
        readOnlyHeaderColor = colors.readOnlyHeader(this);
        textColor = colors.text();
        setBackgroundColor(surfaceColor);

        dividerHeight = Math.max(1, GridStyle.dp(context, GridStyle.DIVIDER_HEIGHT_DP));
        dividerPaint.setColor(colors.divider(this));
        columnDividerPaint.setColor(colors.columnDivider(this));

        hintPaint.setColor(colors.scrollHint(this));
        hintPaint.setStyle(Paint.Style.FILL);
        hintBackgroundPaint.setColor(surfaceColor);
        hintSize = GridStyle.dp(context, GridStyle.SCROLL_HINT_SIZE_DP);
        hintInset = GridStyle.dp(context, GridStyle.SCROLL_HINT_INSET_DP);
        cellPadding = GridStyle.dp(context, GridStyle.CELL_PADDING_VERTICAL_DP);

        // All areas and cells take up the full row height. Gravity keeps the content aligned to
        // the top, but the whole row area reacts to taps.
        numberCell = GridStyle.createTextCell(context, header);
        numberCell.setGravity(Gravity.TOP | Gravity.END);
        numberCell.setMaxLines(1);
        applyTextColor(numberCell);
        addView(numberCell, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        fixedContent = new LinearLayout(context);
        fixedContent.setOrientation(HORIZONTAL);
        fixedContent.setGravity(Gravity.TOP);

        // The fixed block only scrolls on its own once it had to be capped at the boundary
        // share.
        fixedArea = new SyncedHorizontalScrollView(context, fixedSync);
        fixedArea.setScrollingEnabled(false);
        fixedArea.addView(fixedContent, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT));
        addView(fixedArea, new LayoutParams(0, LayoutParams.MATCH_PARENT));

        scrollContent = new LinearLayout(context);
        scrollContent.setOrientation(HORIZONTAL);
        scrollContent.setGravity(Gravity.TOP);

        scrollArea = new SyncedHorizontalScrollView(context, bodySync);
        scrollArea.addView(scrollContent, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // The gap to the fixed area gives the boundary line room and serves as its grab area at
        // the same time.
        final LayoutParams scrollParams = new LayoutParams(0, LayoutParams.MATCH_PARENT);
        scrollParams.leftMargin = GridStyle.dp(context, GridStyle.BOUNDARY_GUTTER_DP);
        addView(scrollArea, scrollParams);

        createCells(context);
    }

    private void createCells(final Context context) {
        final int fixedCount = grid.getFixedColumnCount();
        for (int i = 0; i < grid.getColumnCount(); i++) {
            final GridColumn column = grid.getColumn(i);
            final LinearLayout parent = i < fixedCount ? fixedContent : scrollContent;
            final View cell = createCell(context, column);
            installHooks(cell, i);
            cells.add(cell);
            parent.addView(cell, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        }
    }

    /**
     * Applies the configured text color. Without one the text keeps the color of its text
     * appearance – which is what the theme intends.
     */
    private void applyTextColor(final TextView cell) {
        if (textColor != null) {
            cell.setTextColor(textColor);
        }
    }

    private View createCell(final Context context, final GridColumn column) {
        final TextView cell = GridStyle.createTextCell(context, header);
        cell.setGravity(GridStyle.gravityOf(column.getAlignment()));
        applyTextColor(cell);
        if (header) {
            cell.setText(column.getTitle());
            // Write-protected columns are recognizable by their tinted header cell. The data
            // cells stay untouched: the hint belongs to the column, not to every single cell.
            if (column.isReadOnly()) {
                cell.setBackgroundColor(readOnlyHeaderColor);
            }
        }
        return cell;
    }

    /**
     * Attaches the configured hooks to a cell. Data cells only reach for the record bound last
     * when they are actually tapped, because the same row view is reused for different records
     * while scrolling.
     */
    private void installHooks(final View cell, final int columnIndex) {
        if (header) {
            // A short tap always goes to the GridView: it can sort, fire the hook, or both.
            final boolean reacts = headerTapListener != null && (grid.canSortBy(columnIndex) || grid.getHeaderClickListener() != null);
            final OnHeaderClickListener longClick = grid.getHeaderLongClickListener();
            if (reacts) {
                cell.setOnClickListener(v -> headerTapListener.onHeaderTapped(columnIndex));
            }
            if (longClick != null) {
                cell.setOnLongClickListener(v -> {
                    longClick.onHeaderClick(columnIndex);
                    return true;
                });
            }
            if (reacts || longClick != null) {
                GridStyle.applyTouchFeedback(cell);
            }
            return;
        }

        final OnCellClickListener click = grid.getCellClickListener();
        final OnCellClickListener longClick = grid.getCellLongClickListener();
        if (click != null) {
            cell.setOnClickListener(v -> notifyCellHook(click, columnIndex));
        }
        if (longClick != null) {
            cell.setOnLongClickListener(v -> {
                notifyCellHook(longClick, columnIndex);
                return true;
            });
        }
        if (click != null || longClick != null) {
            GridStyle.applyTouchFeedback(cell);
        }
    }

    /**
     * Looks for the column whose right edge lies close to {@code x} – the edge at which its
     * width can be dragged.
     *
     * @param x         a position in the row's coordinates
     * @param tolerance largest permitted distance from the edge
     * @return 0-based index of the column in the display, or -1 when no edge lies there
     */
    int columnAtEdge(final float x, final float tolerance) {
        int nearest = -1;
        float shortest = tolerance;
        for (int i = 0; i < cells.size(); i++) {
            final float edge = columnEdgeX(i);
            if (Float.isNaN(edge)) {
                continue;
            }
            final float distance = Math.abs(edge - x);
            if (distance <= shortest) {
                shortest = distance;
                nearest = i;
            }
        }
        return nearest;
    }

    /**
     * Horizontal position of a column's right edge, in the row's coordinates.
     *
     * <p>Edges outside their own area do not count: what has been scrolled away is not visible
     * and should not be grabbable either.</p>
     *
     * @return the position, or {@link Float#NaN} when the edge is not visible
     */
    float columnEdgeX(final int columnIndex) {
        if (columnIndex < 0 || columnIndex >= cells.size()) {
            return Float.NaN;
        }
        final View area = columnIndex < grid.getFixedColumnCount() ? fixedArea : scrollArea;
        final float edge = area.getLeft() - area.getScrollX() + cells.get(columnIndex).getRight();
        return edge < area.getLeft() || edge > area.getRight() ? Float.NaN : edge;
    }

    /** Sets the source for the row index; without one the value bound last applies. */
    void setRowIndexProvider(final RowIndexProvider provider) {
        this.rowIndexProvider = provider;
    }

    /**
     * Hooks a long tap on the number of this row, which otherwise reacts to nothing. Only the
     * data rows have a number to tap.
     */
    void setNumberTapListener(final NumberTapListener listener) {
        if (header || listener == null) {
            return;
        }
        numberCell.setOnLongClickListener(view -> {
            final int rowIndex = currentRowIndex();
            if (rowIndex >= 0) {
                listener.onNumberLongTapped(rowIndex);
            }
            return true;
        });
        GridStyle.applyTouchFeedback(numberCell);
    }

    /** The index of the record on display, or -1 when the row stands at no record. */
    private int currentRowIndex() {
        final int rowIndex = rowIndexProvider != null ? rowIndexProvider.currentRowIndex() : boundRowIndex;
        return rowIndex >= 0 && rowIndex < grid.getRowCount() ? rowIndex : -1;
    }

    private void notifyCellHook(final OnCellClickListener listener, final int columnIndex) {
        final int rowIndex = currentRowIndex();
        if (rowIndex < 0 || columnIndex < 0 || columnIndex >= grid.getColumnCount()) {
            return;
        }
        final GridColumn column = grid.getColumn(columnIndex);
        listener.onCellClick(new CellRef(rowIndex, columnIndex, column.getDataIndex(), column.isReadOnly()), boundRow);
    }

    /** Takes over the computed widths for the number column, the blocks and the cells. */
    void applyMetrics(final GridMetrics metrics) {
        setLayoutParamsWidth(this, Math.round(metrics.viewportWidth));
        setLayoutParamsWidth(numberCell, Math.round(metrics.numberColumnWidth));
        setLayoutParamsWidth(fixedContent, Math.round(metrics.fixedContentWidth));
        setLayoutParamsWidth(fixedArea, Math.round(metrics.fixedBlockWidth));
        fixedArea.setScrollingEnabled(metrics.fixedBlockScrollable);
        setLayoutParamsWidth(scrollArea, Math.round(metrics.scrollViewportWidth()));

        for (int i = 0; i < cells.size() && i < metrics.columnWidths.length; i++) {
            setLayoutParamsWidth(cells.get(i), Math.round(metrics.columnWidths[i]));
        }
    }

    /**
     * Asks for the row and its areas to be measured again.
     *
     * <p>{@link #applyMetrics(GridMetrics)} only changes layout parameters. If nothing but a
     * cell's width changes in the process, the parameters of the areas above it stay the same –
     * and their measuring pass is skipped, together with the cells inside them. A
     * {@code requestLayout()} on the row alone is not enough either: it marks the row, not its
     * children.</p>
     *
     * <p>Do not call this from within a running measuring or layout pass; the request would be
     * discarded there.</p>
     */
    void requestFullLayout() {
        fixedContent.requestLayout();
        scrollContent.requestLayout();
        requestLayout();
    }

    /**
     * Sets the row height when paging. Text cells are given the number of lines that fit into
     * that height; text beyond it ends in an ellipsis.
     *
     * @param heightPx fixed row height, or {@link LayoutParams#WRAP_CONTENT} without paging
     */
    void applyRowHeight(final int heightPx) {
        setLayoutParamsHeight(this, heightPx);

        applyCellHeight(numberCell, heightPx);
        numberCell.setMaxLines(1);

        for (int i = 0; i < cells.size(); i++) {
            final View cell = cells.get(i);
            if (cell instanceof TextView) {
                applyCellHeight((TextView) cell, heightPx);
            }
        }
    }

    /**
     * Fits a text cell's padding and number of lines to the row height. If the height is not
     * enough for padding and text, the padding gives way first – that keeps at least one line
     * of text fully readable.
     */
    private void applyCellHeight(final TextView text, final int heightPx) {
        final int padding = heightPx > 0 ? GridLayoutMath.verticalPadding(heightPx, text.getLineHeight(), cellPadding) : cellPadding;
        text.setPadding(text.getPaddingLeft(), padding, text.getPaddingRight(), padding);
        text.setMaxLines(heightPx > 0 ? GridLayoutMath.maxLines(heightPx, 2 * padding, text.getLineHeight()) : Integer.MAX_VALUE);
    }

    // Both setters only change the LayoutParams and deliberately trigger no requestLayout: they
    // are always called before the affected view is measured (from GridView.onMeasure, or when
    // a row is bound). A requestLayout issued from within a running layout pass would be
    // discarded.

    private static void setLayoutParamsHeight(final View view, final int height) {
        final ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) {
            view.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, height));
        } else {
            params.height = height;
        }
    }

    private static void setLayoutParamsWidth(final View view, final int width) {
        final ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) {
            view.setLayoutParams(new LayoutParams(width, LayoutParams.WRAP_CONTENT));
        } else {
            params.width = width;
        }
    }

    /**
     * Fills the row with one data row.
     *
     * @param row      field contents in data order; every column picks its field through
     *                 {@link GridColumn#getDataIndex()}
     * @param rowIndex 0-based index within the whole data set; {@code rowIndex + 1} is shown
     */
    void bind(final String[] row, final int rowIndex) {
        boundRowIndex = rowIndex;
        boundRow = row == null ? NO_VALUES : row;
        // Set on every bind: the same view stands for a different row once the user pages on.
        if (grid.hasAlternatingRowColors()) {
            setBackgroundColor(rowIndex % 2 == 1 ? alternateColor : surfaceColor);
        }
        numberCell.setText(String.valueOf(rowIndex + 1));
        for (int i = 0; i < cells.size(); i++) {
            final View cell = cells.get(i);
            if (cell instanceof TextView) {
                ((TextView) cell).setText(valueOf(grid.getColumn(i)));
            }
        }
    }

    /** A column's field out of the bound row; missing fields count as empty. */
    private String valueOf(final GridColumn column) {
        final int index = column.getDataIndex();
        if (index < 0 || index >= boundRow.length || boundRow[index] == null) {
            return "";
        }
        return boundRow[index];
    }

    /** Resets the header row; the number column stays empty in the header row. */
    void bindHeader() {
        numberCell.setText("");
        for (int i = 0; i < cells.size(); i++) {
            final View cell = cells.get(i);
            if (cell instanceof TextView) {
                ((TextView) cell).setText(headerTitle(i));
            }
        }
    }

    /**
     * A column's title, extended by the direction while sorting is in effect and – as soon as
     * several columns are sorted by – by its rank within the order.
     */
    private CharSequence headerTitle(final int columnIndex) {
        final String title = grid.getColumn(columnIndex).getTitle();
        final SortDirection direction = grid.getSortDirection(columnIndex);
        if (direction == null) {
            return title;
        }

        final StringBuilder marker = new StringBuilder();
        marker.append(direction == SortDirection.ASCENDING ? '▲' : '▼');
        if (grid.getSortCriteriaCount() > 1) {
            marker.append(grid.getSortRank(columnIndex));
        }

        final SpannableStringBuilder text = new SpannableStringBuilder(title);
        text.append(' ').append(marker);
        text.setSpan(new RelativeSizeSpan(0.7f), title.length(), text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    @Override
    protected void dispatchDraw(final @NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        drawColumnDividers(canvas);
        if (header) {
            drawScrollHints(canvas, fixedArea, fixedContent);
            drawScrollHints(canvas, scrollArea, scrollContent);
        }
        canvas.drawRect(0, getHeight() - dividerHeight, getWidth(), getHeight(), dividerPaint);
    }

    /**
     * Draws a fine vertical line at the right edge of every column. It separates the contents
     * of neighbouring columns and at the same time shows where the width can be grabbed.
     *
     * <p>It is drawn in the row rather than in the cells: that way the line runs across the
     * full row height without being interrupted by the cells' padding.</p>
     */
    private void drawColumnDividers(final Canvas canvas) {
        final float bottom = getHeight() - dividerHeight;
        // The number column is no cell of the grid, but it is a column like the others and is
        // set apart from what follows in the same way.
        if (!cells.isEmpty()) {
            final float edge = numberCell.getRight();
            canvas.drawRect(edge - COLUMN_DIVIDER_WIDTH_PX, 0f, edge, bottom, columnDividerPaint);
        }
        for (int i = 0; i < cells.size(); i++) {
            final float edge = columnEdgeX(i);
            if (!Float.isNaN(edge)) {
                canvas.drawRect(edge - COLUMN_DIVIDER_WIDTH_PX, 0f, edge, bottom, columnDividerPaint);
            }
        }
    }

    /**
     * {@code true} when content is hidden right at the boundary between the fixed and the
     * freely scrolling area – that is, at the end of the fixed columns or at the start of the
     * free ones. Exactly then the header row shows one or two arrows there.
     */
    boolean hasHintsAtBoundary() {
        return fixedArea.canScrollHorizontally(1) || scrollArea.canScrollHorizontally(-1);
    }

    /**
     * Draws one arrow per scrolling area when there are still columns outside the visible
     * window to its left or right. The arrows sit as an overlay above the header row and
     * therefore do not scroll along with the content.
     */
    private void drawScrollHints(final Canvas canvas, final View area, final LinearLayout content) {
        if (area.getWidth() == 0) {
            return;
        }
        if (area.canScrollHorizontally(-1)) {
            drawArrow(canvas, area.getLeft() + hintInset, true, area, content);
        }
        if (area.canScrollHorizontally(1)) {
            drawArrow(canvas, area.getRight() - hintInset, false, area, content);
        }
    }

    /**
     * Background color at this position of the row. The strip beneath an arrow takes it over
     * instead of always setting colorSurface – otherwise it would tear a bright hole into the
     * tinted header cell of a write-protected column.
     *
     * @param xInRow a position in the row's coordinates
     */
    private int backgroundAt(final View area, final LinearLayout content, final float xInRow) {
        final float xInContent = xInRow - area.getLeft() + area.getScrollX();
        for (int i = 0; i < content.getChildCount(); i++) {
            final View cell = content.getChildAt(i);
            if (xInContent >= cell.getLeft() && xInContent < cell.getRight()) {
                return cell.getBackground() instanceof ColorDrawable tinted
                        ? tinted.getColor()
                        : surfaceColor;
            }
        }
        return surfaceColor;
    }

    private void drawArrow(final Canvas canvas, final float tipX, final boolean pointsLeft, final View area, final LinearLayout content) {
        final float centerY = getHeight() / 2f;
        final float baseX = pointsLeft ? tipX + hintSize : tipX - hintSize;

        // A narrow opaque strip: the arrow should not overlay the title but replace it at this spot.
        final float stripWidth = hintSize + 2 * hintInset;
        final float stripLeft = pointsLeft ? tipX - hintInset : tipX + hintInset - stripWidth;
        hintBackgroundPaint.setColor(backgroundAt(area, content, stripLeft + stripWidth / 2f));
        canvas.drawRect(stripLeft, 0, stripLeft + stripWidth, getHeight() - dividerHeight, hintBackgroundPaint);

        hintPath.reset();
        hintPath.moveTo(tipX, centerY);
        hintPath.lineTo(baseX, centerY - hintSize);
        hintPath.lineTo(baseX, centerY + hintSize);
        hintPath.close();
        canvas.drawPath(hintPath, hintPaint);
    }

}
