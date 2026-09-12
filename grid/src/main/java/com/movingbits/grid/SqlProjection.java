package com.movingbits.grid;

/**
 * One column of the result: the term and the name it carries.
 *
 * <p>The name is not decoration. The grid asks the query for its columns by name, and the
 * result of a statement is paged and sorted through an enclosing query - both need a name they
 * can rely on. It therefore stands in the statement as {@code AS "name"}, and the validation
 * insists on names that are set and distinct.</p>
 *
 * @param term  what the column shows
 * @param alias its name; empty only for {@code *}
 */
public record SqlProjection(SqlTerm term, String alias) {

    public SqlProjection(final SqlTerm term, final String alias) {
        if (term == null) {
            throw new IllegalArgumentException("term must not be null");
        }
        this.term = term;
        this.alias = alias == null ? "" : alias.trim();
    }

    /** {@code true} for the {@code *} that stands for every column of the table. */
    public boolean isStar() {
        return term instanceof SqlStarTerm;
    }

    /** Short text for the chip in the editor. */
    public String label() {
        return isStar() || alias.equals(term.label()) ? term.label() : term.label() + " AS " + alias;
    }
}
