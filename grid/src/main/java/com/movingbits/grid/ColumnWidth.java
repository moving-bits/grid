package com.movingbits.grid;

import androidx.annotation.NonNull;

/**
 * Width of a column: a fixed value in dp, a share of the total width, or an even split of
 * whatever width is left.
 *
 * <p>Instances are created through the static factory methods {@link #dp(float)},
 * {@link #percent(float)} and {@link #remaining()}.</p>
 */
public final class ColumnWidth {

    /** How the width is stated. */
    public enum Type {
        /** A fixed value in dp. */
        FIXED,
        /** A share of the grid's total width. */
        PERCENT,
        /** An even split of the width left over. */
        REMAINING
    }

    /**
     * Smallest width a column may occupy. It holds without exception: for values set in code,
     * for values from the configuration object, and for a column edge dragged by the user.
     * Smaller values are raised, not rejected.
     */
    public static final float MIN_WIDTH_DP = 20f;

    private static final ColumnWidth REMAINING = new ColumnWidth(Type.REMAINING, 0f);

    private final Type type;
    private final float value;

    private ColumnWidth(final Type type, final float value) {
        this.type = type;
        this.value = value;
    }

    /**
     * A fixed width in dp. Values below {@link #MIN_WIDTH_DP} are raised to it – the minimum
     * width takes precedence over the value given.
     *
     * @param dp width in dp, must be greater than 0
     */
    public static ColumnWidth dp(final float dp) {
        if (dp <= 0f) {
            throw new IllegalArgumentException("width in dp must be > 0, was: " + dp);
        }
        return new ColumnWidth(Type.FIXED, Math.max(MIN_WIDTH_DP, dp));
    }

    /**
     * A share of the grid's total width (including the number column).
     *
     * @param fraction share in the range (0..1]
     */
    public static ColumnWidth percent(final float fraction) {
        if (fraction <= 0f || fraction > 1f) {
            throw new IllegalArgumentException("share must be within (0..1], was: " + fraction);
        }
        return new ColumnWidth(Type.PERCENT, fraction);
    }

    /** The column splits the remaining width evenly with the other columns without a value. */
    public static ColumnWidth remaining() {
        return REMAINING;
    }

    public Type getType() {
        return type;
    }

    /** dp value for {@link Type#FIXED}, the share for {@link Type#PERCENT}, otherwise 0. */
    public float getValue() {
        return value;
    }

    @NonNull
    @Override
    public String toString() {
        return switch (type) {
            case FIXED -> value + "dp";
            case PERCENT -> (value * 100f) + "%";
            default -> "remaining";
        };
    }
}
