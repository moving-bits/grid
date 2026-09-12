package com.movingbits.grid;

import androidx.annotation.StringRes;

import java.util.Locale;

/** How a joined table is added to the query. */
public enum SqlJoinType {

    /** Only rows that have a counterpart on both sides. */
    INNER("j", "JOIN", R.string.grid_sql_join_inner),
    /** Every row of the left side, with empty fields where a counterpart is missing. */
    LEFT("lj", "LEFT JOIN", R.string.grid_sql_join_left);

    private final String code;
    private final String sql;
    private final int labelRes;

    SqlJoinType(final String code, final String sql, final @StringRes int labelRes) {
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
     * @return the matching type, or {@code null} if the code is unknown
     */
    public static SqlJoinType fromCode(final String code) {
        if (code == null) {
            return null;
        }
        final String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (SqlJoinType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
