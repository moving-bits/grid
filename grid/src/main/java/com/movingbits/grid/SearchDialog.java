package com.movingbits.grid;

import android.content.Context;
import android.content.res.Configuration;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Dialog for searching: an operator and a value field per column.
 *
 * <p>Right at the top stands the entry "Global", which searches across every column; below it
 * the columns on display. Which operators are on offer depends on the column's type. An empty
 * value field means: this column is not taken into the search.</p>
 *
 * <p>Nothing is taken over before OK; "Reset" removes the search entirely. The search is
 * transient and does not end up in the configuration object.</p>
 */
final class SearchDialog {

    /** One entry of the list: a column or the entry "Global". */
    private static final class Entry {
        /** {@code null} for the entry "Global". */
        final GridColumn column;
        SearchOperator operator;
        String value = "";

        Entry(final GridColumn column) {
            this.column = column;
            this.operator = operators().get(0);
        }

        /** The operators that suit this entry; "Global" searches as text. */
        List<SearchOperator> operators() {
            return SearchOperator.forType(column == null ? null : column.getType());
        }

        String name() {
            return column == null ? SearchRequest.ALL_COLUMNS : column.getName();
        }
    }

    private final Context context;
    private final Grid grid;
    private final Runnable onApplied;
    private final List<Entry> entries = new ArrayList<>();

    private SearchDialog(final Context context, final Grid grid, final Runnable onApplied) {
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
        new SearchDialog(context, grid, onApplied).open();
    }

    private void open() {
        buildEntries();

        final RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(new SearchAdapter());

        final int padding = GridStyle.dp(context, 8f);
        list.setPadding(0, padding, 0, padding);
        list.setClipToPadding(false);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.grid_search_title)
                .setView(list)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> apply())
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.grid_search_reset, (dialog, which) -> reset())
                .show();
    }

    /**
     * Builds the list out of the entry "Global" and the columns on display, taking over the
     * search in effect so that an existing restriction can be changed.
     */
    private void buildEntries() {
        entries.clear();
        entries.add(new Entry(null));
        for (int i = 0; i < grid.getColumnCount(); i++) {
            entries.add(new Entry(grid.getColumn(i)));
        }

        for (SearchRequest request : grid.getSearch()) {
            for (Entry entry : entries) {
                if (entry.name().equals(request.columnName()) && entry.operators().contains(request.operator())) {
                    entry.operator = request.operator();
                    entry.value = request.value();
                    break;
                }
            }
        }
    }

    /** Takes over the conditions entered; entries without a value are ignored. */
    private void apply() {
        final List<SearchRequest> requests = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.value.trim().isEmpty()) {
                requests.add(new SearchRequest(entry.name(), entry.operator, entry.value.trim()));
            }
        }
        grid.search(requests);
        onApplied.run();
    }

    /** Removes the whole search. */
    private void reset() {
        grid.search(null);
        onApplied.run();
    }

    // ------------------------------------------------------------------ List

    private final class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(final @NonNull ViewGroup parent, final int type) {
            return new RowHolder(createRow());
        }

        @Override
        public void onBindViewHolder(final @NonNull RecyclerView.ViewHolder holder, final int position) {
            ((RowHolder) holder).bind(entries.get(position));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }

    /**
     * One row made of the column name, the operator and the value field.
     *
     * <p>In landscape the three stand next to each other. In portrait the width is not enough
     * for that: there the column name gets a line of its own, with the operator and the value
     * field – indented – below it.</p>
     */
    private View createRow() {
        final boolean landscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        final int padding = GridStyle.dp(context, 8f);

        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(padding, padding / 2, padding, padding / 2);
        row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView title = new TextView(context);
        title.setId(R.id.grid_row_title);
        TextViewCompat.setTextAppearance(title, com.google.android.material.R.style.TextAppearance_MaterialComponents_Body1);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);

        final Spinner operator = new AppCompatSpinner(context);
        operator.setId(R.id.grid_row_operator);

        final EditText value = new AppCompatEditText(context);
        value.setId(R.id.grid_row_value);
        value.setSingleLine(true);
        value.setHint(R.string.grid_search_value);
        TextViewCompat.setTextAppearance(value, com.google.android.material.R.style.TextAppearance_MaterialComponents_Body1);

        if (landscape) {
            row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f));
            row.addView(operator, weighted(4f, GridStyle.dp(context, 4f)));
            row.addView(value, weighted(4f, GridStyle.dp(context, 4f)));
            return row;
        }

        row.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout inputs = new LinearLayout(context);
        inputs.setOrientation(LinearLayout.HORIZONTAL);
        inputs.setGravity(Gravity.CENTER_VERTICAL);
        // The operator needs more room than the value field: "begins not with" is long.
        inputs.addView(operator, weighted(6f, 0));
        inputs.addView(value, weighted(4f, padding));

        final LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.leftMargin = GridStyle.dp(context, 16f);
        row.addView(inputs, inputParams);

        return row;
    }

    private static LinearLayout.LayoutParams weighted(final float weight, final int leftMargin) {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.leftMargin = leftMargin;
        return params;
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {

        private final TextView title;
        private final Spinner operator;
        private final EditText value;
        private Entry entry;

        RowHolder(final View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.grid_row_title);
            operator = itemView.findViewById(R.id.grid_row_operator);
            value = itemView.findViewById(R.id.grid_row_value);

            operator.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
                    if (entry != null && position < entry.operators().size()) {
                        entry.operator = entry.operators().get(position);
                    }
                }

                @Override
                public void onNothingSelected(final AdapterView<?> parent) {
                    // stays with the operator chosen last
                }
            });
            value.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
                }

                @Override
                public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
                }

                @Override
                public void afterTextChanged(final Editable s) {
                    if (entry != null) {
                        entry.value = s.toString();
                    }
                }
            });
        }

        void bind(final Entry entry) {
            // Detach first: setting the selection and the text must not arrive as input by the
            // user into what is by now a different entry.
            this.entry = null;

            final Context context = itemView.getContext();
            title.setText(entry.column == null ? context.getString(R.string.grid_search_global) : entry.column.getTitle());

            final List<SearchOperator> operators = entry.operators();
            final List<String> labels = new ArrayList<>(operators.size());
            for (SearchOperator candidate : operators) {
                labels.add(context.getString(candidate.getLabelRes()));
            }
            // A view of our own instead of simple_spinner_item: its large type and horizontal
            // padding cut the longer labels off before the arrow.
            final ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.grid_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            operator.setAdapter(adapter);
            operator.setSelection(Math.max(0, operators.indexOf(entry.operator)));

            value.setInputType(entry.column != null && entry.column.getType().isNumeric()
                    ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED
                    : InputType.TYPE_CLASS_TEXT);
            value.setText(entry.value);

            this.entry = entry;
        }
    }
}
