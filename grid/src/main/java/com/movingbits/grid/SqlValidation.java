package com.movingbits.grid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks a clicked-together statement before it is run, without asking the database.
 *
 * <p>It is the first of two stages: this one judges the statement as a whole - are the parts
 * there, do they fit together, is what is sorted by part of the result. What only SQLite can
 * tell is left to the second stage, which has it prepare the statement without running it
 * ({@code SqlGrid.checkWithDatabase()}).</p>
 *
 * <p>The check has no side effects and knows nothing of Android, so the tests cover it
 * completely.</p>
 */
public final class SqlValidation {

    private SqlValidation() {
            // utility class
    }

    /** Checks a statement without functions of the application. */
    public static List<SqlFinding> check(final SqlStatement statement) {
        return check(statement, null);
    }

    /**
     * Checks a statement.
     *
     * @param statement      the statement
     * @param extraFunctions function names the application allows on top, or {@code null}
     * @return the findings without repetition; empty when the statement may be run
     */
    public static List<SqlFinding> check(final SqlStatement statement, final Collection<String> extraFunctions) {
        final Set<SqlFinding> findings = new LinkedHashSet<>();
        if (statement == null) {
            findings.add(SqlFinding.NO_TABLE);
            return unmodifiable(findings);
        }

        if (statement.getTable().isEmpty()) {
            findings.add(SqlFinding.NO_TABLE);
        }
        if (statement.getKind() == SqlKind.UPDATE) {
            checkUpdate(statement, findings);
        } else {
            checkSelect(statement, findings);
        }

        // The conditions belong to both kinds, and so do the function calls in every corner.
        if (statement.getWhere().hasAggregate()) {
            findings.add(SqlFinding.AGGREGATE_IN_WHERE);
        }
        if (!statement.getWhere().isComplete() || !statement.getHaving().isComplete()) {
            findings.add(SqlFinding.CONDITION_INCOMPLETE);
        }
        checkTerms(statement, findings, extraFunctions);

        return unmodifiable(findings);
    }

    private static void checkSelect(final SqlStatement statement, final Set<SqlFinding> findings) {
        final List<SqlProjection> projection = statement.getProjection();
        // An empty row of columns asks for every one of them, which only works as long as one
        // table answers: two of them would deliver columns of the same name.
        if (statement.showsEveryColumn() && !statement.getJoins().isEmpty()) {
            findings.add(SqlFinding.STAR_WITH_JOIN);
        }

        for (SqlJoin join : statement.getJoins()) {
            if (!join.isComplete()) {
                findings.add(SqlFinding.JOIN_INCOMPLETE);
            }
        }

        final Set<String> aliases = new LinkedHashSet<>();
        for (SqlProjection column : projection) {
            if (column.isStar()) {
                if (!statement.getJoins().isEmpty()) {
                    findings.add(SqlFinding.STAR_WITH_JOIN);
                }
                continue;
            }
            if (column.alias().isEmpty()) {
                findings.add(SqlFinding.ALIAS_MISSING);
            } else if (!aliases.add(column.alias())) {
                findings.add(SqlFinding.ALIAS_DUPLICATE);
            }
        }

        // Condensed and plain columns only go together when the plain ones say what forms a
        // group.
        if (statement.hasAggregates()) {
            for (SqlProjection column : projection) {
                if (!column.isStar() && !column.term().hasAggregate()
                        && !isGroupedBy(statement, column.term())) {
                    findings.add(SqlFinding.GROUP_BY_MISSING);
                }
            }
        }

        if (!statement.getHaving().isEmpty()
                && statement.getGroupBy().isEmpty() && !statement.getHaving().hasAggregate()) {
            findings.add(SqlFinding.HAVING_WITHOUT_GROUP);
        }

        for (SqlOrder order : statement.getOrderBy()) {
            if (!aliases.contains(order.alias())) {
                findings.add(SqlFinding.ORDER_NOT_IN_RESULT);
            }
        }
    }

    private static void checkUpdate(final SqlStatement statement, final Set<SqlFinding> findings) {
        if (statement.getAssignments().isEmpty()) {
            findings.add(SqlFinding.NO_ASSIGNMENTS);
        }
        if (!statement.getJoins().isEmpty()) {
            findings.add(SqlFinding.JOIN_IN_UPDATE);
        }
        for (SqlAssignment assignment : statement.getAssignments()) {
            if (assignment.value().hasAggregate()) {
                findings.add(SqlFinding.AGGREGATE_IN_ASSIGNMENT);
            }
        }
    }

    /** {@code true} when this term is one of the columns forming a group. */
    private static boolean isGroupedBy(final SqlStatement statement, final SqlTerm term) {
        for (SqlTerm grouped : statement.getGroupBy()) {
            if (grouped.equals(term)) {
                return true;
            }
        }
        return false;
    }

    /** Walks every term of the statement and checks the function calls among them. */
    private static void checkTerms(final SqlStatement statement, final Set<SqlFinding> findings,
                                   final Collection<String> extraFunctions) {
        final List<SqlTerm> terms = new ArrayList<>();
        for (SqlProjection column : statement.getProjection()) {
            terms.add(column.term());
        }
        for (SqlAssignment assignment : statement.getAssignments()) {
            terms.add(assignment.value());
        }
        terms.addAll(statement.getGroupBy());
        collectTerms(statement.getWhere(), terms);
        collectTerms(statement.getHaving(), terms);
        for (SqlJoin join : statement.getJoins()) {
            for (SqlComparison comparison : join.on()) {
                collectTerms(comparison, terms);
            }
        }

        for (SqlTerm term : terms) {
            checkTerm(term, findings, extraFunctions);
        }
    }

    private static void collectTerms(final SqlCondition condition, final List<SqlTerm> terms) {
        if (condition instanceof SqlConditionGroup group) {
            for (SqlCondition part : group.getParts()) {
                collectTerms(part, terms);
            }
        } else if (condition instanceof SqlComparison comparison) {
            if (comparison.left() != null) {
                terms.add(comparison.left());
            }
            terms.addAll(comparison.right());
        }
    }

    private static void checkTerm(final SqlTerm term, final Set<SqlFinding> findings,
                                  final Collection<String> extraFunctions) {
        if (term instanceof SqlFunctionTerm function) {
            if (!SqlFunctions.isAllowed(function.name(), extraFunctions)) {
                findings.add(SqlFinding.FUNCTION_UNKNOWN);
            }
            if (function.arguments().isEmpty()) {
                findings.add(SqlFinding.FUNCTION_WITHOUT_ARGUMENTS);
            }
            for (SqlTerm argument : function.arguments()) {
                checkTerm(argument, findings, extraFunctions);
            }
        } else if (term instanceof SqlAggregateTerm aggregate) {
            if (aggregate.argument() instanceof SqlStarTerm && !aggregate.function().allowsStar()) {
                findings.add(SqlFinding.STAR_NOT_COUNTABLE);
            }
            checkTerm(aggregate.argument(), findings, extraFunctions);
        }
    }

    private static List<SqlFinding> unmodifiable(final Set<SqlFinding> findings) {
        return Collections.unmodifiableList(new ArrayList<>(findings));
    }
}
