package com.movingbits.grid;

import java.util.Locale;

/**
 * Checks a cell against a search condition – as text or as a number.
 */
final class SearchMatch {

    private SearchMatch() {
    }

    /**
     * Comparison as text, ignoring case.
     *
     * @param text  displayed content of the cell
     * @param value the value searched for
     */
    static boolean matchesText(final String text, final String value, final SearchOperator operator) {
        final String cell = text == null ? "" : text.toLowerCase(Locale.ROOT);
        final String needle = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return switch (operator) {
            case EQUALS -> cell.equals(needle);
            case NOT_EQUALS -> !cell.equals(needle);
            case STARTS_WITH -> cell.startsWith(needle);
            case NOT_STARTS_WITH -> !cell.startsWith(needle);
            case ENDS_WITH -> cell.endsWith(needle);
            case NOT_ENDS_WITH -> !cell.endsWith(needle);
            case CONTAINS -> cell.contains(needle);
            case NOT_CONTAINS -> !cell.contains(needle);
            // Magnitude comparisons are not offered for text columns; should one turn up
            // anyway, alphabetical order applies.
            case LESS -> cell.compareTo(needle) < 0;
            case LESS_OR_EQUAL -> cell.compareTo(needle) <= 0;
            case GREATER -> cell.compareTo(needle) > 0;
            case GREATER_OR_EQUAL -> cell.compareTo(needle) >= 0;
            default -> false;
        };
    }

    /**
     * Comparison as a number. If either side cannot be read as a number the condition is not
     * met – not even for the negating operators.
     *
     * @param text  displayed content of the cell, for instance "1,234.50 EUR"
     * @param value the value searched for
     */
    static boolean matchesNumber(final String text, final String value, final SearchOperator operator) {
        final double cell = NumericValues.parse(text);
        final double needle = NumericValues.parse(value);
        if (Double.isNaN(cell) || Double.isNaN(needle)) {
            return false;
        }
        return switch (operator) {
            case EQUALS -> cell == needle;
            case NOT_EQUALS -> cell != needle;
            case LESS -> cell < needle;
            case LESS_OR_EQUAL -> cell <= needle;
            case GREATER -> cell > needle;
            case GREATER_OR_EQUAL -> cell >= needle;
            default -> false;
        };
    }
}
