package com.movingbits.grid;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link DatabaseGrid} that shows the result of a statement clicked together in the SQL
 * editor.
 *
 * <p>It keeps everything the table grid can do - paging, sorting by a tap on the header row,
 * the search dialog, the column settings - because it only exchanges what the queries read
 * from: instead of a table, the statement goes in as a subquery. The columns of the display
 * then come from the statement rather than from the table: every one of them carries the name
 * the projection gave it, and its type follows from the term behind it.</p>
 *
 * <p>A result is read-only. Without a primary key no row of it could be addressed
 * unambiguously, so {@code persistData} refuses; changing data is what the editor's
 * {@code UPDATE} is for.</p>
 *
 * <pre>{@code
 * SqlGrid grid = new SqlGrid();
 * grid.setDatabase(database);
 * grid.rowsPerPage(10);
 * gridView.setGrid(grid);
 * gridView.showSqlEditor();     // the user clicks a statement together
 * }</pre>
 *
 * <p>The chaining methods it inherits return a {@code DatabaseGrid}; whoever needs the
 * {@code SqlGrid} again keeps a reference of their own, as above.</p>
 */
public class SqlGrid extends DatabaseGrid implements SqlColumnTypes {

    private static final String LOGTAG = "SqlGrid";

    /** The name the result of a statement carries as a subquery. */
    private static final String RESULT = "result";

    /** The statement the editor is working on; never {@code null}. */
    private SqlStatement statement = new SqlStatement();
    /** The query whose result is on display, or {@code null} while there is none. */
    private SqlStatement query;
    /** The displayed query, rendered; what {@link #fromSql()} hands out. */
    private RenderedSql rendered;
    /** Function names the application allows on top of those SQLite brings along. */
    private final List<String> extraFunctions = new ArrayList<>();
    private OnSnippetSaveListener snippetSaveListener;
    private OnSnippetLoadListener snippetLoadListener;

    public SqlGrid() {
    }

    /**
     * @param database an opened database
     */
    public SqlGrid(final SQLiteDatabase database) {
        setDatabase(database);
    }

    // ----------------------------------------------------------- Statements

    /**
     * The statement the editor is working on. It is not necessarily the one on display: after
     * an {@code UPDATE} the result of the last query stays where it is.
     */
    public SqlStatement getStatement() {
        return statement;
    }

    /**
     * Takes over a statement. A query is displayed at once; a change is only remembered, so
     * that the editor opens with it again.
     *
     * @param statement the statement, or {@code null} for an empty one
     */
    public void setStatement(final SqlStatement statement) {
        this.statement = statement == null ? new SqlStatement() : statement;
        if (this.statement.getKind() == SqlKind.SELECT) {
            setQuery(this.statement);
        }
    }

    /** The query whose result is on display, or {@code null}. */
    public SqlStatement getQuery() {
        return query;
    }

    /**
     * Shows the result of a query: the columns are built anew out of its projection, and the
     * next page is fetched through it.
     *
     * <p>The view has to be refreshed afterwards, as it has to be after
     * {@code setCurrentTable(String)}.</p>
     *
     * @param query a statement of the kind {@link SqlKind#SELECT}
     * @return {@code true} when it was taken over
     */
    public boolean setQuery(final SqlStatement query) {
        if (query == null || query.getKind() != SqlKind.SELECT) {
            return false;
        }
        this.query = query;
        this.rendered = query.render();
        // A result is no table: what was on display before has nothing to do with it, and
        // nothing may be written back or deleted through the key of a table that is no longer
        // the one on screen.
        currentTable = "";
        tableInfo = null;
        // The open cursor and the number of rows belong to the statement before this one.
        // They are only remembered per sort order and search, which have not changed, so
        // nothing but this would tell them apart.
        closeCursor();
        // As with another table: the old columns do not suit the new result.
        clearColumns();
        buildResultColumns();
        refreshRowCount();
        return true;
    }

    /**
     * Shows a table again instead of the result of a statement. The two exclude each other:
     * whichever is chosen last is the one on display.
     */
    @Override
    public boolean setCurrentTable(final String table) {
        // The statement has to go first: counting, paging and the columns are all built while
        // the table is taken over, and they have to rest on the table by then.
        final SqlStatement previousQuery = query;
        final RenderedSql previousRendered = rendered;
        query = null;
        rendered = null;
        if (super.setCurrentTable(table)) {
            return true;
        }
        // No table was taken over, so nothing changes at all.
        query = previousQuery;
        rendered = previousRendered;
        return false;
    }

    /** Drops the result on display; the grid stays empty until the next query. */
    public void clearQuery() {
        query = null;
        rendered = null;
        clearColumns();
        closeCursor();
    }

