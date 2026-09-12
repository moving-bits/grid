package com.movingbits.grid;

import java.util.ArrayList;
import java.util.List;

/**
 * Conditions that apply together, either all of them or one of them. A group can hold groups
 * of its own, so that {@code a AND (b OR c)} can be clicked together.
 *
 * <p>The list of parts is meant to be edited: the editor adds to it, replaces entries and
 * takes them out again.</p>
 */
public final class SqlConditionGroup implements SqlCondition {

    private SqlJunction junction;
    private final List<SqlCondition> parts = new ArrayList<>();

    /** An empty group; without parts it contributes no clause at all. */
    public SqlConditionGroup() {
        this(SqlJunction.AND, null);
    }

    /**
     * @param junction how the parts apply together; {@code null} counts as
     *                 {@link SqlJunction#AND}
     * @param parts    the conditions, or {@code null} for none
     */
    public SqlConditionGroup(final SqlJunction junction, final List<SqlCondition> parts) {
        this.junction = junction == null ? SqlJunction.AND : junction;
        if (parts != null) {
            for (SqlCondition part : parts) {
                if (part != null) {
                    this.parts.add(part);
                }
            }
        }
    }

    public SqlJunction getJunction() {
        return junction;
    }

    public void setJunction(final SqlJunction junction) {
        this.junction = junction == null ? SqlJunction.AND : junction;
    }

    /** The parts, meant to be edited in place. */
    public List<SqlCondition> getParts() {
        return parts;
    }

    @Override
    public boolean hasAggregate() {
        for (SqlCondition part : parts) {
            if (part.hasAggregate()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        for (SqlCondition part : parts) {
            if (!part.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isComplete() {
        for (SqlCondition part : parts) {
            if (!part.isComplete()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String label() {
        final StringBuilder label = new StringBuilder("(");
        for (int i = 0; i < parts.size(); i++) {
            label.append(i == 0 ? "" : " " + junction.getSql() + " ").append(parts.get(i).label());
        }
        return label.append(")").toString();
    }

    /** A copy with copies of the nested groups; the editor works on one while it is open. */
    SqlConditionGroup copy() {
        final List<SqlCondition> copies = new ArrayList<>();
        for (SqlCondition part : parts) {
            copies.add(part instanceof SqlConditionGroup group ? group.copy() : part);
        }
        return new SqlConditionGroup(junction, copies);
    }
}
