package com.movingbits.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class GridTest {

    @Test
    public void theConfigurationChainReturnsTheSameObject() {
        Grid grid = new Grid(new TestDataSource(25, 2));

        Grid result = grid
                .column("A")
                .column("B", CellAlignment.END, ColumnWidth.dp(80f))
                .fixedColumns(1)
                .rowsPerPage(10)
                .totalWidth(600f);

        assertSame(grid, result);
        assertEquals(2, grid.getColumnCount());
        assertEquals(1, grid.getFixedColumnCount());
        assertEquals(10, grid.getRowsPerPage());
        assertTrue(grid.isPaged());
        assertTrue(grid.hasTotalWidth());
        assertEquals(600f, grid.getTotalWidthDp(), 0.001f);
        assertEquals(25, grid.getRowCount());
        assertEquals(3, grid.getPageCount());
    }

    @Test
    public void defaultsWithoutAConfiguration() {
        Grid grid = new Grid(new TestDataSource(5, 1)).column("A");

        assertEquals(0, grid.getFixedColumnCount());
        assertEquals(0, grid.getRowsPerPage());
        assertFalse(grid.isPaged());
        assertFalse(grid.hasTotalWidth());
        assertFalse(grid.isFixedBoundaryAdjustable());
        assertFalse(grid.hasAlternatingRowColors());
        assertEquals(1, grid.getPageCount());
        assertEquals(CellAlignment.START, grid.getColumn(0).getAlignment());
        assertEquals(ColumnWidth.Type.REMAINING, grid.getColumn(0).getWidth().getType());
    }

    @Test
    public void alternatingRowColorsCanBeSwitchedOn() {
        Grid grid = new Grid(new TestDataSource(5, 1)).column("A").alternatingRowColors(true);

        assertTrue(grid.hasAlternatingRowColors());
        // Purely visual: the configuration object stays untouched by it.
        assertFalse(grid.toConfigurationJson().contains("alternating"));
    }

    @Test
    public void withoutADataSourceThereAreNoRows() {
        Grid grid = new Grid().column("A").rowsPerPage(10);

        assertEquals(0, grid.getRowCount());
        assertEquals(1, grid.getPageCount());
        assertEquals(0, grid.fetchPage(0).length);
    }

    @Test
    public void fixedColumnsAreCappedAtTheColumnsThatExist() {
        Grid grid = new Grid(new TestDataSource(5, 1))
                .column("A")
                .fixedColumns(5);

        assertEquals(1, grid.getFixedColumnCount());
    }

    @Test
    public void invalidValuesAreRejected() {
        Grid grid = new Grid();
        try {
            grid.fixedColumns(-1);
            fail("fixedColumns(-1) has to be rejected");
        } catch (IllegalArgumentException expected) {
            // as expected
        }
        try {
            ColumnWidth.percent(1.5f);
            fail("percent(1.5) has to be rejected");
        } catch (IllegalArgumentException expected) {
            // as expected
        }
        try {
            ColumnWidth.dp(0f);
            fail("dp(0) has to be rejected");
        } catch (IllegalArgumentException expected) {
            // as expected
        }
    }

    @Test
    public void widthsBelowTheMinimumWidthAreRaised() {
        assertEquals(ColumnWidth.MIN_WIDTH_DP, ColumnWidth.dp(1f).getValue(), 0.001f);
        assertEquals(ColumnWidth.MIN_WIDTH_DP, ColumnWidth.dp(ColumnWidth.MIN_WIDTH_DP).getValue(), 0.001f);
        assertEquals(130f, ColumnWidth.dp(130f).getValue(), 0.001f);

        try {
            ColumnWidth.dp(0f);
            fail("dp(0) has to be rejected");
        } catch (IllegalArgumentException expected) {
            // as expected: no width at all is something other than one that is too small
        }
    }

    @Test
    public void columnsCanBeDroppedAndBuiltAnew() {
        Grid grid = new Grid(new TestDataSource(5, 3))
                .column("A")
                .column("B")
                .column("C")
                .fixedColumns(1)
                .configuration("{\"columns_hidden\":[\"B\"],\"sort\":[{\"A\":\"d\"}]}")
                .search(java.util.List.of(new SearchRequest("A", SearchOperator.CONTAINS, "x")));

        assertTrue(grid.hasHiddenColumns());
        assertTrue(grid.isSearching());
        assertEquals(3, grid.getDataColumns().size());

        grid.clearColumns().column("X").column("Y");

        assertEquals(2, grid.getColumnCount());
        assertEquals(0, grid.getFixedColumnCount());
        assertFalse(grid.hasHiddenColumns());
        assertFalse(grid.isSearching());
        assertTrue(grid.getSortOrder().isEmpty());
        // The data order of the old data set does not carry on.
        assertEquals(2, grid.getDataColumns().size());
        assertEquals(0, grid.getColumn(0).getDataIndex());
        assertEquals(1, grid.getColumn(1).getDataIndex());
    }
}
