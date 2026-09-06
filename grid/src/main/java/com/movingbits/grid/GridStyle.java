package com.movingbits.grid;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.color.MaterialColors;

/**
 * Shared measurements, colors and cell construction. Colors and text styles come from the
 * standard attributes of Material Components (Material 2) and therefore follow the theme of
 * the embedding application.
 */
final class GridStyle {

    /**
     * Padding on the left and right of every cell. That leaves 8 dp between two columns and
     * 4 dp at the grid's left and right edge. Kept tight on purpose: in a table width is the
     * scarce resource, and the column edges follow from the alignment of the content anyway.
     */
    static final float CELL_PADDING_HORIZONTAL_DP = 4f;
    static final float CELL_PADDING_VERTICAL_DP = 8f;
    /**
     * Width below which columns without a width of their own are not split when the remaining
     * width is handed out. The hard lower bound of every column is the smaller
     * {@link ColumnWidth#MIN_WIDTH_DP}.
     */
    static final float MIN_SHARED_COLUMN_WIDTH_DP = 48f;
    static final float HEADER_ELEVATION_DP = 4f;
    static final float DIVIDER_HEIGHT_DP = 1f;
    /** Stroke width of the boundary line between the fixed and the scrolling area. */
    static final float BOUNDARY_WIDTH_DP = 2f;
    /** Stroke width of that same line while the boundary is being dragged. */
    static final float BOUNDARY_WIDTH_ACTIVE_DP = 4f;
    /**
     * Gap between the fixed and the freely scrolling area. The boundary line sits in its
     * centre and thereby gets room on both sides towards the cells. A little wider than the
     * gap between two ordinary columns, because a line has to fit in here.
     */
    static final float BOUNDARY_GUTTER_DP = 8f;
    /**
     * Width of the grab area when the boundary may be moved. It covers the gap and reaches
     * beyond it, so that the narrow line is easy to hit.
     */
    static final float BOUNDARY_TOUCH_WIDTH_DP = 48f;
    /**
     * Width of the grab area at a column edge in the header row. Narrower than the one for the
     * area boundary, so that with narrow columns there is still room to hit the header cell
     * itself.
     */
    static final float COLUMN_EDGE_TOUCH_WIDTH_DP = 32f;
    /** Size of the arrows that point at further content to the left or right. */
    static final float SCROLL_HINT_SIZE_DP = 5f;
    /** Distance of those arrows from the edge of their area. */
    static final float SCROLL_HINT_INSET_DP = 3f;
    /** Opacity for medium emphasis labels according to Material 2 (60 %). */
    static final int MEDIUM_EMPHASIS_ALPHA = 153;
    /** Opacity of the divider lines according to Material 2 (12 % on colorOnSurface). */
    static final int DIVIDER_ALPHA = 31;
    /**
     * Opacity of the vertical lines between two columns (25 % on colorOnSurface). Higher than
     * for the horizontal dividers, because this line is only one screen pixel wide and would
     * otherwise be hard to see depending on the screen density.
     */
    static final int COLUMN_DIVIDER_ALPHA = 64;
    /** Opacity of the shading inside the gap at the boundary (20 % on colorOnSurface). */
    static final int BOUNDARY_SHADOW_ALPHA = 51;
    /**
     * Share of colorOnSurface in the background of every other row (5 %). Just enough to tell
     * the rows apart without overpowering the content.
     */
    static final float ALTERNATE_ROW_FRACTION = 0.05f;
    /**
     * Share of colorOnSurface in the header cell of a write-protected column (12 %). Clear
     * enough to stay readable as a statement of its own next to alternating row colors.
     */
    static final float READ_ONLY_HEADER_FRACTION = 0.12f;

    private GridStyle() {
    }

    static int dp(final Context context, final float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics()));
    }

    static float density(final Context context) {
        return context.getResources().getDisplayMetrics().density;
    }

    // The colors themselves live in GridColors, which lets the application override every one
    // of them. Read from the theme here is only what serves as the default.

    static int themeSurface(final View view) {
        return MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface, Color.WHITE);
    }

    static int themeOnSurface(final View view) {
        return MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
    }

    /**
     * Accent color of the theme. Material Components maps {@code colorAccent} onto
     * {@code colorSecondary}; for themes without that attribute it falls back to
     * {@code colorAccent}.
     */
    static int themeAccent(final View view) {
        final int fallback = MaterialColors.getColor(view, android.R.attr.colorAccent, Color.DKGRAY);
        return MaterialColors.getColor(view, com.google.android.material.R.attr.colorSecondary, fallback);
    }

    /**
     * Creates a text cell in Material 2 style.
     *
     * @param header {@code true} for the header row (Subtitle2), otherwise a data cell (Body2)
     */
    static TextView createTextCell(final Context context, final boolean header) {
        final TextView cell = new TextView(context);
        TextViewCompat.setTextAppearance(cell, header
                ? com.google.android.material.R.style.TextAppearance_MaterialComponents_Subtitle2
                : com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2);
        final int paddingH = dp(context, CELL_PADDING_HORIZONTAL_DP);
        final int paddingV = dp(context, CELL_PADDING_VERTICAL_DP);
        cell.setPadding(paddingH, paddingV, paddingH, paddingV);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        cell.setGravity(Gravity.TOP | Gravity.START);
        // Without the extra font padding getLineHeight() matches exactly the room one line of
        // text needs – the precondition for getting the arithmetic right at tight row heights.
        cell.setIncludeFontPadding(false);
        return cell;
    }

    /**
     * Puts the usual Material touch feedback over a cell. Only set for cells that have a hook
     * configured – otherwise there would be visible reactions without any effect.
     */
    static void applyTouchFeedback(final View view) {
        final TypedValue value = new TypedValue();
        if (view.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, value, true)) {
            view.setForeground(ContextCompat.getDrawable(view.getContext(), value.resourceId));
        }
    }

    /** Maps the alignment onto a gravity; vertically always to the top. */
    static int gravityOf(final CellAlignment alignment) {
        return switch (alignment) {
            case CENTER -> Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            case END -> Gravity.TOP | Gravity.END;
            default -> Gravity.TOP | Gravity.START;
        };
    }
}
