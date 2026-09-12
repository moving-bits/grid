package com.movingbits.grid;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.widget.TextViewCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The SQL editor: a statement is clicked together out of blocks, and what comes of it is a
 * grid or a number of changed rows.
 *
 * <p>Every clause has a row of chips: a short tap changes a block, the cross takes it out, the
 * plus adds one. Underneath stands the statement as it looks at that moment, and under that
 * the first thing that keeps it from being run - until nothing does, and only then can it be
 * run.</p>
 *
 * <p>The editor works on a copy of the statement: nothing is taken over before "Run". Values
 * become parameters of a prepared statement, table and column names come from selection lists
 * of what the database actually holds.</p>
 */
final class SqlEditorDialog implements SqlSnippetTarget {

    /** Reports what came of a statement, so that the view can follow along. */
    interface Callback {
        void onExecuted(SqlExecution execution);
    }

    private final Context context;
    private final SqlGrid grid;
    private final Callback callback;
    /** The copy being edited; the grid keeps its own until the statement is run. */
    private SqlStatement statement;

    private AlertDialog dialog;
    /** Whether a statement was carried out before the editor closed. */
    private boolean executed;
    private TextView preview;
    private TextView finding;

    /** Only a query knows duplicates; a change has nothing to leave out. */
    private CheckBox distinct;
    private LinearLayout selectSection;
    private LinearLayout setSection;
    private LinearLayout groupSection;
    private LinearLayout havingSection;
    private LinearLayout orderSection;

    private ChipGroup selectChips;
    private ChipGroup fromChips;
    private ChipGroup setChips;
    private ChipGroup whereChips;
    private ChipGroup groupChips;
    private ChipGroup havingChips;
    private ChipGroup orderChips;

    private SqlEditorDialog(final Context context, final SqlGrid grid, final Callback callback) {
        this.context = context;
        this.grid = grid;
        this.callback = callback;
        // A copy through the stored form: it costs nothing and keeps Cancel a real cancel.
        this.statement = SqlStatement.parse(grid.getStatement().toJson());
        // Opened while a table is on display, and nothing chosen yet: that table is what the
        // statement is most likely about, so it is filled in. Whatever was clicked together
        // before stays as it is.
        if (statement.getTable().isEmpty() && grid.hasCurrentTable()) {
            statement.setTable(grid.getCurrentTable());
        }
    }

    /** Opens the editor with the statement the grid last worked on. */
    static void show(final Context context, final SqlGrid grid, final Callback callback) {
        new SqlEditorDialog(context, grid, callback).open();
    }

    private void open() {
        final LinearLayout content = SqlEditorViews.column(context);
        content.addView(kindRow());

        selectChips = SqlEditorViews.chipGroup(context);
        fromChips = SqlEditorViews.chipGroup(context);
        setChips = SqlEditorViews.chipGroup(context);
        whereChips = SqlEditorViews.chipGroup(context);
        groupChips = SqlEditorViews.chipGroup(context);
        havingChips = SqlEditorViews.chipGroup(context);
        orderChips = SqlEditorViews.chipGroup(context);

        selectSection = section(content, R.string.grid_sql_section_select, selectChips);
        section(content, R.string.grid_sql_section_from, fromChips);
        setSection = section(content, R.string.grid_sql_section_set, setChips);
        section(content, R.string.grid_sql_section_where, whereChips);
        groupSection = section(content, R.string.grid_sql_section_group, groupChips);
        havingSection = section(content, R.string.grid_sql_section_having, havingChips);
        orderSection = section(content, R.string.grid_sql_section_order, orderChips);

        preview = SqlEditorViews.note(context);
        preview.setTypeface(Typeface.MONOSPACE);
        preview.setPadding(0, SqlEditorViews.padding(context), 0, 0);
        content.addView(preview);
        finding = SqlEditorViews.note(context);
        content.addView(finding);

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(SqlEditorViews.padded(context, content));

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        if (grid.getSnippetLoadListener() != null || grid.getSnippetSaveListener() != null) {
            builder.setCustomTitle(titleRow());
        } else {
            builder.setTitle(R.string.grid_sql_title);
        }
        dialog = builder
                .setView(scroll)
                // The button is taken over below: a statement the database refuses must not
                // close the editor, or the work would be gone.
                .setPositiveButton(R.string.grid_sql_run, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(view -> run()));
        // However the editor goes away - run, cancelled, or a tap beside it - the application
        // hears of it exactly once.
        dialog.setOnDismissListener(closed -> {
            final OnEditorClosedListener listener = grid.getEditorClosedListener();
            if (listener != null) {
                listener.onEditorClosed(executed);
            }
        });
        dialog.show();
        refresh();
    }

