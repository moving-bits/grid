package com.movingbits.grid.demo;

import com.movingbits.grid.CellRef;
import com.movingbits.grid.ColumnType;
import com.movingbits.grid.DatabaseGrid;
import com.movingbits.grid.Grid;
import com.movingbits.grid.GridColumn;
import com.movingbits.grid.GridView;
import com.movingbits.grid.MemColumn;
import com.movingbits.grid.MemGrid;
import com.movingbits.grid.SortCriterion;
import com.movingbits.grid.SortDirection;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;


/**
 * Demo app with two fixed columns, 10 rows per page
 */
public class MainActivity extends AppCompatActivity {

    private static final int ROWS_PER_PAGE = 10;

    private static final String SETTINGS = "grid-demo";

    private static final String CONFIG_STRING_CITIES = "config_cities";
    private static final String CONFIG_STRING_MOUNTAINS = "config_mountains";
    private static final String CONFIG_STRING_DATABASE = "config_database";

    /** Key of the running demo within the stored state. */
    private static final String STATE_DEMO = "demo";

    /**
     * Default configs
     */
    private static final String DEMO_CONFIG_CITIES = """
            {
                "columns":{
                    "city":{"t":"s"},
                    "population":{"t":"i", "a":"r"},
                    "area":{"t":"i", "a":"r"}
                },
                "columns_fixed":["city"],
                "order":["*"]
            }
    """;
    private static final String DEMO_CONFIG_MOUNTAINS = "{}";
    private static final String DEMO_CONFIG_DATABASE = "{}";

    private GridView gridView;
    /** Set while the database demo is running; {@code null} otherwise. */
    private DatabaseGrid databaseGrid;
    private TextView tvPageNumOfNum;
    private MaterialButton btColumConfig;
    private MaterialButton btSearch;
    private ColorStateList buttoncolorDefault;
    private int accentColor;
    private Toast hint;

    /** The demos on offer; the stored state names the one that is running. */
    private enum Demo { CITIES, MOUNTAINS, DATABASE }

    /** The demo currently on screen; {@code null} while the selection is showing. */
    private Demo runningDemo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyWindowInsets();

        findViewById(R.id.demo_cities).setOnClickListener(view -> runDemo(Demo.CITIES, null));
        findViewById(R.id.demo_mountains).setOnClickListener(view -> runDemo(Demo.MOUNTAINS, null));
        findViewById(R.id.demo_database).setOnClickListener(view -> runDemo(Demo.DATABASE, null));

