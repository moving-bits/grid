package com.movingbits.grid;

/**
 * Supplies the contents to display. The grid holds no data; it asks for it page by page, right
 * here, as soon as it has to show something.
 *
 * <p>The fields of a row come in <em>data order</em>. That order is fixed as soon as the grid
 * is fully configured – it follows from the configuration object handed in at the start, or,
 * without one, from the order of the {@code column(...)} calls. Reordering or hiding columns
 * later changes nothing about it: the grid picks the fields it currently shows out of each
 * row. {@link Grid#getDataColumns()} names the expected order.</p>
 *
 * <p>Both methods are only called when needed: when the grid is set, when paging, after a
 * changed sort order or search, and on {@code GridView.refresh()} – not while scrolling or
 * measuring.</p>
 */
public interface GridDataSource {

    /**
     * Total number of records across all pages. Page count, number column and the limits of
     * paging follow from it.
     *
     * @param search the current search, see {@link #getPage(int, int, String, String)}; what
     *               it leaves over is what gets counted
     */
    int getRowCount(String search);

    /**
     * Supplies the contents of one page.
     *
     * @param page   0-based page number; always 0 without paging
     * @param count  number of records on this page; fewer on the last page, and
     *               {@link #getRowCount(String)} without paging
     * @param sort   the current sort order as an excerpt of the configuration object, such as
     *               {@code {"sort":[{"city":"a"},{"amount":"d"}]}}; {@code {"sort":[]}} when
     *               nothing is sorted. {@link SortRequest#parse(String)} reads it.
     * @param search the current search, such as
     *               {@code {"search":[{"city":{"o":"sw","v":"Ma"}}]}}; {@code {"search":[]}}
     *               when nothing is searched for. {@link SearchRequest#parse(String)} reads
     *               it. It is not part of the configuration object and is not persisted.
     * @return the rows of the page, 0-based; per row the field contents in data order. Missing
     *         rows and fields count as empty, surplus ones are ignored.
     */
    String[][] getPage(int page, int count, String sort, String search);
}
