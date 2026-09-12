package com.movingbits.grid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.TextViewCompat;

import java.util.List;
import java.util.function.Consumer;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The pieces the SQL editor and its smaller dialogs are built from.
 *
 * <p>Views are put together in code, as they are in the other dialogs of the library: a chip
 * per block, a title with a plus in front of every clause, a list to choose from.</p>
 */
final class SqlEditorViews {

    private SqlEditorViews() {
            // utility class
    }

    static int padding(final Context context) {
        return GridStyle.dp(context, 8f);
    }

    /** A block: a short tap changes it, the cross takes it out. */
    static Chip chip(final Context context, final String text, final Runnable onEdit, final Runnable onRemove) {
        final Chip chip = new Chip(context);
        chip.setText(text);
        chip.setEnsureMinTouchTargetSize(false);
        if (onEdit != null) {
            chip.setOnClickListener(view -> onEdit.run());
        }
        if (onRemove != null) {
            chip.setCloseIconVisible(true);
            chip.setContentDescription(context.getString(R.string.grid_sql_remove));
            chip.setOnCloseIconClickListener(view -> onRemove.run());
        }
        return chip;
    }

    /** The row the chips of one clause stand in; it wraps onto the next line. */
    static ChipGroup chipGroup(final Context context) {
        final ChipGroup group = new ChipGroup(context);
        group.setChipSpacingVertical(GridStyle.dp(context, 2f));
        group.setSingleLine(false);
        return group;
    }

    static LinearLayout column(final Context context) {
        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    static LinearLayout row(final Context context) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return row;
    }

    /** The title of a clause. */
    static TextView title(final Context context, final int titleRes) {
        final TextView title = new TextView(context);
        TextViewCompat.setTextAppearance(title,
                com.google.android.material.R.style.TextAppearance_MaterialComponents_Subtitle2);
        title.setText(titleRes);
        return title;
    }

    /** Small text, for the preview and the finding under it. */
    static TextView note(final Context context) {
        final TextView note = new TextView(context);
        TextViewCompat.setTextAppearance(note,
                com.google.android.material.R.style.TextAppearance_MaterialComponents_Caption);
        return note;
    }

    /** A plus that adds another block to a clause. */
    static Chip addChip(final Context context, final Runnable onClick) {
        final Chip chip = new Chip(context);
        chip.setText("+");
        chip.setContentDescription(context.getString(R.string.grid_sql_add));
        chip.setEnsureMinTouchTargetSize(false);
        chip.setOnClickListener(view -> onClick.run());
        return chip;
    }

    /** A small button with nothing but an icon, as the title row of the editor carries. */
    static View iconButton(final Context context, final int iconRes, final int descriptionRes,
                           final Runnable onClick) {
        final AppCompatImageButton button = new AppCompatImageButton(context);
        button.setImageResource(iconRes);
        button.setContentDescription(context.getString(descriptionRes));
        ImageViewCompat.setImageTintList(button,
                ColorStateList.valueOf(GridStyle.themeOnSurface(button)));
        button.setBackgroundColor(Color.TRANSPARENT);
        GridStyle.applyTouchFeedback(button);
        button.setOnClickListener(view -> onClick.run());
        return button;
    }

    /** Lets one entry of a list be picked. */
    static void choose(final Context context, final int titleRes, final List<String> labels,
                       final Consumer<Integer> onChosen) {
        if (labels.isEmpty()) {
            return;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(titleRes)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> onChosen.accept(which))
                .show();
    }

    /** A labelled selection, as the smaller dialogs use it. */
    static Spinner spinner(final Context context, final List<String> labels, final int selected,
                           final Consumer<Integer> onSelected) {
        final Spinner spinner = new AppCompatSpinner(context);
        // A view of our own instead of simple_spinner_item, which indents its text: here the
        // selection is to stand flush with everything below it.
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                R.layout.grid_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setPadding(0, spinner.getPaddingTop(), spinner.getPaddingRight(),
                spinner.getPaddingBottom());
        if (selected >= 0 && selected < labels.size()) {
            spinner.setSelection(selected);
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
                onSelected.accept(position);
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {
                    // the selection stays as it was
            }
        });
        return spinner;
    }

    /** An input field, with the keyboard the type of value calls for. */
    static EditText input(final Context context, final String value, final ColumnType type) {
        final EditText input = new AppCompatEditText(context);
        TextViewCompat.setTextAppearance(input,
                com.google.android.material.R.style.TextAppearance_MaterialComponents_Body1);
        input.setSingleLine(true);
        input.setHint(R.string.grid_sql_value_hint);
        input.setInputType(inputTypeOf(type));
        input.setText(value);
        input.setSelection(input.getText().length());
        return input;
    }

    private static int inputTypeOf(final ColumnType type) {
        return switch (type) {
            case INTEGER -> InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED;
            case FLOAT -> InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL;
            default -> InputType.TYPE_CLASS_TEXT;
        };
    }

    /** Puts a view into the padding a dialog needs. */
    static View padded(final Context context, final View view) {
        final LinearLayout frame = column(context);
        final int padding = GridStyle.dp(context, 16f);
        frame.setPadding(padding, padding / 2, padding, 0);
        frame.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return frame;
    }
}
