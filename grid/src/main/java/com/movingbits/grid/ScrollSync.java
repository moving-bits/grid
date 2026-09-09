package com.movingbits.grid;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the horizontal scroll position of one area (header row and all data rows) in common.
 * Every participating view reports its position; all others follow along.
 */
final class ScrollSync {

    /** Reports changes of the shared position, e.g. to show the boundary line. */
    interface Listener {
        void onSyncScrollChanged(int scrollX);
    }

    private final List<SyncedHorizontalScrollView> views = new ArrayList<>();
    private Listener listener;
    private int scrollX;
    private boolean applying;

    void setListener(final Listener listener) {
        this.listener = listener;
    }

    void register(final SyncedHorizontalScrollView view) {
        if (!views.contains(view)) {
            views.add(view);
        }
        view.applySyncedScrollX(scrollX);
    }

    void unregister(final SyncedHorizontalScrollView view) {
        views.remove(view);
    }

    /** Drops all views, e.g. when the grid is rebuilt. */
    void clear() {
        views.clear();
        if (scrollX != 0) {
            scrollX = 0;
            notifyListener();
        }
    }

    int getScrollX() {
        return scrollX;
    }

    /**
     * Takes over one view's position and moves all the others along.
     *
     * @param source the view that was scrolled
     * @param x      the new horizontal position
     */
    void onScrolled(final SyncedHorizontalScrollView source, final int x) {
        if (applying) {
            scrollX = x;
            return;
        }
        if (scrollX == x) {
            return;
        }
        applying = true;
        scrollX = x;
        for (int i = 0; i < views.size(); i++) {
            final SyncedHorizontalScrollView view = views.get(i);
            if (view != source) {
                view.applySyncedScrollX(x);
            }
        }
        applying = false;
        // Only report once every view has followed: listeners ask the header row whether it
        // can still scroll and would otherwise see the previous state.
        notifyListener();
    }

    /** Resets all participating views to the start. */
    void reset() {
        moveTo(0);
    }

    /**
     * Takes over a position from outside – out of a restored state, for instance – and moves
     * every participating view along.
     */
    void moveTo(final int x) {
        final int target = Math.max(0, x);
        if (scrollX == target) {
            return;
        }
        applying = true;
        scrollX = target;
        for (int i = 0; i < views.size(); i++) {
            views.get(i).applySyncedScrollX(target);
        }
        applying = false;
        notifyListener();
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onSyncScrollChanged(scrollX);
        }
    }
}
