package com.movingbits.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keeps the sort order across several columns.
 *
 * <p>Tapping the same column repeatedly cycles through ascending, descending and no sorting.
 * A column not yet involved is appended as a subordinate criterion without disturbing the
 * order of the existing ones.</p>
 */
final class GridSortState {

    private final List<SortCriterion> criteria = new ArrayList<>();

    /**
     * Advances a column's sorting: none -> ascending -> descending -> none.
     */
    void toggle(final int columnIndex) {
        final int position = indexOf(columnIndex);
        if (position < 0) {
            criteria.add(new SortCriterion(columnIndex, SortDirection.ASCENDING));
            return;
        }
        final SortCriterion current = criteria.get(position);
        if (current.direction() == SortDirection.ASCENDING) {
            criteria.set(position, current.reversed());
        } else {
            criteria.remove(position);
        }
    }

    /**
     * Appends a criterion at the end of the order. If the column already takes part, only its
     * direction changes.
     */
    void append(final int columnIndex, final SortDirection direction) {
        final int position = indexOf(columnIndex);
        if (position < 0) {
            criteria.add(new SortCriterion(columnIndex, direction));
        } else {
            criteria.set(position, new SortCriterion(columnIndex, direction));
        }
    }

    /**
     * Follows the criteria along when a column moves elsewhere. The criteria refer to column
     * indices; without adjusting them the grid would sort by the wrong columns.
     *
     * @param from previous position of the moved column
     * @param to   its new position
     */
    void moveColumn(final int from, final int to) {
        for (int i = 0; i < criteria.size(); i++) {
            final SortCriterion criterion = criteria.get(i);
            final int index = criterion.columnIndex();
            final int moved;
            if (index == from) {
                moved = to;
            } else if (from < index && index <= to) {
                moved = index - 1;
            } else if (to <= index && index < from) {
                moved = index + 1;
            } else {
                continue;
            }
            criteria.set(i, new SortCriterion(moved, criterion.direction()));
        }
    }

    /** Direction for a column, or {@code null} if it is not sorted by. */
    SortDirection directionOf(final int columnIndex) {
        final int position = indexOf(columnIndex);
        return position < 0 ? null : criteria.get(position).direction();
    }

    /**
     * Rank of a column within the sort order.
     *
     * @return 1 for the strongest criterion, 0 if the column is not sorted by
     */
    int rankOf(final int columnIndex) {
        return indexOf(columnIndex) + 1;
    }

    boolean isEmpty() {
        return criteria.isEmpty();
    }

    int size() {
        return criteria.size();
    }

    List<SortCriterion> getCriteria() {
        return List.copyOf(criteria);
    }

    void clear() {
        criteria.clear();
    }

    private int indexOf(final int columnIndex) {
        for (int i = 0; i < criteria.size(); i++) {
            if (criteria.get(i).columnIndex() == columnIndex) {
                return i;
            }
        }
        return -1;
    }
}