    /**
     * The columns of the result, out of the projection of the query.
     *
     * <p>Every column carries the name the projection gave it - that is how the grid finds it
     * in the cursor - and the type of the term behind it, so that numbers are aligned and
     * sorted as numbers. {@code *} stands in for the columns of the table.</p>
     */
    private void buildResultColumns() {
        for (SqlProjection column : query.getProjection()) {
            if (column.isStar()) {
                addTableColumns(query.getTable());
                continue;
            }
            final ColumnType type = column.term().type(this);
            column(new GridColumn(column.alias())
                    .name(column.alias())
                    .type(type)
                    // A result cannot be written back: no primary key, no unambiguous row.
                    .readOnly(true)
                    .widthDp(widthDpOf(type)));
        }
    }

    /** The columns of a table, for a projection that asks for all of them. */
    private void addTableColumns(final String table) {
        final TableInfo info = tableInfoOf(table);
        if (info == null) {
            return;
        }
        for (ColumnInfo declared : info.columns) {
            final ColumnType type = typeOf(declared.storageClass);
            column(new GridColumn(declared.name)
                    .name(declared.name)
                    .type(type)
                    .readOnly(true)
                    .widthDp(widthDpOf(type)));
        }
    }

    // -------------------------------------------------------- Query source

    @Override
    protected String fromSql() {
        return rendered == null ? super.fromSql() : "(" + rendered.sql() + ") AS " + quote(RESULT);
    }

    @Override
    protected String[] fromArgs() {
        return rendered == null ? super.fromArgs() : rendered.args();
    }

    @Override
    protected boolean isQueryable() {
        return rendered == null ? super.isQueryable() : database != null;
    }

    // ------------------------------------------------------- Column types

    /** The type of a table's column, out of the tables that were read. */
    @Override
    public ColumnType typeOf(final String table, final String column) {
        final TableInfo info = tableInfoOf(table);
        if (info != null) {
            for (ColumnInfo candidate : info.columns) {
                if (candidate.name.equals(column)) {
                    return typeOf(candidate.storageClass);
                }
            }
        }
        return ColumnType.UNKNOWN;
    }

    /** The names of a table's columns, in the order of the table; empty when it is unknown. */
    public List<String> getColumnNames(final String table) {
        final TableInfo info = tableInfoOf(table);
        if (info == null) {
            return Collections.emptyList();
        }
        final List<String> names = new ArrayList<>();
        for (ColumnInfo column : info.columns) {
            names.add(column.name);
        }
        return Collections.unmodifiableList(names);
    }

    // ---------------------------------------------------------- Functions

