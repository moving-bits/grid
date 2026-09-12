package com.movingbits.grid;

import androidx.annotation.StringRes;

/**
 * What keeps a statement from being run. The editor shows the first finding under the
 * preview and only unlocks the button once none is left.
 */
public enum SqlFinding {

    /** No table chosen; without one there is nothing to read or change. */
    NO_TABLE(R.string.grid_sql_finding_no_table),
    /** No column of the result chosen. */
    NO_COLUMNS(R.string.grid_sql_finding_no_columns),
    /** An {@code UPDATE} without a single assignment. */
    NO_ASSIGNMENTS(R.string.grid_sql_finding_no_assignments),
    /** A column of the result carries no name, or two of them carry the same. */
    ALIAS_MISSING(R.string.grid_sql_finding_alias_missing),
    ALIAS_DUPLICATE(R.string.grid_sql_finding_alias_duplicate),
    /** A join without a table or without a complete condition. */
    JOIN_INCOMPLETE(R.string.grid_sql_finding_join_incomplete),
    /** A condition whose parts are not all there. */
    CONDITION_INCOMPLETE(R.string.grid_sql_finding_condition_incomplete),
    /** {@code *} together with a join: the result would carry columns of the same name. */
    STAR_WITH_JOIN(R.string.grid_sql_finding_star_with_join),
    /** An aggregate in {@code WHERE}; that is what {@code HAVING} is for. */
    AGGREGATE_IN_WHERE(R.string.grid_sql_finding_aggregate_in_where),
    /** A plain column next to an aggregate without being grouped by. */
    GROUP_BY_MISSING(R.string.grid_sql_finding_group_by_missing),
    /** {@code HAVING} without anything to condense. */
    HAVING_WITHOUT_GROUP(R.string.grid_sql_finding_having_without_group),
    /** Sorted by something that is no column of the result. */
    ORDER_NOT_IN_RESULT(R.string.grid_sql_finding_order_not_in_result),
    /** A function without a name of the shape SQL expects, or an unknown one. */
    FUNCTION_UNKNOWN(R.string.grid_sql_finding_function_unknown),
    /** A function call without a single parameter. */
    FUNCTION_WITHOUT_ARGUMENTS(R.string.grid_sql_finding_function_without_arguments),
    /** An aggregate as the new value of an {@code UPDATE}. */
    AGGREGATE_IN_ASSIGNMENT(R.string.grid_sql_finding_aggregate_in_assignment),
    /** A join in an {@code UPDATE}; it changes exactly one table. */
    JOIN_IN_UPDATE(R.string.grid_sql_finding_join_in_update),
    /** An aggregate other than {@code COUNT} applied to {@code *}. */
    STAR_NOT_COUNTABLE(R.string.grid_sql_finding_star_not_countable);

    private final int labelRes;

    SqlFinding(final @StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    /** The message shown in the editor. */
    @StringRes
    public int getLabelRes() {
        return labelRes;
    }
}
