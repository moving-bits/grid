package com.movingbits.grid;

import java.util.List;

/**
 * Pure arithmetic for the grid layout. Deliberately free of Android dependencies so that it
 * can be covered by JVM unit tests. All lengths are pixel values.
 */
final class GridLayoutMath {

    private GridLayoutMath() {
    }

    /**
     * Distributes the available width across the columns.
     *
     * <p>Fixed and percentage values are handed out first, the width left over is split evenly
     * across the columns without a value of their own. If the sum exceeds the available width,
     * horizontal scrolling becomes necessary – that is intended and is not corrected here.</p>
     *
     * <p>Two lower bounds apply: an explicitly stated width never falls below
     * {@link ColumnWidth#MIN_WIDTH_DP}, and columns without a value are never split below
     * {@code minSharedPx}. Whoever explicitly wants a narrow column gets one; whoever states
     * nothing gets a readable width.</p>
     *
     * @param totalPx        total width of the grid including the number column
     * @param numberColumnPx width of the number column
     * @param widths         width of each column, in column order
     * @param density        conversion factor dp -> px
     * @param minSharedPx    minimum width of the columns without a width of their own
     * @return width of each column in pixels, in column order
     */
    static float[] resolveColumnWidths(final float totalPx, final float numberColumnPx, final List<ColumnWidth> widths, final float density, final float minSharedPx) {
        final int count = widths.size();
        final float[] result = new float[count];
        if (count == 0) {
            return result;
        }

        final float available = Math.max(0f, totalPx - numberColumnPx);
        final float minPx = ColumnWidth.MIN_WIDTH_DP * density;
        float used = 0f;
        int remainingCount = 0;

        for (int i = 0; i < count; i++) {
            final ColumnWidth width = widths.get(i);
            switch (width.getType()) {
                case FIXED:
                    result[i] = Math.max(minPx, width.getValue() * density);
                    used += result[i];
                    break;
                case PERCENT:
                    result[i] = Math.max(minPx, width.getValue() * totalPx);
                    used += result[i];
                    break;
                default:
                    result[i] = -1f;
                    remainingCount++;
                    break;
            }
        }

        if (remainingCount > 0) {
            final float rest = Math.max(0f, available - used);
            final float each = Math.max(minSharedPx, rest / remainingCount);
            for (int i = 0; i < count; i++) {
                if (result[i] < 0f) {
                    result[i] = each;
                }
            }
        }
        return result;
    }

    /**
     * Sum of the widths of the first {@code fixedCount} columns.
     */
    static float sumOfFirst(final float[] widths, final int fixedCount) {
        float sum = 0f;
        final int limit = Math.min(fixedCount, widths.length);
        for (int i = 0; i < limit; i++) {
            sum += widths[i];
        }
        return sum;
    }

    /**
     * Width the block of fixed columns actually occupies.
     *
     * <p>If it takes up more than {@code maxFraction} of the total width, it is capped at that
     * share and has to scroll horizontally within it.</p>
     *
     * @param contentPx   sum of the widths of the fixed columns
     * @param totalPx     total width of the grid including the number column
     * @param maxFraction largest permitted share, e.g. 0.6f
     */
    static float cappedFixedBlockWidth(final float contentPx, final float totalPx, final float maxFraction) {
        final float limit = totalPx * maxFraction;
        return Math.min(contentPx, limit);
    }

    /** {@code true} when the fixed block was capped and therefore has to be scrollable. */
    static boolean fixedBlockNeedsScrolling(final float contentPx, final float totalPx, final float maxFraction) {
        return contentPx > totalPx * maxFraction;
    }

    /**
     * Clamps a boundary share to the permitted range.
     */
    static float clampBoundaryFraction(final float fraction) {
        if (fraction < Grid.MIN_FIXED_BOUNDARY_FRACTION) {
            return Grid.MIN_FIXED_BOUNDARY_FRACTION;
        }
        return Math.min(fraction, Grid.MAX_FIXED_BOUNDARY_FRACTION);
    }

