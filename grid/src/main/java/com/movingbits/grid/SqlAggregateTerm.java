package com.movingbits.grid;

/**
 * An aggregate over a term: {@code COUNT(*)}, {@code SUM("area")},
 * {@code COUNT(DISTINCT "country")}.
 *
 * @param function the aggregate
 * @param argument what is aggregated
 * @param distinct {@code true} to count every value only once
 */
public record SqlAggregateTerm(SqlAggregate function, SqlTerm argument, boolean distinct) implements SqlTerm {

    public SqlAggregateTerm(final SqlAggregate function, final SqlTerm argument, final boolean distinct) {
        if (function == null || argument == null) {
            throw new IllegalArgumentException("function and argument must not be null");
        }
        this.function = function;
        this.argument = argument;
        this.distinct = distinct;
    }

    @Override
    public boolean hasAggregate() {
        return true;
    }

    @Override
    public ColumnType type(final SqlColumnTypes types) {
        return function.resultType(argument.type(types));
    }

    @Override
    public String label() {
        return function.getSql() + '(' + (distinct ? "DISTINCT " : "") + argument.label() + ')';
    }
}
