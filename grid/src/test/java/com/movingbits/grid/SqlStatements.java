package com.movingbits.grid;

import java.util.Arrays;
import java.util.Collections;

/**
 * Statements for the tests, clicked together the way the editor would do it.
 */
final class SqlStatements {

    /** Types as the demo database has them; enough for the tests. */
    static final SqlColumnTypes TYPES = (table, column) ->
            "popestimate".equals(column) || "area".equals(column) || "height".equals(column)
                    ? ColumnType.INTEGER
                    : ColumnType.STRING;

    private SqlStatements() {
    }

    static SqlColumnTerm column(final String table, final String name) {
        return new SqlColumnTerm(table, name);
    }

    static SqlValueTerm number(final String value) {
        return new SqlValueTerm(ColumnType.INTEGER, value);
    }

    static SqlValueTerm text(final String value) {
        return new SqlValueTerm(ColumnType.STRING, value);
    }

    /** {@code SELECT "cities"."city" AS "city" FROM "cities"} */
    static SqlStatement simpleSelect() {
        final SqlStatement statement = new SqlStatement();
        statement.setTable("cities");
        statement.getProjection().add(new SqlProjection(column("cities", "city"), "city"));
        return statement;
    }

    /** A grouped query over a join, as the demo shows it. */
    static SqlStatement groupedSelect() {
        final SqlStatement statement = new SqlStatement();
        statement.setTable("cities");
        statement.getJoins().add(new SqlJoin(SqlJoinType.INNER, "mountains",
                Collections.singletonList(new SqlComparison(
                        column("cities", "country"), SqlOperator.EQUALS, column("mountains", "country")))));
        statement.getProjection().add(new SqlProjection(column("cities", "country"), "country"));
        statement.getProjection().add(new SqlProjection(
                new SqlAggregateTerm(SqlAggregate.COUNT, new SqlStarTerm(), false), "number"));
        statement.getProjection().add(new SqlProjection(
                new SqlAggregateTerm(SqlAggregate.SUM, column("cities", "popestimate"), false), "population"));
        statement.getGroupBy().add(column("cities", "country"));
        statement.getHaving().getParts().add(new SqlComparison(
                new SqlAggregateTerm(SqlAggregate.COUNT, new SqlStarTerm(), false),
                SqlOperator.GREATER, number("2")));
        statement.getOrderBy().add(new SqlOrder("population", SortDirection.DESCENDING));
        return statement;
    }

    /** An update of one column with a condition. */
    static SqlStatement update() {
        final SqlStatement statement = new SqlStatement();
        statement.setKind(SqlKind.UPDATE);
        statement.setTable("cities");
        statement.getAssignments().add(new SqlAssignment("country", text("Germany")));
        statement.getWhere().getParts().add(new SqlComparison(
                column("cities", "city"), SqlOperator.IN, Arrays.asList(text("Berlin"), text("Hamburg"))));
        return statement;
    }
}
