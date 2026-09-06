package com.movingbits.grid;

import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;

/**
 * The colors a grid paints with.
 *
 * <p>Without one of these the grid follows the Material 2 theme of the embedding application,
 * which is the usual case and needs no setting up. Whoever wants other colors hands a
 * {@code GridColors} to {@link Grid#colors(GridColors)}; the palette can be built once and
 * shared by several grids.</p>
 *
 * <p>Two of the colors carry the rest: {@link #surface(int)} is the background, and
 * {@link #onSurface(int)} is the base for the text and for everything drawn on top of the
 * background – the divider lines, the column lines, the scroll hints, the shading at the
 * boundary and the tints of alternating rows and write-protected headers. Setting those two is
 * enough for a coherent look. Every derived color can be set on its own as well; an explicit
 * value always wins.</p>
 *
 * <pre>{@code
 * Grid grid = new Grid(dataSource)
 *         .colors(new GridColors()
 *                 .surface(0xFFFFFDF7)
 *                 .onSurface(0xFF20242A)
 *                 .accent(0xFF0066CC))
 *         .column("Name");
 * }</pre>
 *
 * <p>Colors are ARGB values as {@code android.graphics.Color} produces them. A fully
 * transparent color is a valid value, so "not set" is kept apart from "set to zero".</p>
 */
public final class GridColors {

    private Integer surface;
    private Integer onSurface;
    private Integer accent;
    private Integer alternateRow;
    private Integer readOnlyHeader;
    private Integer divider;
    private Integer columnDivider;
    private Integer boundaryShadow;
    private Integer scrollHint;

    /**
     * Background of the header row and the data rows, and of the view behind them. Without a
     * value the theme's {@code colorSurface} applies.
     */
    public GridColors surface(final int color) {
        this.surface = color;
        return this;
    }

    /**
     * Color of the text and base for everything drawn over the background. Without a value the
     * theme's {@code colorOnSurface} applies – and the text keeps the color of its text
     * appearance, which is what a theme intends.
     */
    public GridColors onSurface(final int color) {
        this.onSurface = color;
        return this;
    }

    /**
     * Color of the boundary line between the fixed and the scrolling area, of the line while a
     * column edge is dragged, and of the separator in the column dialog. Without a value the
     * theme's {@code colorSecondary} applies.
     */
    public GridColors accent(final int color) {
        this.accent = color;
        return this;
    }

    /**
     * Background of every other data row, used only after
     * {@link Grid#alternatingRowColors(boolean)}. Without a value a hint of the on-surface
     * color is layered over the background.
     */
    public GridColors alternateRow(final int color) {
        this.alternateRow = color;
        return this;
    }

    /**
     * Background of the header cell of a write-protected column. Without a value a stronger
     * hint of the on-surface color is layered over the background.
     */
    public GridColors readOnlyHeader(final int color) {
        this.readOnlyHeader = color;
        return this;
    }

    /** Color of the horizontal line below every row. */
    public GridColors divider(final int color) {
        this.divider = color;
        return this;
    }

    /** Color of the vertical line at the right edge of every column. */
    public GridColors columnDivider(final int color) {
        this.columnDivider = color;
        return this;
    }

    /** Color of the shading inside the gap at the boundary of the fixed area. */
    public GridColors boundaryShadow(final int color) {
        this.boundaryShadow = color;
        return this;
    }

    /** Color of the arrows that point at columns outside the window. */
    public GridColors scrollHint(final int color) {
        this.scrollHint = color;
        return this;
    }

    // The view is only consulted where nothing was set: it is what the theme is read from.

    int surface(final View view) {
        return surface != null ? surface : GridStyle.themeSurface(view);
    }

    int onSurface(final View view) {
        return onSurface != null ? onSurface : GridStyle.themeOnSurface(view);
    }

    int accent(final View view) {
        return accent != null ? accent : GridStyle.themeAccent(view);
    }

    /**
     * Background of every other row: a hint of the on-surface color over the background. Not a
     * hard-wired light gray, so that the color also works in a dark theme – there the row
     * turns a little lighter instead of darker.
     */
    int alternateRow(final View view) {
        return alternateRow != null ? alternateRow
                : MaterialColors.layer(surface(view), onSurface(view), GridStyle.ALTERNATE_ROW_FRACTION);
    }

    int readOnlyHeader(final View view) {
        return readOnlyHeader != null ? readOnlyHeader
                : MaterialColors.layer(surface(view), onSurface(view), GridStyle.READ_ONLY_HEADER_FRACTION);
    }

    int divider(final View view) {
        return divider != null ? divider
                : ColorUtils.setAlphaComponent(onSurface(view), GridStyle.DIVIDER_ALPHA);
    }

    int columnDivider(final View view) {
        return columnDivider != null ? columnDivider
                : ColorUtils.setAlphaComponent(onSurface(view), GridStyle.COLUMN_DIVIDER_ALPHA);
    }

    int boundaryShadow(final View view) {
        return boundaryShadow != null ? boundaryShadow
                : ColorUtils.setAlphaComponent(onSurface(view), GridStyle.BOUNDARY_SHADOW_ALPHA);
    }

    int scrollHint(final View view) {
        return scrollHint != null ? scrollHint
                : ColorUtils.setAlphaComponent(onSurface(view), GridStyle.MEDIUM_EMPHASIS_ALPHA);
    }

    /**
     * Color for the text of a cell, or {@code null} to leave the text appearance's own color
     * alone. Only an explicitly set on-surface color counts here: reading the theme would
     * override what the text appearance says, and that is exactly what a theme is for.
     */
    Integer text() {
        return onSurface;
    }
}
