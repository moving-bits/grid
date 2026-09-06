package com.movingbits.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GridConfigurationTest {


    /** Grid with five columns A..E, each carrying its name as its value. */
    private static Grid grid() {
        Grid grid = new Grid(new TestDataSource(3, 5));
        for (String name : new String[]{"A", "B", "C", "D", "E"}) {
            grid.column(new GridColumn(name).name(name));
        }
        return grid;
    }

    /** The column with this name; the tests know them all. */
    private static GridColumn column(Grid grid, String name) {
        return grid.findColumn(name);
    }

    private static List<String> visible(Grid grid) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < grid.getColumnCount(); i++) {
            names.add(grid.getColumn(i).getName());
        }
        return names;
    }

    // --------------------------------------------------------------- Reading

    @Test
    public void withoutAConfigurationTheOrderHandedOverRemains() {
        Grid grid = grid().configuration(null);

        assertEquals(Arrays.asList("A", "B", "C", "D", "E"), visible(grid));
        assertEquals(0, grid.getFixedColumnCount());
    }

    @Test
    public void withoutTheFieldTheColumnsFixedInCodeStayFixed() {
        Grid grid = grid().fixedColumns(2).configuration("{}");

        assertEquals(Arrays.asList("A", "B", "C", "D", "E"), visible(grid));
        assertEquals(2, grid.getFixedColumnCount());
    }

    @Test
    public void anEmptyFieldRemovesTheFixing() {
        Grid grid = grid().fixedColumns(2).configuration("{\"columns_fixed\":[]}");

        assertEquals(0, grid.getFixedColumnCount());
    }

    // ---------------------------------------------------------------- Widths

    @Test
    public void theWidthIsTakenOverAndWrittenBack() throws Exception {
        Grid grid = grid().configuration("{\"columns\":{\"B\":{\"w\":130}}}");

        assertEquals(ColumnWidth.Type.FIXED, column(grid, "B").getWidth().getType());
        assertEquals(130f, column(grid, "B").getWidth().getValue(), 0.001f);

        JSONObject columns = new JSONObject(grid.toConfigurationJson()).getJSONObject("columns");
        assertEquals(130, columns.getJSONObject("B").getInt("w"));
    }

    @Test
    public void theMinimumWidthTakesPrecedenceOverTheValue() {
        Grid grid = grid().configuration("{\"columns\":{\"B\":{\"w\":5}}}");

        assertEquals(ColumnWidth.MIN_WIDTH_DP, column(grid, "B").getWidth().getValue(), 0.001f);
    }

    @Test
    public void withoutAWidthTheWidthSetInCodeRemains() throws Exception {
        Grid grid = grid();
        column(grid, "B").widthDp(90f);

        grid.configuration("{\"columns\":{\"B\":{\"t\":\"s\"}}}");

        assertEquals(90f, column(grid, "B").getWidth().getValue(), 0.001f);
        // Split remaining widths do not freeze, so they do not end up in the object at all.
        JSONObject columns = new JSONObject(grid.toConfigurationJson()).getJSONObject("columns");
        assertEquals(90, columns.getJSONObject("B").getInt("w"));
        assertFalse(columns.getJSONObject("A").has("w"));
    }

    @Test
    public void orderShowsOnlyTheColumnsItLists() {
        Grid grid = grid().configuration("{\"order\":[\"C\",\"A\"]}");

        assertEquals(Arrays.asList("C", "A"), visible(grid));
    }

    @Test
    public void theStarInsertsTheRemainingColumnsAtItsOwnPosition() {
        Grid grid = grid().configuration("{\"order\":[\"C\",\"*\",\"A\"]}");

        assertEquals(Arrays.asList("C", "B", "D", "E", "A"), visible(grid));
    }

    @Test
    public void aSecondStarIsSkipped() {
        Grid grid = grid().configuration("{\"order\":[\"C\",\"*\",\"*\"]}");

        assertEquals(Arrays.asList("C", "A", "B", "D", "E"), visible(grid));
    }

    @Test
    public void fixedColumnsComeFirstRegardlessOfOrder() {
        Grid grid = grid().configuration(
                "{\"columns_fixed\":[\"D\",\"B\"],\"order\":[\"A\",\"C\"]}");

        assertEquals(Arrays.asList("D", "B", "A", "C"), visible(grid));
        assertEquals(2, grid.getFixedColumnCount());
    }

    @Test
    public void hiddenColumnsDoNotAppearAndNotThroughTheStarEither() {
        Grid grid = grid().configuration(
                "{\"columns_hidden\":[\"B\"],\"order\":[\"*\",\"B\"]}");

        assertEquals(Arrays.asList("A", "C", "D", "E"), visible(grid));
    }

    @Test
    public void hiddenColumnsKeepPositionAndArea() throws Exception {
        // B is fixed and hidden, D stands in the middle of the free area and is hidden too.
        Grid grid = grid().configuration(
                "{\"columns_fixed\":[\"A\",\"B\"],\"order\":[\"C\",\"D\",\"E\"],"
                        + "\"columns_hidden\":[\"B\",\"D\"]}");

        // Only what is not hidden is displayed ...
        assertEquals(Arrays.asList("A", "C", "E"), visible(grid));
        assertEquals(1, grid.getFixedColumnCount());

        // ... but the order and the areas are kept in full.
        JSONObject json = new JSONObject(grid.toConfigurationJson());
        assertEquals(Arrays.asList("A", "B"), names(json.getJSONArray("columns_fixed")));
        assertEquals(Arrays.asList("C", "D", "E"), names(json.getJSONArray("order")));
        assertEquals(Arrays.asList("B", "D"), names(json.getJSONArray("columns_hidden")));
    }

    @Test
    public void showingAColumnAgainRestoresItsOldPosition() {
        Grid grid = grid().configuration(
                "{\"columns_fixed\":[\"A\",\"B\"],\"order\":[\"C\",\"D\",\"E\"],"
                        + "\"columns_hidden\":[\"B\",\"D\"]}");
        String stored = grid.toConfigurationJson();

        // The same state, then without anything hidden: B is fixed again, D is back in third
        // place of the free area.
        Grid again = grid().configuration(stored)
                .configuration(stored.replace("[\"B\",\"D\"]", "[]"));

        assertEquals(Arrays.asList("A", "B", "C", "D", "E"), visible(again));
        assertEquals(2, again.getFixedColumnCount());
    }

    @Test
    public void ignoredColumnsCannotBeBroughtBack() {
        Grid grid = grid()
                .ignoreColumns("D")
                .configuration("{\"columns_fixed\":[\"D\"],\"order\":[\"D\",\"*\"]}");

        assertEquals(Arrays.asList("A", "B", "C", "E"), visible(grid));
        assertEquals(0, grid.getFixedColumnCount());
    }

    @Test
    public void ignoringAlsoWorksBeforeTheColumnsAreAdded() {
        Grid before = new Grid(new TestDataSource(3, 5)).ignoreColumns("B");
        for (String name : new String[]{"A", "B", "C"}) {
            before.column(new GridColumn(name).name(name));
        }

        assertEquals(Arrays.asList("A", "C"), visible(before));
    }

    @Test
    public void theColumnPropertiesAreTakenOver() {
        Grid grid = grid().configuration(
                "{\"columns\":{"
                        + "\"A\":{\"t\":\"i\"},"
                        + "\"B\":{\"t\":\"f\",\"a\":\"c\"},"
                        + "\"C\":{\"r\":\"y\"},"
                        + "\"D\":{\"t\":\"u\"}}}");

        // The type determines the alignment ...
        assertEquals(ColumnType.INTEGER, grid.getColumn(0).getType());
        assertEquals(CellAlignment.END, grid.getColumn(0).getAlignment());
        // ... an explicit value takes precedence.
        assertEquals(CellAlignment.CENTER, grid.getColumn(1).getAlignment());
        // Write protection set explicitly.
        assertTrue(grid.getColumn(2).isReadOnly());
        // Type "u" is write-protected and leading aligned by default.
        assertTrue(grid.getColumn(3).isReadOnly());
        assertEquals(CellAlignment.START, grid.getColumn(3).getAlignment());
        // The default without a value.
        assertEquals(ColumnType.STRING, grid.getColumn(4).getType());
        assertFalse(grid.getColumn(4).isReadOnly());
    }

    @Test
    public void theSortOrderIsTakenOverAndKeepsItsOrder() {
        Grid grid = grid().configuration(
                "{\"sort\":[{\"C\":\"d\"},{\"A\":\"a\"}]}");

        assertEquals(Arrays.asList(
                        new SortCriterion(2, SortDirection.DESCENDING),
                        new SortCriterion(0, SortDirection.ASCENDING)),
                grid.getSortOrder());
    }

    @Test
    public void theSortOrderRefersToTheNewDisplayOrder() {
        Grid grid = grid().configuration(
                "{\"order\":[\"C\",\"A\"],\"sort\":[{\"A\":\"a\"}]}");

        assertEquals(1, grid.getSortRank(1));
        assertEquals(SortDirection.ASCENDING, grid.getSortDirection(1));
    }

    @Test
    public void theUnknownAndTheInvalidAreSkipped() {
        Grid grid = grid().configuration(
                "{\"nonsense\":42,"
                        + "\"columns\":{\"A\":{\"t\":\"q\",\"a\":\"x\",\"r\":\"maybe\"},"
                        + "\"DoesNotExist\":{\"t\":\"i\"}},"
                        + "\"order\":[\"C\",\"DoesNotExist\",\"A\"],"
                        + "\"columns_hidden\":[\"DoesNotExist\"],"
                        + "\"sort\":[{\"DoesNotExist\":\"d\"},\"broken\"]}");

        assertEquals(Arrays.asList("C", "A"), visible(grid));
        assertEquals(ColumnType.STRING, grid.getColumn(1).getType());
        assertEquals(CellAlignment.START, grid.getColumn(1).getAlignment());
        assertFalse(grid.getColumn(1).isReadOnly());
        assertTrue(grid.getSortOrder().isEmpty());
    }

    @Test
    public void theWidthOfTheFixedAreaIsTakenOverAndClamped() {
        assertEquals(0.4f, grid().getFixedBoundaryFraction(), 0.001f);

        assertEquals(0.25f,
                grid().configuration("{\"width_fixed\":25}").getFixedBoundaryFraction(), 0.001f);
        // Outside the permitted range it is clamped.
        assertEquals(Grid.MIN_FIXED_BOUNDARY_FRACTION,
                grid().configuration("{\"width_fixed\":5}").getFixedBoundaryFraction(), 0.001f);
        assertEquals(Grid.MAX_FIXED_BOUNDARY_FRACTION,
                grid().configuration("{\"width_fixed\":200}").getFixedBoundaryFraction(), 0.001f);
        // A missing or unusable value leaves the default in place.
        assertEquals(0.4f,
                grid().configuration("{\"width_fixed\":\"wide\"}").getFixedBoundaryFraction(),
                0.001f);
        assertEquals(0.4f, grid().configuration("{}").getFixedBoundaryFraction(), 0.001f);
    }

    @Test
    public void theWidthOfTheFixedAreaIsWritten() throws Exception {
        Grid grid = grid().configuration("{\"width_fixed\":38}");

        JSONObject json = new JSONObject(grid.toConfigurationJson());

        assertEquals(38, json.getInt("width_fixed"));
    }

    @Test
    public void jsonThatCannotBeReadHasNoEffect() {
        Grid grid = grid().configuration("this is not JSON");

        assertEquals(Arrays.asList("A", "B", "C", "D", "E"), visible(grid));
        assertTrue(grid.getSortOrder().isEmpty());
    }

    @Test
    public void withoutANameOfItsOwnTheTitleServesAsTheKey() {
        Grid grid = new Grid(new TestDataSource(3, 5))
                .column("Surname")
                .column("City")
                .configuration("{\"order\":[\"City\"]}");

        assertEquals(Arrays.asList("City"), visible(grid));
    }

    // --------------------------------------------------------------- Writing

    @Test
    public void theWrittenStateContainsEveryField() throws Exception {
        Grid grid = grid()
                .ignoreColumns("E")
                .configuration("{\"columns_fixed\":[\"B\"],\"order\":[\"A\"],"
                        + "\"columns_hidden\":[\"C\"],\"sort\":[{\"A\":\"d\"}]}");

        JSONObject json = new JSONObject(grid.toConfigurationJson());

        assertEquals(Arrays.asList("B"), names(json.getJSONArray("columns_fixed")));
        // C is hidden but stays part of the order.
        assertEquals(Arrays.asList("A", "C"), names(json.getJSONArray("order")));
        assertEquals(Arrays.asList("C"), names(json.getJSONArray("columns_hidden")));

        JSONArray sort = json.getJSONArray("sort");
        assertEquals(1, sort.length());
        assertEquals("d", sort.getJSONObject(0).getString("A"));

        // D is neither listed nor hidden - it turns up nowhere.
        assertFalse(names(json.getJSONArray("order")).contains("D"));
        assertFalse(names(json.getJSONArray("columns_hidden")).contains("D"));
        // E is ignored and is missing from the column properties as well.
        assertTrue(json.getJSONObject("columns").has("D"));
        assertFalse(json.getJSONObject("columns").has("E"));
    }

    @Test
    public void readingAgainYieldsTheSameState() {
        Grid first = grid()
                .ignoreColumns("E")
                .configuration("{\"columns\":{\"A\":{\"t\":\"i\"},\"B\":{\"a\":\"c\",\"r\":\"y\"}},"
                        + "\"columns_fixed\":[\"B\"],\"order\":[\"A\",\"D\"],"
                        + "\"columns_hidden\":[\"C\"],\"width_fixed\":42,"
                        + "\"sort\":[{\"D\":\"d\"},{\"A\":\"a\"}]}");
        String json = first.toConfigurationJson();

        Grid again = grid().ignoreColumns("E").configuration(json);

        assertEquals(visible(first), visible(again));
        assertEquals(first.getFixedColumnCount(), again.getFixedColumnCount());
        assertEquals(first.getSortOrder(), again.getSortOrder());
        assertEquals(0.42f, again.getFixedBoundaryFraction(), 0.001f);
        assertEquals(ColumnType.INTEGER, again.getColumn(1).getType());
        assertEquals(CellAlignment.CENTER, again.getColumn(0).getAlignment());
        assertTrue(again.getColumn(0).isReadOnly());
        assertEquals(json, again.toConfigurationJson());
    }

    private static List<String> names(JSONArray array) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            names.add(array.optString(i));
        }
        return names;
    }

    @Test
    public void sortingByAHiddenColumnIsDiscarded() {
        Grid grid = grid().configuration(
                "{\"columns_hidden\":[\"A\"],\"sort\":[{\"A\":\"a\"},{\"B\":\"d\"}]}");

        // Only the criterion of the column that stays visible is left over.
        assertEquals(1, grid.getSortOrder().size());
        assertEquals("B", grid.getColumn(grid.getSortOrder().get(0).columnIndex()).getName());
        assertEquals(SortDirection.DESCENDING, grid.getSortOrder().get(0).direction());
    }
}
