package com.movingbits.grid;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks what the MemGrid delivers as a data source.
 */
public class MemGridTest {

    private static List<String> dataOrder(Grid grid) {
        List<String> names = new ArrayList<>();
        for (GridColumn column : grid.getDataColumns()) {
            names.add(column.getName());
        }
        return names;
    }

    @Test
    public void theConfigurationChainReturnsTheSameObject() {
        MemGrid<Person> grid = Person.grid(Person.data());

        MemGrid<Person> result = grid.fixedColumns(1).rowsPerPage(2).adjustableFixedBoundary(true);

        assertSame(grid, result);
        assertSame(grid, grid.getDataSource());
        assertEquals(4, grid.getRowCount());
        assertEquals(2, grid.getPageCount());
    }

    @Test
    public void aPageDeliversTheFieldsInDataOrder() {
        MemGrid<Person> grid = Person.grid(Person.data()).rowsPerPage(2);

        String[][] page = grid.getPage(0, 2, grid.getSortJson(), grid.getSearchJson());

        assertEquals(Arrays.asList("city", "name", "amount"), dataOrder(grid));
        assertEquals(2, page.length);
        assertArrayEquals(new String[]{"New York", "Schulz", "300"}, page[0]);
        assertArrayEquals(new String[]{"Rio", "Ärmel", "1000"}, page[1]);
    }

    @Test
    public void theLastPageEndsWithTheRowsThatExist() {
        MemGrid<Person> grid = Person.grid(Person.data()).rowsPerPage(3);

        String[][] last = grid.getPage(1, 1, grid.getSortJson(), grid.getSearchJson());

        assertEquals(1, last.length);
        assertArrayEquals(new String[]{"Rio", "Zander", "20"}, last[0]);
    }

    @Test
    public void withoutPagingEverythingComesInOneGo() {
        MemGrid<Person> grid = Person.grid(Person.data());

        assertEquals(4, grid.getPage(0, grid.getRowCount(), grid.getSortJson(), grid.getSearchJson()).length);
    }

    @Test
    public void aPageBeyondTheDataStaysEmpty() {
        MemGrid<Person> grid = Person.grid(Person.data()).rowsPerPage(2);

        assertEquals(0, grid.getPage(7, 2, grid.getSortJson(), grid.getSearchJson()).length);
    }

    @Test
    public void theDataOrderSurvivesReorderingTheDisplay() {
        MemGrid<Person> grid = Person.grid(Person.data());
        List<String> before = dataOrder(grid);

        grid.moveColumn(2, 0);

        assertEquals(before, dataOrder(grid));
        assertEquals("amount", grid.getColumn(0).getName());
        assertEquals(2, grid.getColumn(0).getDataIndex());
        // The row itself is unchanged; the display picks its field through the index.
        assertArrayEquals(new String[]{"New York", "Schulz", "300"},
                grid.getPage(0, 1, grid.getSortJson(), grid.getSearchJson())[0]);
    }

    @Test
    public void anIgnoredColumnOccupiesNoField() {
        MemGrid<Person> grid = Person.grid(Person.data()).ignoreColumns("name");

        assertEquals(Arrays.asList("city", "amount"), dataOrder(grid));
        assertArrayEquals(new String[]{"New York", "300"},
                grid.getPage(0, 1, grid.getSortJson(), grid.getSearchJson())[0]);
    }

    @Test
    public void theRecordBehindACell() {
        MemGrid<Person> grid = Person.grid(Person.data());
        grid.toggleSort(1);                 // by name, ascending

        // A cell's index refers to the display order.
        assertEquals("Adam", grid.getItem(0).name());
        assertNull(grid.getItem(99));

        final List<String> reported = new ArrayList<>();
        grid.onItemClick((cell, person) -> reported.add(person.name() + "/" + cell.dataIndex()));
        grid.getCellClickListener().onCellClick(
                new CellRef(2, 1, 1, false), new String[]{"Herten", "Schulz", "300"});

        assertEquals(Arrays.asList("Schulz/1"), reported);
    }

    @Test
    public void aColumnWithoutAValueProviderStaysEmpty() {
        MemGrid<Person> grid = Person.grid(Person.data());
        grid.column(new GridColumn("Empty").name("empty"));

        assertArrayEquals(new String[]{"New York", "Schulz", "300", ""},
                grid.getPage(0, 1, grid.getSortJson(), grid.getSearchJson())[0]);
    }

    @Test
    public void aNullValueYieldsEmptyText() {
        MemColumn<Person> column = new MemColumn<>("Without", item -> null);

        assertEquals("", column.textOf(Person.data().get(0)));
    }
}
