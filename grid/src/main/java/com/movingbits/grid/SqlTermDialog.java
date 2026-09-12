package com.movingbits.grid;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.widget.AppCompatCheckBox;

import java.util.ArrayList;
import java.util.List;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Puts one block of a statement together: a column, an entered value, a condensed value, a
 * function call or {@code *}.
 *
 * <p>The dialogs follow one another: what a block is made of is asked for step by step, and a
 * function call asks for its parameters through this same dialog again - that is what lets
 * calls nest.</p>
 */
final class SqlTermDialog {

    /** Receives the block that was put together. */
    interface Callback {
        void onTerm(SqlTerm term);
    }

    private final Context context;
    private final SqlGrid grid;
    private final SqlStatement statement;
    private final boolean allowAggregate;
    private final boolean allowStar;
    /** The block being changed, or {@code null} for a new one. */
    private final SqlTerm existing;
    private final Callback callback;

    private SqlTermDialog(final Context context, final SqlGrid grid, final SqlStatement statement,
                          final boolean allowAggregate, final boolean allowStar,
                          final SqlTerm existing, final Callback callback) {
        this.context = context;
        this.grid = grid;
        this.statement = statement;
        this.allowAggregate = allowAggregate;
        this.allowStar = allowStar;
        this.existing = existing;
        this.callback = callback;
    }

    /**
     * Asks for a block.
     *
     * @param allowAggregate whether a condensed value is on offer - it is not everywhere
     * @param allowStar      whether {@code *} is on offer
     */
    static void show(final Context context, final SqlGrid grid, final SqlStatement statement,
                     final boolean allowAggregate, final boolean allowStar, final Callback callback) {
        show(context, grid, statement, allowAggregate, allowStar, null, callback);
    }

    /**
     * Asks for a block that is replacing one.
     *
     * @param existing the block being changed; an entered value starts from it instead of from
     *                 nothing
     */
    static void show(final Context context, final SqlGrid grid, final SqlStatement statement,
                     final boolean allowAggregate, final boolean allowStar,
                     final SqlTerm existing, final Callback callback) {
        new SqlTermDialog(context, grid, statement, allowAggregate, allowStar, existing, callback)
                .chooseKind();
    }

    /** Straight to a column of the tables taking part; used where nothing else fits. */
    static void chooseColumn(final Context context, final SqlGrid grid, final SqlStatement statement,
                             final Callback callback) {
        new SqlTermDialog(context, grid, statement, false, false, null, callback).column();
    }

    private void chooseKind() {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> steps = new ArrayList<>();

        labels.add(context.getString(R.string.grid_sql_term_column));
        steps.add(this::column);
        labels.add(context.getString(R.string.grid_sql_term_value));
        steps.add(this::value);
        if (allowAggregate) {
            labels.add(context.getString(R.string.grid_sql_term_aggregate));
            steps.add(this::aggregate);
        }
        labels.add(context.getString(R.string.grid_sql_term_function));
        steps.add(this::function);
        if (allowStar) {
            labels.add(context.getString(R.string.grid_sql_term_star));
            steps.add(() -> callback.onTerm(new SqlStarTerm()));
        }

        SqlEditorViews.choose(context, R.string.grid_sql_choose_block, labels,
                which -> steps.get(which).run());
    }

    // -------------------------------------------------------------- Column

    /** A column: with a join in play the table comes first. */
    private void column() {
        final List<String> tables = statement.getTables();
        if (tables.isEmpty()) {
            return;
        }
        if (tables.size() == 1) {
            columnOf(tables.get(0));
            return;
        }
        SqlEditorViews.choose(context, R.string.grid_sql_choose_table, tables,
                which -> columnOf(tables.get(which)));
    }

    private void columnOf(final String table) {
        final List<String> columns = grid.getColumnNames(table);
        SqlEditorViews.choose(context, R.string.grid_sql_choose_column, columns,
                which -> callback.onTerm(new SqlColumnTerm(table, columns.get(which))));
    }

    // --------------------------------------------------------------- Value

