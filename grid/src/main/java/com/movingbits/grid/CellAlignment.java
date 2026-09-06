package com.movingbits.grid;

/**
 * Horizontal alignment of a cell's content. Vertically, values are always aligned to the top.
 */
public enum CellAlignment {
    /** Leading edge (trailing edge in an RTL layout). The default. */
    START,
    /** Horizontally centred. */
    CENTER,
    /** Trailing edge (leading edge in an RTL layout). */
    END
}
