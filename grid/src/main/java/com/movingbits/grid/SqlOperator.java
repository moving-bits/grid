package com.movingbits.grid;

import androidx.annotation.StringRes;

import java.util.Locale;

/**
 * Comparison of a condition in the SQL editor.
 *
 * <p>How many operands an operator takes on its right differs: {@code IS NULL} takes none,
 * most take one, {@code BETWEEN} takes two and {@code IN} takes any number.</p>
 */
public enum SqlOperator {

    EQUALS("eq", "=", 1, R.string.grid_operator_equals),
    NOT_EQUALS("ne", "<>", 1, R.string.grid_operator_not_equals),
    LESS("lt", "<", 1, R.string.grid_operator_less),
    LESS_OR_EQUAL("le", "<=", 1, R.string.grid_operator_less_or_equal),
    GREATER("gt", ">", 1, R.string.grid_operator_greater),
    GREATER_OR_EQUAL("ge", ">=", 1, R.string.grid_operator_greater_or_equal),
    /** Pattern comparison; the value carries the wildcards {@code %} and {@code _} itself. */
    LIKE("lk", "LIKE", 1, R.string.grid_sql_operator_like),
    NOT_LIKE("nlk", "NOT LIKE", 1, R.string.grid_sql_operator_not_like),
    IN("in", "IN", -1, R.string.grid_sql_operator_in),
    NOT_IN("nin", "NOT IN", -1, R.string.grid_sql_operator_not_in),
    BETWEEN("bw", "BETWEEN", 2, R.string.grid_sql_operator_between),
    IS_NULL("nu", "IS NULL", 0, R.string.grid_sql_operator_is_null),
    IS_NOT_NULL("nn", "IS NOT NULL", 0, R.string.grid_sql_operator_is_not_null);

    /**
     * Stands for "one operand or more" as the number of operands. The constants above spell
     * the value out: an enum constant may not look ahead to a field of its own class.
     */
    public static final int ANY = -1;

    private final String code;
    private final String sql;
    private final int operandCount;
    private final int labelRes;

    SqlOperator(final String code, final String sql, final int operandCount, final @StringRes int labelRes) {
        this.code = code;
        this.sql = sql;
        this.operandCount = operandCount;
        this.labelRes = labelRes;
    }

    public String getCode() {
        return code;
    }

    /** The operator as it stands in the statement. */
    public String getSql() {
        return sql;
    }

    /** How many operands stand on the right; {@link #ANY} for one or more. */
    public int getOperandCount() {
        return operandCount;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    /** {@code true} when that many operands suit this operator. */
    public boolean fits(final int count) {
        return operandCount == ANY ? count >= 1 : count == operandCount;
    }

    /**
     * Turns a short code from a stored statement back into an operator.
     *
     * @return the matching operator, or {@code null} if the code is unknown
     */
    public static SqlOperator fromCode(final String code) {
        if (code == null) {
            return null;
        }
        final String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (SqlOperator operator : values()) {
            if (operator.code.equals(normalized)) {
                return operator;
            }
        }
        return null;
    }
}