    /**
     * Function names the editor is to accept on top of those SQLite brings along - for
     * functions the application has registered on the database itself.
     *
     * @param names names of the functions
     */
    public SqlGrid sqlFunctions(final String... names) {
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.trim().isEmpty()) {
                    extraFunctions.add(name.trim());
                }
            }
        }
        return this;
    }

    /** The function names the application has added. */
    public List<String> getSqlFunctions() {
        return Collections.unmodifiableList(extraFunctions);
    }

    // ---------------------------------------------------------- Snippets

    /**
     * Hook for storing the statement the editor is working on. Without it the editor shows no
     * button for saving.
     */
    public SqlGrid onSnippetSave(final OnSnippetSaveListener listener) {
        this.snippetSaveListener = listener;
        return this;
    }

    /**
     * Hook for fetching a statement that was stored earlier. Without it the editor shows no
     * button for loading.
     */
    public SqlGrid onSnippetLoad(final OnSnippetLoadListener listener) {
        this.snippetLoadListener = listener;
        return this;
    }

    OnSnippetSaveListener getSnippetSaveListener() {
        return snippetSaveListener;
    }

    OnSnippetLoadListener getSnippetLoadListener() {
        return snippetLoadListener;
    }

    // --------------------------------------------------------- Checking

    /**
     * Checks a statement on the model alone: are the parts there, do they fit together. The
     * first of the two stages, and the one the editor keeps up with while it is being clicked
     * together.
     *
     * @return the findings; empty when nothing stands in the way
     */
    public List<SqlFinding> validate(final SqlStatement statement) {
        return SqlValidation.check(statement, extraFunctions);
    }

    /**
     * Has the database judge a statement, without running it: {@code EXPLAIN} prepares it and
     * hands back its plan, and it neither reads a row nor changes one. The second stage, which
     * catches whatever only SQLite itself can tell.
     *
     * @return what the database complained about, or {@code null} when it accepts the
     *         statement
     */
    public String checkWithDatabase(final SqlStatement statement) {
        if (database == null || statement == null) {
            return null;
        }
        final RenderedSql sql = statement.render();
        try (Cursor plan = database.rawQuery("EXPLAIN " + sql.sql(), sql.args())) {
            // Preparing happens with the query, reading its plan makes sure of it.
            plan.getCount();
            return null;
        } catch (SQLiteException rejected) {
            return rejected.getMessage();
        }
    }

    /**
     * How many rows a change would affect. The editor names the number in its question before
     * a change without a condition goes ahead.
     *
     * @return the number of rows, or {@code -1} when it cannot be determined
     */
    public int countAffected(final SqlStatement statement) {
        if (database == null || statement == null || statement.getKind() != SqlKind.UPDATE) {
            return -1;
        }
        // Counted through a query of its own, built out of the same table and the same
        // condition - that way the count cannot say something different than the change does.
        final SqlStatement count = new SqlStatement();
        count.setTable(statement.getTable());
        count.getProjection().add(new SqlProjection(
                new SqlAggregateTerm(SqlAggregate.COUNT, new SqlStarTerm(), false), "number"));
        count.getWhere().setJunction(statement.getWhere().getJunction());
        count.getWhere().getParts().addAll(statement.getWhere().getParts());

        final RenderedSql sql = count.render();
        try (Cursor number = database.rawQuery(sql.sql(), sql.args())) {
            return number.moveToFirst() ? number.getInt(0) : -1;
        } catch (SQLiteException unreadable) {
            Log.w(LOGTAG, "counting failed: " + unreadable.getMessage() + " - " + sql.sql());
            return -1;
        }
    }

    // ---------------------------------------------------------- Executing

    /**
     * Runs a statement.
     *
     * <p>A query becomes the result on display - the view is to be refreshed afterwards. A
     * change is carried out and the rows it touched are counted; what is on display stays as
     * it is, but its open cursor is dropped, because the rows underneath may have changed.</p>
     *
     * <p>Whether it worked out or not, the statement is reported through
     * {@link #onStatementExecuted(OnStatementExecutedListener)}.</p>
     *
     * @param statement the statement; it is taken over as the one the editor works on
     * @return what came of it
     */
    public SqlExecution execute(final SqlStatement statement) {
        final RenderedSql sql = statement == null ? new RenderedSql("", null) : statement.render();
        if (statement == null || database == null) {
            return report(new SqlExecution(SqlExecution.Origin.EDITOR, SqlKind.SELECT,
                    sql.sql(), sql.parameters(), -1, false, "no database"));
        }

        this.statement = statement;
        return statement.getKind() == SqlKind.UPDATE
                ? change(statement, sql)
                : query(statement, sql);
    }

    /** Takes over a query and reports how many rows its result holds. */
    private SqlExecution query(final SqlStatement statement, final RenderedSql sql) {
        setQuery(statement);
        // Asked for right here, so that the report says what the display shows.
        refreshRowCount();
        return report(new SqlExecution(SqlExecution.Origin.EDITOR, SqlKind.SELECT,
                sql.sql(), sql.parameters(), getRowCount(), true, null));
    }

    /**
     * Carries a change out.
     *
     * <p>The number of rows comes from the prepared statement itself: asking the database for
     * {@code changes()} afterwards could land on another connection of the pool and count
     * something else entirely.</p>
     */
    private SqlExecution change(final SqlStatement statement, final RenderedSql sql) {
        int changed = -1;
        String error = null;
        try (SQLiteStatement compiled = database.compileStatement(sql.sql())) {
            final List<String> parameters = sql.parameters();
            for (int i = 0; i < parameters.size(); i++) {
                final String value = parameters.get(i);
                if (value == null) {
                    compiled.bindNull(i + 1);
                } else {
                    compiled.bindString(i + 1, value);
                }
            }
            changed = compiled.executeUpdateDelete();
        } catch (SQLiteException notWritable) {
            Log.w(LOGTAG, "change failed: " + notWritable.getMessage() + " - " + sql.sql());
            error = notWritable.getMessage();
        }

        if (error == null) {
            // What is on display may rest on rows that have just changed.
            closeCursor();
        }
        return report(new SqlExecution(SqlExecution.Origin.EDITOR, SqlKind.UPDATE,
                sql.sql(), sql.parameters(), changed, error == null, error));
    }

    private SqlExecution report(final SqlExecution execution) {
        notifyStatementExecuted(execution);
        return execution;
    }

    // -------------------------------------------------------------- State

    /**
     * The state, with the statement the editor is working on and the query on display. Both
     * belong to it: after a change of screen orientation the result is to be shown again, and
     * the editor is to open with what it had.
     */
    @Override
    public String toStateJson() {
        return GridState.withStatements(super.toStateJson(),
                statement.toJson(), query == null ? null : query.toJson());
    }

    @Override
    public SqlGrid state(final String json) {
        // The query first: it settles the columns, which the configuration object in the state
        // then refers to.
        final String storedQuery = GridState.queryOf(json);
        if (!storedQuery.isEmpty()) {
            setQuery(SqlStatement.parse(storedQuery));
        }
        super.state(json);
        final String storedStatement = GridState.statementOf(json);
        if (!storedStatement.isEmpty()) {
            this.statement = SqlStatement.parse(storedStatement);
        }
        return this;
    }

    @Override
    public SqlGrid readState(final Bundle state) {
        super.readState(state);
        return this;
    }
}
