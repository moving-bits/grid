package com.movingbits.grid;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;

/**
 * Compares two records along several sort criteria.
 *
 * <p>For each criterion the column's own comparator is used if one is set. Otherwise the
 * column type decides: numeric columns compare as numbers, everything else compares through a
 * {@link Collator} so that accented letters sort where a reader expects them. The first
 * criterion that finds a difference decides.</p>
 *
 * @param <T> type of a row's record
 */
final class RowComparator<T> implements Comparator<T> {

    private final List<MemColumn<T>> columns;
    private final List<SortDirection> directions;
    private final Collator collator;

    RowComparator(final List<MemColumn<T>> columns, final List<SortDirection> directions) {
        this.columns = columns;
        this.directions = directions;
        this.collator = Collator.getInstance();
        // Case and accents only tell records apart once the letters themselves are equal.
        this.collator.setStrength(Collator.SECONDARY);
    }

    @Override
    public int compare(final T first, final T second) {
        for (int i = 0; i < columns.size(); i++) {
            final MemColumn<T> column = columns.get(i);

            final int result;
            final Comparator<T> comparator = column.getComparator();
            if (comparator != null) {
                result = comparator.compare(first, second);
            } else if (column.sortsNumerically()) {
                result = NumericValues.compare(column.textOf(first), column.textOf(second));
            } else {
                result = collator.compare(column.textOf(first), column.textOf(second));
            }

            if (result != 0) {
                return directions.get(i) == SortDirection.DESCENDING ? -result : result;
            }
        }
        return 0;
    }
}
