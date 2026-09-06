package com.movingbits.grid;

/**
 * Definition of a column: title, type, alignment and width.
 *
 * <p>Where the content comes from is none of the column's business – the grid fetches it page
 * by page through a {@link GridDataSource}. The column only remembers at which position of a
 * data row its field sits ({@link #getDataIndex()}).</p>
 *
 * <p>The configuration methods return {@code this} and can be chained.</p>
 */
public class GridColumn {

    private final String title;

    private String name;
    private ColumnType type = ColumnType.STRING;
    /** {@code null} means: use the type's default. */
    private CellAlignment alignment;
    /** {@code null} means: use the type's default. */
    private Boolean readOnly;
    private ColumnWidth width = ColumnWidth.remaining();
    private boolean sortable = true;
    /** Position of the field in a data row; -1 as long as the data order is still open. */
    private int dataIndex = -1;

    /**
     * @param title title shown in the header row
     */
    public GridColumn(final String title) {
        this.title = title == null ? "" : title;
    }

    /**
     * Sets the name under which the configuration object and the sort order address this
     * column. Without one the title serves as the name.
     */
    public GridColumn name(final String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the semantic type; the default is {@link ColumnType#STRING}. The type determines
     * alignment and write protection as far as those are not set explicitly. For sorting it is
     * evaluated by whoever supplies the data.
     */
    public GridColumn type(final ColumnType type) {
        this.type = type == null ? ColumnType.STRING : type;
        return this;
    }

    /**
     * Sets the horizontal alignment. Without a value – or with {@code null} – the type's
     * default applies: leading for text, trailing for numbers.
     */
    public GridColumn align(final CellAlignment alignment) {
        this.alignment = alignment;
        return this;
    }

    /**
     * Sets the write protection. Without a value the type's default applies; only
     * {@link ColumnType#UNKNOWN} is write-protected by default.
     *
     * <p>The library provides no editing; the value is passed on to the hooks for data cells
     * so that the application can act on it there. It is visible nevertheless: the header cell
     * of a write-protected column is slightly tinted.</p>
     */
    public GridColumn readOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    /** Sets the column width; the default is {@link ColumnWidth#remaining()}. */
    public GridColumn width(final ColumnWidth width) {
        this.width = width == null ? ColumnWidth.remaining() : width;
        return this;
    }

    /** Shorthand for {@code width(ColumnWidth.dp(dp))}. */
    public GridColumn widthDp(final float dp) {
        return width(ColumnWidth.dp(dp));
    }

    /** Shorthand for {@code width(ColumnWidth.percent(fraction))}. */
    public GridColumn widthPercent(final float fraction) {
        return width(ColumnWidth.percent(fraction));
    }

    /** States whether this column may be sorted by. The default is {@code true}. */
    public GridColumn sortable(final boolean sortable) {
        this.sortable = sortable;
        return this;
    }

    /** {@code true} when this column can be sorted by. */
    public boolean isSortable() {
        return sortable;
    }

    public String getTitle() {
        return title;
    }

    /** Name for the configuration object and the sort order; the title without one of its own. */
    public String getName() {
        return name == null || name.isEmpty() ? title : name;
    }

    /** {@code true} when a name of its own was set. */
    public boolean hasOwnName() {
        return name != null && !name.isEmpty();
    }

    public ColumnType getType() {
        return type;
    }

    public CellAlignment getAlignment() {
        return alignment == null ? type.getDefaultAlignment() : alignment;
    }

    /** {@code true} when the alignment was set explicitly. */
    public boolean hasOwnAlignment() {
        return alignment != null;
    }

    /**
     * Write protection of this column. The view tints the header cell of such a column; beyond
     * that the library does not act on it, but passes it on to the hooks for data cells
     * through {@link CellRef#readOnly()}.
     */
    public boolean isReadOnly() {
        return readOnly == null ? type.isDefaultReadOnly() : readOnly;
    }

    /** {@code true} when the write protection was set explicitly. */
    public boolean hasOwnReadOnly() {
        return readOnly != null;
    }

    public ColumnWidth getWidth() {
        return width;
    }

    /**
     * Position at which this column's field sits inside a data row.
     *
     * <p>The value is settled as soon as the data order is settled and does not change after
     * that – not even when columns are reordered or hidden. Before that it is -1.</p>
     */
    public int getDataIndex() {
        return dataIndex;
    }

    /** Set by the grid as soon as the data order is settled. */
    void setDataIndex(final int dataIndex) {
        this.dataIndex = dataIndex;
    }
}
