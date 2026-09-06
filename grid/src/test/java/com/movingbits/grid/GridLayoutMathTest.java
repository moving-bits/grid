package com.movingbits.grid;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GridLayoutMathTest {

    private static final float EPS = 0.001f;

    @Test
    public void theRemainingWidthIsSplitEvenly() {
        List<ColumnWidth> widths = Arrays.asList(
                ColumnWidth.remaining(), ColumnWidth.remaining(), ColumnWidth.remaining());

        float[] result = GridLayoutMath.resolveColumnWidths(1000f, 100f, widths, 1f, 10f);

        assertArrayEquals(new float[]{300f, 300f, 300f}, result, EPS);
    }

    @Test
    public void fixedAndPercentageWidthsAreHandedOutFirst() {
        List<ColumnWidth> widths = Arrays.asList(
                ColumnWidth.percent(0.25f),  // 25 % of 1000 = 250
                ColumnWidth.dp(100f),        // 100 dp at density 2 = 200
                ColumnWidth.remaining());    // what is left of 900 - 450 = 450

        float[] result = GridLayoutMath.resolveColumnWidths(1000f, 100f, widths, 2f, 10f);

        assertArrayEquals(new float[]{250f, 200f, 450f}, result, EPS);
    }

    @Test
    public void theMinimumWidthIsHonoured() {
        List<ColumnWidth> widths = Arrays.asList(
                ColumnWidth.dp(200f), ColumnWidth.remaining(), ColumnWidth.remaining());

        float[] result = GridLayoutMath.resolveColumnWidths(300f, 50f, widths, 1f, 48f);

        assertEquals(200f, result[0], EPS);
        assertEquals(48f, result[1], EPS);
        assertEquals(48f, result[2], EPS);
    }

    @Test
    public void anExplicitlyNarrowWidthIsKept() {
        // The minimum width of split columns (48) does not raise an explicit value; only
        // below ColumnWidth.MIN_WIDTH_DP is out of reach.
        List<ColumnWidth> widths = Arrays.asList(
                ColumnWidth.dp(25f), ColumnWidth.dp(5f), ColumnWidth.remaining());

        float[] result = GridLayoutMath.resolveColumnWidths(1000f, 100f, widths, 1f, 48f);

        assertEquals(25f, result[0], EPS);
        assertEquals(ColumnWidth.MIN_WIDTH_DP, result[1], EPS);
        assertEquals(900f - 25f - ColumnWidth.MIN_WIDTH_DP, result[2], EPS);
    }

    @Test
    public void withoutColumnsTheResultIsEmpty() {
        float[] result = GridLayoutMath.resolveColumnWidths(
                1000f, 100f, Collections.<ColumnWidth>emptyList(), 1f, 48f);

        assertEquals(0, result.length);
    }

    @Test
    public void theFixedBlockIsCappedWhenItGoesOverTheShare() {
        float[] widths = {300f, 400f, 200f};
        float content = GridLayoutMath.sumOfFirst(widths, 2); // 700 out of 1000 = 70 %

        assertEquals(700f, content, EPS);
        assertTrue(GridLayoutMath.fixedBlockNeedsScrolling(content, 1000f, 0.6f));
        assertEquals(600f, GridLayoutMath.cappedFixedBlockWidth(content, 1000f, 0.6f), EPS);
    }

    @Test
    public void aFixedBlockBelowTheShareStaysUnchanged() {
        float[] widths = {200f, 300f, 200f};
        float content = GridLayoutMath.sumOfFirst(widths, 2); // 500 out of 1000 = 50 %

        assertFalse(GridLayoutMath.fixedBlockNeedsScrolling(content, 1000f, 0.6f));
        assertEquals(500f, GridLayoutMath.cappedFixedBlockWidth(content, 1000f, 0.6f), EPS);
    }

    @Test
    public void theRowHeightsAddUpToTheAvailableHeightExactly() {
        int[] heights = GridLayoutMath.distributeRowHeights(1004, 10);

        int sum = 0;
        for (int height : heights) {
            sum += height;
        }
        assertEquals(1004, sum);
        assertEquals(101, heights[0]);
        assertEquals(101, heights[3]);
        assertEquals(100, heights[4]);
        assertEquals(100, heights[9]);
    }

    @Test
    public void thePaddingGivesWayBeforeTheLineOfTextIsCutOff() {
        // Enough room: the preferred padding stays.
        assertEquals(22, GridLayoutMath.verticalPadding(120, 54, 22));
        // Tight: the padding shrinks and the line of text fits in full.
        assertEquals(13, GridLayoutMath.verticalPadding(80, 54, 22));
        // Exactly one line tall: no padding left.
        assertEquals(0, GridLayoutMath.verticalPadding(54, 54, 22));
        // Too little for one line: giving up the padding is all that can be done.
        assertEquals(0, GridLayoutMath.verticalPadding(40, 54, 22));
        // Without a fixed row height the preferred padding stands.
        assertEquals(22, GridLayoutMath.verticalPadding(0, 54, 22));
    }

    @Test
    public void atLeastOneLineStaysVisible() {
        int lineHeight = 54;
        for (int rowHeight = 55; rowHeight <= 200; rowHeight++) {
            int padding = GridLayoutMath.verticalPadding(rowHeight, lineHeight, 22);
            assertTrue("row height " + rowHeight,
                    2 * padding + lineHeight <= rowHeight);
            assertTrue("row height " + rowHeight,
                    GridLayoutMath.maxLines(rowHeight, 2 * padding, lineHeight) >= 1);
        }
    }

    @Test
    public void maxLinesFromRowHeightAndLineHeight() {
        assertEquals(3, GridLayoutMath.maxLines(100, 16, 28));
        assertEquals(1, GridLayoutMath.maxLines(20, 16, 28));
        assertEquals(1, GridLayoutMath.maxLines(100, 16, 0));
    }

    @Test
    public void pageCountAndPageBoundaries() {
        assertEquals(1, GridLayoutMath.pageCount(0, 10));
        assertEquals(1, GridLayoutMath.pageCount(10, 10));
        assertEquals(2, GridLayoutMath.pageCount(11, 10));
        assertEquals(1, GridLayoutMath.pageCount(1234, 0));

        assertEquals(20, GridLayoutMath.firstRowOfPage(2, 10));
        assertEquals(10, GridLayoutMath.rowsOnPage(25, 1, 10));
        assertEquals(5, GridLayoutMath.rowsOnPage(25, 2, 10));
        assertEquals(0, GridLayoutMath.rowsOnPage(25, 3, 10));
    }
}
