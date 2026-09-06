package com.movingbits.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks what the grid asks its data source and how it copes with the answer.
 */
public class GridDataSourceTest {

    /** Grid with five columns A..E and a data source with five fields per row. */
    private static Grid grid(TestDataSource source) {
        Grid grid = new Grid(source);
        for (String name : new String[]{"A", "B", "C", "D", "E"}) {
            grid.column(new GridColumn(name).name(name));
        }
        return grid;
    }

    private static List<String> dataOrder(Grid grid) {
        List<String> names = new ArrayList<>();
        for (GridColumn column : grid.getDataColumns()) {
            names.add(column.getName());
        }
        return names;
    }

    @Test
    public void pageNumberAndCountAreHandedOver() {
        TestDataSource source = new TestDataSource(37, 5);
        Grid grid = grid(source).rowsPerPage(10);

        grid.fetchPage(0);
        assertEquals(0, source.lastCall().page());
        assertEquals(10, source.lastCall().count());

        // On the last page only the remaining records are left.
        grid.fetchPage(3);
        assertEquals(3, source.lastCall().page());
        assertEquals(7, source.lastCall().count());
    }

    @Test
    public void withoutPagingEverythingIsFetchedInOneGo() {
        TestDataSource source = new TestDataSource(37, 5);
        Grid grid = grid(source);

        String[][] rows = grid.fetchPage(0);

        assertEquals(0, source.lastCall().page());
        assertEquals(37, source.lastCall().count());
        assertEquals(37, rows.length);
    }

    @Test
    public void theSortOrderIsHandedOverAsAnExcerpt() {
        TestDataSource source = new TestDataSource(20, 5);
        Grid grid = grid(source).rowsPerPage(10);

        grid.fetchPage(0);
        assertEquals("{\"sort\":[]}", source.lastCall().sort());

        grid.toggleSort(2);                     // C ascending
        grid.toggleSort(0);                     // then A ascending
        grid.toggleSort(0);                     // turn A to descending
        grid.fetchPage(0);

        assertEquals("{\"sort\":[{\"C\":\"a\"},{\"A\":\"d\"}]}", source.lastCall().sort());
        assertEquals(SortRequest.parse(source.lastCall().sort()), Arrays.asList(
                new SortRequest("C", SortDirection.ASCENDING),
                new SortRequest("A", SortDirection.DESCENDING)));
    }

    @Test
    public void anEmptyAnswerIsCopedWith() {
        Grid grid = grid(new TestDataSource(0, 5)).rowsPerPage(10);

        assertNotNull(grid.fetchPage(0));
        assertEquals(0, grid.fetchPage(0).length);

        Grid withoutAnswer = new Grid(new GridDataSource() {
            @Override
            public int getRowCount(String search) {
                return 5;
            }

            @Override
            public String[][] getPage(int page, int count, String sort, String search) {
                return null;
            }
        }).column("A");

        assertEquals(0, withoutAnswer.fetchPage(0).length);
    }

    @Test
    public void theDataOrderFollowsTheOrderHandedOver() {
        Grid grid = grid(new TestDataSource(5, 5));

        assertEquals(Arrays.asList("A", "B", "C", "D", "E"), dataOrder(grid));
        for (int i = 0; i < 5; i++) {
            assertEquals(i, grid.getColumn(i).getDataIndex());
        }
    }

    @Test
    public void theDataOrderFollowsTheFirstConfigurationObject() {
        Grid grid = grid(new TestDataSource(5, 5))
                .configuration("{\"columns_fixed\":[\"D\"],\"order\":[\"C\",\"*\"]}");

        assertEquals(Arrays.asList("D", "C", "A", "B", "E"), dataOrder(grid));
        assertEquals(0, grid.findColumn("D").getDataIndex());
        assertEquals(1, grid.findColumn("C").getDataIndex());
    }

    @Test
    public void theDataOrderSurvivesReorderingAndHiding() {
        Grid grid = grid(new TestDataSource(5, 5)).fixedColumns(2);
        List<String> before = dataOrder(grid);

        grid.moveColumn(4, 2);
        // Hide through the stored state, so that the order that was moved stays in place.
        grid.configuration(grid.toConfigurationJson()
                .replace("\"columns_hidden\":[]", "\"columns_hidden\":[\"B\"]"));

        assertEquals(before, dataOrder(grid));
        // The display did change, of course.
        assertEquals("E", grid.getColumn(1).getName());
        assertEquals(4, grid.getColumn(1).getDataIndex());
    }

    @Test
    public void ignoredColumnsOccupyNoField() {
        Grid grid = grid(new TestDataSource(5, 4)).ignoreColumns("B");

        assertEquals(Arrays.asList("A", "C", "D", "E"), dataOrder(grid));
        assertEquals(1, grid.findColumn("C").getDataIndex());
    }

    @Test
    public void columnsAddedLaterJoinAtTheBack() {
        Grid grid = grid(new TestDataSource(5, 6));
        grid.getDataColumns();                  // settle the order

        grid.column(new GridColumn("F").name("F"));

        assertEquals(Arrays.asList("A", "B", "C", "D", "E", "F"), dataOrder(grid));
        assertEquals(5, grid.findColumn("F").getDataIndex());
    }

    @Test
    public void theTotalCountIsRefreshedOnEveryFetch() {
        TestDataSource source = new TestDataSource(20, 5);
        Grid grid = grid(source).rowsPerPage(10);

        assertEquals(2, grid.getPageCount());

        source.setRowCount(45);
        grid.fetchPage(0);

        assertEquals(45, grid.getRowCount());
        assertEquals(5, grid.getPageCount());
    }

    @Test
    public void sortingChangesOnlyTheStateNotTheData() {
        TestDataSource source = new TestDataSource(20, 5);
        Grid grid = grid(source).rowsPerPage(10);
        int fetches = source.calls.size();

        assertTrue(grid.toggleSort(1));

        // Sorting happens on the next fetch, and it is the data source that does it.
        assertEquals(fetches, source.calls.size());
        assertEquals(SortDirection.ASCENDING, grid.getSortDirection(1));
    }
}
