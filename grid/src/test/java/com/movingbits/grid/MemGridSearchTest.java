package com.movingbits.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Checks that the MemGrid applies the search to its data.
 */
public class MemGridSearchTest {

    private static MemGrid<Person> grid() {
        return Person.grid(Person.data());
    }

    private static List<String> names(MemGrid<Person> grid) {
        List<String> names = new ArrayList<>();
        for (Person person : grid.getItems()) {
            names.add(person.name());
        }
        return names;
    }

    private static List<String> search(MemGrid<Person> grid, String column,
                                       SearchOperator operator, String value) {
        grid.search(Collections.singletonList(new SearchRequest(column, operator, value)));
        return names(grid);
    }

    @Test
    public void textOperators() {
        assertEquals(Arrays.asList("Schulz", "Adam"),
                search(grid(), "city", SearchOperator.EQUALS, "New York"));
        assertEquals(Arrays.asList("Ärmel", "Zander"),
                search(grid(), "city", SearchOperator.NOT_EQUALS, "New York"));
        assertEquals(Arrays.asList("Ärmel", "Zander"),
                search(grid(), "city", SearchOperator.STARTS_WITH, "Ri"));
        assertEquals(Arrays.asList("Schulz", "Adam"),
                search(grid(), "city", SearchOperator.NOT_STARTS_WITH, "Ri"));
        assertEquals(Arrays.asList("Schulz", "Adam"),
                search(grid(), "city", SearchOperator.ENDS_WITH, "York"));
        assertEquals(Arrays.asList("Ärmel", "Zander"),
                search(grid(), "city", SearchOperator.NOT_ENDS_WITH, "York"));
        assertEquals(Arrays.asList("Schulz", "Ärmel"),
                search(grid(), "name", SearchOperator.CONTAINS, "l"));
        assertEquals(Arrays.asList("Adam", "Zander"),
                search(grid(), "name", SearchOperator.NOT_CONTAINS, "l"));
    }

    @Test
    public void caseMakesNoDifference() {
        assertEquals(Arrays.asList("Schulz", "Adam"),
                search(grid(), "city", SearchOperator.EQUALS, "nEw YoRk"));
        assertEquals(Arrays.asList("Schulz", "Adam"),
                search(grid(), "city", SearchOperator.CONTAINS, "W YO"));
    }

    @Test
    public void numberOperators() {
        // The amount is text ("300"), but the column is of type Float.
        MemGrid<Person> grid = grid();
        grid.getColumn(2).type(ColumnType.FLOAT);

        grid.search(Collections.singletonList(
                new SearchRequest("amount", SearchOperator.GREATER, "100")));
        assertEquals(Arrays.asList("Schulz", "Ärmel"), names(grid));

        grid.search(Collections.singletonList(
                new SearchRequest("amount", SearchOperator.GREATER_OR_EQUAL, "300")));
        assertEquals(Arrays.asList("Schulz", "Ärmel"), names(grid));

        grid.search(Collections.singletonList(
                new SearchRequest("amount", SearchOperator.LESS, "300")));
        assertEquals(Arrays.asList("Adam", "Zander"), names(grid));

        grid.search(Collections.singletonList(
                new SearchRequest("amount", SearchOperator.LESS_OR_EQUAL, "90")));
        assertEquals(Arrays.asList("Adam", "Zander"), names(grid));

        grid.search(Collections.singletonList(
                new SearchRequest("amount", SearchOperator.EQUALS, "1000")));
        assertEquals(Arrays.asList("Ärmel"), names(grid));

        grid.search(Collections.singletonList(
                new SearchRequest("amount", SearchOperator.NOT_EQUALS, "1000")));
        assertEquals(Arrays.asList("Schulz", "Adam", "Zander"), names(grid));
    }

    @Test
    public void searchingNumbersInTheDisplayedText() {
        // Formatted text, searched for with a plain number.
        MemGrid<String> grid = new MemGrid<>(
                Arrays.asList("1.000,00 €", "203,00 €", "45,00 €"));
        grid.column(new MemColumn<String>("Amount", item -> item)
                .name("amount").type(ColumnType.FLOAT));

        grid.search(Collections.singletonList(
                new SearchRequest("amount", SearchOperator.LESS, "300")));

        assertEquals(Arrays.asList("203,00 €", "45,00 €"), grid.getItems());
    }

    @Test
    public void anUnreadableNumberFindsNothing() {
        MemGrid<Person> grid = grid();
        grid.getColumn(2).type(ColumnType.FLOAT);

        grid.search(Collections.singletonList(
                new SearchRequest("amount", SearchOperator.GREATER, "a lot")));

        assertTrue(names(grid).isEmpty());
    }

    @Test
    public void globalSearchesEveryColumn() {
        // "New York" only appears in the city, "Zander" only in the name, "20" only in the amount.
        assertEquals(Arrays.asList("Schulz", "Adam"),
                search(grid(), SearchRequest.ALL_COLUMNS, SearchOperator.CONTAINS, "new york"));
        assertEquals(Arrays.asList("Zander"),
                search(grid(), SearchRequest.ALL_COLUMNS, SearchOperator.CONTAINS, "Zand"));
        assertEquals(Arrays.asList("Zander"),
                search(grid(), SearchRequest.ALL_COLUMNS, SearchOperator.EQUALS, "20"));
        assertTrue(search(grid(), SearchRequest.ALL_COLUMNS, SearchOperator.CONTAINS, "Marl")
                .isEmpty());
    }

    @Test
    public void severalConditionsApplyTogether() {
        MemGrid<Person> grid = grid();

        grid.search(Arrays.asList(
                new SearchRequest("city", SearchOperator.EQUALS, "New York"),
                new SearchRequest("name", SearchOperator.STARTS_WITH, "A")));

        assertEquals(Arrays.asList("Adam"), names(grid));
    }

    @Test
    public void theSearchAffectsPageCountAndRowNumbers() {
        MemGrid<Person> grid = grid().rowsPerPage(2);
        assertEquals(4, grid.getRowCount());
        assertEquals(2, grid.getPageCount());

        grid.search(Collections.singletonList(new SearchRequest("city", SearchOperator.EQUALS, "New York")));
        assertEquals(2, grid.getRowCount());
        assertEquals(1, grid.getPageCount());
        assertEquals(2, grid.getPage(0, 2, grid.getSortJson(), grid.getSearchJson()).length);
    }

    @Test
    public void searchAndSortingWorkTogether() {
        MemGrid<Person> grid = grid();
        grid.toggleSort(1);                 // by name, ascending

        grid.search(Collections.singletonList(new SearchRequest("name", SearchOperator.CONTAINS, "a")));

        // Only Adam and Zander contain an "a"; the sorting stays in place.
        assertEquals(Arrays.asList("Adam", "Zander"), names(grid));
        assertEquals("Adam", grid.getItem(0).name());
    }

    @Test
    public void anUnknownColumnFindsNothing() {
        assertTrue(search(grid(), "doesnotexist", SearchOperator.CONTAINS, "x").isEmpty());
    }

    @Test
    public void withoutASearchTheWholeDataSetRemains() {
        MemGrid<Person> grid = grid();
        grid.search(Collections.singletonList(new SearchRequest("city", SearchOperator.EQUALS, "New York")));
        grid.search(null);

        assertEquals(4, grid.getRowCount());
        assertEquals(Arrays.asList("Schulz", "Ärmel", "Adam", "Zander"), names(grid));
    }
}
