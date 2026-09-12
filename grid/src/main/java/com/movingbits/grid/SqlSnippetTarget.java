package com.movingbits.grid;

/**
 * What the SQL editor offers for handing a stored statement back to it.
 *
 * <p>The application receives it through {@link OnSnippetLoadListener} and calls it whenever it
 * has the statement at hand.</p>
 */
public interface SqlSnippetTarget {

    /**
     * Takes a stored statement over: what is in the editor is dropped, the statement is read
     * and the editor shows it.
     *
     * <p>A string that cannot be read leaves an empty statement behind - the same as anywhere
     * else in the library, where what cannot be read is quietly skipped.</p>
     *
     * @param statement a statement as {@link OnSnippetSaveListener} handed it out
     */
    void load(String statement);
}
