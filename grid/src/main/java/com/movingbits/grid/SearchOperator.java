package com.movingbits.grid;

import androidx.annotation.StringRes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Comparison of a search condition. Which operators suit a column depends on its
 * {@link ColumnType}: text columns compare strings, numeric columns compare magnitudes.
 */
public enum SearchOperator {

    /** Equal; for text the whole cell, not just a part of it. */
    EQUALS("eq", R.string.grid_operator_equals, true, true),
    /** Not equal. */
    NOT_EQUALS("ne", R.string.grid_operator_not_equals, true, true),
    STARTS_WITH("sw", R.string.grid_operator_starts_with, true, false),
    NOT_STARTS_WITH("nsw", R.string.grid_operator_not_starts_with, true, false),
    ENDS_WITH("ew", R.string.grid_operator_ends_with, true, false),
    NOT_ENDS_WITH("new", R.string.grid_operator_not_ends_with, true, false),
    CONTAINS("ct", R.string.grid_operator_contains, true, false),
    NOT_CONTAINS("nct", R.string.grid_operator_not_contains, true, false),
    LESS("lt", R.string.grid_operator_less, false, true),
    LESS_OR_EQUAL("le", R.string.grid_operator_less_or_equal, false, true),
    GREATER("gt", R.string.grid_operator_greater, false, true),
    GREATER_OR_EQUAL("ge", R.string.grid_operator_greater_or_equal, false, true);

    private static final List<SearchOperator> FOR_TEXT = unmodifiable(
            EQUALS, NOT_EQUALS,
            STARTS_WITH, NOT_STARTS_WITH,
            ENDS_WITH, NOT_ENDS_WITH,
            CONTAINS, NOT_CONTAINS);

    private static final List<SearchOperator> FOR_NUMBERS = unmodifiable(
            EQUALS, NOT_EQUALS, LESS, LESS_OR_EQUAL, GREATER, GREATER_OR_EQUAL);

    private final String code;
    private final int labelRes;
    private final boolean forText;
    private final boolean forNumbers;

    SearchOperator(final String code, final @StringRes int labelRes, final boolean forText, final boolean forNumbers) {
        this.code = code;
        this.labelRes = labelRes;
        this.forText = forText;
        this.forNumbers = forNumbers;
    }

    /** Short code used in the search that is handed over. */
    public String getCode() {
        return code;
    }

    /** Label in the search dialog. */
    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    /**
     * The operators that belong to a column type.
     *
     * <p>{@link ColumnType#STRING} and {@link ColumnType#UNKNOWN} allow equality, start, end
     * and containment together with their negations; {@link ColumnType#INTEGER} and
     * {@link ColumnType#FLOAT} allow the magnitude comparisons. The search across all columns
     * uses the text operators.</p>
     *
     * @param type type of the column, or {@code null} for the search across all columns
     */
    public static List<SearchOperator> forType(final ColumnType type) {
        return type != null && type.isNumeric() ? FOR_NUMBERS : FOR_TEXT;
    }

    /** {@code true} when this operator suits that type. */
    public boolean fits(final ColumnType type) {
        return type != null && type.isNumeric() ? forNumbers : forText;
    }

    /**
     * Turns a short code from the search that was handed over back into an operator.
     *
     * @return the matching operator, or {@code null} if the code is unknown
     */
    public static SearchOperator fromCode(final String code) {
        if (code == null) {
            return null;
        }
        final String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (SearchOperator operator : values()) {
            if (operator.code.equals(normalized)) {
                return operator;
            }
        }
        return null;
    }

    private static List<SearchOperator> unmodifiable(final SearchOperator... operators) {
        return Collections.unmodifiableList(Arrays.asList(operators));
    }
}
