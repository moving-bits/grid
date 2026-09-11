# Grid

Small Android library for tabular views with a fixed header row, fixed columns, optional
paging and tap hooks. Plain Java on Android views and Material Components (Material Design 2),
without Kotlin and without Compose.

| Module  | Content                                                                   |
|---------|---------------------------------------------------------------------------|
| `:grid` | The library (`com.movingbits.grid`)                                       |
| `:demo` | Sample application: two demos from memory, one from a database            |

Android API 24 at minimum, built with AGP 8.13 / Gradle 8.14 / JDK 17.

`Grid` holds no data. It keeps the configuration object – columns, order, visibility, boundary
share, widths, sort order – and fetches the contents to display page by page through a
`GridDataSource`. For the two common cases the library brings the source along: `MemGrid` for
data that sits in memory anyway, and `DatabaseGrid` for the tables of an SQLite database.

## Usage

```java
MemGrid<City> grid = new MemGrid<>(data)
        .column("City",       CellAlignment.START, ColumnWidth.dp(130f), City::getCity)
        .column("Country",    CellAlignment.START, ColumnWidth.dp(110f), City::getCountry)
        .column("Population", CellAlignment.END,   ColumnWidth.dp(110f), City::getPopulationAsString)
        .column("Area",       City::getAreaAsString)          // default: left-aligned, use remaining width
        .fixedColumns(2)
        .rowsPerPage(10)
        .onItemClick((cell, city) -> { /* ... */ })
        .onHeaderLongClick(col -> { /* ... */ });

GridView view = new GridView(context);
view.setGrid(grid);
```

Every configuration method returns the grid, so they can be chained into a single call. Changes
made after the grid has been handed to the view take effect on `GridView.refresh()`.

### Without `MemGrid`

When the data does not sit in memory – in a database, say, or behind an interface – the
application drives `Grid` directly:

```java
Grid grid = new Grid(new GridDataSource() {
            @Override
            public int getRowCount(String search) {
                return countRecords(SearchRequest.parse(search));
            }

            @Override
            public String[][] getPage(int page, int count, String sort, String search) {
                return load(page, count,
                        SortRequest.parse(sort), SearchRequest.parse(search));
            }
        })
        .column(new GridColumn("City").name("city").widthDp(130f))
        .column(new GridColumn("Population").name("population").type(ColumnType.INTEGER).widthDp(110f))
        .fixedColumns(1)
        .rowsPerPage(10)
        .onCellClick((cell, row) -> { /* row[cell.getDataIndex()] */ });
```

`getPage` returns a 0-based two-dimensional array: the first dimension is the row, the second
holds the fields **in data order**. Missing rows and fields count as empty, surplus ones are
ignored.

The data order is settled as soon as the grid is fully configured: it follows from the
configuration object handed in at the start (`columns_fixed`, then `order`), or, without one,
from the order of the `column(...)` calls. Reordering and hiding columns afterwards only
changes the display – the data rows stay as they are and are therefore not fetched again for
it. `Grid.getDataColumns()` names the expected order, `GridColumn.getDataIndex()` the position
of one individual column.

Data is only asked for when needed: when the grid is set, when paging, after a changed sort
order or search, and on `GridView.refresh()` – not while scrolling or measuring. On the last
page `count` is correspondingly smaller; without paging everything is fetched in one go. What
gets counted and delivered is in each case what the search leaves over.

### Databases: `DatabaseGrid`

For the tables of an SQLite database the data source comes ready-made. All that is handed over
is the database; which tables there are, which columns they carry and of what type, is
something `DatabaseGrid` gathers itself:

```java
DatabaseGrid grid = new DatabaseGrid()
        .setDatabase(database)
        .onTablesLoaded(tables -> showSelection(tables))   // the selection is the app's job
        .rowsPerPage(10)
        .onCellLongClick((cell, row) -> edit(cell, row));

// after the application's choice:
grid.setCurrentTable("cities").configuration(storedObject);
view.refresh();
```

As long as no table is chosen the display stays empty. `setCurrentTable(String)` drops the
previous columns (`Grid.clearColumns()`) and builds them anew from the table's own
declarations; a configuration object belongs immediately afterwards, because it refers to the
new columns. Every table therefore needs a stored object of its own. The selection itself is
provided by the application; for a fitting control the Material symbol "table" is available in
the library: `com.movingbits.grid.R.drawable.grid_ic_table`.

