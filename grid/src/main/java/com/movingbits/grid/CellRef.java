package com.movingbits.grid;

import androidx.annotation.NonNull;

/**
 * Reference to a cell in the data area.
 *
 * <p>{@link #rowIndex ()} always refers to the whole data set, not to the page currently on
 * screen. {@link #columnIndex ()} is the position in the display, {@link #dataIndex ()}
 * the position of the field within the data row; the number column counts for neither.</p>
 */
public record CellRef(int rowIndex, int columnIndex, int dataIndex, boolean readOnly) {

    /**
     * @param rowIndex    0-based index of the record within the whole data set
     * @param columnIndex 0-based position of the column in the display
     * @param dataIndex   0-based position of the field within the data row
     * @param readOnly    write protection of the column; the library does not act on it itself
     */
    public CellRef {
    }

    /**
     * 0-based index of the record within the whole data set.
     */
    @Override
    public int rowIndex() {
        return rowIndex;
    }

    /**
     * 0-based position of the column in the display, not counting the number column.
     */
    @Override
    public int columnIndex() {
        return columnIndex;
    }

    /**
     * 0-based position of the field within the data row. It stays the same when columns are
     * reordered or hidden – unlike {@link #columnIndex ()}.
     */
    @Override
    public int dataIndex() {
        return dataIndex;
    }

    /**
     * Write protection of the column, either from the configuration object or from
     * {@link GridColumn#readOnly(boolean)}. The library itself offers no editing; what the
     * application makes of it is up to it.
     */
    @Override
    public boolean readOnly() {
        return readOnly;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CellRef that)) {
            return false;
        }
        return rowIndex == that.rowIndex
                && columnIndex == that.columnIndex
                && dataIndex == that.dataIndex
                && readOnly == that.readOnly;
    }

    @NonNull
    @Override
    public String toString() {
        return "CellRef(row=" + rowIndex + ", column=" + columnIndex + ", data=" + dataIndex
                + (readOnly ? ", read-only" : "") + ")";
    }
}
