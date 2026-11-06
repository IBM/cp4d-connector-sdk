/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Precomputed per-table field mapping plan.
 *
 * <p>Built once from the {@link RestFieldDefinition}s and reused for every record of every page.
 * Field paths are split and organised in a trie ({@link Node}) and each declared type is
 * normalised to a {@link TargetType}, so the per-record hot path performs no {@code split()},
 * {@code toLowerCase()} or type-string parsing.
 *
 * <p>Instances are immutable after construction and may be shared.
 */
final class FieldPlan
{
    private final List<Entry> entries;
    private final String[] names;
    private final Node root = new Node();

    /**
     * Normalised target type of a field. {@link #TEXT} is only used by lookup plans: scalars are
     * returned as text and containers yield {@code null}.
     */
    enum TargetType
    {
        INT, BIGINT, BOOLEAN, DOUBLE, DECIMAL, DATE, TIMESTAMP, JSON, VARCHAR, TEXT
    }

    /** One configured field. */
    static final class Entry
    {
        final String name;
        final TargetType targetType;
        final int index;

        Entry(String name, TargetType targetType, int index)
        {
            this.name = name;
            this.targetType = targetType;
            this.index = index;
        }
    }

    /** Trie node over the field-path segments. */
    static final class Node
    {
        private final Map<String, Node> children = new HashMap<>();
        private Entry entry;

        Node child(String key)
        {
            return children.get(key);
        }

        boolean hasChildren()
        {
            return !children.isEmpty();
        }

        /** The field that ends at this node, or {@code null}. */
        Entry entry()
        {
            return entry;
        }
    }

    /**
     * Creates the plan.
     *
     * @param fieldDefs
     *            the configured fields, in output order
     * @throws IllegalArgumentException
     *             if a field name is empty or contains an empty path segment
     */
    FieldPlan(List<RestFieldDefinition> fieldDefs)
    {
        this(fieldDefs, null);
    }

    /** Creates a plan that reads the single named field as text (see {@link TargetType#TEXT}). */
    static FieldPlan forLookup(String fieldName)
    {
        return new FieldPlan(Collections.singletonList(new RestFieldDefinition(fieldName, "VARCHAR", false, false)),
                TargetType.TEXT);
    }

    private FieldPlan(List<RestFieldDefinition> fieldDefs, TargetType forcedType)
    {
        entries = new ArrayList<>(fieldDefs.size());
        names = new String[fieldDefs.size()];
        int index = 0;
        for (final RestFieldDefinition fieldDef : fieldDefs) {
            final List<String> segments = JsonPathTrie.segments(fieldDef.getName());
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("Field name must not be empty");
            }
            final TargetType type = forcedType != null ? forcedType : resolveType(fieldDef.getTypeString());
            final Entry entry = new Entry(fieldDef.getName(), type, index);
            Node node = root;
            for (final String segment : segments) {
                node = node.children.computeIfAbsent(segment, k -> new Node());
            }
            node.entry = entry; // a duplicate field name overwrites; the writer would see the same name twice anyway
            entries.add(entry);
            names[index++] = fieldDef.getName();
        }
    }

    Node root()
    {
        return root;
    }

    int size()
    {
        return entries.size();
    }

    /** Returns the name of the field at the given output position. */
    String nameAt(int index)
    {
        return names[index];
    }

    private static TargetType resolveType(String typeString)
    {
        final String lower = typeString == null ? "" : typeString.toLowerCase(Locale.ENGLISH);
        final int paren = lower.indexOf('(');
        final String base = (paren >= 0 ? lower.substring(0, paren) : lower).trim();
        switch (base) {
        case "integer":
        case "int":
        case "smallint":
        case "tinyint":
            return TargetType.INT;
        case "bigint":
            return TargetType.BIGINT;
        case "boolean":
        case "bool":
        case "bit":
            return TargetType.BOOLEAN;
        case "double":
        case "float8":
        case "float":
        case "real":
        case "float4":
            return TargetType.DOUBLE;
        case "decimal":
        case "numeric":
            return TargetType.DECIMAL;
        case "date":
            return TargetType.DATE;
        case "timestamp":
        case "datetime":
            return TargetType.TIMESTAMP;
        case "json":
        case "jsonb":
        case "array":
        case "object":
            return TargetType.JSON;
        default:
            return TargetType.VARCHAR;
        }
    }
}
