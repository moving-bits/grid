package com.movingbits.grid;

/**
 * A column of a table, as the editor's selection list offers it.
 *
 * @param table  name of the table the column belongs to
 * @param column name of the column
 */
public record SqlColumnTerm(String table, String column) implements SqlTerm {

    public SqlColumnTerm(final String table, final String column) {
        if (table == null || column == null) {
            throw new IllegalArgumentException("table and column must not be null");
        }
        this.table = table;
        this.column = column;
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }

    @Override
    public ColumnType type(final SqlColumnTypes types) {
        return types == null ? ColumnType.UNKNOWN : types.typeOf(table, column);
    }

    @Override
    public String label() {
        return column;
    }
}
