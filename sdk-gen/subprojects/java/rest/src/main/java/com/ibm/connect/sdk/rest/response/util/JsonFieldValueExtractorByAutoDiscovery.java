/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.response.util;

import static com.ibm.connect.sdk.rest.response.util.JsonUtils.extractPrimitiveValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.connect.sdk.rest.utils.models.FieldDefinition;

public class JsonFieldValueExtractorByAutoDiscovery {

    public List<Map<String, Object>> extractRows(JsonNode root,
                                                 List<FieldDefinition> discoveredFields) {
            // Extract row based on pre-calculated discoveredFields
            return extractRowsFromDiscoveredFields(root, discoveredFields);
    }

    /**
     * Extract row values bases on auto-discovery. {@link FieldDefinition}
     */
    private List<Map<String, Object>> extractRowsFromDiscoveredFields(JsonNode root,
                                                                      List<FieldDefinition> discoveredFields) {
        // Map fieldName -> list of values
        final Map<String, List<Object>> fieldValues = new LinkedHashMap<>();
        for (final FieldDefinition field : discoveredFields) {
            final List<Object> values = extractValuesForPath(root, field.getName());
            fieldValues.put(field.getName(), values);
        }

        // Build row-based
        final int maxRows = fieldValues.values().stream().mapToInt(List::size).max().orElse(1);
        final List<Map<String, Object>> rows = new ArrayList<>();

        for (int i = 0; i < maxRows; i++) {
            final Map<String, Object> row = new LinkedHashMap<>();
            for (final Map.Entry<String, List<Object>> entry : fieldValues.entrySet()) {
                final List<Object> vals = entry.getValue();
                row.put(entry.getKey(), i < vals.size() ? vals.get(i) : null);
            }
            rows.add(row);
        }

        return rows;
    }

    /**
     * Extract all values for a flattened path like "users[].name"
     */
    private List<Object> extractValuesForPath(JsonNode node, String path) {
        final List<Object> results = new ArrayList<>();
        if (path == null || path.isEmpty()) {
            if (node.isValueNode()) {
                results.add(extractPrimitiveValue(node));
            }
            return results;
        }

        final String[] parts = path.split("\\.");
        extractRecursive(node, parts, 0, results);
        return results;
    }

    private void extractRecursive(JsonNode current, String[] parts, int index, List<Object> results) {
        if (current == null || current.isNull()) {
            return;
        }

        if (index == parts.length) {
            if (current.isValueNode()) {
                results.add(extractPrimitiveValue(current));
            } else if (current.isArray()) {
                current.forEach(n -> {
                    if (n.isValueNode()) {
                        results.add(extractPrimitiveValue(n));
                    }
                });
            }
            return;
        }

        final String key = parts[index];
        final boolean isArray = key.endsWith("[]");
        final String cleanKey = isArray ? key.substring(0, key.length() - 2) : key;

        final JsonNode next = current.get(cleanKey);
        if (next == null) {
            return;
        }

        if (isArray && next.isArray()) {
            next.forEach(n -> extractRecursive(n, parts, index + 1, results));
        } else {
            extractRecursive(next, parts, index + 1, results);
        }
    }
}
