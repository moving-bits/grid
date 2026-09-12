package com.movingbits.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

/**
 * Checks that a statement survives being stored and read again - what a change of screen
 * orientation and a stored favourite both need.
 */
public class SqlStatementJsonTest {

    /** The statement once around: stored, read, stored again. */
    private static void assertSurvives(final SqlStatement statement) {
        final SqlStatement restored = SqlStatement.parse(statement.toJson());

        assertEquals(statement.toJson(), restored.toJson());
        assertEquals(statement.render().sql(), restored.render().sql());
        assertEquals(statement.render().parameters(), restored.render().parameters());
    }

    @Test
    public void aPlainQuerySurvives() {
        assertSurvives(SqlStatements.simpleSelect());
    }

    @Test
    public void aGroupedQueryOverAJoinSurvives() {
        assertSurvives(SqlStatements.groupedSelect());
    }

    @Test
    public void anUpdateSurvives() {
        assertSurvives(SqlStatements.update());
    }

    @Test
    public void nestedGroupsAndFunctionsSurvive() {
        SqlStatement statement = SqlStatements.simpleSelect();
        statement.setDistinct(true);
        statement.getProjection().add(new SqlProjection(new SqlFunctionTerm("round",
                Collections.singletonList(new SqlAggregateTerm(
                        SqlAggregate.AVG, SqlStatements.column("cities", "area"), true))), "average"));
        statement.getWhere().setJunction(SqlJunction.OR);
        statement.getWhere().getParts().add(new SqlConditionGroup(SqlJunction.AND,
                Collections.singletonList(new SqlComparison(
                        SqlStatements.column("cities", "area"),
                        SqlOperator.IS_NOT_NULL, Collections.emptyList()))));
        statement.getGroupBy().add(SqlStatements.column("cities", "city"));

        assertSurvives(statement);
    }

    @Test
    public void theKindAndTheStarSurvive() {
        SqlStatement statement = new SqlStatement();
        statement.setTable("cities");
        statement.getProjection().add(new SqlProjection(new SqlStarTerm(), ""));

        SqlStatement restored = SqlStatement.parse(statement.toJson());

        assertEquals(SqlKind.SELECT, restored.getKind());
        assertTrue(restored.getProjection().get(0).isStar());
    }

    @Test
    public void whatCannotBeReadYieldsAnEmptyStatement() {
        for (String json : new String[]{null, "", "   ", "{", "[]", "no json at all"}) {
            SqlStatement statement = SqlStatement.parse(json);

            assertEquals(SqlKind.SELECT, statement.getKind());
            assertEquals("", statement.getTable());
            assertEquals(Collections.emptyList(), statement.getProjection());
            assertTrue(statement.getWhere().isEmpty());
        }
    }

    @Test
    public void unusablePartsAreSkipped() {
        // A condition without an operator, a column without a name, a sort criterion without
        // an alias: none of them can be used, all of them are left out.
        SqlStatement statement = SqlStatement.parse("{\"kind\":\"s\",\"table\":\"cities\","
                + "\"select\":[{\"term\":{\"t\":\"c\",\"table\":\"cities\",\"column\":\"\"},\"alias\":\"x\"}],"
                + "\"where\":{\"c\":\"g\",\"junction\":\"and\",\"parts\":["
                + "{\"c\":\"c\",\"left\":{\"t\":\"c\",\"table\":\"cities\",\"column\":\"city\"}}]},"
                + "\"order_by\":[{\"dir\":\"d\"}]}");

        assertEquals("cities", statement.getTable());
        assertEquals(Collections.emptyList(), statement.getProjection());
        assertTrue(statement.getWhere().isEmpty());
        assertEquals(Collections.emptyList(), statement.getOrderBy());
    }
}
