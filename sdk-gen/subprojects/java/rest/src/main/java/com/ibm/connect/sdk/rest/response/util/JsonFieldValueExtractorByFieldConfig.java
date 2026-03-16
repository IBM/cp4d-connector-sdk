/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.response.util;

import static com.ibm.connect.sdk.rest.response.util.JsonUtils.isNotEmptyArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.connect.sdk.rest.utils.ObjectMapperUtils;
import com.ibm.connect.sdk.rest.utils.models.FieldDefinition;
import com.ibm.connect.sdk.rest.utils.models.ResponseFieldConfig;
import com.jayway.jsonpath.JsonPath;

public class JsonFieldValueExtractorByFieldConfig {

    /**
     * Build rows from JsonNode based on FieldConfigs.
     */
    public List<Map<String, Object>> extractRows(final JsonNode responseJsonNode,
                                                 final List<ResponseFieldConfig> fieldConfigs,
                                                 List<FieldDefinition> fieldDefinitions) throws JsonProcessingException {
        // Group configs by their array-parent path, e.g. "$.users[*]" or "" for none
        final Map<String, List<ResponseFieldConfig>> groups = new LinkedHashMap<>();
        for (final ResponseFieldConfig cfg : fieldConfigs) {
            final String parent = getArrayParentPath(cfg.getJsonPath()); // "" if no array parent
            groups.computeIfAbsent(parent, k -> new ArrayList<>()).add(cfg);
        }

        List<Map<String, Object>> allRows = new ArrayList<>();

        // Process each group independently
        for (final Map.Entry<String, List<ResponseFieldConfig>> groupEntry : groups.entrySet()) {
            final String groupParent = groupEntry.getKey();
            final List<ResponseFieldConfig> configsInGroup = groupEntry.getValue();

            final List<Map<String, Object>> groupRows;
            if (groupParent.isEmpty()) {
                // No array parent: each config operates on independent nodes -> extract then merge (cartesian)
                groupRows = new ArrayList<>();
                for (final ResponseFieldConfig cfg : configsInGroup) {
                    final Object extracted = JsonPath.read(ObjectMapperUtils.toJsonString(responseJsonNode), cfg.getJsonPath());
                    final JsonNode extractedNode = ObjectMapperUtils.toJsonNode(extracted);
                    final List<Map<String, Object>> cfgRows = extractFieldValues(cfg.getName(), extractedNode);
                    groupRows.addAll(cfgRows); // configs without an array parent typically are singletons or simple maps
                }
            } else {
                // Group parent is an array path like "$.users[*]".
                // For each element in that array, extract values for every config in the group relative to that element,
                // then combine per-element rows (cartesian within element).
                final Object parentArrayObj = JsonPath.read(ObjectMapperUtils.toJsonString(responseJsonNode), groupParent);
                final JsonNode parentArrayNode = ObjectMapperUtils.toJsonNode(parentArrayObj);

                final List<Map<String, Object>> rowsForGroup = new ArrayList<>();
                if (parentArrayNode != null && parentArrayNode.isArray()) {
                    for (final JsonNode element : parentArrayNode) {
                        // For this element, extract rows for each config (relative to the element)
                        List<Map<String, Object>> elementRows = Collections.singletonList(initialiseRowWithFixedField(fieldDefinitions));

                        for (final ResponseFieldConfig cfg : configsInGroup) {
                            // Compute relative path (tail) after the groupParent
                            final String tail = getTailPath(cfg.getJsonPath(), groupParent);
                            final String tailForElement = tail.isEmpty() ? "$" : (tail.startsWith("$") ? tail : "$" + tail);

                            final Object extractedForElement;
                            try {
                                // Evaluate tail path against the element JSON
                                extractedForElement = JsonPath.read(ObjectMapperUtils.toJsonString(element), tailForElement);
                            } catch (final Exception e) {
                                // if tail path is invalid for this element, treat as missing
                                continue;
                            }

                            final JsonNode extractedNode = ObjectMapperUtils.toJsonNode(extractedForElement);
                            final String prefix = (extractedNode != null && extractedNode.isValueNode()) ? cfg.getName() : cfg.getName() + "[]" ;
                            final List<Map<String, Object>> cfgRowsForElement = extractFieldValues(prefix, extractedNode);

                            // Merge rows within this element (cartesian among configs for this element)
                            elementRows = mergeRows(elementRows, cfgRowsForElement);
                        }

                        // Append elementRows (may be multiple rows per element if nested arrays exist)
                        rowsForGroup.addAll(elementRows);
                    }
                }
                groupRows = rowsForGroup;
            }

            // Merge this group's rows into global result:
            // - If allRows empty, assign directly
            // - Otherwise Cartesian-merge with existing allRows (they are independent groups)
            if (allRows.isEmpty()) {
                allRows.addAll(groupRows);
            } else {
                allRows = mergeRows(allRows, groupRows); // cartesian merge between different groups
            }
        }

        return allRows;
    }


