package com.movingbits.grid;

import android.content.Context;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Puts a condition together: a block on the left, a comparison, and as many blocks on the
 * right as the comparison takes.
 *
 * <p>Conditions form groups, and a group can hold groups of its own. The chips of a group are
 * drawn by {@link #fillGroup} - the editor uses it for its {@code WHERE} and {@code HAVING}
 * clauses, and a nested group uses it again for its own parts.</p>
 */
final class SqlConditionDialog {

    /** Receives the condition that was put together. */
    interface Callback {
        void onCondition(SqlCondition condition);
    }

    private final Context context;
    private final SqlGrid grid;
    private final SqlStatement statement;
    private final boolean allowAggregate;
    private final Callback callback;

    private SqlTerm left;
    private SqlOperator operator;
    private final List<SqlTerm> right = new ArrayList<>();

    private SqlConditionDialog(final Context context, final SqlGrid grid, final SqlStatement statement,
                               final boolean allowAggregate, final SqlComparison existing,
                               final Callback callback) {
        this.context = context;
        this.grid = grid;
        this.statement = statement;
        this.allowAggregate = allowAggregate;
        this.callback = callback;
        this.left = existing == null ? null : existing.left();
        this.operator = existing == null || existing.operator() == null
                ? SqlOperator.EQUALS : existing.operator();
        if (existing != null) {
            right.addAll(existing.right());
        }
    }

    /**
     * Asks for a comparison.
     *
     * @param existing       the comparison being changed, or {@code null} for a new one
     * @param allowAggregate whether a condensed value may take part - it may in
     *                       {@code HAVING}, not in {@code WHERE}
     */
    static void showComparison(final Context context, final SqlGrid grid, final SqlStatement statement,
                               final boolean allowAggregate, final SqlComparison existing,
                               final Callback callback) {
        new SqlConditionDialog(context, grid, statement, allowAggregate, existing, callback).open();
    }

    private void open() {
        final LinearLayout content = SqlEditorViews.column(context);

        final ChipGroup leftChip = SqlEditorViews.chipGroup(context);
        content.addView(leftChip);

        final SqlOperator[] operators = SqlOperator.values();
        final List<String> labels = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < operators.length; i++) {
            labels.add(context.getString(operators[i].getLabelRes()));
            if (operators[i] == operator) {
                selected = i;
            }
        }
        final ChipGroup operands = SqlEditorViews.chipGroup(context);
        content.addView(SqlEditorViews.spinner(context, labels, selected, which -> {
            operator = operators[which];
            // Only as many operands as the new comparison takes; the rest falls away.
            final int allowed = maxOperands();
            while (right.size() > allowed) {
                right.remove(right.size() - 1);
            }
            fillOperands(operands);
        }));
        content.addView(operands);

        fillLeft(leftChip);
        fillOperands(operands);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.grid_sql_condition_title)
                .setView(SqlEditorViews.padded(context, content))
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        callback.onCondition(new SqlComparison(left, operator, right)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** How many operands the comparison in effect takes; {@link Integer#MAX_VALUE} for any. */
    private int maxOperands() {
        return operator.getOperandCount() == SqlOperator.ANY
                ? Integer.MAX_VALUE : operator.getOperandCount();
    }

    private void fillLeft(final ChipGroup chips) {
        chips.removeAllViews();
        final Runnable choose = () -> SqlTermDialog.show(context, grid, statement, allowAggregate, false,
                left, term -> {
                    left = term;
                    fillLeft(chips);
                });
        if (left == null) {
            chips.addView(SqlEditorViews.addChip(context, choose));
        } else {
            chips.addView(SqlEditorViews.chip(context, left.label(), choose, () -> {
                left = null;
                fillLeft(chips);
            }));
        }
    }

    private void fillOperands(final ChipGroup chips) {
        chips.removeAllViews();
        for (int i = 0; i < right.size(); i++) {
            final int index = i;
            chips.addView(SqlEditorViews.chip(context, right.get(i).label(),
                    () -> SqlTermDialog.show(context, grid, statement, allowAggregate, false,
                            right.get(index), term -> {
                                right.set(index, term);
                                fillOperands(chips);
                            }),
                    () -> {
                        right.remove(index);
                        fillOperands(chips);
                    }));
        }
        if (right.size() < maxOperands()) {
            chips.addView(SqlEditorViews.addChip(context,
                    () -> SqlTermDialog.show(context, grid, statement, allowAggregate, false, term -> {
                        right.add(term);
                        fillOperands(chips);
                    })));
        }
    }

    // --------------------------------------------------------------- Group

    /**
     * Draws the parts of a group as chips: a short tap changes a part, the cross takes it out,
     * and the two pluses add another condition or a group of its own.
     *
     * @param onChanged called after every change, so that the preview follows along
     */
    static void fillGroup(final Context context, final SqlGrid grid, final SqlStatement statement,
                          final SqlConditionGroup group, final ChipGroup chips,
                          final boolean allowAggregate, final Runnable onChanged) {
        chips.removeAllViews();
        final List<SqlCondition> parts = group.getParts();
        for (int i = 0; i < parts.size(); i++) {
            final int index = i;
            final SqlCondition part = parts.get(i);
            chips.addView(SqlEditorViews.chip(context, part.label(),
                    () -> edit(context, grid, statement, group, index, allowAggregate, onChanged),
                    () -> {
                        parts.remove(index);
                        onChanged.run();
                    }));
        }

        chips.addView(SqlEditorViews.addChip(context, () ->
                showComparison(context, grid, statement, allowAggregate, null, condition -> {
                    parts.add(condition);
                    onChanged.run();
                })));
        // A group of its own, for conditions that are to apply the other way round. It joins
        // the parts once it holds something; an empty one would only confuse.
        chips.addView(SqlEditorViews.chip(context, context.getString(R.string.grid_sql_add_group),
                () -> {
                    final SqlConditionGroup nested = new SqlConditionGroup(
                            group.getJunction() == SqlJunction.AND ? SqlJunction.OR : SqlJunction.AND,
                            Collections.emptyList());
                    showGroup(context, grid, statement, nested, allowAggregate, () -> {
                        if (!nested.getParts().isEmpty() && !parts.contains(nested)) {
                            parts.add(nested);
                        }
                        onChanged.run();
                    });
                }, null));

        // How the parts apply together only matters from the second one on.
        if (parts.size() > 1) {
            chips.addView(SqlEditorViews.chip(context,
                    context.getString(group.getJunction().getLabelRes()),
                    () -> {
                        group.setJunction(group.getJunction() == SqlJunction.AND
                                ? SqlJunction.OR : SqlJunction.AND);
                        onChanged.run();
                    }, null));
        }
    }

    /** Changes one part of a group: a comparison in its dialog, a group in the group dialog. */
    private static void edit(final Context context, final SqlGrid grid, final SqlStatement statement,
                             final SqlConditionGroup group, final int index,
                             final boolean allowAggregate, final Runnable onChanged) {
        final SqlCondition part = group.getParts().get(index);
        if (part instanceof SqlConditionGroup nested) {
            showGroup(context, grid, statement, nested, allowAggregate, onChanged);
            return;
        }
        showComparison(context, grid, statement, allowAggregate, (SqlComparison) part, condition -> {
            group.getParts().set(index, condition);
            onChanged.run();
        });
    }

    /** Shows a group of its own with its parts. */
    static void showGroup(final Context context, final SqlGrid grid, final SqlStatement statement,
                          final SqlConditionGroup group, final boolean allowAggregate,
                          final Runnable onChanged) {
        final LinearLayout content = SqlEditorViews.column(context);
        final ChipGroup chips = SqlEditorViews.chipGroup(context);
        content.addView(chips);

        // The refresh has to know itself: every change draws these chips anew and reports on
        // to the caller, however often it happens.
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            fillGroup(context, grid, statement, group, chips, allowAggregate, refresh[0]);
            onChanged.run();
        };
        fillGroup(context, grid, statement, group, chips, allowAggregate, refresh[0]);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.grid_sql_condition_group)
                .setView(SqlEditorViews.padded(context, content))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> onChanged.run())
                .show();
    }
}
