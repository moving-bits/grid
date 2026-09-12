package com.movingbits.grid;

import java.util.Locale;

/**
 * An SQL aggregate function: it condenses the rows of a group into a single value.
 *
 * <p>Which type the result carries follows from the function, and for {@code SUM}, {@code MIN}
 * and {@code MAX} from their argument – the smallest of a set of whole numbers is a whole
 * number again.</p>
 */
public enum SqlAggregate {

    COUNT("cnt", "COUNT", ColumnType.INTEGER),
    SUM("sum", "SUM", null),
    /** Like {@code SUM}, but always a floating point number and never {@code NULL}. */
    TOTAL("tot", "TOTAL", ColumnType.FLOAT),
    AVG("avg", "AVG", ColumnType.FLOAT),
    MIN("min", "MIN", null),
    MAX("max", "MAX", null),
    /** Joins the values of a group into one text. */
    GROUP_CONCAT("gc", "GROUP_CONCAT", ColumnType.STRING);

    private final String code;
    private final String sql;
    /** The type of the result, or {@code null} when the argument decides. */
    private final ColumnType resultType;

    SqlAggregate(final String code, final String sql, final ColumnType resultType) {
        this.code = code;
        this.sql = sql;
        this.resultType = resultType;
    }

    public String getCode() {
        return code;
    }

    /** The function's name as it stands in the statement. */
    public String getSql() {
        return sql;
    }

    /**
     * The type of the result.
     *
     * @param argumentType type of the argument, as far as it is known
     */
    public ColumnType resultType(final ColumnType argumentType) {
        if (resultType != null) {
            return resultType;
        }
        return argumentType == null ? ColumnType.UNKNOWN : argumentType;
    }

    /** {@code true} when the function may be applied to {@code *} – only {@code COUNT} may. */
    public boolean allowsStar() {
        return this == COUNT;
    }

    /**
     * Turns a short code from a stored statement back into a function.
     *
     * @return the matching function, or {@code null} if the code is unknown
     */
    public static SqlAggregate fromCode(final String code) {
        if (code == null) {
            return null;
        }
        final String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (SqlAggregate aggregate : values()) {
            if (aggregate.code.equals(normalized)) {
                return aggregate;
            }
        }
        return null;
    }
}
