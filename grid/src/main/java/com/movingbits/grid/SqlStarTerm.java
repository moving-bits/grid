package com.movingbits.grid;

/**
 * The {@code *} of {@code SELECT *} and {@code COUNT(*)}.
 *
 * <p>As a column of the result it only suits a query without a join: two joined tables would
 * otherwise deliver columns of the same name.</p>
 */
public record SqlStarTerm() implements SqlTerm {

    @Override
    public boolean hasAggregate() {
        return false;
    }

    @Override
    public ColumnType type(final SqlColumnTypes types) {
        return ColumnType.UNKNOWN;
    }

    @Override
    public String label() {
        return "*";
    }
}
