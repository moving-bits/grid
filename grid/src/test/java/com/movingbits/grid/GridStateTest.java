package com.movingbits.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Checks that a grid's state survives being stored and read again – what happens on every
 * change of screen orientation.
 */
public class GridStateTest {

    /** Grid with five columns A..E, as the configuration tests use it. */
    private static Grid grid() {
        Grid grid = new Grid(new TestDataSource(3, 5));
        for (String name : new String[]{"A", "B", "C", "D", "E"}) {
            grid.column(new GridColumn(name).name(name));
        }
        return grid;
    }

    /** A grid built anew, as the activity does it, with the stored state applied. */
    private static Grid restore(Grid original) {
        return grid().state(original.toStateJson());
    }

    private static List<String> visible(Grid grid) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < grid.getColumnCount(); i++) {
            names.add(grid.getColumn(i).getName());
        }
        return names;
    }

    private static List<String> dataOrder(Grid grid) {
        List<String> names = new ArrayList<>();
        for (GridColumn column : grid.getDataColumns()) {
            names.add(column.getName());
        }
        return names;
    }

    private static List<String> names(MemGrid<Person> grid) {
        List<String> names = new ArrayList<>();
        for (Person person : grid.getItems()) {
            names.add(person.name());
        }
        return names;
    }

    // -------------------------------------------------------- Configuration

    @Test
    public void orderFixingAndHidingSurvive() {
        Grid original = grid().fixedColumns(2).configuration(
                "{\"columns_fixed\":[\"C\",\"A\"],\"order\":[\"E\",\"B\",\"D\"],\"columns_hidden\":[\"B\"]}");
        assertEquals(Arrays.asList("C", "A", "E", "D"), visible(original));

        Grid restored = restore(original);

        assertEquals(Arrays.asList("C", "A", "E", "D"), visible(restored));
        assertEquals(2, restored.getFixedColumnCount());
        assertTrue(restored.isHidden(restored.findColumn("B")));
    }

    @Test
    public void theSortOrderSurvives() {
        Grid original = grid();
        original.toggleSort(2);
        original.toggleSort(0);
        original.toggleSort(0);

        Grid restored = restore(original);

        assertEquals(2, restored.getSortCriteriaCount());
        assertEquals(1, restored.getSortRank(2));
        assertEquals(SortDirection.ASCENDING, restored.getSortDirection(2));
        assertEquals(SortDirection.DESCENDING, restored.getSortDirection(0));
    }

    @Test
    public void theBoundaryShareSurvives() {
        assertEquals(0.55f, restore(grid().fixedBoundaryFraction(0.55f)).getFixedBoundaryFraction(), 0.005f);
    }

    // --------------------------------------------------------------- Search

    @Test
    public void theSearchSurvivesAlthoughTheConfigurationObjectDoesNotHoldIt() {
        Grid original = grid().search(Collections.singletonList(
                new SearchRequest("C", SearchOperator.CONTAINS, "text")));
        assertFalse(original.toConfigurationJson().contains("text"));

        Grid restored = restore(original);

        assertTrue(restored.isSearching());
        assertEquals(original.getSearchJson(), restored.getSearchJson());
    }

    @Test
    public void aStateWithoutASearchRemovesTheOneInEffect() {
        Grid grid = grid().search(Collections.singletonList(
                new SearchRequest("C", SearchOperator.CONTAINS, "text")));

        grid.state(grid().toStateJson());

        assertFalse(grid.isSearching());
    }

    // ----------------------------------------------------------- Data order

    @Test
    public void theDataOrderSurvivesAMovedColumn() {
        Grid original = grid();
        // The first fetch settles the data order; moving a column only changes the display.
        original.fetchPage(0);
        assertTrue(original.moveColumn(0, 2));
        assertEquals(Arrays.asList("B", "C", "A", "D", "E"), visible(original));
        assertEquals(Arrays.asList("A", "B", "C", "D", "E"), dataOrder(original));

        Grid restored = restore(original);

        assertEquals(Arrays.asList("B", "C", "A", "D", "E"), visible(restored));
        assertEquals(Arrays.asList("A", "B", "C", "D", "E"), dataOrder(restored));
        assertEquals(2, restored.findColumn("C").getDataIndex());
        // That is what the state is needed for: the configuration object alone would settle
        // the data order on the display order and thereby rearrange the data rows.
        assertEquals(Arrays.asList("B", "C", "A", "D", "E"),
                dataOrder(grid().configuration(original.toConfigurationJson())));
    }

    @Test
    public void withoutAFetchTheStateNamesNoDataOrder() {
        Grid original = grid();
        assertNull(original.getFrozenDataColumns());
        assertFalse(original.toStateJson().contains("data_order"));

        // As a configuration object does, reading settles the order – here on the display
        // order, which is the one handed over.
        assertEquals(Arrays.asList("A", "B", "C", "D", "E"), dataOrder(restore(original)));
    }

    @Test
    public void anIncompleteDataOrderIsMadeUpFor() {
        // A name that belongs to no column falls away, columns the order does not know join at
        // the back - as columns added later do.
        Grid grid = grid().state("{\"configuration\":{\"order\":[\"A\",\"B\",\"C\",\"D\",\"E\"]},"
                + "\"data_order\":[\"C\",\"gone\",\"A\"]}");

        assertEquals(Arrays.asList("C", "A", "B", "D", "E"), dataOrder(grid));
    }

    // ---------------------------------------------------------------- Table

    @Test
    public void aTableIsAddedToTheStateAndReadBackFromIt() {
        String state = GridState.withTable(grid().fixedColumns(2).toStateJson(), "cities");

        assertEquals("cities", GridState.tableOf(state));
        assertEquals("", GridState.tableOf(grid().toStateJson()));
        assertEquals("", GridState.tableOf("{"));
        // What cannot be read is handed back untouched.
        assertEquals("{", GridState.withTable("{", "cities"));
        // The rest of the state stays intact next to the table.
        assertEquals(2, grid().state(state).getFixedColumnCount());
    }

    // -------------------------------------------------------------- MemGrid

    @Test
    public void theStateOfAMemGridCarriesSortOrderAndSearch() {
        MemGrid<Person> original = Person.grid(Person.data());
        original.toggleSort(2);
        original.search(Collections.singletonList(
                new SearchRequest("city", SearchOperator.EQUALS, "New York")));

        MemGrid<Person> restored = Person.grid(Person.data()).state(original.toStateJson());

        assertEquals(Arrays.asList("Adam", "Schulz"), names(restored));
    }

    // ----------------------------------------------------------- Robustness

    @Test
    public void aStateThatCannotBeReadHasNoEffect() {
        for (String json : new String[]{null, "", "   ", "{", "[]", "no json at all"}) {
            Grid grid = grid().fixedColumns(2);
            grid.state(json);

            assertEquals(Arrays.asList("A", "B", "C", "D", "E"), visible(grid));
            assertEquals(2, grid.getFixedColumnCount());
            assertFalse(grid.isSearching());
        }
    }
}
