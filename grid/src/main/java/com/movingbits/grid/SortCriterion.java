package com.movingbits.grid;

import androidx.annotation.NonNull;

/**
 * One sort criterion: column and direction.
 */
public record SortCriterion(int columnIndex, SortDirection direction) {

    /**
     * 0-based index of the column, not counting the number column.
     */
    @Override
    public int columnIndex() {
        return columnIndex;
    }

    /**
     * The same criterion with the direction reversed.
     */
    public SortCriterion reversed() {
        return new SortCriterion(columnIndex, direction == SortDirection.ASCENDING
                ? SortDirection.DESCENDING
                : SortDirection.ASCENDING);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SortCriterion that)) {
            return false;
        }
        return columnIndex == that.columnIndex && direction == that.direction;
    }

    @NonNull
    @Override
    public String toString() {
        return "SortCriterion(column=" + columnIndex + ", " + direction + ")";
    }
}
