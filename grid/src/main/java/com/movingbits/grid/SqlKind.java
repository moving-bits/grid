package com.movingbits.grid;

import java.util.Locale;

/**
 * What a statement does. There is deliberately nothing else: the editor can neither build a
 * {@code DELETE} nor any other statement that would change the schema.
 */
public enum SqlKind {

    /** Reads rows; the result is displayed as a grid. */
    SELECT("s"),
    /** Changes rows of one table; the result is a number of affected rows. */
    UPDATE("u");

    private final String code;

    SqlKind(final String code) {
        this.code = code;
    }

    /** Short code used in the stored statement. */
    public String getCode() {
        return code;
    }

    /**
     * Turns a short code from a stored statement back into a kind.
     *
     * @return the matching kind, or {@code null} if the code is unknown
     */
    public static SqlKind fromCode(final String code) {
        if (code == null) {
            return null;
        }
        final String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (SqlKind kind : values()) {
            if (kind.code.equals(normalized)) {
                return kind;
            }
        }
        return null;
    }
}
