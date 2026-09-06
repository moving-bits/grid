package com.movingbits.grid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Dialog for hiding, showing and reordering columns.
 *
 * <p>Listed are all columns except those excluded through
 * {@link Grid#ignoreColumns(String...)} – the currently hidden ones included. The checkbox on
 * the left controls visibility, the handle on the right the moving. Fixed and freely scrolling
 * columns are separated by a line; moving only happens within a column's own area.</p>
 *
 * <p>Nothing is taken over before OK, so that several changes can be collected.</p>
 */
final class ColumnSettingsDialog {

    /** One entry of the list: a column, or the separator between the two areas. */
    private static final class Entry {
        final GridColumn column;
        boolean visible;

        Entry(final GridColumn column, final boolean visible) {
            this.column = column;
            this.visible = visible;
        }

        boolean isSeparator() {
            return column == null;
        }
    }

    private static final int TYPE_COLUMN = 0;
    private static final int TYPE_SEPARATOR = 1;

    private final Context context;
    private final Grid grid;
    private final Runnable onApplied;
    private final List<Entry> entries = new ArrayList<>();

    private ItemTouchHelper touchHelper;

    private ColumnSettingsDialog(final Context context, final Grid grid, final Runnable onApplied) {
        this.context = context;
        this.grid = grid;
        this.onApplied = onApplied;
    }

    /**
     * Shows the dialog.
     *
     * @param onApplied called once the changes have been taken over
     */
    static void show(final Context context, final Grid grid, final Runnable onApplied) {
        new ColumnSettingsDialog(context, grid, onApplied).open();
    }

    private void open() {
        buildEntries();

        final RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        final ColumnAdapter adapter = new ColumnAdapter();
        list.setAdapter(adapter);

        touchHelper = new ItemTouchHelper(new DragCallback(adapter));
        touchHelper.attachToRecyclerView(list);

        final int padding = GridStyle.dp(context, 8f);
        list.setPadding(0, padding, 0, padding);
        list.setClipToPadding(false);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.grid_columns_title)
                .setView(list)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> apply())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Builds the list out of the layout order, with the separator between the fixed and the
     * free area. Hidden columns stand at their place within their area and are recognisable by
     * the missing tick.
     */
    private void buildEntries() {
        entries.clear();
        final List<GridColumn> layout = grid.getLayoutColumns();
        final int fixedCount = grid.getLayoutFixedCount();
        for (int i = 0; i < layout.size(); i++) {
            if (i == fixedCount && fixedCount > 0) {
                entries.add(new Entry(null, false));
            }
            final GridColumn column = layout.get(i);
            entries.add(new Entry(column, !grid.isHidden(column)));
        }
        if (fixedCount > 0 && fixedCount == layout.size()) {
            entries.add(new Entry(null, false));
        }
    }

    /** Position of the separator, or -1 when there is no fixed area. */
    private int separatorPosition() {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).isSeparator()) {
                return i;
            }
        }
        return -1;
    }

    /** Takes over the order, boundary and visibility set in the dialog. */
    private void apply() {
        final int separator = separatorPosition();
        final List<GridColumn> layout = new ArrayList<>();
        final Set<String> hidden = new LinkedHashSet<>();
        int fixedCount = 0;

        for (int i = 0; i < entries.size(); i++) {
            final Entry entry = entries.get(i);
            if (entry.isSeparator()) {
                continue;
            }
            layout.add(entry.column);
            if (separator >= 0 && i < separator) {
                fixedCount++;
            }
            if (!entry.visible) {
                hidden.add(entry.column.getName());
            }
        }

        grid.applyColumnLayout(layout, hidden, fixedCount);
        onApplied.run();
    }

    // ------------------------------------------------------------------ List

    private final class ColumnAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(final int position) {
            return entries.get(position).isSeparator() ? TYPE_SEPARATOR : TYPE_COLUMN;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(final @NonNull ViewGroup parent, final int type) {
            return type == TYPE_SEPARATOR
                    ? new SeparatorHolder(createSeparator())
                    : new ColumnHolder(createRow(), holder -> touchHelper.startDrag(holder));
        }

        @Override
        public void onBindViewHolder(final @NonNull RecyclerView.ViewHolder holder, final int position) {
            if (holder instanceof ColumnHolder) {
                ((ColumnHolder) holder).bind(entries.get(position));
            }
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        /** Moves an entry; the separator stays where it is. */
        boolean move(final int from, final int to) {
            final int separator = separatorPosition();
            if (from == to || entries.get(from).isSeparator() || to == separator) {
                return false;
            }
            // Not across the area boundary.
            if (separator >= 0 && (from < separator) != (to < separator)) {
                return false;
            }
            // Remove and insert instead of swapping: the ItemTouchHelper also reports jumps
            // across several positions, where a swap would twist the entries that were passed
            // over.
            entries.add(to, entries.remove(from));
            notifyItemMoved(from, to);
            return true;
        }
    }

    private View createSeparator() {
        final View separator = new View(context);
        separator.setBackgroundColor(grid.getColors().accent(separator));
        final int margin = GridStyle.dp(context, 8f);
        final RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, GridStyle.dp(context, GridStyle.BOUNDARY_WIDTH_DP)));
        params.topMargin = margin;
        params.bottomMargin = margin;
        separator.setLayoutParams(params);
        return separator;
    }

    private View createRow() {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final int padding = GridStyle.dp(context, 8f);
        row.setPadding(padding, 0, padding, 0);
        row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, GridStyle.dp(context, 48f)));

        final MaterialCheckBox checkBox = new MaterialCheckBox(context);
        row.addView(checkBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView title = new TextView(context);
        TextViewCompat.setTextAppearance(title,
                com.google.android.material.R.style.TextAppearance_MaterialComponents_Body1);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        final LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = GridStyle.dp(context, 8f);
        row.addView(title, titleParams);

        final ImageView handle = new ImageView(context);
        handle.setImageResource(R.drawable.grid_ic_reorder);
        ImageViewCompat.setImageTintList(handle,
                android.content.res.ColorStateList.valueOf(grid.getColors().scrollHint(handle)));
        handle.setContentDescription(null);
        final int handleSize = GridStyle.dp(context, 40f);
        row.addView(handle, new LinearLayout.LayoutParams(handleSize, handleSize));

        return row;
    }

    /** Picks the row up for moving. */
    private interface DragStarter {
        void startDrag(RecyclerView.ViewHolder holder);
    }

    private static final class ColumnHolder extends RecyclerView.ViewHolder {

        private final MaterialCheckBox checkBox;
        private final TextView title;
        private Entry entry;

        @SuppressLint("ClickableViewAccessibility")
        ColumnHolder(final View itemView, final DragStarter dragStarter) {
            super(itemView);
            final LinearLayout row = (LinearLayout) itemView;
            checkBox = (MaterialCheckBox) row.getChildAt(0);
            title = (TextView) row.getChildAt(1);
            final View handle = row.getChildAt(2);

            checkBox.setOnCheckedChangeListener((button, checked) -> {
                if (entry != null) {
                    entry.visible = checked;
                }
            });
            // The handle picks the row up; the moving is the ItemTouchHelper's job.
            handle.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dragStarter.startDrag(this);
                    return true;
                }
                return false;
            });
        }

        void bind(final Entry entry) {
            this.entry = entry;
            title.setText(entry.column.getTitle());
            checkBox.setChecked(entry.visible);
        }
    }

    private static final class SeparatorHolder extends RecyclerView.ViewHolder {
        SeparatorHolder(final View itemView) {
            super(itemView);
        }
    }

    /** Allows moving only through the handle and only within one area. */
    private final class DragCallback extends ItemTouchHelper.Callback {

        private final ColumnAdapter adapter;

        DragCallback(final ColumnAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public int getMovementFlags(final @NonNull RecyclerView recyclerView, final @NonNull RecyclerView.ViewHolder viewHolder) {
            if (viewHolder instanceof SeparatorHolder) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(final @NonNull RecyclerView recyclerView, final @NonNull RecyclerView.ViewHolder source, final @NonNull RecyclerView.ViewHolder target) {
            return adapter.move(source.getBindingAdapterPosition(), target.getBindingAdapterPosition());
        }

        @Override
        public boolean canDropOver(final @NonNull RecyclerView recyclerView, final @NonNull RecyclerView.ViewHolder current, final @NonNull RecyclerView.ViewHolder target) {
            if (target instanceof SeparatorHolder) {
                return false;
            }
            final int separator = separatorPosition();
            final int from = current.getBindingAdapterPosition();
            final int to = target.getBindingAdapterPosition();
            return separator < 0 || (from < separator) == (to < separator);
        }

        @Override
        public void onSwiped(final @NonNull RecyclerView.ViewHolder viewHolder, final int direction) {
            // Swiping is switched off.
        }
    }
}
