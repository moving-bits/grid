package com.movingbits.grid;

import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Adapter for the data rows. It offers the rows of the current page exactly as the data source
 * delivered them; without paging that is the whole data set.
 */
final class GridAdapter extends RecyclerView.Adapter<GridAdapter.RowHolder> {

    private static final String[][] NO_ROWS = new String[0][];

    private final Grid grid;
    private final ScrollSync fixedSync;
    private final ScrollSync bodySync;

    private GridMetrics metrics;
    private int[] rowHeights;
    /** Hook for a long tap on a row's number; without one the number reacts to nothing. */
    private GridRowView.NumberTapListener numberTapListener;
    private int page;
    private String[][] rows = NO_ROWS;

    GridAdapter(final Grid grid, final ScrollSync fixedSync, final ScrollSync bodySync) {
        this.grid = grid;
        this.fixedSync = fixedSync;
        this.bodySync = bodySync;
    }

    /** Sets the hook for a long tap on the number of a row, before the rows are created. */
    void setNumberTapListener(final GridRowView.NumberTapListener listener) {
        this.numberTapListener = listener;
    }

    /** Takes over freshly computed widths and rebuilds the visible rows. */
    void setMetrics(final GridMetrics metrics) {
        this.metrics = metrics;
        notifyDataSetChanged();
    }

    /**
     * Takes over freshly computed widths for future bindings only. Used while dragging a
     * boundary, where the visible rows are updated directly and a full rebuild per movement
     * step would be too expensive.
     */
    void setMetricsQuietly(final GridMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Takes over the row heights derived from the available height of a page.
     *
     * @param rowHeights height of each row of the page, or {@code null} without paging
     */
    void setRowHeights(final int[] rowHeights) {
        this.rowHeights = rowHeights;
        notifyDataSetChanged();
    }

    /**
     * Shows the contents of one page.
     *
     * @param page 0-based page number
     * @param rows rows of the page as delivered by the data source
     */
    void setPage(final int page, final String[][] rows) {
        this.page = page;
        this.rows = rows == null ? NO_ROWS : rows;
        notifyDataSetChanged();
    }

    int getPage() {
        return page;
    }

    /** The rows of the current page; they survive a rebuild of the view. */
    String[][] getRows() {
        return rows;
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(final @NonNull ViewGroup parent, final int viewType) {
        GridRowView row = new GridRowView(parent.getContext(), grid, false, fixedSync, bodySync, null);
        if (grid.hasRowActions()) {
            row.setNumberTapListener(numberTapListener);
        }
        final RowHolder holder = new RowHolder(row);
        // On a tap the row asks the list for its index instead of trusting the value it
        // remembered when it was bound.
        row.setRowIndexProvider(() -> {
            int position = holder.getBindingAdapterPosition();
            return position == RecyclerView.NO_POSITION ? -1 : firstRowIndex() + position;
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(final @NonNull RowHolder holder, final int position) {
        GridRowView row = (GridRowView) holder.itemView;
        if (metrics != null) {
            row.applyMetrics(metrics);
        }
        row.applyRowHeight(heightOf(position));
        row.bind(rows[position], firstRowIndex() + position);
    }

    @Override
    public int getItemCount() {
        if (metrics == null) {
            return 0;
        }
        if (!grid.isPaged()) {
            return rows.length;
        }
        if (rowHeights == null) {
            // The row height is only known after the first layout pass.
            return 0;
        }
        // More rows than belong on the page are ignored.
        return Math.min(rows.length, grid.getRowsPerPage());
    }

    /** Index of the first displayed record within the whole data set. */
    private int firstRowIndex() {
        return grid.isPaged() ? GridLayoutMath.firstRowOfPage(page, grid.getRowsPerPage()) : 0;
    }

    private int heightOf(final int position) {
        if (rowHeights == null || position >= rowHeights.length) {
            return LinearLayout.LayoutParams.WRAP_CONTENT;
        }
        return rowHeights[position];
    }

    static final class RowHolder extends RecyclerView.ViewHolder {
        RowHolder(GridRowView itemView) {
            super(itemView);
        }
    }
}
