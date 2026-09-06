package com.movingbits.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


/**
 * Checks the interplay of a given width, the number column and the boundary share. All values
 * in pixels; the conversion factor dp to px is 2.
 */
public class GridMetricsTest {

    private static final float EPS = 0.001f;
    private static final float DENSITY = 2f;
    private static final float NUMBER_COLUMN = 50f;
    private static final float MIN_COLUMN = 20f;
    /** Gap between the fixed and the scrolling area. */
    private static final float GUTTER = 20f;


    /** Grid with {@code count} columns of 100 dp each. */
    private static Grid gridWithColumns(int count) {
        Grid grid = new Grid();
        for (int i = 0; i < count; i++) {
            grid.column("Column " + i, CellAlignment.START, ColumnWidth.dp(100f));
        }
        return grid;
    }

    private static GridMetrics metrics(Grid grid, float availableWidth) {
        return metrics(grid, availableWidth, grid.getFixedBoundaryFraction());
    }

    private static GridMetrics metrics(Grid grid, float availableWidth,
                                       float boundaryFraction) {
        return GridMetrics.compute(grid, availableWidth, DENSITY, NUMBER_COLUMN, MIN_COLUMN,
                boundaryFraction, GUTTER);
    }

    @Test
    public void withoutAGivenWidthTheAvailableWidthDecides() {
        GridMetrics m = metrics(gridWithColumns(4).fixedColumns(2), 1000f);

        assertEquals(1000f, m.totalWidth, EPS);
        assertEquals(1000f, m.viewportWidth, EPS);
        assertEquals(200f, m.columnWidths[0], EPS);
        assertEquals(400f, m.fixedContentWidth, EPS);
        assertEquals(400f, m.fixedBlockWidth, EPS);
        assertFalse(m.fixedBlockScrollable);
        // 1000 - 50 number column - 400 fixed - 20 gap
        assertEquals(530f, m.scrollViewportWidth(), EPS);
        // The boundary line sits in the middle of the gap.
        assertEquals(450f, m.boundaryStartX(), EPS);
        assertEquals(460f, m.boundaryCenterX(), EPS);
    }

    @Test
    public void aFixedBlockOverTheShareIsCappedAndScrollable() {
        GridMetrics m = metrics(gridWithColumns(4).fixedColumns(4), 1000f);

        assertEquals(800f, m.fixedContentWidth, EPS);
        assertEquals(400f, m.fixedBlockWidth, EPS);
        assertTrue(m.fixedBlockScrollable);
        assertEquals(530f, m.scrollViewportWidth(), EPS);
    }

    @Test
    public void exactlySixtyPercentStayUncapped() {
        GridMetrics m = metrics(gridWithColumns(4).fixedColumns(3), 1000f);

        assertEquals(600f, m.fixedContentWidth, EPS);
        assertEquals(400f, m.fixedBlockWidth, EPS);
        assertTrue(m.fixedBlockScrollable);
    }

    @Test
    public void aGivenWidthSmallerThanTheScreenLimitsTheWidthOccupied() {
        Grid grid = gridWithColumns(4).fixedColumns(1).totalWidth(300f); // 600 px

        GridMetrics m = metrics(grid, 1000f);

        assertEquals(600f, m.totalWidth, EPS);
        assertEquals(600f, m.viewportWidth, EPS);
        assertEquals(330f, m.scrollViewportWidth(), EPS);
    }

    @Test
    public void aGivenWidthLargerThanTheScreenScrollsHorizontally() {
        Grid grid = new Grid()
                .column("Half", CellAlignment.START, ColumnWidth.percent(0.5f))
                .column("Rest")
                .totalWidth(600f); // 1200 px

        GridMetrics m = metrics(grid, 1000f);

        assertEquals(1200f, m.totalWidth, EPS);
        // At most the width actually available is occupied.
        assertEquals(1000f, m.viewportWidth, EPS);
        // Percentages refer to the total width that was given.
        assertEquals(600f, m.columnWidths[0], EPS);
        assertEquals(550f, m.columnWidths[1], EPS);
    }

    @Test
    public void aMovedBoundaryChangesTheCapping() {
        Grid grid = gridWithColumns(4).fixedColumns(4); // 800 px of content

        // Moved to the left: capped more strictly.
        GridMetrics narrow = metrics(grid, 1000f, 0.3f);
        assertEquals(300f, narrow.fixedBlockWidth, EPS);
        assertTrue(narrow.fixedBlockScrollable);

        // Moved to the right: more fixed area, but never more than its content.
        GridMetrics wide = metrics(grid, 1000f, 0.6f);
        assertEquals(600f, wide.fixedBlockWidth, EPS);
        assertTrue(wide.fixedBlockScrollable);
    }

    @Test
    public void invalidBoundarySharesAreClamped() {
        Grid grid = gridWithColumns(4).fixedColumns(4);

        assertEquals(150f, metrics(grid, 1000f, 0.01f).fixedBlockWidth, EPS);
        assertEquals(600f, metrics(grid, 1000f, 1.5f).fixedBlockWidth, EPS);
    }

    @Test
    public void aDraggedPositionIsTurnedIntoABoundaryShare() {
        // Line at 350 px, number column 50 px, total width 1000 px -> 30 percent.
        assertEquals(0.3f, GridLayoutMath.boundaryFractionFor(350f, 50f, 1000f), EPS);
        // Outside the permitted range it is clamped.
        assertEquals(Grid.MIN_FIXED_BOUNDARY_FRACTION,
                GridLayoutMath.boundaryFractionFor(60f, 50f, 1000f), EPS);
        assertEquals(Grid.MAX_FIXED_BOUNDARY_FRACTION,
                GridLayoutMath.boundaryFractionFor(990f, 50f, 1000f), EPS);
    }

    @Test
    public void aGridWithoutColumnsYieldsEmptyWidths() {
        GridMetrics m = metrics(new Grid(), 1000f);

        assertEquals(0, m.columnWidths.length);
        assertEquals(0f, m.fixedContentWidth, EPS);
        assertEquals(930f, m.scrollViewportWidth(), EPS);
    }
}
