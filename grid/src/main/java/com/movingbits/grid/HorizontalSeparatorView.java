package com.movingbits.grid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * Vertical line at the transition from the fixed to the freely scrolling area.
 *
 * <p>The line sits as an overlay above the whole grid rather than inside the individual rows –
 * only that way does it run across the header row and all data rows without seams. It appears
 * as soon as the freely scrolling area has been moved and disappears again once its start is
 * reached.</p>
 *
 * <p>The view is wider than the stroke so that it can be grabbed; only the stroke in its
 * centre is drawn.</p>
 */
final class HorizontalSeparatorView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint();
    private int shadowColor;
    private final float lineWidth;
    private final float lineWidthActive;

    private boolean active;

    HorizontalSeparatorView(final Context context) {
        super(context);
        // The view exists before a grid is set; until then the theme decides. The colors are
        // handed over in applyColors as soon as the grid is known.
        paint.setColor(GridStyle.themeAccent(this));
        shadowColor = GridStyle.themeOnSurface(this);
        lineWidth = GridStyle.dp(context, GridStyle.BOUNDARY_WIDTH_DP);
        lineWidthActive = GridStyle.dp(context, GridStyle.BOUNDARY_WIDTH_ACTIVE_DP);
        setVisibility(INVISIBLE);
    }

    /** Takes over the colors of the grid that is now being displayed. */
    void applyColors(final GridColors colors) {
        paint.setColor(colors.accent(this));
        shadowColor = colors.boundaryShadow(this);
        buildShadow(getWidth());
        invalidate();
    }

    @Override
    protected void onSizeChanged(final int width, final int height, final int oldWidth, final int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        buildShadow(width);
    }

    private void buildShadow(final int width) {
        if (width <= 0) {
            return;
        }
        shadowPaint.setShader(new LinearGradient(0f, 0f, width, 0f,
                new int[]{Color.TRANSPARENT, shadowColor, Color.TRANSPARENT},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
    }

    /** highlights separator while dragging */
    void setActive(final boolean active) {
        if (this.active != active) {
            this.active = active;
            invalidate();
        }
    }

    boolean isActive() {
        return active;
    }

    /**
     * Sets the horizontal position of the line within the grid.
     *
     * @param centerX centre of the line in pixels, measured from the grid's left edge
     */
    void setCenterX(final float centerX) {
        setTranslationX(centerX - getWidth() / 2f);
    }

    /** Centre of the line in pixels, measured from the grid's left edge. */
    float getCenterX() {
        return getTranslationX() + getWidth() / 2f;
    }

    @Override
    protected void onDraw(final @NonNull Canvas canvas) {
        if (shadowPaint.getShader() != null) {
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), shadowPaint);
        }
        final float width = active ? lineWidthActive : lineWidth;
        final float center = getWidth() / 2f;
        canvas.drawRect(center - width / 2f, 0f, center + width / 2f, getHeight(), paint);
    }
}