        // After a change of screen orientation the demo carries on where it left off.
        final Demo running = getDemoFrom(savedInstanceState);
        if (running != null) {
            runDemo(running, savedInstanceState);
        }
    }

    /**
     * Stores which demo is running and lets the grid and its view add what they hold: the grid
     * its configuration, the search and the data order, the view the page and the scroll
     * positions.
     */
    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (runningDemo == null) {
            return;
        }
        outState.putString(STATE_DEMO, runningDemo.name());
        grid().addState(outState);
        gridView.addState(outState);
    }

    /**
     * Releases the cursor of the database demo. Its grid is done for with this activity – and
     * a change of screen orientation leaves a new one behind every time.
     */
    @Override
    protected void onDestroy() {
        if (databaseGrid != null) {
            databaseGrid.close();
        }
        super.onDestroy();
    }

    /** The demo a stored state belongs to; {@code null} when there is none. */
    private Demo getDemoFrom(final @Nullable Bundle state) {
        final String name = state == null ? null : state.getString(STATE_DEMO);
        for (Demo demo : Demo.values()) {
            if (demo.name().equals(name)) {
                return demo;
            }
        }
        return null;
    }

    /**
     * Builds a demo and shows it.
     *
     * <p>A state at hand goes to the grid before it is handed to the view: it settles the
     * columns, their order and the data order. The state of the view itself follows right
     * after, because it needs the grid.</p>
     *
     * @param state the stored state, or {@code null} for a fresh start
     */
    private void runDemo(final Demo demo, final Bundle state) {
        runningDemo = demo;
        final Grid grid = switch (demo) {
            case CITIES -> initMemDemoCities();
            case MOUNTAINS -> initMemDemoMountains();
            case DATABASE -> initDatabaseDemo();
        };
        databaseGrid = grid instanceof DatabaseGrid database ? database : null;
        grid.readState(state);

        gridView = new GridView(this);
        gridView.setGrid(grid);
        ((FrameLayout) findViewById(R.id.grid_container)).addView(gridView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        gridView.readState(state);
        configureUI();
    }

    private Grid initMemDemoCities() {
        return new MemGrid<>(DemoData.CITIES)
                .column(new MemColumn<>(getString(R.string.col_city), City::city).name("city").widthDp(130f))
                .column(new MemColumn<>(getString(R.string.col_country), City::country).name("country").widthDp(130f))
                .column(new MemColumn<>(getString(R.string.col_population), City::getPopulationAsString).name("population").widthDp(130f))
                .column(new MemColumn<>(getString(R.string.col_area), City::getAreaAsString).name("area").widthDp(130f))
                .configuration(loadConfiguration(CONFIG_STRING_CITIES, DEMO_CONFIG_CITIES))
                .onConfigurationChanged(json -> {
                    storeConfiguration(CONFIG_STRING_CITIES, json);
                    highlightButton(btColumConfig, grid().hasHiddenColumns());
                })
                .onSearchChanged(searchConfig -> highlightButton(btSearch, !searchConfig.isEmpty()))
                .rowsPerPage(ROWS_PER_PAGE)
                .alternatingRowColors(true)
                .adjustableFixedBoundary(true)
                .onFixedBoundaryChanged(percent -> showHint(getString(R.string.hint_boundary, Math.round(percent * 100))))
                .onItemClick((cell, city) -> showHint(getString(R.string.hint_cell_short, cell.rowIndex() + 1, cell.columnIndex() + 1, city.city())))
                .onItemLongClick((cell, city) -> showHint(getString(R.string.hint_cell_long, city.city(), city.country())))
                .onSortChanged(order -> showHint(addSortIndicator(order)))
                .onHeaderLongClick(column -> showHint(getString(R.string.hint_title_long, column + 1)));
    }

    private Grid initMemDemoMountains() {
        return new MemGrid<>(DemoData.MOUNTAINS)
                .column(new MemColumn<>(getString(R.string.col_mountain), Mountain::getMountain).name("mountain").widthDp(130f))
                .column(new MemColumn<>(getString(R.string.col_height), Mountain::getHeightAsString).name("height").widthDp(60f))
                .column(new MemColumn<>(getString(R.string.col_lat), Mountain::getLat).name("lat").widthDp(100f))
                .column(new MemColumn<>(getString(R.string.col_lon), Mountain::getLon).name("lon").widthDp(100f))
                .column(new MemColumn<>(getString(R.string.col_first), Mountain::getFirstAsString).name("first").widthDp(60f))
                .column(new MemColumn<>(getString(R.string.col_country), Mountain::getCountry).name("country").widthDp(130f))
                .configuration(loadConfiguration(CONFIG_STRING_MOUNTAINS, DEMO_CONFIG_MOUNTAINS))
                .onConfigurationChanged(json -> {
                    storeConfiguration(CONFIG_STRING_MOUNTAINS, json);
                    highlightButton(btColumConfig, grid().hasHiddenColumns());
                })
                .onSearchChanged(searchConfig -> highlightButton(btSearch, !searchConfig.isEmpty()))
                .rowsPerPage(ROWS_PER_PAGE)
                .alternatingRowColors(true)
                .adjustableFixedBoundary(true)
                .onFixedBoundaryChanged(percent -> showHint(getString(R.string.hint_boundary, Math.round(percent * 100))))
                .onItemClick((cell, city) -> showHint(getString(R.string.hint_cell_short, cell.rowIndex() + 1, cell.columnIndex() + 1, city.getMountain())))
                .onItemLongClick((cell, city) -> showHint(getString(R.string.hint_cell_long, city.getMountain(), city.getCountry())))
                .onSortChanged(order -> showHint(addSortIndicator(order)))
                .onHeaderLongClick(column -> showHint(getString(R.string.hint_title_long, column + 1)));
    }

    /**
     * The database demo starts without columns: which ones there are is only known once a
     * table has been chosen. The grid reports the table names as soon as it has read them; the
     * selection is shown once the view is in place.
     */
    private Grid initDatabaseDemo() {
        databaseGrid = new DatabaseGrid()
                .setDatabase(DataStore.getDatabase(this))
                // Posted, so that a restored table is already in place by then: after a
                // change of screen orientation the selection would only be in the way.
                .onTablesLoaded(tables -> findViewById(R.id.root).post(() -> {
                    if (databaseGrid != null && !databaseGrid.hasCurrentTable()) {
                        showTableSelection(tables);
                    }
                }))
                .onConfigurationChanged(json -> {
                    storeConfiguration(databaseConfigKey(), json);
                    highlightButton(btColumConfig, grid().hasHiddenColumns());
                })
                .onSearchChanged(searchConfig -> highlightButton(btSearch, !searchConfig.isEmpty()))
                .rowsPerPage(ROWS_PER_PAGE)
                .alternatingRowColors(true)
                .adjustableFixedBoundary(true)
                .onFixedBoundaryChanged(percent -> showHint(getString(R.string.hint_boundary, Math.round(percent * 100))))
                .onCellLongClick(this::showCell)
                .onSortChanged(order -> showHint(addSortIndicator(order)))
                .onHeaderLongClick(column -> showHint(getString(R.string.hint_title_long, column + 1)));
        return databaseGrid;
    }

    // ------------------------------------------------------ table selection

    /** table selection for DatabaseGrid */
    private void showTableSelection(final List<String> tables) {
        if (tables.isEmpty()) {
            showHint(getString(R.string.no_tables));
            return;
        }
        final String[] names = tables.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_table)
                .setItems(names, (dialog, which) -> selectTable(names[which]))
                .show();
    }

    /** select table, initialize configuration with table-specific config key */
    private void selectTable(final String table) {
        if (databaseGrid == null || !databaseGrid.setCurrentTable(table)) {
            return;
        }
        databaseGrid.configuration(loadConfiguration(databaseConfigKey(), DEMO_CONFIG_DATABASE));
        // The new table is a different data set; the old one's page number does not suit it.
        gridView.setCurrentPage(0);
        gridView.refresh();
        highlightButton(btColumConfig, grid().hasHiddenColumns());
        highlightButton(btSearch, grid().isSearching());
        showHint(getString(R.string.hint_table_selected, table));
    }

    /** each table has an individual config key */
    private String databaseConfigKey() {
        return CONFIG_STRING_DATABASE + "_" + (databaseGrid == null ? "" : databaseGrid.getCurrentTable());
    }

    // ---------------------------------------------------------- cell dialogs

    /** long tap on cell opens edit/view mask */
    private void showCell(final CellRef cell, final String[] row) {
        final GridColumn column = grid().getColumn(cell.columnIndex());
        final String text = cell.dataIndex() < row.length && row[cell.dataIndex()] != null
                ? row[cell.dataIndex()]
                : "";
        if (cell.readOnly() || column.getType() == ColumnType.UNKNOWN) {
            showReadOnlyCell(column, text);
        } else {
            editCell(column, cell.rowIndex(), text);
        }
    }

    /** view for read/only cells */
    private void showReadOnlyCell(final GridColumn column, final String text) {
        final TextView view = new TextView(this);
        TextViewCompat.setTextAppearance(view, com.google.android.material.R.style.TextAppearance_MaterialComponents_Body1);
        view.setText(text);
        view.setTextIsSelectable(true);

        final ScrollView scroll = new ScrollView(this);
        scroll.addView(view);

        new MaterialAlertDialogBuilder(this)
                .setTitle(column.getTitle())
                .setView(addDialogPadding(scroll))
                .setPositiveButton(R.string.close, null)
                .show();
    }

    /** view for editing a cell */
    private void editCell(final GridColumn column, final int rowIndex, final String text) {
        final EditText input = new AppCompatEditText(this);
        TextViewCompat.setTextAppearance(input, com.google.android.material.R.style.TextAppearance_MaterialComponents_Body1);
        input.setInputType(inputTypeOf(column.getType()));
        if (column.getType() == ColumnType.STRING) {
            input.setSingleLine(false);
            input.setMinLines(3);
            input.setGravity(Gravity.TOP | Gravity.START);
        } else {
            input.setSingleLine(true);
        }
        input.setText(text);
        input.setSelection(input.getText().length());

        new MaterialAlertDialogBuilder(this)
                .setTitle(column.getTitle())
                .setView(addDialogPadding(input))
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> saveCell(column, rowIndex, input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int inputTypeOf(final ColumnType type) {
        return switch (type) {
            case INTEGER -> InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED;
            case FLOAT -> InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL;
            default -> InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        };
    }

    private void saveCell(final GridColumn column, final int rowIndex, final String value) {
        final boolean saved = databaseGrid != null
                && databaseGrid.persistData(rowIndex, column.getName(), value);
        showHint(getString(saved ? R.string.hint_cell_saved : R.string.hint_cell_not_saved, column.getTitle()));
        if (saved) {
            gridView.refresh();
        }
    }

    /** add padding to dialog */
    private View addDialogPadding(final View view) {
        final FrameLayout frame = new FrameLayout(this);
        final int padding = Math.round(getResources().getDisplayMetrics().density * 16f);
        frame.setPadding(padding, padding / 2, padding, 0);
        frame.addView(view);
        return frame;
    }

    /** edge2edge handling */
    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (view, windowInsets) -> {
            final Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private String loadConfiguration(final String configString, final String defaultConfig) {
        return getSharedPreferences(SETTINGS, MODE_PRIVATE).getString(configString, defaultConfig);
    }

    private void storeConfiguration(final String configString, final String json) {
        getSharedPreferences(SETTINGS, MODE_PRIVATE).edit().putString(configString, json).apply();
    }

    /** get current configuration */
    private Grid grid() {
        return gridView.getGrid();
    }

    /** Add sort indicator to column title */
    private String addSortIndicator(final List<SortCriterion> order) {
        if (order.isEmpty()) {
            return getString(R.string.hint_sort_reset);
        }
        final StringBuilder text = new StringBuilder();
        for (SortCriterion criterion : order) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(grid().getColumn(criterion.columnIndex()).getTitle())
                    .append(criterion.direction() == SortDirection.ASCENDING ? " ▲" : " ▼");
        }
        return getString(R.string.hint_sorted_by, text.toString());
    }

    /** show hint (and cancel current hint, if available, to avoid stacking) */
    private void showHint(final String text) {
        if (hint != null) {
            hint.cancel();
        }
        hint = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        hint.show();
    }

    /** configure control bar at the bottom */
    private void configureUI() {
        findViewById(R.id.control_bar).setVisibility(View.VISIBLE);

        tvPageNumOfNum = findViewById(R.id.pageNumOfNum);
        final View tvPrev = findViewById(R.id.prevPage);
        final View tvNext = findViewById(R.id.nextPage);

        tvPrev.setOnClickListener(v -> gridView.previousPage());
        tvNext.setOnClickListener(v -> gridView.nextPage());
        MaterialButton btSelectTable = findViewById(R.id.button_selectTable);
        btColumConfig = findViewById(R.id.button_configColumns);
        btSearch = findViewById(R.id.button_search);
        btColumConfig.setOnClickListener(v -> gridView.showColumnSettings());
        btSearch.setOnClickListener(v -> gridView.showSearch());

        // The table selection only exists where there are tables to choose from.
        btSelectTable.setVisibility(databaseGrid == null ? View.GONE : View.VISIBLE);
        btSelectTable.setOnClickListener(v -> showTableSelection(databaseGrid.getTables()));

        // The ordinary colour comes from the theme; highlighting uses the accent colour.
        buttoncolorDefault = btColumConfig.getIconTint();
        accentColor = MaterialColors.getColor(btColumConfig, com.google.android.material.R.attr.colorSecondary, buttoncolorDefault.getDefaultColor());
        highlightButton(btColumConfig, grid().hasHiddenColumns());
        highlightButton(btSearch, grid().isSearching());

        gridView.setOnPageChangedListener((page, numPages) -> {
            tvPageNumOfNum.setText(getString(R.string.pageNumOfNum, page + 1, numPages));
            tvPrev.setEnabled(page > 0);
            tvNext.setEnabled(page + 1 < numPages);
        });
    }

    /** highlights button */
    private void highlightButton(final MaterialButton button, final boolean highlight) {
        if (button != null) {
            button.setIconTint(highlight ? ColorStateList.valueOf(accentColor) : buttoncolorDefault);
        }
    }
}