| Declaration in the table | Becomes                                                    |
|--------------------------|------------------------------------------------------------|
| `INTEGER`, `INT`, …       | `ColumnType.INTEGER`, trailing aligned                    |
| `REAL`, `FLOAT`, …        | `ColumnType.FLOAT`, trailing aligned                      |
| `TEXT`, `VARCHAR`, …      | `ColumnType.STRING`                                       |
| `BLOB`, unknown           | `ColumnType.UNKNOWN`, write-protected; the cell shows `[BLOB]` |
| Primary key               | write-protected, comes first and forms the fixed area     |

Sort order and search go into the query as `ORDER BY` and `WHERE`; the search behaves as it
does in memory, so text columns ignore case and numeric columns compare as numbers. As long as
the application only pages, one open cursor serves every page – only another sort order or
search issues a new query.

`persistData(row, column, value)` writes the content of a cell back. That requires a primary
key – without one the row could not be addressed unambiguously – and a column without write
protection; otherwise the call returns `false`. The library brings no input mask along, that is
the application's job (see the demo).

**Deleting a row** is offered by long-tap on the number column, and can only be triggered if
the table has a primary key (so that the row can be addressed) and the application has
hooked into it:

```java
grid.onRowAction((type, key, columns) -> {
            // type is OnRowActionListener.DELETE; key carries one entry per field of the
            // primary key, columns the rest of the row as it is on display, ready to be shown
            askTheUser(columns, () -> grid.performRowAction(type, key));   // whenever it comes
        })
        .onRowActionPerformed((type, key, success) -> log(type, key, success));
```

The long press just reports to the calling application. Along with the key it hands over the
rest of the row as it is on display – in display order and without the hidden columns – so that
a question put to the user can name the record instead of its number. That can perform any
check it wants and needs to call `performRowAction(type, key)` to trigger deletion:

`performRowAction(type, key)` checks its parameters first – a known action, and a key that
addresses a row of the current table with one value per field of its primary key – then deletes
the row and finally reports through `onRowActionPerformed`. That hook hears of every call, and
`success` tells whether the row is really gone; what the application turned down beforehand
never reaches the grid. After a deletion the display is fetched anew by itself.

### Configuration

| Method                          | Effect                                                          |
|---------------------------------|-----------------------------------------------------------------|
| `column(...)`                   | add a column: title, type, alignment, width                     |
| `data(GridDataSource)`          | source of the contents to display (`rows(List)` for `MemGrid`)  |
| `fixedColumns(int)`             | fix the first n columns (default 0)                             |
| `rowsPerPage(int)`              | switch paging on (0 = off)                                      |
| `totalWidth(float dp)`          | state the total width (without one: the available width)        |
| `colors(GridColors)`            | paint with colors of your own instead of the theme's            |
| `configuration(String)`         | take over a configuration object as JSON                        |
| `onConfigurationChanged`        | hook carrying the new configuration object after every change   |
| `addState` / `readState`        | store and read the state on a change of screen orientation      |
| `ignoreColumns(String...)`      | exclude columns entirely                                        |
| `clearColumns()`                | drop every column (a different data set, e.g. another table)    |
| `sortable(boolean)`             | sorting by tapping the header row (default on)                  |
| `onSortChanged`                 | hook for a changed sort order                                   |
| `fixedBoundaryFraction(float)`  | boundary share of the fixed area (default 0.4)                  |
| `adjustableFixedBoundary(bool)` | let the boundary be moved by a long tap (default off)           |
| `alternatingRowColors(bool)`    | tint every other data row slightly (default off)                |
| `onFixedBoundaryChanged`        | hook for a boundary moved by the user                           |
| `search(List&lt;SearchRequest&gt;)`   | set search conditions; transient, not in the configuration object |
| `onSearchChanged`               | hook for a changed search                                       |
| `onRowAction` / `onRowActionPerformed` | delete a row by a long tap on its number (`DatabaseGrid`)  |
| `onCellClick` / `onCellLongClick`     | hook for a short / long tap on a data cell                 |
| `onItemClick` / `onItemLongClick`     | the same with the record instead of the row of texts (`MemGrid`) |
| `onHeaderClick` / `onHeaderLongClick` | hook for a short / long tap on a header cell               |

Column widths: `ColumnWidth.dp(x)`, `ColumnWidth.percent(0..1)` or – as the default –
`ColumnWidth.remaining()`. Alignment: `CellAlignment.START` (default), `CENTER`, `END`.

