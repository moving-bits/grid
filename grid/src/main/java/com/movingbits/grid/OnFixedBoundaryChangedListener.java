package com.movingbits.grid;

/**
 * Notified when the user has moved the boundary of the fixed area. The report comes on
 * release, not continuously while dragging.
 */
public interface OnFixedBoundaryChangedListener {

    /**
     * @param fraction new share of the total width that the fixed area may occupy at most;
     *                 lies between {@link Grid#MIN_FIXED_BOUNDARY_FRACTION} and
     *                 {@link Grid#MAX_FIXED_BOUNDARY_FRACTION}
     */
    void onFixedBoundaryChanged(float fraction);
}
