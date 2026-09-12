package com.movingbits.grid;

import java.util.ArrayList;
import java.util.List;

/**
 * A statement clicked together out of blocks: what is read or changed, from where, under which
 * conditions and in which order.
 *
 * <p>The object is a working document of the editor and therefore mutable: its lists are
 * handed out as they are and are edited in place. {@link #render()} turns it into a statement
 * with parameters, {@link SqlValidation} checks it, {@link #toJson()} stores it.</p>
 *
 * <pre>{@code
 * SqlStatement statement = new SqlStatement();
 * statement.setTable("cities");
 * statement.getProjection().add(new SqlProjection(new SqlColumnTerm("cities", "city"), "city"));
 * statement.getWhere().getParts().add(new SqlComparison(
 *         new SqlColumnTerm("cities", "popestimate"),
 *         SqlOperator.GREATER,
 *         new SqlValueTerm(ColumnType.INTEGER, "1000000")));
 * RenderedSql sql = statement.render();
 * }</pre>
 */
public final class SqlStatement {

    private SqlKind kind = SqlKind.SELECT;
    private boolean distinct;
    /** The table read from, or the one being changed; empty while none is chosen. */
    private String table = "";
    private final List<SqlJoin> joins = new ArrayList<>();
    private final List<SqlProjection> projection = new ArrayList<>();
    private final List<SqlAssignment> assignments = new ArrayList<>();
    private final SqlConditionGroup where = new SqlConditionGroup();
    private final List<SqlTerm> groupBy = new ArrayList<>();
    private final SqlConditionGroup having = new SqlConditionGroup();
    private final List<SqlOrder> orderBy = new ArrayList<>();

    public SqlKind getKind() {
        return kind;
    }

    public void setKind(final SqlKind kind) {
        this.kind = kind == null ? SqlKind.SELECT : kind;
    }

    /** {@code true} when equal rows of the result are shown only once. */
    public boolean isDistinct() {
        return distinct;
    }

    public void setDistinct(final boolean distinct) {
        this.distinct = distinct;
    }

    public String getTable() {
        return table;
    }

    public void setTable(final String table) {
        this.table = table == null ? "" : table;
    }

    /** The joined tables, meant to be edited in place. */
    public List<SqlJoin> getJoins() {
        return joins;
    }

    /** The columns of the result, meant to be edited in place. */
    public List<SqlProjection> getProjection() {
        return projection;
    }

    /** The assignments of an {@code UPDATE}, meant to be edited in place. */
    public List<SqlAssignment> getAssignments() {
        return assignments;
    }

    /** The {@code WHERE} clause; an empty group means no clause at all. */
    public SqlConditionGroup getWhere() {
        return where;
    }

    /** The columns grouped by, meant to be edited in place. */
    public List<SqlTerm> getGroupBy() {
        return groupBy;
    }

    /** The {@code HAVING} clause; an empty group means no clause at all. */
    public SqlConditionGroup getHaving() {
        return having;
    }

    /** The sort criteria, meant to be edited in place. */
    public List<SqlOrder> getOrderBy() {
        return orderBy;
    }

    /** Every table that takes part: the one read from and the joined ones. */
    public List<String> getTables() {
        final List<String> tables = new ArrayList<>();
        if (!table.isEmpty()) {
            tables.add(table);
        }
        for (SqlJoin join : joins) {
            if (!join.table().isEmpty() && !tables.contains(join.table())) {
                tables.add(join.table());
            }
        }
        return tables;
    }

    /** {@code true} when one of the result columns is an aggregate. */
    public boolean hasAggregates() {
        for (SqlProjection column : projection) {
            if (column.term().hasAggregate()) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code true} when this statement changes rows without saying which ones - the case the
     * editor asks about before it goes ahead.
     */
    public boolean isCritical() {
        return kind == SqlKind.UPDATE && where.isEmpty();
    }

    /** The statement with its parameters, ready for the database. */
    public RenderedSql render() {
        return SqlRenderer.render(this);
    }

    /** The statement as a JSON string, to be read again through {@link #parse(String)}. */
    public String toJson() {
        return SqlStatementJson.toJson(this);
    }

    /**
     * Reads a stored statement. The evaluation is as forgiving as it is elsewhere in the
     * library: what cannot be read yields an empty statement, unusable parts are skipped.
     *
     * @param json a stored statement, or {@code null}
     */
    public static SqlStatement parse(final String json) {
        return SqlStatementJson.parse(json);
    }
}
