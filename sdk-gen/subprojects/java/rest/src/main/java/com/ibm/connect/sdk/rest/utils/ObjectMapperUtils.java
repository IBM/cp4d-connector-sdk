/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.utils;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Utility class to provide a globally configured Jackson ObjectMapper
 * and helper methods for JSON serialization and deserialization.
 * <p>
 * This class is designed for reuse across the application.
 * The ObjectMapper instance is thread-safe and immutable after initialization.
 */
public final class ObjectMapperUtils {

    // Singleton ObjectMapper instance with default configurations
    private static final ObjectMapper MAPPER = createDefaultMapper();

    // Private constructor to prevent instantiation
    private ObjectMapperUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Provides the globally shared ObjectMapper instance.
     *
     * @return configured ObjectMapper
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     * Converts an arbitrary object into a Jackson JsonNode.
     *
     * @param obj object to convert
     * @return JsonNode representing the object
     */
    public static JsonNode toJsonNode(Object obj) {
        return MAPPER.valueToTree(obj);
    }

    /**
     * Converts a JSON string into a JsonNode.
     *
     * @param json JSON string
     * @return JsonNode
     * @throws JsonProcessingException if parsing fails
     */
    public static JsonNode parse(String json) throws JsonProcessingException {
        return MAPPER.readTree(json);
    }

    /**
     * Converts an object to its JSON string representation.
     *
     * @param obj object to serialize
     * @return JSON string
     * @throws JsonProcessingException if serialization fails
     */
    public static String toJsonString(Object obj) throws JsonProcessingException {
        return MAPPER.writeValueAsString(obj);
    }


    /**
     * Converts a JSON string into a HashMap<String, String>.
     *
     * @param jsonString the JSON string to convert
     * @return HashMap<String, String> containing JSON key-value pairs
     * @throws RuntimeException if parsing fails
     */
    public static Map<String, String> toStringMap(String jsonString) throws JsonProcessingException {
        if (jsonString == null || jsonString.isEmpty()) {
            return new HashMap<>();
        }

        return MAPPER.readValue(
                jsonString, new TypeReference<HashMap<String, String>>() {}
        );
    }

    public static Object toJavaValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        // Converts JsonNode → Boolean / Number / String / Map / List / byte[] automatically
        return MAPPER.convertValue(node, Object.class);
    }

    /**
     * Creates a default ObjectMapper with sensible configurations.
     *
     * @return configured ObjectMapper
     */
    private static ObjectMapper createDefaultMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
