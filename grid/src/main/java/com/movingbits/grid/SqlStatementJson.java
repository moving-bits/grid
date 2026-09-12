package com.movingbits.grid;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Reads and writes a statement as a JSON string.
 *
 * <p>Structure:</p>
 * <pre>{@code
 * {
 *   "kind": "s|u", "distinct": false, "table": "<name>",
 *   "joins":    [ { "type": "j|lj", "table": "<name>", "on": [ <condition> ] } ],
 *   "select":   [ { "term": <term>, "alias": "<name>" } ],
 *   "set":      [ { "column": "<name>", "term": <term> } ],
 *   "where":    <group>,
 *   "group_by": [ <term> ],
 *   "having":   <group>,
 *   "order_by": [ { "alias": "<name>", "dir": "a|d" } ]
 * }
 * }</pre>
 *
 * <p>A term is one of {@code {"t":"c","table":..,"column":..}},
 * {@code {"t":"v","type":"s|i|f|u","value":..}}, {@code {"t":"a","fn":..,"arg":<term>}},
 * {@code {"t":"f","name":..,"args":[<term>]}} or {@code {"t":"s"}} for the star; a condition is
 * either {@code {"c":"c","left":<term>,"op":..,"right":[<term>]}} or
 * {@code {"c":"g","junction":"and|or","parts":[<condition>]}}.</p>
 *
 * <p>Reading is forgiving, as it is for the configuration object: what cannot be read is
 * skipped, and a string that is no JSON at all yields an empty statement.</p>
 */
final class SqlStatementJson {

    private static final String FIELD_KIND = "kind";
    private static final String FIELD_DISTINCT = "distinct";
    private static final String FIELD_TABLE = "table";
    private static final String FIELD_JOINS = "joins";
    private static final String FIELD_SELECT = "select";
    private static final String FIELD_SET = "set";
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_GROUP_BY = "group_by";
    private static final String FIELD_HAVING = "having";
    private static final String FIELD_ORDER_BY = "order_by";

    private static final String FIELD_TERM = "term";
    private static final String FIELD_ALIAS = "alias";
    private static final String FIELD_COLUMN = "column";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_DIRECTION = "dir";
    private static final String FIELD_ON = "on";
    private static final String FIELD_LEFT = "left";
    private static final String FIELD_RIGHT = "right";
    private static final String FIELD_OPERATOR = "op";
    private static final String FIELD_JUNCTION = "junction";
    private static final String FIELD_PARTS = "parts";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_ARGUMENT = "arg";
    private static final String FIELD_ARGUMENTS = "args";
    private static final String FIELD_FUNCTION = "fn";
    private static final String FIELD_VALUE = "value";

    private static final String TERM = "t";
    private static final String TERM_COLUMN = "c";
    private static final String TERM_VALUE = "v";
    private static final String TERM_AGGREGATE = "a";
    private static final String TERM_FUNCTION = "f";
    private static final String TERM_STAR = "s";

    private static final String CONDITION = "c";
    private static final String CONDITION_COMPARISON = "c";
    private static final String CONDITION_GROUP = "g";

    private SqlStatementJson() {
            // utility class
    }

    // --------------------------------------------------------------- Writing

    static String toJson(final SqlStatement statement) {
        final JSONObject root = new JSONObject();
        try {
            root.put(FIELD_KIND, statement.getKind().getCode());
            root.put(FIELD_DISTINCT, statement.isDistinct());
            root.put(FIELD_TABLE, statement.getTable());

            final JSONArray joins = new JSONArray();
            for (SqlJoin join : statement.getJoins()) {
                final JSONObject entry = new JSONObject();
                entry.put(FIELD_TYPE, join.type().getCode());
                entry.put(FIELD_TABLE, join.table());
                final JSONArray on = new JSONArray();
                for (SqlComparison comparison : join.on()) {
                    on.put(conditionOf(comparison));
                }
                entry.put(FIELD_ON, on);
                joins.put(entry);
            }
            root.put(FIELD_JOINS, joins);

            final JSONArray select = new JSONArray();
            for (SqlProjection column : statement.getProjection()) {
                final JSONObject entry = new JSONObject();
                entry.put(FIELD_TERM, termOf(column.term()));
                entry.put(FIELD_ALIAS, column.alias());
                select.put(entry);
            }
            root.put(FIELD_SELECT, select);

            final JSONArray set = new JSONArray();
            for (SqlAssignment assignment : statement.getAssignments()) {
                final JSONObject entry = new JSONObject();
                entry.put(FIELD_COLUMN, assignment.column());
                entry.put(FIELD_TERM, termOf(assignment.value()));
                set.put(entry);
            }
            root.put(FIELD_SET, set);

            root.put(FIELD_WHERE, conditionOf(statement.getWhere()));
            root.put(FIELD_HAVING, conditionOf(statement.getHaving()));

            final JSONArray groupBy = new JSONArray();
            for (SqlTerm term : statement.getGroupBy()) {
                groupBy.put(termOf(term));
            }
            root.put(FIELD_GROUP_BY, groupBy);

            final JSONArray orderBy = new JSONArray();
            for (SqlOrder order : statement.getOrderBy()) {
                final JSONObject entry = new JSONObject();
                entry.put(FIELD_ALIAS, order.alias());
                entry.put(FIELD_DIRECTION, order.direction() == SortDirection.DESCENDING ? "d" : "a");
                orderBy.put(entry);
            }
            root.put(FIELD_ORDER_BY, orderBy);
        } catch (JSONException ignore) {
            return "{}";
        }
        return root.toString();
    }

