package com.movingbits.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/**
 * Checks the sort order as a state of the grid. The data itself is sorted by the data source –
 * that is what the tests of {@link MemGrid} check.
 */
public class GridSortTest {

    private static final int COLUMN_CITY = 0;
    private static final int COLUMN_NAME = 1;
    private static final int COLUMN_AMOUNT = 2;

    private static Grid grid() {
        return new Grid(new TestDataSource(4, 3))
                .column(new GridColumn("City").name("city"))
                .column(new GridColumn("Name").name("name"))
                .column(new GridColumn("Amount").name("amount").type(ColumnType.FLOAT));
    }

    @Test
    public void withoutSortingTheOrderIsEmpty() {
        Grid grid = grid();

        assertTrue(grid.getSortOrder().isEmpty());
        assertEquals("{\"sort\":[]}", grid.getSortJson());
    }

    @Test
    public void tappingThreeTimesCyclesThroughUpDownAndNoSorting() {
        Grid grid = grid();

        grid.toggleSort(COLUMN_NAME);
        assertEquals(SortDirection.ASCENDING, grid.getSortDirection(COLUMN_NAME));

        grid.toggleSort(COLUMN_NAME);
        assertEquals(SortDirection.DESCENDING, grid.getSortDirection(COLUMN_NAME));

        grid.toggleSort(COLUMN_NAME);
        assertNull(grid.getSortDirection(COLUMN_NAME));
        assertTrue(grid.getSortOrder().isEmpty());
    }

    @Test
    public void aFurtherColumnBecomesASubordinateCriterion() {
        Grid grid = grid();

        grid.toggleSort(COLUMN_CITY);
        grid.toggleSort(COLUMN_NAME);

        assertEquals(Arrays.asList(
                new SortCriterion(COLUMN_CITY, SortDirection.ASCENDING),
                new SortCriterion(COLUMN_NAME, SortDirection.ASCENDING)), grid.getSortOrder());
        assertEquals(1, grid.getSortRank(COLUMN_CITY));
        assertEquals(2, grid.getSortRank(COLUMN_NAME));
    }

    @Test
    public void changingDirectionLeavesTheOrderOfTheCriteriaAlone() {
        Grid grid = grid();

        grid.toggleSort(COLUMN_CITY);
        grid.toggleSort(COLUMN_NAME);
        grid.toggleSort(COLUMN_NAME); // name now descending

        assertEquals(1, grid.getSortRank(COLUMN_CITY));
        assertEquals(2, grid.getSortRank(COLUMN_NAME));
        assertEquals(SortDirection.DESCENDING, grid.getSortDirection(COLUMN_NAME));
    }

    @Test
    public void theOrderGoesToTheDataSourceAsAnExcerpt() {
        Grid grid = grid();

        grid.toggleSort(COLUMN_AMOUNT);
        grid.toggleSort(COLUMN_CITY);
        grid.toggleSort(COLUMN_CITY);

        assertEquals("{\"sort\":[{\"amount\":\"a\"},{\"city\":\"d\"}]}", grid.getSortJson());
    }

    @Test
    public void columnsThatCannotBeSortedAreIgnored() {
        Grid grid = grid().column(new GridColumn("Without").name("without").sortable(false));

        assertFalse(grid.canSortBy(3));
        assertFalse(grid.toggleSort(3));
        assertTrue(grid.getSortOrder().isEmpty());

        grid.sortable(false);
        assertFalse(grid.toggleSort(COLUMN_NAME));
    }

    @Test
    public void sortingCanBeRemoved() {
        Grid grid = grid();
        grid.toggleSort(COLUMN_NAME);

        grid.clearSort();

        assertTrue(grid.getSortOrder().isEmpty());
    }
}
