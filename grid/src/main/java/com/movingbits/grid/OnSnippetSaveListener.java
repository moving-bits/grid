package com.movingbits.grid;

/**
 * Hook for storing the statement the SQL editor is working on.
 *
 * <p>Where it is kept is the application's business - a file, a table of its own, a server.
 * The editor hands out the statement as a string and hears nothing more of it; what comes back
 * one day goes through {@link OnSnippetLoadListener}.</p>
 *
 * <p>Without this hook the editor shows no button for saving at all.</p>
 */
public interface OnSnippetSaveListener {

    /**
     * @param statement the statement as it stands in the editor, as a string. It is the
     *                  library's own form and is only to be handed back as it is; what it
     *                  says in SQL is {@code SqlStatement.parse(statement).render()}
     */
    void onSnippetSave(String statement);
}