    /**
     * Turns a dragged position of the boundary line into a boundary share.
     *
     * <p>The number column sits to the left of the fixed block and therefore does not count
     * towards the share.</p>
     *
     * @param boundaryX      position of the line in pixels, measured from the grid's left edge
     * @param numberColumnPx width of the number column
     * @param totalPx        total width of the grid
     * @return a share within the permitted range
     */
    static float boundaryFractionFor(final float boundaryX, final float numberColumnPx, final float totalPx) {
        if (totalPx <= 0f) {
            return Grid.DEFAULT_FIXED_BOUNDARY_FRACTION;
        }
        return clampBoundaryFraction((boundaryX - numberColumnPx) / totalPx);
    }

    /**
     * Splits the available height evenly across the rows of a page. The rounding remainder is
     * handed out pixel by pixel to the first rows, so that the sum matches the available
     * height exactly.
     *
     * @param availablePx available height minus the header row
     * @param rowCount    rows per page, {@code > 0}
     * @return height of each row in pixels
     */
    static int[] distributeRowHeights(final int availablePx, final int rowCount) {
        if (rowCount <= 0) {
            return new int[0];
        }
        final int[] heights = new int[rowCount];
        final int base = Math.max(0, availablePx) / rowCount;
        final int remainder = Math.max(0, availablePx) % rowCount;
        for (int i = 0; i < rowCount; i++) {
            heights[i] = base + (i < remainder ? 1 : 0);
        }
        return heights;
    }

    /**
     * Padding above and below a cell's content at a fixed row height.
     *
     * <p>If enough room is left after one line of text, the preferred padding is used.
     * Otherwise it shrinks far enough for at least one line of text to stay fully visible – in
     * landscape with many rows per page that is the deciding case.</p>
     *
     * @param rowHeightPx        fixed height of the data row
     * @param lineHeightPx       height of one line of text
     * @param preferredPaddingPx desired padding per side
     * @return padding per side, between 0 and {@code preferredPaddingPx}
     */
    static int verticalPadding(final int rowHeightPx, final int lineHeightPx, final int preferredPaddingPx) {
        if (rowHeightPx <= 0) {
            return preferredPaddingPx;
        }
        final int rest = rowHeightPx - lineHeightPx;
        return rest <= 0 ? 0 : Math.min(preferredPaddingPx, rest / 2);
    }

    /**
     * Number of text lines that fit entirely into a fixed row height.
     *
     * @param rowHeightPx       height of the data row
     * @param verticalPaddingPx sum of the cell's top and bottom padding
     * @param lineHeightPx      height of one line of text
     * @return number of lines, at least 1
     */
    static int maxLines(final int rowHeightPx, final int verticalPaddingPx, final int lineHeightPx) {
        if (lineHeightPx <= 0) {
            return 1;
        }
        final int usable = rowHeightPx - verticalPaddingPx;
        return Math.max(1, usable / lineHeightPx);
    }

    /**
     * Number of pages.
     *
     * @param rowCount    number of records
     * @param rowsPerPage rows per page, {@code 0} when paging is switched off
     * @return at least 1
     */
    static int pageCount(final int rowCount, final int rowsPerPage) {
        return rowsPerPage <= 0 ? 1 : Math.max(1, (rowCount + rowsPerPage - 1) / rowsPerPage);
    }

    /** Index of a page's first record within the whole data set. */
    static int firstRowOfPage(final int page, final int rowsPerPage) {
        return rowsPerPage <= 0 ? 0 : Math.max(0, page) * rowsPerPage;
    }

    /** Number of records actually present on a page (the last page may be shorter). */
    static int rowsOnPage(final int rowCount, final int page, final int rowsPerPage) {
        if (rowsPerPage <= 0) {
            return rowCount;
        }
        final int first = firstRowOfPage(page, rowsPerPage);
        if (first >= rowCount) {
            return 0;
        }
        return Math.min(rowsPerPage, rowCount - first);
    }
}
