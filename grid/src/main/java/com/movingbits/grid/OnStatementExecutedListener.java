package com.movingbits.grid;

/**
 * Hook for statements that have been run, so that the application can log them.
 *
 * <p>It reports what the user set off: the queries and changes of the SQL editor, and the
 * change that writes an edited cell back. The queries the grid makes on its own while paging
 * are not reported - they are no decision of anyone.</p>
 *
 * <p>It reports in both cases, whether the database carried the statement out or refused it;
 * {@link SqlExecution#successful()} tells which.</p>
 */
public interface OnStatementExecutedListener {

    /**
     * @param execution the statement together with what came of it
     */
    void onStatementExecuted(SqlExecution execution);
}