## Behaviour

* **The header row** is fixed to the top edge and scrolls horizontally along with the data.
* **The number column** on the far left shows the running number within the whole data set
  (1-based, so on page 2 the numbers 11–20). It is always fixed and only as wide as the highest
  number that occurs requires. A short tap does nothing; a long press deletes the row where a
  `DatabaseGrid` allows it (see [Databases](#databases-databasegrid)).
* **Column widths**: fixed and percentage values are handed out first, the remaining width is
  split evenly across the columns without a value. Percentages refer to the total width
  including the number column. If the sum exceeds the available width, the grid scrolls
  horizontally. Two lower bounds: an explicitly stated width never falls below
  `ColumnWidth.MIN_WIDTH_DP` (20 dp), and columns without a value are never split below 48 dp.
  So whoever explicitly wants a narrow column gets one; whoever states nothing gets a readable
  width.
* **Column lines**: at the right edge of every column stands a fine vertical line across the
  full row height – in the header row as in every data row, semi-transparent on
  `colorOnSurface`. It separates the contents of neighbouring columns and at the same time
  shows where the width can be grabbed. It is drawn in the row rather than in the cells, so
  that it runs on without being interrupted by the padding.
* **Moving a column width**: a long tap in the **header row** on a column's right edge grabs
  that edge; a line in the accent color follows the finger, and on release the width is taken
  over and reported through `onConfigurationChanged`. Below 20 dp it does not go – the line
  stops there. The column has a fixed width afterwards, even if it was `remaining()` or
  `percent(...)` before.

  In the header row the column edge takes precedence over the boundary of the fixed area; that
  one can be grabbed in the data rows. A long tap in the middle of a header cell still reaches
  `onHeaderLongClick`, only the 32 dp wide zone around an edge does not.
* **Spacing** is kept tight, because in a table width is the scarce resource: 4 dp of padding
  per cell side, so 8 dp between two columns and 4 dp to the grid's left and right edge.
  Whoever wants more air gives it through wider columns.
* **Fixed columns** occupy at most 60 % of the total width. Beyond that the block is capped at
  that share and scrolls horizontally on its own – with a position independent of the free
  columns.
* **Scroll hints**: in the header row a small arrow per area – fixed block as well as free area
  – shows that there are still columns outside the window to the left or right. The arrows sit
  as an overlay at the edge of their area, so they do not scroll along, and they replace the
  title at that spot rather than overlaying it.
* **The boundary line**: when content is hidden at the boundary – at the end of the fixed
  columns or at the start of the free ones, that is, exactly when the header row shows one of
  the two arrows there – a vertical line in the theme's accent color appears at the
  transition, running across the header row and all data rows. When both areas end flush at the
  boundary it disappears. Between the two areas lies a shaded gap of 8 dp with the line in its
  middle; the gap is at the same time part of the line's 48 dp wide grab area.
* **Moving the boundary** (only after `adjustableFixedBoundary(true)`): a long tap on the line
  followed by dragging moves the boundary share between 15 % and 60 %. The fixed block never
  gets wider than its content – drag further to the right and the line stops at its edge. On
  release `onFixedBoundaryChanged` reports the new share and `onConfigurationChanged` the
  configuration object with the field `width_fixed`; through
  `GridView.setFixedBoundaryFraction(float)` it can also be set from code (without the hook
  firing). Short taps and swipes in the area of the line still reach the cell underneath.
* **Without paging** rows are as tall as their content and scroll vertically.
* **With paging** the header row and the data rows of one page fill the available height
  exactly: row height = (available height − header row height) / rows per page, with the
  rounding remainder handed to the first rows. Text wraps up to the row height and then ends in
  "…". Vertical scrolling is switched off.
* **Alignment**: horizontally configurable per column, vertically always to the top.
* **Write-protected columns** are recognisable by their slightly tinted header cell
  (`colorOnSurface` over `colorSurface`, as with the alternating row colors, only stronger).
  The hint sits in the header row, not on every data cell: it applies to the column. What counts
  as write-protected is stated by `GridColumn.readOnly(boolean)` or the field `r` in the
  configuration object; without a value it is `ColumnType.UNKNOWN` alone.
* **Alternating row colors** (only after `alternatingRowColors(true)`): every other data row
  gets a hint of `colorOnSurface` laid over `colorSurface` — in a light theme that is a light
  grey, in a dark one correspondingly a little lighter instead of darker. What counts is the
  running number within the whole data set, not the position on the page; the header row stays
  untouched. The setting is purely visual and is not part of the configuration object.
* **The appearance** follows the standard attributes of the application's Material 2 theme
  (`colorSurface`, `colorOnSurface`, `TextAppearance.MaterialComponents.Subtitle2` and `Body2`
  respectively), unless a palette is handed over through `colors(GridColors)`. Touch feedback
  only appears where a hook is configured as well.

### Colors

By default the grid follows the Material 2 theme of the embedding application, and that needs
no setting up. Whoever wants other colors hands a `GridColors` to `Grid.colors(...)`; the
palette can be built once and shared by several grids.

```java
new Grid(dataSource)
        .colors(new GridColors()
                .surface(0xFF12202B)      // background
                .onSurface(0xFFE8F0F5)    // text and everything drawn on top of it
                .accent(0xFFFFB300))      // boundary line, dragged column edge
```

Two of the colors carry the rest: `surface` is the background, `onSurface` the base for the
text and for everything drawn over the background. Setting those two is enough for a coherent
look — the divider lines, column lines, scroll hints, the shading at the boundary and the tints
of alternating rows and write-protected headers are all derived from them, with the same
relative weights the theme path uses.

| Method | Colors |
|--------|--------|
| `surface(int)` | background of the header row, the data rows and the view behind them |
| `onSurface(int)` | the text, and the base for every color below that is not set on its own |
| `accent(int)` | the boundary line, the line while a column edge is dragged, the separator in the column dialog |
| `alternateRow(int)` | background of every other data row (only after `alternatingRowColors(true)`) |
| `readOnlyHeader(int)` | background of the header cell of a write-protected column |
| `divider(int)` | the horizontal line below every row |
| `columnDivider(int)` | the vertical line at the right edge of every column |
| `boundaryShadow(int)` | the shading inside the gap at the boundary of the fixed area |
| `scrollHint(int)` | the arrows pointing at columns outside the window |

Every color left unset comes from the theme, and an explicit value always wins over a derived
one. Colors are ARGB values, so a fully transparent color is a valid value — "not set" is
kept apart from "set to zero".

`onSurface` is the one color that also reaches the text. Without it the cells keep the color
of their text appearance, which is what a theme intends; with it, a dark background stays
readable. The colors are read while the view is built, so a different palette handed over
afterwards takes effect on `GridView.refresh()`.

### Translation

Texts are defined in grid's `values\strings.xml` file. To add translations to your app, copy the
contents of that file to your app's `string.xml` and let them be translated the same way as your
app's other strings. Contents of your app's `string.xml` will override grid's original versions.

### The configuration object

Display and sort order can be handed over and read back as a JSON string — that is how the
application persists the state and restores it on the next start:

```java
MemGrid<City> grid = new MemGrid<>(data)
        .column(new MemColumn<City>("City", City::getCity).name("city"))
        .column(new MemColumn<City>("Population", City::getPopulationAsString).name("population"))
        .ignoreColumns("internalId")
        .configuration(configString)
        .onConfigurationChanged(json -> updateConfig(json));
```

```json
{
  "columns": { "population": { "t": "i", "a": "r", "r": "n", "w": 110 } },
  "order": ["city", "*"],
  "columns_fixed": ["city", "country"],
  "columns_hidden": ["area"],
  "width_fixed": 40,
  "sort": [{ "country": "a" }, { "population": "d" }]
}
```

| Field | Meaning |
|-------|---------|
| `columns` | properties per column: `t` type (`s` string, `i` integer, `f` float, `u` unknown), `a` alignment (`l`, `r`, `c`), `r` write protection (`y`, `n`), `w` fixed width in dp |
| `order` | order of the freely scrolling area; only the columns listed are part of it. `*` stands for every column not yet listed and inserts them at its own position |
| `columns_fixed` | columns of the fixed area, in this order and first — independent of `order`. If the field is missing, whatever `fixedColumns(int)` set in code stays; an empty field removes the fixing |
| `columns_hidden` | columns that are present but not visible |
| `width_fixed` | width of the fixed area as a percentage of the total width (15–60); deliberately not an absolute value, which would no longer suit after a change of device or orientation |
| `sort` | sort criteria in the order they take effect, each entry a column name and `a`/`d` |

`columns_fixed` and `order` carry **all** columns — the hidden ones included. Hiding therefore
only changes visibility, not position and area: a column brought back stands where it stood
before. A column that appears neither in `columns_fixed` nor in `order`, and that `*` does not
cover either, does not belong to the grid at all; named under `columns_hidden` alone, it joins
the free area at the back.

`w` only appears for columns with a fixed width: a percentage share and the split remaining
width should keep adapting to the device and the orientation instead of freezing on the value
just computed the first time the state is stored. When reading, the minimum width of 20 dp
takes precedence over the value; if `w` is missing, the width set in code stays.

Columns are addressed through `GridColumn.name(...)`; without a name of its own the title
serves as the name. The **type** determines what applies without an explicit value: alignment
(numbers trailing aligned), sorting (`i`/`f` numerically out of the displayed text, `s`/`u` as
text) and write protection (only `u` is protected by default). The type is evaluated for
sorting by whoever supplies the data – for `MemGrid` that happens there, where a comparator
that has been set takes precedence. The **write protection** reaches the application through
`CellRef.isReadOnly()` in the hooks for data cells; the library itself offers no editing.

`ignoreColumns(...)` excludes columns entirely: they do not appear, they cannot be brought back
through `order`, `*` or `columns_fixed`, and they stand in no field of the object that is
written out — not even under `columns_hidden`. The same holds for columns missing from an
`order` that is given without `*`.

**Reading** is forgiving: missing values keep their default, unknown fields, unknown column
names and invalid values are skipped, and a string that cannot be read has no effect.
**Writing**, by contrast, is complete, so that the stored state does not later depend on
defaults in the code.

### Keeping state on configuration changes (eg. screen rotation)

A change of screen orientation builds the activity anew. To prevent a full restart with
falling back to default position, the grid and its view preserve their current state in
a JSON string, that the application can put into the bundle and read it back from it:

```java
@Override
protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    grid.addState(outState);        // configuration, search, data order
    gridView.addState(outState);    // page, scroll positions
}

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Grid grid = buildGrid();        // columns and hooks as always
    grid.readState(savedInstanceState);
    gridView.setGrid(grid);
    gridView.readState(savedInstanceState);
}
```

The order matters: the grid's state belongs **before** `setGrid(...)` – it settles the columns
and the data order – the view's state directly **after** it, because it needs the grid. Both
calls cope with a bundle that carries no state, `null` included, so the same code serves a
fresh start.

| Where | What is stored |
|-------|----------------|
| `Grid.addState(Bundle)` | the whole configuration object, plus the search and the data order, which the display order does not tell |
| `GridView.addState(Bundle)` | the displayed page, the scroll position of both areas and, without paging, the row the list is scrolled to |
| `DatabaseGrid` | in addition the current table: its columns are what everything else refers to. The database has to be set beforehand |
| `MemGrid` | nothing of its own – the records come from the application anyway |

What comes out of the application's own code – data source, columns, colors, hooks, rows per
page – is not part of the state: that is built anew anyway. Widths and the boundary share are
kept as percentages of the total width and therefore suit the new orientation.
`Grid.toStateJson()` and `Grid.state(String)` are the same thing without a bundle, for whoever
wants to store the state elsewhere and outlive the death of the process as well. (Also used for
testing.)

Opened dialogues are not preserved, the column selection and the search close with the turn.

### Sorting

A short tap on a header cell sorts by that column: ascending first, descending on the second
tap, and on the third the criterion is removed again. A further column that is tapped is
appended as a subordinate criterion, and the criteria already set keep their order and
direction – that is how sorting across any number of columns works. The header row shows the
direction and, as soon as several criteria are in effect, their rank (`Country ▲1`,
`Population ▼2`).

The whole data set is always sorted, not just the page on display; afterwards the display
starts at page 1 again. `:grid` itself only keeps the order as state and passes it on with
every page fetch – the sorting is done by the data source. `MemGrid` does it itself; the list
handed over stays unchanged, `getUnsortedRows()` keeps the original order accessible and
`getItems()` the displayed one.

Without anything further, `MemGrid` compares the displayed text in a language-aware way
(`Collator`, so accented letters included), and as a number for the types `i` and `f`. For
columns whose textual form is not in the meaningful order, a comparator of their own belongs on
the column:

```java
.column(new MemColumn<City>("Population", City::getPopulationAsString)
        .align(CellAlignment.END)
        .comparator((a, b) -> Integer.compare(a.getPopulation(), b.getPopulation())))
```

Individual columns can be excluded from sorting through `GridColumn.sortable(false)`, the whole
grid through `Grid.sortable(false)`. From code, `GridView.toggleSort(int)` and
`GridView.clearSort()` are available.

### Searching

`GridView.showSearch()` opens the search dialog; the control is provided by the application. A
fitting symbol is available in the library:
`com.movingbits.grid.R.drawable.grid_ic_search`.

```java
findViewById(R.id.search).setOnClickListener(v -> gridView.showSearch());
```

The dialog lists the displayed columns one below the other, per row the column title, an
operator and a value field. Above them stands the entry **Global**: it searches across every
column and is met as soon as one of them matches. Which operators are on offer depends on the
column's type:

| Type | Operators |
|------|-----------|
| `s`, `u` and the entry "Global" | `=`, `!=`, begins with, begins not with, ends with, ends not with, contains, does not contain |
| `i`, `f` | `=`, `!=`, `<`, `<=`, `>`, `>=` |

An empty value field means: this column is not taken into the search – that is also how a
condition is removed again. Several filled-in rows apply together: only what meets all of them
is displayed. Nothing is taken over before **OK**, **Reset** removes the search entirely in one
go, and **Cancel** leaves it as it was. After it has been taken over the display starts at page
1 again, and both page count and number column refer to the narrowed-down data set.

The search is **transient**: it is not part of the configuration object, fires no
`onConfigurationChanged` and is not persisted. It goes to the data source with every page
fetch, and the data source applies it – `:grid` itself does no filtering. From code,
`GridView.setSearch(List<SearchRequest>)`, `Grid.getSearch()` and `Grid.isSearching()` are
available; `Grid.onSearchChanged(...)` reports every change – the way for an application to
mark its control as "search active".

`MemGrid` searches on the displayed text: text columns ignoring case, the types `i` and `f` as
a number, so out of a form like "1,234.50 EUR" as well. If the value entered cannot be read as
a number there, the condition finds nothing. The entry "Global" checks every column of the data
set, including those currently hidden.

### Hiding, showing and reordering columns

`GridView.showColumnSettings()` opens a dialog for it. The control is provided by the
application – that leaves it free in the design of its own interface:

```java
findViewById(R.id.configColumns).setOnClickListener(v -> gridView.showColumnSettings());
```

For a fitting symbol the Material symbol "reorder" is available in the library:
`com.movingbits.grid.R.drawable.grid_ic_reorder`.

The dialog lists all columns except those excluded through `ignoreColumns(...)` – the currently
hidden ones included, at their place within their respective area and recognisable by the
missing tick. The checkbox on the left controls visibility, the handle on the right the moving.
A line separates the fixed from the freely scrolling columns; moving only happens within a
column's own area. Nothing is taken over before **OK**, so that several changes can be
collected; **Cancel** discards them. After it has been taken over the view is up to date and
`onConfigurationChanged` has been reported. A sort order in effect follows the columns;
criteria of hidden columns fall away. Whether anything is hidden at all is answered by
`Grid.hasHiddenColumns()`.

From code the moving is also available through `Grid.moveColumn(from, to)`.

### Paging

The controls for paging deliberately belong to the application, so that the header row and the
data rows can fill the height they were promised in full. The view provides for that:

```java
view.getPageCount();
view.getCurrentPage();
view.setCurrentPage(int);
view.nextPage();
view.previousPage();
view.setOnPageChangedListener((page, numPages) -> { /* update control bar */ });
```

## Building and testing

```
gradlew :demo:assembleDebug      # build the demo
gradlew :demo:installDebug       # install the demo on a device or emulator
gradlew :grid:testDebugUnitTest  # unit tests of the library
```

The layout arithmetic (width distribution, row heights, page boundaries) lives free of Android
in `GridLayoutMath` and `GridMetrics` and is covered by JVM unit tests; the same holds for the
configuration object, page fetching, sorting and searching.

## Not included

Selecting and editing are not part of the library; the tap hooks are the intended place to
start from. `DatabaseGrid` too brings only the way to write (`persistData(...)`), no input
mask. There are no custom cell views: a row consists of texts. The current page is not
persisted across a configuration change – it lies in the hands of the application, which
provides the paging bar as well.
