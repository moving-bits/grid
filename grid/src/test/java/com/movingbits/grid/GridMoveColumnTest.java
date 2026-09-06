package com.movingbits.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GridMoveColumnTest {


    /** Grid with five columns A..E, the first two of them fixed. */
    private static Grid grid() {
        Grid grid = new Grid(new TestDataSource(3, 5));
        for (String name : new String[]{"A", "B", "C", "D", "E"}) {
            grid.column(new GridColumn(name).name(name));
        }
        return grid.fixedColumns(2);
    }

    private static List<String> order(Grid grid) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < grid.getColumnCount(); i++) {
            names.add(grid.getColumn(i).getName());
        }
        return names;
    }

    @Test
    public void movingWithinTheFreeColumns() {
        Grid grid = grid();

        assertTrue(grid.moveColumn(4, 2));

        assertEquals(Arrays.asList("A", "B", "E", "C", "D"), order(grid));
    }

    @Test
    public void movingWithinTheFixedColumns() {
        Grid grid = grid();

        assertTrue(grid.moveColumn(1, 0));

        assertEquals(Arrays.asList("B", "A", "C", "D", "E"), order(grid));
        assertEquals(2, grid.getFixedColumnCount());
    }

    @Test
    public void movingAcrossTheBoundaryIsRejected() {
        Grid grid = grid();

        assertFalse(grid.canMoveColumn(0, 3));
        assertFalse(grid.moveColumn(0, 3));
        assertFalse(grid.moveColumn(3, 1));

        assertEquals(Arrays.asList("A", "B", "C", "D", "E"), order(grid));
    }

    @Test
    public void nonsensicalPositionsAreRejected() {
        Grid grid = grid();

        assertFalse(grid.moveColumn(2, 2));
        assertFalse(grid.moveColumn(-1, 2));
        assertFalse(grid.moveColumn(2, 99));
    }

    @Test
    public void withoutFixedColumnsEverythingIsOneArea() {
        Grid grid = grid().fixedColumns(0);

        assertTrue(grid.moveColumn(4, 0));

        assertEquals(Arrays.asList("E", "A", "B", "C", "D"), order(grid));
    }

    @Test
    public void theSortOrderFollowsTheMovedColumn() {
        Grid grid = grid();
        grid.toggleSort(4);                 // by E, ascending
        grid.toggleSort(2);                 // then by C

        grid.moveColumn(4, 2);              // move E ahead of C

        assertEquals("E", grid.getColumn(2).getName());
        assertEquals("C", grid.getColumn(3).getName());
        // The criteria still point at E (rank 1) and C (rank 2).
        assertEquals(1, grid.getSortRank(2));
        assertEquals(2, grid.getSortRank(3));
        assertEquals(SortDirection.ASCENDING, grid.getSortDirection(2));
    }

    @Test
    public void theSortOrderOfUninvolvedColumnsIsKept() {
        Grid grid = grid();
        grid.toggleSort(2);                 // by C

        grid.moveColumn(4, 3);              // E between C and D

        assertEquals("C", grid.getColumn(2).getName());
        assertEquals(1, grid.getSortRank(2));
    }

    @Test
    public void theNewOrderEndsUpInTheConfigurationObject() throws Exception {
        Grid grid = grid();

        grid.moveColumn(4, 2);
        JSONObject json = new JSONObject(grid.toConfigurationJson());

        assertEquals("[\"A\",\"B\"]", json.getJSONArray("columns_fixed").toString());
        assertEquals("[\"E\",\"C\",\"D\"]", json.getJSONArray("order").toString());
    }

    @Test
    public void orderAndVisibilityInOneGo() {
        Grid grid = grid();
        grid.toggleSort(3);                        // sort by D

        // Hide C, pull E ahead of D; A and B stay fixed.
        List<GridColumn> visible = Arrays.asList(
                grid.getColumn(0), grid.getColumn(1), grid.getColumn(4), grid.getColumn(3));
        grid.applyColumnLayout(visible, Collections.singletonList("C"), 2);

        assertEquals(Arrays.asList("A", "B", "E", "D"), order(grid));
        assertEquals(2, grid.getFixedColumnCount());
        // The sorting follows column D to its new position.
        assertEquals(1, grid.getSortRank(3));
        assertEquals(SortDirection.ASCENDING, grid.getSortDirection(3));
    }

    @Test
    public void theSortOrderOfHiddenColumnsFallsAway() {
        Grid grid = grid();
        grid.toggleSort(2);                        // sort by C

        List<GridColumn> visible = Arrays.asList(
                grid.getColumn(0), grid.getColumn(1), grid.getColumn(3), grid.getColumn(4));
        grid.applyColumnLayout(visible, Collections.singletonList("C"), 2);

        assertTrue(grid.getSortOrder().isEmpty());
    }
}
