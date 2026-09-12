package com.movingbits.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A comparison: on the left a term, then the operator, and on its right as many operands as
 * the operator takes.
 *
 * @param left     what is compared
 * @param operator the comparison
 * @param right    the operands on the right; empty for {@code IS NULL}
 */
public record SqlComparison(SqlTerm left, SqlOperator operator, List<SqlTerm> right) implements SqlCondition {

    public SqlComparison(final SqlTerm left, final SqlOperator operator, final List<SqlTerm> right) {
        this.left = left;
        this.operator = operator;
        final List<SqlTerm> copy = new ArrayList<>();
        if (right != null) {
            for (SqlTerm term : right) {
                if (term != null) {
                    copy.add(term);
                }
            }
        }
        this.right = Collections.unmodifiableList(copy);
    }

    /** A comparison with a single operand on the right. */
    public SqlComparison(final SqlTerm left, final SqlOperator operator, final SqlTerm right) {
        this(left, operator, Collections.singletonList(right));
    }

    @Override
    public boolean hasAggregate() {
        if (left != null && left.hasAggregate()) {
            return true;
        }
        for (SqlTerm term : right) {
            if (term.hasAggregate()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        return left == null && right.isEmpty();
    }

    @Override
    public boolean isComplete() {
        return left != null && operator != null && operator.fits(right.size());
    }

    @Override
    public String label() {
        final StringBuilder label = new StringBuilder();
        label.append(left == null ? "?" : left.label());
        label.append(" ").append(operator == null ? "?" : operator.getSql());
        for (int i = 0; i < right.size(); i++) {
            final String separator = i == 0 ? " " : (operator == SqlOperator.BETWEEN ? " AND " : ", ");
            label.append(separator).append(right.get(i).label());
        }
        return label.toString();
    }
}
