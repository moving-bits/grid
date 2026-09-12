package com.movingbits.grid;

import static com.movingbits.grid.SqlStatements.column;
import static com.movingbits.grid.SqlStatements.number;
import static com.movingbits.grid.SqlStatements.text;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Checks what keeps a statement from being run. Every finding is provoked on its own.
 */
public class SqlValidationTest {

    private static List<SqlFinding> check(final SqlStatement statement) {
        return SqlValidation.check(statement);
    }

    private static void assertFinding(final SqlFinding expected, final SqlStatement statement) {
        assertTrue("expected " + expected + " but got " + check(statement),
                check(statement).contains(expected));
    }

    @Test
    public void aCompleteQueryHasNothingToComplainAbout() {
        assertEquals(Collections.emptyList(), check(SqlStatements.simpleSelect()));
        assertEquals(Collections.emptyList(), check(SqlStatements.groupedSelect()));
        assertEquals(Collections.emptyList(), check(SqlStatements.update()));
    }

    @Test
    public void withoutATableOrColumnsNothingRuns() {
        assertFinding(SqlFinding.NO_TABLE, new SqlStatement());
        assertFinding(SqlFinding.NO_COLUMNS, new SqlStatement());
        assertFinding(SqlFinding.NO_TABLE, null);
    }

    @Test
    public void anUpdateNeedsSomethingToAssign() {
        SqlStatement statement = new SqlStatement();
        statement.setKind(SqlKind.UPDATE);
        statement.setTable("cities");

        assertFinding(SqlFinding.NO_ASSIGNMENTS, statement);
    }

    @Test
    public void everyResultColumnNeedsItsOwnName() {
        SqlStatement missing = SqlStatements.simpleSelect();
        missing.getProjection().add(new SqlProjection(column("cities", "area"), ""));
        assertFinding(SqlFinding.ALIAS_MISSING, missing);

        SqlStatement duplicate = SqlStatements.simpleSelect();
        duplicate.getProjection().add(new SqlProjection(column("cities", "country"), "city"));
        assertFinding(SqlFinding.ALIAS_DUPLICATE, duplicate);
    }

    @Test
    public void aJoinNeedsATableAndACondition() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.getJoins().add(new SqlJoin(SqlJoinType.INNER, "mountains", null));

        assertFinding(SqlFinding.JOIN_INCOMPLETE, statement);
    }

    @Test
    public void anIncompleteConditionIsNoticed() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.getWhere().getParts().add(new SqlComparison(
                column("cities", "city"), SqlOperator.EQUALS, Collections.emptyList()));

        assertFinding(SqlFinding.CONDITION_INCOMPLETE, statement);
    }

    @Test
    public void theStarDoesNotGoWithAJoin() {
        SqlStatement statement = SqlStatements.groupedSelect();
        statement.getProjection().add(new SqlProjection(new SqlStarTerm(), ""));

        assertFinding(SqlFinding.STAR_WITH_JOIN, statement);
    }

    @Test
    public void aCondensedValueBelongsInHaving() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.getWhere().getParts().add(new SqlComparison(
                new SqlAggregateTerm(SqlAggregate.COUNT, new SqlStarTerm(), false),
                SqlOperator.GREATER, number("2")));

        assertFinding(SqlFinding.AGGREGATE_IN_WHERE, statement);
    }

    @Test
    public void mixingCondensedAndPlainColumnsNeedsAGroup() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.getProjection().add(new SqlProjection(
                new SqlAggregateTerm(SqlAggregate.COUNT, new SqlStarTerm(), false), "number"));
        assertFinding(SqlFinding.GROUP_BY_MISSING, statement);

        statement.getGroupBy().add(column("cities", "city"));
        assertEquals(Collections.emptyList(), check(statement));
    }

    @Test
    public void havingNeedsAGroupOrACondensedValue() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.getHaving().getParts().add(new SqlComparison(
                column("cities", "city"), SqlOperator.EQUALS, text("Rome")));

        assertFinding(SqlFinding.HAVING_WITHOUT_GROUP, statement);
    }

    @Test
    public void sortingNeedsAColumnOfTheResult() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.getOrderBy().add(new SqlOrder("population", SortDirection.ASCENDING));

        assertFinding(SqlFinding.ORDER_NOT_IN_RESULT, statement);
    }

    @Test
    public void onlyKnownFunctionsWithParametersPass() {
        SqlStatement unknown = SqlStatements.simpleSelect();
        unknown.getProjection().add(new SqlProjection(new SqlFunctionTerm(
                "drop_everything", Collections.singletonList(column("cities", "area"))), "x"));
        assertFinding(SqlFinding.FUNCTION_UNKNOWN, unknown);

        SqlStatement injected = SqlStatements.simpleSelect();
        injected.getProjection().add(new SqlProjection(new SqlFunctionTerm(
                "abs); DROP TABLE cities; --", Collections.singletonList(column("cities", "area"))), "x"));
        assertFinding(SqlFinding.FUNCTION_UNKNOWN, injected);

        SqlStatement empty = SqlStatements.simpleSelect();
        empty.getProjection().add(new SqlProjection(new SqlFunctionTerm("abs", null), "x"));
        assertFinding(SqlFinding.FUNCTION_WITHOUT_ARGUMENTS, empty);
    }

    @Test
    public void theApplicationCanAllowFunctionsOfItsOwn() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.getProjection().add(new SqlProjection(new SqlFunctionTerm(
                "distance", Collections.singletonList(column("cities", "area"))), "x"));

        assertTrue(SqlValidation.check(statement).contains(SqlFinding.FUNCTION_UNKNOWN));
        assertEquals(Collections.emptyList(),
                SqlValidation.check(statement, Collections.singletonList("distance")));
    }

    @Test
    public void anUpdateChangesOneTableWithoutCondensedValues() {
        SqlStatement joined = SqlStatements.update();
        joined.getJoins().add(new SqlJoin(SqlJoinType.INNER, "mountains",
                Collections.singletonList(new SqlComparison(
                        column("cities", "country"), SqlOperator.EQUALS, column("mountains", "country")))));
        assertFinding(SqlFinding.JOIN_IN_UPDATE, joined);

        SqlStatement condensed = SqlStatements.update();
        condensed.getAssignments().add(new SqlAssignment("area",
                new SqlAggregateTerm(SqlAggregate.SUM, column("cities", "area"), false)));
        assertFinding(SqlFinding.AGGREGATE_IN_ASSIGNMENT, condensed);
    }

    @Test
    public void onlyCountWorksOnTheStar() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.getProjection().add(new SqlProjection(
                new SqlAggregateTerm(SqlAggregate.SUM, new SqlStarTerm(), false), "sum"));

        assertFinding(SqlFinding.STAR_NOT_COUNTABLE, statement);
    }

    @Test
    public void anUpdateWithoutAConditionCountsAsCritical() {
        SqlStatement statement = SqlStatements.update();
        assertFalse(statement.isCritical());

        statement.getWhere().getParts().clear();
        assertTrue(statement.isCritical());
        // A query changes nothing and is never critical.
        assertFalse(SqlStatements.simpleSelect().isCritical());
    }

    @Test
    public void nestedFunctionsAreCheckedAsWell() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.getProjection().add(new SqlProjection(new SqlFunctionTerm("round", Arrays.asList(
                new SqlFunctionTerm("nowhere", Collections.singletonList(column("cities", "area"))),
                number("2"))), "x"));

        assertFinding(SqlFinding.FUNCTION_UNKNOWN, statement);
    }
}
