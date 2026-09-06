package com.movingbits.grid;

import java.util.ArrayList;
import java.util.List;

/**
 * Data source for the tests: delivers fields of the form {@code R<row>F<field>} and records
 * what it was called with.
 */
final class TestDataSource implements GridDataSource {

    /**
     * Record of one call to {@link #getPage(int, int, String, String)}.
     */
        record Call(int page, int count, String sort, String search) {
    }

    final List<Call> calls = new ArrayList<>();

    private int rowCount;
    private int fieldsPerRow;

    TestDataSource(int rowCount, int fieldsPerRow) {
        this.rowCount = rowCount;
        this.fieldsPerRow = fieldsPerRow;
    }

    void setRowCount(final int rowCount) {
        this.rowCount = rowCount;
    }

    void setFieldsPerRow(final int fieldsPerRow) {
        this.fieldsPerRow = fieldsPerRow;
    }

    /** The call recorded last, or {@code null}. */
    Call lastCall() {
        return calls.isEmpty() ? null : calls.get(calls.size() - 1);
    }

    /** The search that was counted with last. */
    String rowCountSearch;

    @Override
    public int getRowCount(final String search) {
        rowCountSearch = search;
        return rowCount;
    }

    @Override
    public String[][] getPage(final int page, final int count, final String sort, final String search) {
        calls.add(new Call(page, count, sort, search));
        String[][] rows = new String[count][];
        for (int i = 0; i < count; i++) {
            rows[i] = new String[fieldsPerRow];
            for (int f = 0; f < fieldsPerRow; f++) {
                rows[i][f] = "R" + (page * Math.max(1, count) + i) + "F" + f;
            }
        }
        return rows;
    }
}
