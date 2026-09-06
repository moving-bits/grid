package com.movingbits.grid;

import java.util.Locale;

/**
 * Semantic type of a column. It determines the default alignment, the default sorting and the
 * default write protection.
 */
public enum ColumnType {

    /** Text: leading aligned, sorted as text in a language-aware way. */
    STRING("s", CellAlignment.START, false),
    /** Whole number: trailing aligned, sorted numerically. */
    INTEGER("i", CellAlignment.END, false),
    /** Floating point number: trailing aligned, sorted numerically. */
    FLOAT("f", CellAlignment.END, false),
    /** Unknown: leading aligned, sorted as text, write-protected by default. */
    UNKNOWN("u", CellAlignment.START, true);

    private final String code;
    private final CellAlignment defaultAlignment;
    private final boolean defaultReadOnly;

    ColumnType(final String code, final CellAlignment defaultAlignment, final boolean defaultReadOnly) {
        this.code = code;
        this.defaultAlignment = defaultAlignment;
        this.defaultReadOnly = defaultReadOnly;
    }

    /** Short code used in the configuration object. */
    public String getCode() {
        return code;
    }

    public CellAlignment getDefaultAlignment() {
        return defaultAlignment;
    }

    public boolean isDefaultReadOnly() {
        return defaultReadOnly;
    }

    /** {@code true} when values are sorted numerically without a comparator of their own. */
    public boolean isNumeric() {
        return this == INTEGER || this == FLOAT;
    }

    /**
     * Turns a short code from the configuration object back into a type.
     *
     * @return the matching type, or {@code null} if the code is unknown
     */
    public static ColumnType fromCode(final String code) {
        if (code == null) {
            return null;
        }
        final String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (ColumnType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
