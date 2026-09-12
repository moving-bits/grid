package com.movingbits.grid;

/**
 * Hook for fetching a statement that was stored earlier.
 *
 * <p>The editor asks and waits for nothing: the application picks whatever it has to pick -
 * out of a list put to the user, say - and hands the statement over through the editor it was
 * given, whenever that may be.</p>
 *
 * <p>Without this hook the editor shows no button for loading at all.</p>
 */
public interface OnSnippetLoadListener {

    /**
     * @param editor takes the stored statement; it stays usable for as long as the editor is
     *               open
     */
    void onSnippetLoad(SqlSnippetTarget editor);
}