    /** A value: its kind decides how it is compared, its text becomes a parameter. */
    private void value() {
        final ColumnType[] types = {ColumnType.STRING, ColumnType.INTEGER, ColumnType.FLOAT};
        final List<String> labels = new ArrayList<>();
        labels.add(context.getString(R.string.grid_sql_value_text));
        labels.add(context.getString(R.string.grid_sql_value_integer));
        labels.add(context.getString(R.string.grid_sql_value_decimal));

        // A value being changed starts from what it was; nobody wants to type it again.
        final SqlValueTerm previous = existing instanceof SqlValueTerm value ? value : null;
        int selected = 0;
        for (int i = 0; previous != null && i < types.length; i++) {
            if (types[i] == previous.type()) {
                selected = i;
            }
        }

        final LinearLayout content = SqlEditorViews.column(context);
        final EditText input = SqlEditorViews.input(context,
                previous == null ? "" : previous.text(), types[selected]);
        final int[] chosen = {selected};
        content.addView(SqlEditorViews.spinner(context, labels, selected, which -> {
            chosen[0] = which;
            // The keyboard follows the kind of value that was chosen.
            final EditText fresh = SqlEditorViews.input(context, input.getText().toString(), types[which]);
            input.setInputType(fresh.getInputType());
        }));
        content.addView(input);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.grid_sql_term_value)
                .setView(SqlEditorViews.padded(context, content))
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        callback.onTerm(new SqlValueTerm(types[chosen[0]], input.getText().toString())))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ----------------------------------------------------------- Aggregate

    /** A condensed value: which function, whether every value counts, and over what. */
    private void aggregate() {
        final SqlAggregate[] functions = SqlAggregate.values();
        final List<String> labels = new ArrayList<>();
        for (SqlAggregate function : functions) {
            labels.add(function.getSql());
        }

        final LinearLayout content = SqlEditorViews.column(context);
        final int[] chosen = {0};
        final CheckBox distinct = new AppCompatCheckBox(context);
        distinct.setText(R.string.grid_sql_distinct_values);
        content.addView(SqlEditorViews.spinner(context, labels, 0, which -> chosen[0] = which));
        content.addView(distinct);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.grid_sql_choose_aggregate)
                .setView(SqlEditorViews.padded(context, content))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    final SqlAggregate function = functions[chosen[0]];
                    // What is condensed is a block of its own - only not another aggregate.
                    show(context, grid, statement, false, function.allowsStar(),
                            argument -> callback.onTerm(new SqlAggregateTerm(
                                    function, argument, distinct.isChecked())));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------ Function

    /**
     * A function call: a name out of the ones that are allowed, and its parameters - each of
     * them a block again, so calls nest as deeply as needed.
     */
    private void function() {
        final List<String> names = new ArrayList<>(SqlFunctions.builtIn());
        names.addAll(grid.getSqlFunctions());
        final List<SqlTerm> arguments = new ArrayList<>();
        final int[] chosen = {0};

        final LinearLayout content = SqlEditorViews.column(context);
        content.addView(SqlEditorViews.spinner(context, names, 0, which -> chosen[0] = which));
        final ChipGroup parameters = SqlEditorViews.chipGroup(context);
        content.addView(parameters);
        showParameters(parameters, arguments);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.grid_sql_term_function)
                .setView(SqlEditorViews.padded(context, content))
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        callback.onTerm(new SqlFunctionTerm(names.get(chosen[0]), arguments)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The parameters of the call as chips, with a plus for one more. */
    private void showParameters(final ChipGroup parameters, final List<SqlTerm> arguments) {
        parameters.removeAllViews();
        for (int i = 0; i < arguments.size(); i++) {
            final int index = i;
            parameters.addView(SqlEditorViews.chip(context, arguments.get(i).label(),
                    () -> show(context, grid, statement, allowAggregate, false, arguments.get(index), term -> {
                        arguments.set(index, term);
                        showParameters(parameters, arguments);
                    }),
                    () -> {
                        arguments.remove(index);
                        showParameters(parameters, arguments);
                    }));
        }
        parameters.addView(SqlEditorViews.addChip(context,
                () -> show(context, grid, statement, allowAggregate, false, term -> {
                    arguments.add(term);
                    showParameters(parameters, arguments);
                })));
    }
}
