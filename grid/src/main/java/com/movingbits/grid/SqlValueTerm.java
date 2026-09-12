package com.movingbits.grid;

/**
 * A value the user has entered. It never stands in the statement itself: it goes to the
 * database as the parameter of a prepared statement.
 *
 * <p>An empty text means {@code NULL}. The type decides how the value is compared – Android
 * can only bind parameters as text, so the statement says of a number that it is one.</p>
 *
 * @param type type of the value; {@code null} counts as {@link ColumnType#STRING}
 * @param text the value as it was entered
 */
public record SqlValueTerm(ColumnType type, String text) implements SqlTerm {

    public SqlValueTerm(final ColumnType type, final String text) {
        this.type = type == null ? ColumnType.STRING : type;
        this.text = text == null ? "" : text;
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }

    @Override
    public ColumnType type(final SqlColumnTypes types) {
        return type;
    }

    /** {@code true} when the value stands for {@code NULL}. */
    public boolean isNull() {
        return text.isEmpty();
    }

    @Override
    public String label() {
        return text.isEmpty() ? "NULL" : text;
    }
}