    private static JSONObject termOf(final SqlTerm term) throws JSONException {
        final JSONObject entry = new JSONObject();
        if (term instanceof SqlColumnTerm column) {
            entry.put(TERM, TERM_COLUMN);
            entry.put(FIELD_TABLE, column.table());
            entry.put(FIELD_COLUMN, column.column());
        } else if (term instanceof SqlValueTerm value) {
            entry.put(TERM, TERM_VALUE);
            entry.put(FIELD_TYPE, value.type().getCode());
            entry.put(FIELD_VALUE, value.text());
        } else if (term instanceof SqlAggregateTerm aggregate) {
            entry.put(TERM, TERM_AGGREGATE);
            entry.put(FIELD_FUNCTION, aggregate.function().getCode());
            entry.put(FIELD_DISTINCT, aggregate.distinct());
            entry.put(FIELD_ARGUMENT, termOf(aggregate.argument()));
        } else if (term instanceof SqlFunctionTerm function) {
            entry.put(TERM, TERM_FUNCTION);
            entry.put(FIELD_NAME, function.name());
            final JSONArray arguments = new JSONArray();
            for (SqlTerm argument : function.arguments()) {
                arguments.put(termOf(argument));
            }
            entry.put(FIELD_ARGUMENTS, arguments);
        } else {
            entry.put(TERM, TERM_STAR);
        }
        return entry;
    }

    private static JSONObject conditionOf(final SqlCondition condition) throws JSONException {
        final JSONObject entry = new JSONObject();
        if (condition instanceof SqlConditionGroup group) {
            entry.put(CONDITION, CONDITION_GROUP);
            entry.put(FIELD_JUNCTION, group.getJunction().getCode());
            final JSONArray parts = new JSONArray();
            for (SqlCondition part : group.getParts()) {
                parts.put(conditionOf(part));
            }
            entry.put(FIELD_PARTS, parts);
        } else if (condition instanceof SqlComparison comparison) {
            entry.put(CONDITION, CONDITION_COMPARISON);
            if (comparison.left() != null) {
                entry.put(FIELD_LEFT, termOf(comparison.left()));
            }
            if (comparison.operator() != null) {
                entry.put(FIELD_OPERATOR, comparison.operator().getCode());
            }
            final JSONArray right = new JSONArray();
            for (SqlTerm term : comparison.right()) {
                right.put(termOf(term));
            }
            entry.put(FIELD_RIGHT, right);
        }
        return entry;
    }

    // --------------------------------------------------------------- Reading

    static SqlStatement parse(final String json) {
        final SqlStatement statement = new SqlStatement();
        final JSONObject root = parseObject(json);
        if (root == null) {
            return statement;
        }

        final SqlKind kind = SqlKind.fromCode(root.optString(FIELD_KIND, null));
        if (kind != null) {
            statement.setKind(kind);
        }
        statement.setDistinct(root.optBoolean(FIELD_DISTINCT, false));
        statement.setTable(root.optString(FIELD_TABLE, ""));

        final JSONArray joins = root.optJSONArray(FIELD_JOINS);
        for (int i = 0; joins != null && i < joins.length(); i++) {
            final JSONObject entry = joins.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            final List<SqlComparison> on = new ArrayList<>();
            final JSONArray conditions = entry.optJSONArray(FIELD_ON);
            for (int c = 0; conditions != null && c < conditions.length(); c++) {
                final SqlCondition condition = conditionOf(conditions.optJSONObject(c));
                if (condition instanceof SqlComparison comparison) {
                    on.add(comparison);
                }
            }
            statement.getJoins().add(new SqlJoin(
                    SqlJoinType.fromCode(entry.optString(FIELD_TYPE, null)),
                    entry.optString(FIELD_TABLE, ""), on));
        }

        final JSONArray select = root.optJSONArray(FIELD_SELECT);
        for (int i = 0; select != null && i < select.length(); i++) {
            final JSONObject entry = select.optJSONObject(i);
            final SqlTerm term = entry == null ? null : termOf(entry.optJSONObject(FIELD_TERM));
            if (term != null) {
                statement.getProjection().add(new SqlProjection(term, entry.optString(FIELD_ALIAS, "")));
            }
        }

        final JSONArray set = root.optJSONArray(FIELD_SET);
        for (int i = 0; set != null && i < set.length(); i++) {
            final JSONObject entry = set.optJSONObject(i);
            final SqlTerm term = entry == null ? null : termOf(entry.optJSONObject(FIELD_TERM));
            final String column = entry == null ? "" : entry.optString(FIELD_COLUMN, "");
            if (term != null && !column.isEmpty()) {
                statement.getAssignments().add(new SqlAssignment(column, term));
            }
        }

        fill(statement.getWhere(), root.optJSONObject(FIELD_WHERE));
        fill(statement.getHaving(), root.optJSONObject(FIELD_HAVING));

        final JSONArray groupBy = root.optJSONArray(FIELD_GROUP_BY);
        for (int i = 0; groupBy != null && i < groupBy.length(); i++) {
            final SqlTerm term = termOf(groupBy.optJSONObject(i));
            if (term != null) {
                statement.getGroupBy().add(term);
            }
        }

        final JSONArray orderBy = root.optJSONArray(FIELD_ORDER_BY);
        for (int i = 0; orderBy != null && i < orderBy.length(); i++) {
            final JSONObject entry = orderBy.optJSONObject(i);
            final String alias = entry == null ? "" : entry.optString(FIELD_ALIAS, "");
            if (!alias.isEmpty()) {
                statement.getOrderBy().add(new SqlOrder(alias,
                        "d".equalsIgnoreCase(entry.optString(FIELD_DIRECTION, "a").trim())
                                ? SortDirection.DESCENDING : SortDirection.ASCENDING));
            }
        }
        return statement;
    }

