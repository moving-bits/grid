package com.movingbits.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Checks the search as a transient state of the grid and its way to the data source.
 */
public class GridSearchTest {

    private static Grid grid(TestDataSource source) {
        return new Grid(source)
                .column(new GridColumn("City").name("city"))
                .column(new GridColumn("Amount").name("amount").type(ColumnType.FLOAT));
    }

    @Test
    public void withoutASearchTheListOfConditionsIsEmpty() {
        Grid grid = grid(new TestDataSource(20, 2));

        assertFalse(grid.isSearching());
        assertTrue(grid.getSearch().isEmpty());
        assertEquals("{\"search\":[]}", grid.getSearchJson());
    }

    @Test
    public void conditionsGoToTheDataSourceAsAnExcerpt() {
        TestDataSource source = new TestDataSource(20, 2);
        Grid grid = grid(source).rowsPerPage(10);

        List<SearchRequest> conditions = Arrays.asList(
                new SearchRequest("city", SearchOperator.STARTS_WITH, "To"),
                new SearchRequest("amount", SearchOperator.GREATER_OR_EQUAL, "100"));
        grid.search(conditions);
        grid.fetchPage(0);

        // The order of the keys within one entry is org.json's business; what is checked is
        // therefore the content once it has been read back.
        assertEquals(conditions, SearchRequest.parse(source.lastCall().search()));
        // Counting, too, only covers what the search leaves over.
        assertEquals(conditions, SearchRequest.parse(source.rowCountSearch));
    }

    @Test
    public void conditionsWithoutAValueAreIgnored() {
        Grid grid = grid(new TestDataSource(20, 2));

        grid.search(Arrays.asList(
                new SearchRequest("city", SearchOperator.CONTAINS, ""),
                new SearchRequest("amount", SearchOperator.LESS, "50")));

        assertEquals(1, grid.getSearch().size());
        assertEquals("amount", grid.getSearch().get(0).columnName());
    }

    @Test
    public void theSearchCanBeRemoved() {
        Grid grid = grid(new TestDataSource(20, 2));
        grid.search(Collections.singletonList(
                new SearchRequest("city", SearchOperator.CONTAINS, "To")));

        grid.search(null);

        assertFalse(grid.isSearching());
        assertEquals("{\"search\":[]}", grid.getSearchJson());
    }

    @Test
    public void theSearchIsNotPartOfTheConfigurationObject() {
        Grid grid = grid(new TestDataSource(20, 2));
        String before = grid.toConfigurationJson();

        grid.search(Collections.singletonList(
                new SearchRequest("city", SearchOperator.EQUALS, "Tokyo")));

        assertEquals(before, grid.toConfigurationJson());
        assertFalse(grid.toConfigurationJson().contains("search"));
    }

    @Test
    public void whatWasWrittenIsWhatIsRead() {
        List<SearchRequest> conditions = Arrays.asList(
                new SearchRequest(SearchRequest.ALL_COLUMNS, SearchOperator.CONTAINS, "New York"),
                new SearchRequest("amount", SearchOperator.NOT_EQUALS, "0"));
        Grid grid = grid(new TestDataSource(20, 2)).search(conditions);

        assertEquals(conditions, SearchRequest.parse(grid.getSearchJson()));
        assertTrue(SearchRequest.parse(grid.getSearchJson()).get(0).isAllColumns());
    }

    @Test
    public void unusableInputIsSkipped() {
        // No JSON, no "search" field, an unknown operator, a missing value.
        assertTrue(SearchRequest.parse(null).isEmpty());
        assertTrue(SearchRequest.parse("this is not JSON").isEmpty());
        assertTrue(SearchRequest.parse("{}").isEmpty());
        assertTrue(SearchRequest.parse("{\"search\":[{\"city\":{\"o\":\"xx\",\"v\":\"a\"}}]}")
                .isEmpty());
        assertTrue(SearchRequest.parse("{\"search\":[{\"city\":{\"o\":\"ct\",\"v\":\"\"}}]}")
                .isEmpty());
        assertTrue(SearchRequest.parse("{\"search\":[\"broken\"]}").isEmpty());
    }

    @Test
    public void theOperatorsDependOnTheColumnType() {
        List<SearchOperator> text = SearchOperator.forType(ColumnType.STRING);
        assertEquals(SearchOperator.forType(ColumnType.UNKNOWN), text);
        assertEquals(Arrays.asList(
                SearchOperator.EQUALS, SearchOperator.NOT_EQUALS,
                SearchOperator.STARTS_WITH, SearchOperator.NOT_STARTS_WITH,
                SearchOperator.ENDS_WITH, SearchOperator.NOT_ENDS_WITH,
                SearchOperator.CONTAINS, SearchOperator.NOT_CONTAINS), text);

        List<SearchOperator> numbers = SearchOperator.forType(ColumnType.INTEGER);
        assertEquals(SearchOperator.forType(ColumnType.FLOAT), numbers);
        assertEquals(Arrays.asList(
                SearchOperator.EQUALS, SearchOperator.NOT_EQUALS,
                SearchOperator.LESS, SearchOperator.LESS_OR_EQUAL,
                SearchOperator.GREATER, SearchOperator.GREATER_OR_EQUAL), numbers);

        // The search across all columns behaves as if on text.
        assertEquals(text, SearchOperator.forType(null));
        assertTrue(SearchOperator.CONTAINS.fits(ColumnType.STRING));
        assertFalse(SearchOperator.CONTAINS.fits(ColumnType.FLOAT));
        assertTrue(SearchOperator.EQUALS.fits(ColumnType.FLOAT));
    }
}
