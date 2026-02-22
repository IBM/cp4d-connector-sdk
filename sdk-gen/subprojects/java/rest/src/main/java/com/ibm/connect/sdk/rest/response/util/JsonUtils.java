/*
  ***************************************************

  (C) Copyright IBM Corp. 2026

  ***************************************************
 */
package com.ibm.connect.sdk.rest.response.util;

import com.fasterxml.jackson.databind.JsonNode;

public class JsonUtils {

    public static Object extractPrimitiveValue(JsonNode node) {
        if (node.isInt()) {
            return node.intValue();
        }
        if (node.isLong()) {
            return node.longValue();
        }
        if (node.isDouble()) {
            return node.doubleValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        return node.toString();
    }

    public static boolean isPrimitiveOrNotEmptyPrimitiveArray(final JsonNode node) {
        return node != null && (node.isValueNode() || (isNotEmptyArray(node) && isPrimitiveArray(node)));
    }

    public static boolean isPrimitiveArray(final JsonNode node) {
        if (node == null || !node.isArray()) {
            return false;
        }
        for (final JsonNode child : node) {
            if (!child.isValueNode()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotEmptyArray(final JsonNode node) {
        return node != null && node.isArray() && node.size() > 0;
    }
}