    /** Takes over junction and parts of a stored group into an existing one. */
    private static void fill(final SqlConditionGroup group, final JSONObject entry) {
        if (entry == null) {
            return;
        }
        group.setJunction(SqlJunction.fromCode(entry.optString(FIELD_JUNCTION, null)));
        final JSONArray parts = entry.optJSONArray(FIELD_PARTS);
        for (int i = 0; parts != null && i < parts.length(); i++) {
            final SqlCondition part = conditionOf(parts.optJSONObject(i));
            if (part != null) {
                group.getParts().add(part);
            }
        }
    }

    private static SqlCondition conditionOf(final JSONObject entry) {
        if (entry == null) {
            return null;
        }
        if (CONDITION_GROUP.equals(entry.optString(CONDITION, CONDITION_COMPARISON))) {
            final SqlConditionGroup group = new SqlConditionGroup();
            fill(group, entry);
            return group;
        }
        final SqlTerm left = termOf(entry.optJSONObject(FIELD_LEFT));
        final SqlOperator operator = SqlOperator.fromCode(entry.optString(FIELD_OPERATOR, null));
        final List<SqlTerm> right = new ArrayList<>();
        final JSONArray operands = entry.optJSONArray(FIELD_RIGHT);
        for (int i = 0; operands != null && i < operands.length(); i++) {
            final SqlTerm term = termOf(operands.optJSONObject(i));
            if (term != null) {
                right.add(term);
            }
        }
        // Without a left side or an operator there is nothing to compare; the entry falls away.
        return left == null || operator == null ? null : new SqlComparison(left, operator, right);
    }

    private static SqlTerm termOf(final JSONObject entry) {
        if (entry == null) {
            return null;
        }
        final String type = entry.optString(TERM, "");
        if (TERM_STAR.equals(type)) {
            return new SqlStarTerm();
        }
        if (TERM_VALUE.equals(type)) {
            final ColumnType valueType = ColumnType.fromCode(entry.optString(FIELD_TYPE, null));
            return new SqlValueTerm(valueType, entry.optString(FIELD_VALUE, ""));
        }
        if (TERM_AGGREGATE.equals(type)) {
            final SqlAggregate function = SqlAggregate.fromCode(entry.optString(FIELD_FUNCTION, null));
            final SqlTerm argument = termOf(entry.optJSONObject(FIELD_ARGUMENT));
            return function == null || argument == null ? null
                    : new SqlAggregateTerm(function, argument, entry.optBoolean(FIELD_DISTINCT, false));
        }
        if (TERM_FUNCTION.equals(type)) {
            final List<SqlTerm> arguments = new ArrayList<>();
            final JSONArray stored = entry.optJSONArray(FIELD_ARGUMENTS);
            for (int i = 0; stored != null && i < stored.length(); i++) {
                final SqlTerm argument = termOf(stored.optJSONObject(i));
                if (argument != null) {
                    arguments.add(argument);
                }
            }
            return new SqlFunctionTerm(entry.optString(FIELD_NAME, ""), arguments);
        }
        final String table = entry.optString(FIELD_TABLE, "");
        final String column = entry.optString(FIELD_COLUMN, "");
        return column.isEmpty() ? null : new SqlColumnTerm(table, column);
    }

    private static JSONObject parseObject(final String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(json);
        } catch (JSONException ignore) {
            return null;
        }
    }
}
