package com.movingbits.grid;

/**
 * Tells the type of a table's column. What the columns of a statement's result mean follows
 * from it: a sum over whole numbers is a whole number, an average is not.
 *
 * <p>{@link SqlGrid} implements it out of the tables it has read; the tests get by with a
 * stand-in.</p>
 */
public interface SqlColumnTypes {

    /**
     * @param table  name of the table
     * @param column name of the column
     * @return its type, or {@link ColumnType#UNKNOWN} when there is no telling
     */
    ColumnType typeOf(String table, String column);
}
