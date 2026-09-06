package com.movingbits.grid;

import java.util.List;

/**
 * Hook for a changed search. Unlike the sort order, the search is transient – whoever wants to
 * show that it is in effect learns about it here.
 */
public interface OnSearchChangedListener {

    /**
     * @param search the search conditions in effect; empty when nothing is searched for
     */
    void onSearchChanged(List<SearchRequest> search);
}
