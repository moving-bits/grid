package com.movingbits.grid;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of the width calculation for one concrete view width. Recomputed on every size change
 * and shared by the header row and the data rows.
 */
final class GridMetrics {

    /** Width of each column in pixels, without the number column. */
    final float[] columnWidths;
    /** Width of the number column in pixels. */
    final float numberColumnWidth;
    /** Width the fixed block actually occupies; may be capped against its content. */
    final float fixedBlockWidth;
    /** Sum of the widths of the fixed columns before capping. */
    final float fixedContentWidth;
    /** {@code true} when the fixed block was capped and has to scroll on its own. */
    final boolean fixedBlockScrollable;
    /** Nominal total width of the grid (either given or the width available). */
    final float totalWidth;
    /** Width actually occupied: never more than the width available. */
    final float viewportWidth;
    /** Gap between the fixed and the freely scrolling area. */
    final float gutterWidth;

    private GridMetrics(final float[] columnWidths, final float numberColumnWidth, final float fixedBlockWidth,
                        final float fixedContentWidth, final boolean fixedBlockScrollable,
                        final float totalWidth, final float viewportWidth, final float gutterWidth) {
        this.gutterWidth = gutterWidth;
        this.columnWidths = columnWidths;
        this.numberColumnWidth = numberColumnWidth;
        this.fixedBlockWidth = fixedBlockWidth;
        this.fixedContentWidth = fixedContentWidth;
        this.fixedBlockScrollable = fixedBlockScrollable;
        this.totalWidth = totalWidth;
        this.viewportWidth = viewportWidth;
    }

    /**
     * Computes the widths for one concrete view width.
     *
     * @param grid             the configuration
     * @param availableWidthPx width available to the view, in pixels
     * @param density          conversion factor dp -> px
     * @param numberColumnPx   measured width of the number column, in pixels
     * @param minSharedPx      minimum width of the columns without a width of their own
     * @param boundaryFraction share of the total width the fixed block occupies at most; may
     *                         have been moved by the user
     * @param gutterPx         gap between the fixed and the scrolling area
     */
    static GridMetrics compute(final Grid grid, final float availableWidthPx, final float density,
                               final float numberColumnPx, final float minSharedPx, final float boundaryFraction,
                               final float gutterPx) {
        final float totalWidth = grid.hasTotalWidth() ? grid.getTotalWidthDp() * density : availableWidthPx;
        final float viewportWidth = Math.min(totalWidth, availableWidthPx);

        final List<ColumnWidth> widths = new ArrayList<>(grid.getColumnCount());
        for (int i = 0; i < grid.getColumnCount(); i++) {
            widths.add(grid.getColumn(i).getWidth());
        }

        final float[] columnWidths = GridLayoutMath.resolveColumnWidths(totalWidth, numberColumnPx, widths, density, minSharedPx);

        final float fraction = GridLayoutMath.clampBoundaryFraction(boundaryFraction);
        final float fixedContent = GridLayoutMath.sumOfFirst(columnWidths, grid.getFixedColumnCount());
        final boolean scrollable = GridLayoutMath.fixedBlockNeedsScrolling(fixedContent, totalWidth, fraction);
        final float fixedBlock = GridLayoutMath.cappedFixedBlockWidth(fixedContent, totalWidth, fraction);

        return new GridMetrics(columnWidths, numberColumnPx, fixedBlock, fixedContent, scrollable, totalWidth, viewportWidth, gutterPx);
    }

    /** Width of the horizontally scrolling area (the window, not its content). */
    float scrollViewportWidth() {
        return Math.max(0f, viewportWidth - numberColumnWidth - fixedBlockWidth - gutterWidth);
    }

    /** Left edge of the gap, that is, the end of the fixed area. */
    float boundaryStartX() {
        return numberColumnWidth + fixedBlockWidth;
    }

    /** Center of the gap – where the boundary line sits. */
    float boundaryCenterX() {
        return boundaryStartX() + gutterWidth / 2f;
    }
}
