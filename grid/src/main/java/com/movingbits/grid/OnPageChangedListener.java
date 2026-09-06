package com.movingbits.grid;

/**
 * Notified whenever the displayed page changes. The controls for paging are provided by the
 * application itself; this hook keeps their labels and state up to date.
 */
public interface OnPageChangedListener {

    /**
     * @param page      0-based index of the displayed page
     * @param pageCount total number of pages
     */
    void onPageChanged(int page, int pageCount);
}
