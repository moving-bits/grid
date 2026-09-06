package com.movingbits.grid;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;

/**
 * Horizontally scrolling area whose position is shared through a {@link ScrollSync} with all
 * other areas of the same group. That is what makes the header row and the data rows scroll
 * together.
 */
final class SyncedHorizontalScrollView extends HorizontalScrollView {

    private final ScrollSync sync;
    private boolean scrollingEnabled = true;

    SyncedHorizontalScrollView(final Context context, final ScrollSync sync) {
        super(context);
        this.sync = sync;
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(true);
    }

    /** Switches off operation by the user; scrolling from code stays possible. */
    void setScrollingEnabled(final boolean enabled) {
        this.scrollingEnabled = enabled;
    }

    /** Sets the position without reporting it back to the {@link ScrollSync}. */
    void applySyncedScrollX(final int x) {
        if (getScrollX() != x) {
            super.scrollTo(x, getScrollY());
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // When a row is recycled the view rejoins the group and immediately takes over the
        // shared position.
        sync.register(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        sync.unregister(this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onScrollChanged(final int left, final int top, final int oldLeft, final int oldTop) {
        super.onScrollChanged(left, top, oldLeft, oldTop);
        // The row draws the column lines across its areas. Scrolling an area on its own leaves
        // them where they were – the row has to draw them again.
        if (getParent() instanceof View parent) {
            parent.invalidate();
        }
        sync.onScrolled(this, left);
    }

    @Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r, final int b) {
        super.onLayout(changed, l, t, r, b);
        // After a layout change (when a row is recycled, for instance) restore the shared position.
        final int target = sync.getScrollX();
        if (getScrollX() != target) {
            super.scrollTo(target, getScrollY());
        }
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        return scrollingEnabled && super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent event) {
        return scrollingEnabled && super.onInterceptTouchEvent(event);
    }
}
