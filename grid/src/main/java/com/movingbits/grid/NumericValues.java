package com.movingbits.grid;

/**
 * Reads numbers back out of a cell's displayed text.
 *
 * <p>For columns of type {@code ColumnType.INTEGER} or {@code ColumnType.FLOAT} the value is
 * only available as formatted text, for instance "1,234.50 EUR". For sorting, a number is
 * recovered from it: the last comma or dot counts as the decimal separator, every other
 * separator counts as a thousands separator. That covers both the German and the English
 * notation.</p>
 */
final class NumericValues {

    private NumericValues() {
    }

    /**
     * @param text displayed text of a cell
     * @return the number it contains, or {@link Double#NaN} if none can be made out
     */
    static double parse(final String text) {
        if (text == null) {
            return Double.NaN;
        }

        final int decimalSeparator = Math.max(text.lastIndexOf(','), text.lastIndexOf('.'));
        final StringBuilder number = new StringBuilder();
        boolean negative = false;
        boolean anyDigit = false;

        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                number.append(c);
                anyDigit = true;
            } else if ((c == '-' || c == '−') && !anyDigit) {
                // A sign only counts ahead of the first digit.
                negative = true;
            } else if (i == decimalSeparator && anyDigit) {
                number.append('.');
            }
        }

        if (!anyDigit) {
            return Double.NaN;
        }
        try {
            final double value = Double.parseDouble(number.toString());
            return negative ? -value : value;
        } catch (NumberFormatException notANumber) {
            return Double.NaN;
        }
    }

    /**
     * Compares two texts as numbers. Texts without a recognisable number count as smaller than
     * any number, so that gaps in the data do not leave the order undefined.
     */
    static int compare(final String left, final String right) {
        final double a = parse(left);
        final double b = parse(right);
        final boolean aMissing = Double.isNaN(a);
        final boolean bMissing = Double.isNaN(b);
        if (aMissing || bMissing) {
            return aMissing && bMissing ? 0 : (aMissing ? -1 : 1);
        }
        return Double.compare(a, b);
    }
}