    /**
     * Returns "$.xxx[*]" if jsonPath contains an array segment; empty string if none.
     */
    private String getArrayParentPath(final String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank()) {
            return "";
        }
        final int idx = jsonPath.indexOf("[*]");
        return idx == -1 ? "" : jsonPath.substring(0, idx + 3);
    }

    /**
     * Given fullPath and parentPath (which is a prefix), returns the tail part after parentPath.
     * Example: full="$.users[*].addresses[*].postcode", parent="$.users[*]" -> tail=".addresses[*].postcode"
     * If parentPath is not a prefix, returns the fullPath.
     */
    private String getTailPath(final String fullPath, final String parentPath) {
        if (fullPath == null) {
            return "";
        }
        if (parentPath == null || parentPath.isEmpty()) {
            return fullPath;
        }
        if (fullPath.startsWith(parentPath)) {
            return fullPath.substring(parentPath.length()); // may start with "." or "["
        }
        return fullPath;
    }

    /**
     * Extracts flattened maps for a node using the given prefix.
     * This is the same helper we used before: returns list of maps (may be multiple rows for arrays).
     */
    private List<Map<String, Object>> extractFieldValues(final String prefix, final JsonNode node) {
        final List<Map<String, Object>> rows = new ArrayList<>();
        if (node == null || node.isNull()) {
            return rows;
        }

        // Case 1: Array
        if (node.isArray()) {
            if (isNotEmptyArray(node) && JsonUtils.isPrimitiveArray(node)) {
                // Array of primitives -> single entry
                final List<String> values = new ArrayList<>();
                for (final JsonNode child : node) {
                    values.add(child.isNull() ? null : child.asText());
                }
                rows.add(Collections.singletonMap(prefix, String.join(", ", values))); //If primitive array, then no need to add `[]` as suffix on name. Also its values joined with comma.
            } else {
                // Array of objects -> flatten each element recursively
                for (final JsonNode child : node) {
                    final String newPrefix = prefix + "[]";
                    rows.addAll(extractFieldValues(newPrefix, child));
                }
            }
            return rows;
        }

        // Case 2: Primitive node
        if (node.isValueNode()) {
            final Map<String, Object> single = new LinkedHashMap<>();
            single.put(prefix, node.isNull() ? null : ObjectMapperUtils.toJavaValue(node));
            rows.add(single);
            return rows;
        }

        // Case 3: Object node
        if (node.isObject()) {
            List<Map<String, Object>> resultRows = Collections.singletonList(new LinkedHashMap<>());

            final Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                final String field = fieldNames.next();
                final JsonNode child = node.get(field);
                final String fullName = prefix.isEmpty() ? field : prefix + "." + field;

                final List<Map<String, Object>> childRows = extractFieldValues(fullName, child);
                resultRows = mergeRows(resultRows, childRows);
            }

            rows.addAll(resultRows);
            return rows;
        }

        return rows;
    }

    /**
     * Cross-merge two lists of row-maps. Returns new list (does not mutate inputs).
     * This is a generic Cartesian merge. Use it for merging groups from different parents.
     */
    private List<Map<String, Object>> mergeRows(final List<Map<String, Object>> first,
                                                final List<Map<String, Object>> second) {
        if (first.isEmpty()) {
            return new ArrayList<>(second);
        }
        if (second.isEmpty()) {
            return new ArrayList<>(first);
        }
        final List<Map<String, Object>> merged = new ArrayList<>();
        for (final Map<String, Object> a : first) {
            for (final Map<String, Object> b : second) {
                final Map<String, Object> comb = new LinkedHashMap<>(a);
                comb.putAll(b);
                merged.add(comb);
            }
        }
        return merged;
    }

    private Map<String, Object> initialiseRowWithFixedField(final List<FieldDefinition> fieldDefinitions) {
        final LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        if (fieldDefinitions == null) {
            return map;
        }

        for (final FieldDefinition fd : fieldDefinitions) {
            if (fd.getName() != null) { // ignore null keys. It never happen normally
                map.put(fd.getName(), null); 
            }
        }
        return map;
    }
}