    /**
     * The title of the dialog with the two buttons for stored statements on its right. Each
     * only appears where the application has said what to do with it.
     */
    private View titleRow() {
        final LinearLayout row = SqlEditorViews.row(context);
        final int padding = GridStyle.dp(context, 16f);
        row.setPadding(padding, padding, padding / 2, 0);

        final TextView title = new TextView(context);
        TextViewCompat.setTextAppearance(title,
                com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline6);
        title.setText(R.string.grid_sql_title);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // A little air between them: two icons side by side are easily mistaken for each
        // other under a thumb.
        final LinearLayout.LayoutParams spaced = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spaced.setMarginStart(GridStyle.dp(context, 12f));
        if (grid.getSnippetLoadListener() != null) {
            row.addView(SqlEditorViews.iconButton(context, R.drawable.grid_ic_open,
                    R.string.grid_sql_load, this::loadSnippet), spaced);
        }
        if (grid.getSnippetSaveListener() != null) {
            row.addView(SqlEditorViews.iconButton(context, R.drawable.grid_ic_save,
                    R.string.grid_sql_save, this::saveSnippet), spaced);
        }
        return row;
    }

    /**
     * Asks the application for a stored statement. Nothing happens here: the application hands
     * one over through {@link #load(String)} whenever it has one.
     */
    private void loadSnippet() {
        final OnSnippetLoadListener listener = grid.getSnippetLoadListener();
        if (listener != null) {
            listener.onSnippetLoad(this);
        }
    }

    /** Hands the statement the editor is working on to the application, to be stored. */
    private void saveSnippet() {
        final OnSnippetSaveListener listener = grid.getSnippetSaveListener();
        if (listener != null) {
            listener.onSnippetSave(statement.toJson());
        }
    }

    /**
     * Takes a stored statement over. What was clicked together so far is dropped - the
     * application asked for the stored one - and the editor shows what was read.
     */
    @Override
    public void load(final String snippet) {
        if (dialog == null || !dialog.isShowing()) {
            // The editor is gone; there is nothing left to show it in.
            return;
        }
        statement = SqlStatement.parse(snippet);
        refresh();
    }

