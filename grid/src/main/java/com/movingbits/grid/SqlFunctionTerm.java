package com.movingbits.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A call of an SQL function with one or more parameters, each of them a term again – calls can
 * therefore be nested as deeply as needed.
 *
 * <p>What a function delivers is beyond the library's knowledge; its result counts as
 * {@link ColumnType#UNKNOWN}. Which names are allowed is settled by
 * {@link SqlFunctions}.</p>
 *
 * @param name      name of the function
 * @param arguments its parameters in order
 */
public record SqlFunctionTerm(String name, List<SqlTerm> arguments) implements SqlTerm {

    public SqlFunctionTerm(final String name, final List<SqlTerm> arguments) {
        this.name = name == null ? "" : name.trim();
        final List<SqlTerm> copy = new ArrayList<>();
        if (arguments != null) {
            for (SqlTerm argument : arguments) {
                if (argument != null) {
                    copy.add(argument);
                }
            }
        }
        this.arguments = Collections.unmodifiableList(copy);
    }

    @Override
    public boolean hasAggregate() {
        for (SqlTerm argument : arguments) {
            if (argument.hasAggregate()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ColumnType type(final SqlColumnTypes types) {
        return ColumnType.UNKNOWN;
    }

    @Override
    public String label() {
        final StringBuilder label = new StringBuilder(name).append('(');
        for (int i = 0; i < arguments.size(); i++) {
            label.append(i == 0 ? "" : ", ").append(arguments.get(i).label());
        }
        return label.append(')').toString();
    }
}
