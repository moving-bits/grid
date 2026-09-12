package com.movingbits.grid;

/**
 * One assignment of an {@code UPDATE}: which column receives which value.
 *
 * @param column name of the column in the table being changed
 * @param value  the new value; a term, so {@code "area" * 2} is possible as well
 */
public record SqlAssignment(String column, SqlTerm value) {

    public SqlAssignment(final String column, final SqlTerm value) {
        if (column == null || value == null) {
            throw new IllegalArgumentException("column and value must not be null");
        }
        this.column = column;
        this.value = value;
    }

    /** Short text for the chip in the editor. */
    public String label() {
        return column + " = " + value.label();
    }
}
