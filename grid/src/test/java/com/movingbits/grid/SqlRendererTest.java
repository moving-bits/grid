package com.movingbits.grid;

import static com.movingbits.grid.SqlStatements.column;
import static com.movingbits.grid.SqlStatements.number;
import static com.movingbits.grid.SqlStatements.text;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Checks the statement that a clicked-together model turns into, and that values never end up
 * in its text.
 */
public class SqlRendererTest {

    @Test
    public void aPlainQueryNamesItsColumns() {
        RenderedSql sql = SqlStatements.simpleSelect().render();

        assertEquals("SELECT \"cities\".\"city\" AS \"city\" FROM \"cities\"", sql.sql());
        assertEquals(Collections.emptyList(), sql.parameters());
    }

    @Test
    public void theStarStaysWithoutAName() {
        SqlStatement statement = new SqlStatement();
        statement.setTable("cities");
        statement.setDistinct(true);
        statement.getProjection().add(new SqlProjection(new SqlStarTerm(), ""));

        assertEquals("SELECT DISTINCT * FROM \"cities\"", statement.render().sql());
    }

    @Test
    public void valuesBecomeParameters() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.getWhere().getParts().add(new SqlComparison(
                column("cities", "popestimate"), SqlOperator.GREATER, number("1000000")));
        statement.getWhere().getParts().add(new SqlComparison(
                column("cities", "country"), SqlOperator.LIKE, text("Ger%")));

        RenderedSql sql = statement.render();

        assertEquals("SELECT \"cities\".\"city\" AS \"city\" FROM \"cities\""
                        + " WHERE \"cities\".\"popestimate\" > CAST(? AS INTEGER)"
                        + " AND \"cities\".\"country\" LIKE ?",
                sql.sql());
        assertEquals(Arrays.asList("1000000", "Ger%"), sql.parameters());
    }

    @Test
    public void anEmptyValueStandsForNull() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.getWhere().getParts().add(new SqlComparison(
                column("cities", "country"), SqlOperator.EQUALS, text("")));

        RenderedSql sql = statement.render();

        assertEquals(1, sql.parameters().size());
        assertNull(sql.parameters().get(0));
    }

    @Test
    public void operatorsWithoutOrWithSeveralOperands() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.getWhere().getParts().add(new SqlComparison(
                column("cities", "area"), SqlOperator.IS_NULL, Collections.emptyList()));
        statement.getWhere().getParts().add(new SqlComparison(
                column("cities", "popestimate"), SqlOperator.BETWEEN,
                Arrays.asList(number("1000"), number("2000"))));
        statement.getWhere().getParts().add(new SqlComparison(
                column("cities", "city"), SqlOperator.IN, Arrays.asList(text("Berlin"), text("Rome"))));

        assertEquals("SELECT \"cities\".\"city\" AS \"city\" FROM \"cities\""
                        + " WHERE \"cities\".\"area\" IS NULL"
                        + " AND \"cities\".\"popestimate\" BETWEEN CAST(? AS INTEGER) AND CAST(? AS INTEGER)"
                        + " AND \"cities\".\"city\" IN (?, ?)",
                statement.render().sql());
    }

    @Test
    public void groupsNestAndCarryTheirOwnJunction() {
        SqlStatement statement = SqlStatements.simpleSelect();
        SqlConditionGroup inner = new SqlConditionGroup(SqlJunction.OR, Arrays.asList(
                new SqlComparison(column("cities", "country"), SqlOperator.EQUALS, text("Italy")),
                new SqlComparison(column("cities", "country"), SqlOperator.EQUALS, text("Spain"))));
        statement.getWhere().getParts().add(new SqlComparison(
                column("cities", "popestimate"), SqlOperator.GREATER, number("100")));
        statement.getWhere().getParts().add(inner);

        assertEquals("SELECT \"cities\".\"city\" AS \"city\" FROM \"cities\""
                        + " WHERE \"cities\".\"popestimate\" > CAST(? AS INTEGER)"
                        + " AND (\"cities\".\"country\" = ? OR \"cities\".\"country\" = ?)",
                statement.render().sql());
    }

    @Test
    public void aJoinedTableWithGroupHavingAndOrder() {
        assertEquals("SELECT \"cities\".\"country\" AS \"country\", COUNT(*) AS \"number\","
                        + " SUM(\"cities\".\"popestimate\") AS \"population\""
                        + " FROM \"cities\" JOIN \"mountains\""
                        + " ON \"cities\".\"country\" = \"mountains\".\"country\""
                        + " GROUP BY \"cities\".\"country\""
                        + " HAVING COUNT(*) > CAST(? AS INTEGER)"
                        + " ORDER BY \"population\" COLLATE NOCASE DESC",
                SqlStatements.groupedSelect().render().sql());
    }

    @Test
    public void functionCallsNest() {
        SqlStatement statement = new SqlStatement();
        statement.setTable("cities");
        statement.getProjection().add(new SqlProjection(new SqlFunctionTerm("round", Arrays.asList(
                new SqlAggregateTerm(SqlAggregate.AVG, column("cities", "area"), false),
                number("2"))), "average"));

        assertEquals("SELECT round(AVG(\"cities\".\"area\"), CAST(? AS INTEGER)) AS \"average\""
                        + " FROM \"cities\"",
                statement.render().sql());
    }

    @Test
    public void distinctInsideAnAggregate() {
        SqlStatement statement = new SqlStatement();
        statement.setTable("cities");
        statement.getProjection().add(new SqlProjection(
                new SqlAggregateTerm(SqlAggregate.COUNT, column("cities", "country"), true), "countries"));

        assertEquals("SELECT COUNT(DISTINCT \"cities\".\"country\") AS \"countries\" FROM \"cities\"",
                statement.render().sql());
    }

    @Test
    public void anUpdateSetsAndNarrowsDown() {
        RenderedSql sql = SqlStatements.update().render();

        assertEquals("UPDATE \"cities\" SET \"country\" = ?"
                + " WHERE \"cities\".\"city\" IN (?, ?)", sql.sql());
        assertEquals(Arrays.asList("Germany", "Berlin", "Hamburg"), sql.parameters());
    }

    @Test
    public void quotationMarksInANameAreDoubled() {
        SqlStatement statement = new SqlStatement();
        statement.setTable("od\"d");
        statement.getProjection().add(new SqlProjection(column("od\"d", "a\"b"), "a\"b"));

        assertEquals("SELECT \"od\"\"d\".\"a\"\"b\" AS \"a\"\"b\" FROM \"od\"\"d\"",
                statement.render().sql());
    }

    @Test
    public void anIncompleteStatementStillRenders() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.getWhere().getParts().add(new SqlComparison(
                column("cities", "city"), null, Collections.emptyList()));

        assertEquals("SELECT \"cities\".\"city\" AS \"city\" FROM \"cities\""
                + " WHERE \"cities\".\"city\" ?", statement.render().sql());
    }
}
