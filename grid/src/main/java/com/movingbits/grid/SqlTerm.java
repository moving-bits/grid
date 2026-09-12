package com.movingbits.grid;

/**
 * One value within a statement: a column, an entered value, an aggregate, a function call or
 * {@code *}.
 *
 * <p>Terms nest: the argument of an aggregate is a term, and so is every parameter of a
 * function call – that is what lets {@code round(avg("area"), 2)} be clicked together. The set
 * of terms is closed; there is no implementation of this interface outside the library.</p>
 */
public interface SqlTerm {

    /** {@code true} when this term is an aggregate or carries one within it. */
    boolean hasAggregate();

    /**
     * The type the term yields.
     *
     * @param types tells the type of a table's column; may be {@code null}
     */
    ColumnType type(SqlColumnTypes types);

    /** Short text for the editor's chip, e.g. {@code SUM(area)}. */
    String label();
}
