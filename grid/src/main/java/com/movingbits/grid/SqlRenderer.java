package com.movingbits.grid;

import java.util.List;

/**
 * Turns a statement into SQL together with its parameters.
 *
 * <p>Everything is spelled out: every column of the result carries its name through
 * {@code AS "name"}, so that the enclosing query the grid pages and sorts with can address it.
 * {@code ORDER BY} always compares ignoring case - for numbers SQLite ignores the collating
 * sequence anyway, so the one rule serves both.</p>
 */
final class SqlRenderer {

    private SqlRenderer() {
            // utility class
    }

    /** The statement with its parameters, in the order they occur in the text. */
    static RenderedSql render(final SqlStatement statement) {
        final SqlText text = new SqlText();
        if (statement.getKind() == SqlKind.UPDATE) {
            renderUpdate(statement, text);
        } else {
            renderSelect(statement, text);
        }
        return new RenderedSql(text.sql(), text.parameters());
    }

    private static void renderSelect(final SqlStatement statement, final SqlText text) {
        text.append("SELECT ");
        if (statement.isDistinct()) {
            text.append("DISTINCT ");
        }
        final List<SqlProjection> projection = statement.getProjection();
        for (int i = 0; i < projection.size(); i++) {
            final SqlProjection column = projection.get(i);
            text.append(i == 0 ? "" : ", ");
            renderTerm(column.term(), text);
            // The star delivers the names of the table; everything else is named here.
            if (!column.isStar() && !column.alias().isEmpty()) {
                text.append(" AS ");
                text.identifier(column.alias());
            }
        }

        text.append(" FROM ");
        text.identifier(statement.getTable());
        for (SqlJoin join : statement.getJoins()) {
            text.append(" ").append(join.type().getSql()).append(" ");
            text.identifier(join.table());
            final List<SqlComparison> on = join.on();
            for (int i = 0; i < on.size(); i++) {
                text.append(i == 0 ? " ON " : " AND ");
                renderComparison(on.get(i), text);
            }
        }

        renderWhere(statement.getWhere(), text);

        final List<SqlTerm> groupBy = statement.getGroupBy();
        for (int i = 0; i < groupBy.size(); i++) {
            text.append(i == 0 ? " GROUP BY " : ", ");
            renderTerm(groupBy.get(i), text);
        }

        if (!statement.getHaving().isEmpty()) {
            text.append(" HAVING ");
            renderGroup(statement.getHaving(), text, false);
        }

        final List<SqlOrder> orderBy = statement.getOrderBy();
        for (int i = 0; i < orderBy.size(); i++) {
            final SqlOrder order = orderBy.get(i);
            text.append(i == 0 ? " ORDER BY " : ", ");
            text.identifier(order.alias());
            text.append(" COLLATE NOCASE");
            text.append(order.direction() == SortDirection.DESCENDING ? " DESC" : " ASC");
        }
    }

    private static void renderUpdate(final SqlStatement statement, final SqlText text) {
        text.append("UPDATE ");
        text.identifier(statement.getTable());
        text.append(" SET ");
        final List<SqlAssignment> assignments = statement.getAssignments();
        for (int i = 0; i < assignments.size(); i++) {
            final SqlAssignment assignment = assignments.get(i);
            text.append(i == 0 ? "" : ", ");
            text.identifier(assignment.column());
            text.append(" = ");
            renderTerm(assignment.value(), text);
        }
        renderWhere(statement.getWhere(), text);
    }

    private static void renderWhere(final SqlConditionGroup where, final SqlText text) {
        if (!where.isEmpty()) {
            text.append(" WHERE ");
            renderGroup(where, text, false);
        }
    }

    /**
     * @param parenthesize {@code true} for a nested group, which needs its own brackets
     */
    private static void renderGroup(final SqlConditionGroup group, final SqlText text, final boolean parenthesize) {
        if (parenthesize) {
            text.append("(");
        }
        final List<SqlCondition> parts = group.getParts();
        boolean first = true;
        for (SqlCondition part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!first) {
                text.append(" ").append(group.getJunction().getSql()).append(" ");
            }
            first = false;
            renderCondition(part, text);
        }
        if (parenthesize) {
            text.append(")");
        }
    }

    private static void renderCondition(final SqlCondition condition, final SqlText text) {
        if (condition instanceof SqlConditionGroup group) {
            renderGroup(group, text, true);
        } else if (condition instanceof SqlComparison comparison) {
            renderComparison(comparison, text);
        }
    }

    private static void renderComparison(final SqlComparison comparison, final SqlText text) {
        renderTerm(comparison.left(), text);
        // The preview shows the statement while it is still being built, so a part that is
        // not there yet stands as a question mark instead of bringing the rendering down.
        if (comparison.operator() == null) {
            text.append(" ?");
            return;
        }
        text.append(" ").append(comparison.operator().getSql());

        final List<SqlTerm> right = comparison.right();
        if (right.isEmpty()) {
            return;
        }
        if (comparison.operator() == SqlOperator.BETWEEN && right.size() >= 2) {
            text.append(" ");
            renderTerm(right.get(0), text);
            text.append(" AND ");
            renderTerm(right.get(1), text);
            return;
        }
        if (comparison.operator().getOperandCount() == SqlOperator.ANY) {
            text.append(" (");
            for (int i = 0; i < right.size(); i++) {
                text.append(i == 0 ? "" : ", ");
                renderTerm(right.get(i), text);
            }
            text.append(")");
            return;
        }
        text.append(" ");
        renderTerm(right.get(0), text);
    }

    private static void renderTerm(final SqlTerm term, final SqlText text) {
        if (term == null) {
            text.append("?");
        } else if (term instanceof SqlColumnTerm column) {
            text.qualified(column.table(), column.column());
        } else if (term instanceof SqlValueTerm value) {
            text.parameter(value.type(), value.text());
        } else if (term instanceof SqlStarTerm) {
            text.append("*");
        } else if (term instanceof SqlAggregateTerm aggregate) {
            text.append(aggregate.function().getSql()).append("(");
            if (aggregate.distinct()) {
                text.append("DISTINCT ");
            }
            renderTerm(aggregate.argument(), text);
            text.append(")");
        } else if (term instanceof SqlFunctionTerm function) {
            text.append(function.name()).append("(");
            final List<SqlTerm> arguments = function.arguments();
            for (int i = 0; i < arguments.size(); i++) {
                text.append(i == 0 ? "" : ", ");
                renderTerm(arguments.get(i), text);
            }
            text.append(")");
        }
    }
}
