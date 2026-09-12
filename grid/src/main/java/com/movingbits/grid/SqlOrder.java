package com.movingbits.grid;

/**
 * One criterion of the {@code ORDER BY} clause. It names a column of the result, so what is
 * sorted by has to be part of the projection.
 *
 * @param alias     name of the result column
 * @param direction ascending or descending
 */
public record SqlOrder(String alias, SortDirection direction) {

    public SqlOrder(final String alias, final SortDirection direction) {
        if (alias == null) {
            throw new IllegalArgumentException("alias must not be null");
        }
        this.alias = alias;
        this.direction = direction == null ? SortDirection.ASCENDING : direction;
    }

    /** Short text for the chip in the editor. */
    public String label() {
        return alias + (direction == SortDirection.ASCENDING ? " ▲" : " ▼");
    }
}
