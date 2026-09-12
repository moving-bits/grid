package com.movingbits.grid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The function names the editor accepts.
 *
 * <p>Values are parameters of a prepared statement and identifiers come from the tables that
 * were read, so the name of a function is the only piece of free text that ends up in the
 * statement itself. It is therefore checked twice: against the shape of an SQL name, and
 * against the functions SQLite brings along. An application that has registered functions of
 * its own hands them over through {@code SqlGrid.sqlFunctions(String...)}.</p>
 */
public final class SqlFunctions {

    /** The scalar functions of SQLite, as far as they are of use in a query. */
    private static final Set<String> BUILT_IN = names(
            "abs", "char", "coalesce", "format", "glob", "hex", "ifnull", "iif", "instr",
            "length", "likelihood", "lower", "ltrim", "max", "min", "nullif", "printf",
            "quote", "random", "replace", "round", "rtrim", "sign", "substr", "trim",
            "typeof", "unicode", "unhex", "upper",
            "date", "time", "datetime", "julianday", "strftime", "unixepoch");

    private SqlFunctions() {
            // utility class
    }

    /** The built-in names, alphabetically, as the editor offers them. */
    public static List<String> builtIn() {
        final List<String> sorted = new ArrayList<>(BUILT_IN);
        Collections.sort(sorted);
        return Collections.unmodifiableList(sorted);
    }

    /**
     * {@code true} when a name may stand in a statement.
     *
     * @param name  the name as it was entered
     * @param extra names the application has added, or {@code null}
     */
    public static boolean isAllowed(final String name, final Collection<String> extra) {
        if (!hasNameShape(name)) {
            return false;
        }
        final String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (BUILT_IN.contains(normalized)) {
            return true;
        }
        if (extra != null) {
            for (String allowed : extra) {
                if (allowed != null && allowed.trim().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@code true} when the name is shaped like an SQL name: a letter or underscore, then
     * letters, digits and underscores. Everything else could smuggle SQL of its own into the
     * statement.
     */
    public static boolean hasNameShape(final String name) {
        final String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            final char c = trimmed.charAt(i);
            final boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
            final boolean digit = c >= '0' && c <= '9';
            if (!letter && !(digit && i > 0)) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> names(final String... names) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(names)));
    }
}
