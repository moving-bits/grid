package com.movingbits.grid;

import java.util.Comparator;

/**
 * Column of a {@link MemGrid}: a {@link GridColumn} that additionally knows how to win its
 * text from a record and how it is sorted.
 *
 * <p>The configuration methods return {@code this} and can be chained.</p>
 *
 * @param <T> type of a row's record
 */
public class MemColumn<T> extends GridColumn {

    private final ValueProvider<T> valueProvider;
    private Comparator<T> comparator;

    /**
     * @param title         title shown in the header row
     * @param valueProvider supplies the text for each record
     */
    public MemColumn(final String title, final ValueProvider<T> valueProvider) {
        super(title);
        if (valueProvider == null) {
            throw new IllegalArgumentException("valueProvider must not be null");
        }
        this.valueProvider = valueProvider;
    }

    /**
     * Sets the comparator this column is sorted by.
     *
     * <p>Without one the displayed text is compared in a language-aware way, and as a number
     * for the types {@link ColumnType#INTEGER} and {@link ColumnType#FLOAT}. Dates and other
     * values whose textual form is not in the meaningful order need a comparator of their
     * own.</p>
     */
    public MemColumn<T> comparator(final Comparator<T> comparator) {
        this.comparator = comparator;
        return this;
    }

    /** The explicitly set comparator, or {@code null}. */
    public Comparator<T> getComparator() {
        return comparator;
    }

    /**
     * {@code true} when the column sorts numerically without a comparator of its own – that
     * is, for type {@link ColumnType#INTEGER} or {@link ColumnType#FLOAT}.
     */
    public boolean sortsNumerically() {
        return comparator == null && getType().isNumeric();
    }

    /**
     * Determines the cell's text for one record.
     *
     * @return the text, never {@code null}
     */
    public String textOf(final T item) {
        final String value = valueProvider.valueOf(item);
        return value == null ? "" : value;
    }

    // The inherited configuration methods return a MemColumn again, so that the chain can
    // carry on without a detour.

    @Override
    public MemColumn<T> name(final String name) {
        super.name(name);
        return this;
    }

    @Override
    public MemColumn<T> type(final ColumnType type) {
        super.type(type);
        return this;
    }

    @Override
    public MemColumn<T> align(final CellAlignment alignment) {
        super.align(alignment);
        return this;
    }

    @Override
    public MemColumn<T> readOnly(final boolean readOnly) {
        super.readOnly(readOnly);
        return this;
    }

    @Override
    public MemColumn<T> width(final ColumnWidth width) {
        super.width(width);
        return this;
    }

    @Override
    public MemColumn<T> widthDp(final float dp) {
        super.widthDp(dp);
        return this;
    }

    @Override
    public MemColumn<T> widthPercent(final float fraction) {
        super.widthPercent(fraction);
        return this;
    }

    @Override
    public MemColumn<T> sortable(final boolean sortable) {
        super.sortable(sortable);
        return this;
    }
}