    /** A clause: its title, and the chips of its blocks underneath. */
    private LinearLayout section(final LinearLayout content, final int titleRes, final ChipGroup chips) {
        final LinearLayout section = SqlEditorViews.column(context);
        section.setPadding(0, SqlEditorViews.padding(context), 0, 0);
        section.addView(SqlEditorViews.title(context, titleRes));
        section.addView(chips);
        content.addView(section, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return section;
    }

    /**
     * Query or change - the clauses on offer follow from it - and beside it whether equal rows
     * of the result are shown only once. The two stand in one row: the dialog is long enough
     * as it is.
     */
    private LinearLayout kindRow() {
        final List<String> labels = new ArrayList<>();
        labels.add(context.getString(R.string.grid_sql_kind_select));
        labels.add(context.getString(R.string.grid_sql_kind_update));
        final LinearLayout row = SqlEditorViews.row(context);
        row.addView(SqlEditorViews.spinner(context, labels,
                statement.getKind() == SqlKind.UPDATE ? 1 : 0,
                which -> {
                    statement.setKind(which == 1 ? SqlKind.UPDATE : SqlKind.SELECT);
                    refresh();
                }));

        distinct = new AppCompatCheckBox(context);
        distinct.setText(R.string.grid_sql_distinct);
        distinct.setChecked(statement.isDistinct());
        distinct.setOnCheckedChangeListener((view, checked) -> {
            statement.setDistinct(checked);
            refresh();
        });
        final LinearLayout.LayoutParams beside = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        beside.setMarginStart(GridStyle.dp(context, 16f));
        row.addView(distinct, beside);
        return row;
    }

    // ------------------------------------------------------------- Drawing

    /** Draws every clause anew, then the preview and the finding under it. */
    private void refresh() {
        final boolean query = statement.getKind() == SqlKind.SELECT;
        distinct.setVisibility(query ? View.VISIBLE : View.GONE);
        selectSection.setVisibility(query ? View.VISIBLE : View.GONE);
        groupSection.setVisibility(query ? View.VISIBLE : View.GONE);
        havingSection.setVisibility(query ? View.VISIBLE : View.GONE);
        orderSection.setVisibility(query ? View.VISIBLE : View.GONE);
        setSection.setVisibility(query ? View.GONE : View.VISIBLE);

        fillProjection();
        fillFrom();
        fillAssignments();
        fillGroupBy();
        fillOrderBy();
        SqlConditionDialog.fillGroup(context, grid, statement, statement.getWhere(), whereChips,
                false, this::refresh);
        SqlConditionDialog.fillGroup(context, grid, statement, statement.getHaving(), havingChips,
                true, this::refresh);

        preview.setText(statement.render().sql());
        final List<SqlFinding> findings = grid.validate(statement);
        finding.setText(findings.isEmpty()
                ? context.getString(R.string.grid_sql_valid)
                : context.getString(findings.get(0).getLabelRes()));
        finding.setTextColor(findings.isEmpty()
                ? GridStyle.themeOnSurface(finding) : GridStyle.themeAccent(finding));
        if (dialog != null && dialog.getButton(DialogInterface.BUTTON_POSITIVE) != null) {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(findings.isEmpty());
        }
    }

    /** The columns of the result; a short tap renames one, the cross takes it out. */
    private void fillProjection() {
        selectChips.removeAllViews();
        final List<SqlProjection> projection = statement.getProjection();
        for (int i = 0; i < projection.size(); i++) {
            final int index = i;
            selectChips.addView(SqlEditorViews.chip(context, projection.get(i).label(),
                    () -> renameColumn(index),
                    () -> {
                        // A criterion that sorts by this column would lose its ground.
                        dropOrder(projection.get(index).alias());
                        projection.remove(index);
                        refresh();
                    }));
        }
        selectChips.addView(SqlEditorViews.addChip(context,
                () -> SqlTermDialog.show(context, grid, statement, true, true, term -> {
                    projection.add(new SqlProjection(term, aliasFor(term)));
                    refresh();
                })));
    }

    /** The table read from or changed, and the tables joined to it. */
    private void fillFrom() {
        fromChips.removeAllViews();
        final List<String> tables = grid.getTables();
        final Runnable chooseTable = () -> SqlEditorViews.choose(context,
                R.string.grid_sql_choose_table, tables, which -> {
                    if (!tables.get(which).equals(statement.getTable())) {
                        // The old table's columns mean nothing in the new one.
                        setTable(tables.get(which));
                    }
                    refresh();
                });
        if (statement.getTable().isEmpty()) {
            fromChips.addView(SqlEditorViews.addChip(context, chooseTable));
        } else {
            fromChips.addView(SqlEditorViews.chip(context, statement.getTable(), chooseTable, null));
        }

        if (statement.getKind() == SqlKind.UPDATE || statement.getTable().isEmpty()) {
            // A change works on one table, and without a first one there is nothing to join to.
            return;
        }
        final List<SqlJoin> joins = statement.getJoins();
        for (int i = 0; i < joins.size(); i++) {
            final int index = i;
            fromChips.addView(SqlEditorViews.chip(context, joins.get(i).label(),
                    () -> editJoin(index),
                    () -> {
                        joins.remove(index);
                        refresh();
                    }));
        }
        fromChips.addView(SqlEditorViews.chip(context,
                context.getString(R.string.grid_sql_choose_join), () -> editJoin(-1), null));
    }

    /** What an update writes into which column. */
    private void fillAssignments() {
        setChips.removeAllViews();
        final List<SqlAssignment> assignments = statement.getAssignments();
        for (int i = 0; i < assignments.size(); i++) {
            final int index = i;
            setChips.addView(SqlEditorViews.chip(context, assignments.get(i).label(),
                    () -> chooseValue(assignments.get(index).column(), index),
                    () -> {
                        assignments.remove(index);
                        refresh();
                    }));
        }
        setChips.addView(SqlEditorViews.addChip(context, () -> {
            final List<String> columns = grid.getColumnNames(statement.getTable());
            SqlEditorViews.choose(context, R.string.grid_sql_choose_column, columns,
                    which -> chooseValue(columns.get(which), -1));
        }));
    }

    /** The columns that form the groups. */
    private void fillGroupBy() {
        groupChips.removeAllViews();
        final List<SqlTerm> groupBy = statement.getGroupBy();
        for (int i = 0; i < groupBy.size(); i++) {
            final int index = i;
            groupChips.addView(SqlEditorViews.chip(context, groupBy.get(i).label(), null,
                    () -> {
                        groupBy.remove(index);
                        refresh();
                    }));
        }
        groupChips.addView(SqlEditorViews.addChip(context,
                () -> SqlTermDialog.chooseColumn(context, grid, statement, term -> {
                    groupBy.add(term);
                    refresh();
                })));
    }

    /** What is sorted by: a column of the result, and its direction on every tap. */
    private void fillOrderBy() {
        orderChips.removeAllViews();
        final List<SqlOrder> orderBy = statement.getOrderBy();
        for (int i = 0; i < orderBy.size(); i++) {
            final int index = i;
            orderChips.addView(SqlEditorViews.chip(context, orderBy.get(i).label(),
                    () -> {
                        final SqlOrder order = orderBy.get(index);
                        orderBy.set(index, new SqlOrder(order.alias(),
                                order.direction() == SortDirection.ASCENDING
                                        ? SortDirection.DESCENDING : SortDirection.ASCENDING));
                        refresh();
                    },
                    () -> {
                        orderBy.remove(index);
                        refresh();
                    }));
        }
        orderChips.addView(SqlEditorViews.addChip(context, () -> {
            final List<String> aliases = new ArrayList<>();
            for (SqlProjection column : statement.getProjection()) {
                if (!column.isStar()) {
                    aliases.add(column.alias());
                }
            }
            SqlEditorViews.choose(context, R.string.grid_sql_section_order, aliases, which -> {
                orderBy.add(new SqlOrder(aliases.get(which), SortDirection.ASCENDING));
                refresh();
            });
        }));
    }

    // ------------------------------------------------------------ Changing

    /**
     * Takes over another table. What was clicked together for the old one refers to columns
     * that the new one does not have, so it falls away.
     */
    private void setTable(final String table) {
        statement.setTable(table);
        statement.getJoins().clear();
        statement.getProjection().clear();
        statement.getAssignments().clear();
        statement.getWhere().getParts().clear();
        statement.getHaving().getParts().clear();
        statement.getGroupBy().clear();
        statement.getOrderBy().clear();
    }

    /** The name a column of the result carries; the editor suggests one, the user may change it. */
    private void renameColumn(final int index) {
        final SqlProjection column = statement.getProjection().get(index);
        if (column.isStar()) {
            return;
        }
        final EditText input = SqlEditorViews.input(context, column.alias(), ColumnType.STRING);
        input.setHint(R.string.grid_sql_alias);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.grid_sql_alias)
                .setView(SqlEditorViews.padded(context, input))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dropOrder(column.alias());
                    statement.getProjection().set(index,
                            new SqlProjection(column.term(), input.getText().toString()));
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Drops what sorts by this name; without the column there is nothing to sort by. */
    private void dropOrder(final String alias) {
        final List<SqlOrder> orderBy = statement.getOrderBy();
        for (int i = orderBy.size() - 1; i >= 0; i--) {
            if (orderBy.get(i).alias().equals(alias)) {
                orderBy.remove(i);
            }
        }
    }

    /**
     * Asks for the value a column is to receive.
     *
     * @param index position of the assignment being changed, or {@code -1} for a new one
     */
    private void chooseValue(final String column, final int index) {
        final SqlTerm previous = index < 0 ? null : statement.getAssignments().get(index).value();
        SqlTermDialog.show(context, grid, statement, false, false, previous, term -> {
            final SqlAssignment assignment = new SqlAssignment(column, term);
            if (index < 0) {
                statement.getAssignments().add(assignment);
            } else {
                statement.getAssignments().set(index, assignment);
            }
            refresh();
        });
    }

    /**
     * Joins a table, or changes a join: how it is joined, which table, and on which condition.
     *
     * @param index position of the join being changed, or {@code -1} for a new one
     */
    private void editJoin(final int index) {
        final SqlJoin existing = index < 0 ? null : statement.getJoins().get(index);
        final List<String> tables = new ArrayList<>();
        for (String table : grid.getTables()) {
            // A table takes part at most once; its own name qualifies its columns.
            if (!table.equals(statement.getTable()) && !isJoined(table, index)) {
                tables.add(table);
            }
        }
        if (existing != null && !tables.contains(existing.table())) {
            tables.add(0, existing.table());
        }
        if (tables.isEmpty()) {
            return;
        }

        final SqlJoinType[] types = SqlJoinType.values();
        final List<String> typeLabels = new ArrayList<>();
        for (SqlJoinType type : types) {
            typeLabels.add(context.getString(type.getLabelRes()));
        }
        final int[] chosenType = {existing == null ? 0 : existing.type().ordinal()};
        final int[] chosenTable = {existing == null ? 0 : tables.indexOf(existing.table())};
        final List<SqlComparison> on = new ArrayList<>(existing == null ? new ArrayList<>() : existing.on());

        final LinearLayout content = SqlEditorViews.column(context);
        content.addView(SqlEditorViews.spinner(context, typeLabels, chosenType[0],
                which -> chosenType[0] = which));
        content.addView(SqlEditorViews.spinner(context, tables, Math.max(0, chosenTable[0]),
                which -> chosenTable[0] = which));
        final ChipGroup conditions = SqlEditorViews.chipGroup(context);
        content.addView(conditions);
        fillJoinConditions(conditions, on, tables, chosenTable);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.grid_sql_choose_join)
                .setView(SqlEditorViews.padded(context, content))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    final SqlJoin join = new SqlJoin(types[chosenType[0]], tables.get(chosenTable[0]), on);
                    if (index < 0) {
                        statement.getJoins().add(join);
                    } else {
                        statement.getJoins().set(index, join);
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private boolean isJoined(final String table, final int except) {
        final List<SqlJoin> joins = statement.getJoins();
        for (int i = 0; i < joins.size(); i++) {
            if (i != except && joins.get(i).table().equals(table)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The conditions a join rests on. The table being joined is only chosen in this dialog, so
     * its columns are offered through a statement that already knows about it.
     */
    private void fillJoinConditions(final ChipGroup chips, final List<SqlComparison> on,
                                    final List<String> tables, final int[] chosenTable) {
        chips.removeAllViews();
        final SqlStatement reachable = SqlStatement.parse(statement.toJson());
        reachable.getJoins().add(new SqlJoin(SqlJoinType.INNER, tables.get(Math.max(0, chosenTable[0])), null));

        for (int i = 0; i < on.size(); i++) {
            final int index = i;
            chips.addView(SqlEditorViews.chip(context, on.get(i).label(),
                    () -> SqlConditionDialog.showComparison(context, grid, reachable, false,
                            on.get(index), condition -> {
                                on.set(index, (SqlComparison) condition);
                                fillJoinConditions(chips, on, tables, chosenTable);
                            }),
                    () -> {
                        on.remove(index);
                        fillJoinConditions(chips, on, tables, chosenTable);
                    }));
        }
        chips.addView(SqlEditorViews.addChip(context,
                () -> SqlConditionDialog.showComparison(context, grid, reachable, false, null,
                        condition -> {
                            on.add((SqlComparison) condition);
                            fillJoinConditions(chips, on, tables, chosenTable);
                        })));
    }

    /**
     * A name for a new column of the result: the column's own name, the aggregate together
     * with what it condenses, or the name of the function. A number is appended where the name
     * is taken already, because two columns of the same name cannot both be addressed.
     */
    private String aliasFor(final SqlTerm term) {
        String base = "expression";
        if (term instanceof SqlColumnTerm column) {
            base = column.column();
        } else if (term instanceof SqlAggregateTerm aggregate) {
            base = aggregate.function().getSql().toLowerCase(Locale.ROOT);
            if (aggregate.argument() instanceof SqlColumnTerm argument) {
                base = base + "_" + argument.column();
            }
        } else if (term instanceof SqlFunctionTerm function) {
            base = function.name().toLowerCase(Locale.ROOT);
        }

        String alias = base;
        for (int number = 2; isTaken(alias); number++) {
            alias = base + "_" + number;
        }
        return alias;
    }

    private boolean isTaken(final String alias) {
        for (SqlProjection column : statement.getProjection()) {
            if (column.alias().equals(alias)) {
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------- Executing

    /**
     * Runs the statement: the model has already been checked, so the database is asked next
     * whether it accepts it, and a change without a condition is asked about.
     */
    private void run() {
        if (!grid.validate(statement).isEmpty()) {
            return;
        }
        final String rejected = grid.checkWithDatabase(statement);
        if (rejected != null) {
            // The editor stays open: whoever gets a complaint wants to correct it.
            showFailure(rejected);
            return;
        }
        if (statement.isCritical()) {
            confirm();
            return;
        }
        execute();
    }

    /** The question before a change without a condition, with the number of rows it affects. */
    private void confirm() {
        final int rows = grid.countAffected(statement);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.grid_sql_confirm_title)
                .setMessage(rows < 0
                        ? context.getString(R.string.grid_sql_confirm_all)
                        : context.getResources().getQuantityString(
                                R.plurals.grid_sql_confirm_rows, rows, rows))
                .setPositiveButton(R.string.grid_sql_run, (dialog, which) -> execute())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void execute() {
        final SqlExecution execution = grid.execute(statement);
        executed = execution.successful();
        // The view first: a result is then on screen behind the message, and the application
        // hears of the editor closing with the display already up to date.
        callback.onExecuted(execution);
        dialog.dismiss();
        if (!execution.successful()) {
            showFailure(execution.error());
        } else if (execution.kind() == SqlKind.UPDATE) {
            showResult(context.getResources().getQuantityString(
                    R.plurals.grid_sql_result_changed, execution.rowCount(), execution.rowCount()));
        }
    }

    private void showFailure(final String error) {
        showResult(context.getString(R.string.grid_sql_result_failed, String.valueOf(error)));
    }

    private void showResult(final String message) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.grid_sql_result_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
