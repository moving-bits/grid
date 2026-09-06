package com.movingbits.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks that the MemGrid applies the grid's order to its data.
 */
public class MemGridSortTest {

    private static final int COLUMN_CITY = 0;
    private static final int COLUMN_NAME = 1;
    private static final int COLUMN_AMOUNT = 2;

    private static List<String> names(MemGrid<Person> grid) {
        List<String> names = new ArrayList<>();
        for (Person person : grid.getItems()) {
            names.add(person.name());
        }
        return names;
    }

    @Test
    public void withoutSortingTheOriginalOrderRemains() {
        List<Person> people = Person.data();
        MemGrid<Person> grid = Person.grid(people);

        assertEquals(Arrays.asList("Schulz", "Ärmel", "Adam", "Zander"), names(grid));
        assertSame(people, grid.getUnsortedRows());
    }

    @Test
    public void tappingThreeTimesCyclesThroughUpDownAndNoSorting() {
        MemGrid<Person> grid = Person.grid(Person.data());

        grid.toggleSort(COLUMN_NAME);
        assertEquals(Arrays.asList("Adam", "Ärmel", "Schulz", "Zander"), names(grid));

        grid.toggleSort(COLUMN_NAME);
        assertEquals(Arrays.asList("Zander", "Schulz", "Ärmel", "Adam"), names(grid));

        grid.toggleSort(COLUMN_NAME);
        assertEquals(Arrays.asList("Schulz", "Ärmel", "Adam", "Zander"), names(grid));
    }

    @Test
    public void severalCriteriaTakeEffectInTheirOrder() {
        MemGrid<Person> grid = Person.grid(Person.data());

        grid.toggleSort(COLUMN_CITY);
        grid.toggleSort(COLUMN_NAME);

        assertEquals(Arrays.asList("Adam", "Schulz", "Ärmel", "Zander"), names(grid));

        grid.toggleSort(COLUMN_NAME);   // name now descending
        assertEquals(Arrays.asList("Schulz", "Adam", "Zander", "Ärmel"), names(grid));
    }

    @Test
    public void aComparatorOfItsOwnSortsTheWayTheSubjectRequires() {
        MemGrid<Person> grid = Person.grid(Person.data());

        grid.toggleSort(COLUMN_AMOUNT);

        assertEquals(Arrays.asList("Zander", "Adam", "Schulz", "Ärmel"), names(grid));
    }

    @Test
    public void sortingLeavesTheListHandedOverUnchanged() {
        List<Person> people = Person.data();
        MemGrid<Person> grid = Person.grid(people);

        grid.toggleSort(COLUMN_NAME);

        assertEquals("Schulz", people.get(0).name());
        assertEquals("Adam", grid.getItems().get(0).name());
        assertSame(people, grid.getUnsortedRows());
    }

    @Test
    public void newDataIsSortedByTheOrderInEffect() {
        MemGrid<Person> grid = Person.grid(Person.data());
        grid.toggleSort(COLUMN_NAME);

        grid.rows(Arrays.asList(
                new Person("Tokyo", "Weber", 10),
                new Person("Tokyo", "Bauer", 20)));

        assertEquals(Arrays.asList("Bauer", "Weber"), names(grid));
        assertEquals(2, grid.getRowCount());
    }

    @Test
    public void theTypeDecidesTheSortingOnlyWithoutAComparatorOfItsOwn() {
        // Formatted the German way on purpose: that is what NumericValues has to cope with.
        List<String> amounts = Arrays.asList("1.000,00 €", "203,00 €", "45,00 €");

        // As text: 1.000 before 203 before 45.
        MemGrid<String> asText = new MemGrid<>(amounts);
        asText.column(new MemColumn<String>("Amount", item -> item).name("amount"));
        asText.toggleSort(0);
        assertEquals(amounts, asText.getItems());

        // As a number: 45 before 203 before 1000.
        MemGrid<String> asNumber = new MemGrid<>(amounts);
        asNumber.column(new MemColumn<String>("Amount", item -> item)
                .name("amount").type(ColumnType.FLOAT));
        asNumber.toggleSort(0);
        assertEquals(Arrays.asList("45,00 €", "203,00 €", "1.000,00 €"), asNumber.getItems());

        // A comparator of its own wins: here descending by text length.
        MemGrid<String> ownComparator = new MemGrid<>(amounts);
        ownComparator.column(new MemColumn<String>("Amount", item -> item)
                .name("amount").type(ColumnType.FLOAT)
                .comparator((a, b) -> Integer.compare(b.length(), a.length())));
        ownComparator.toggleSort(0);
        assertEquals(amounts, ownComparator.getItems());
    }

    @Test
    public void sortingFromTheConfigurationObjectTakesEffectOnTheData() {
        MemGrid<Person> grid = Person.grid(Person.data())
                .configuration("{\"sort\":[{\"city\":\"d\"},{\"name\":\"a\"}]}");

        assertEquals(Arrays.asList("Ärmel", "Zander", "Adam", "Schulz"), names(grid));
    }

    @Test
    public void columnsWithoutAValueProviderStayEmptyAndDoNotSort() {
        MemGrid<Person> grid = Person.grid(Person.data());
        grid.column(new GridColumn("Empty").name("empty"));

        grid.toggleSort(3);

        // The criterion does end up in the state, but it changes no order.
        assertFalse(grid.getSortOrder().isEmpty());
        assertEquals(Arrays.asList("Schulz", "Ärmel", "Adam", "Zander"), names(grid));
        assertEquals("", grid.getPage(0, 4, grid.getSortJson(), grid.getSearchJson())[0][3]);
    }
}
