package com.movingbits.grid;

import androidx.annotation.StringRes;

import java.util.Locale;

/** How the conditions of a group apply together. */
public enum SqlJunction {

    /** Every condition has to be met. */
    AND("and", "AND", R.string.grid_sql_junction_and),
    /** One met condition is enough. */
    OR("or", "OR", R.string.grid_sql_junction_or);

    private final String code;
    private final String sql;
    private final int labelRes;

    SqlJunction(final String code, final String sql, final @StringRes int labelRes) {
        this.code = code;
        this.sql = sql;
        this.labelRes = labelRes;
    }

    public String getCode() {
        return code;
    }

    public String getSql() {
        return sql;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    /**
     * @return the matching junction, or {@code null} if the code is unknown
     */
    public static SqlJunction fromCode(final String code) {
        if (code == null) {
            return null;
        }
        final String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (SqlJunction junction : values()) {
            if (junction.code.equals(normalized)) {
                return junction;
            }
        }
        return null;
    }
}
