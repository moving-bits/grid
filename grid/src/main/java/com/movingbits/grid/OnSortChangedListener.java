package com.movingbits.grid;

import java.util.List;

/**
 * Notified when tapping the header row has changed the sort order.
 */
public interface OnSortChangedListener {

    /**
     * @param sortOrder criteria in the order they take effect; empty when nothing is sorted
     */
    void onSortChanged(List<SortCriterion> sortOrder);
}
