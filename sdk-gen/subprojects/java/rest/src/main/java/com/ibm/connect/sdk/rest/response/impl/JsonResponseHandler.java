/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.response.impl;

import static com.ibm.connect.sdk.rest.response.util.JsonUtils.isNotEmptyArray;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.connect.sdk.rest.response.EntityTypeResponseHandler;
import com.ibm.connect.sdk.rest.response.util.JsonFieldValueExtractorByAutoDiscovery;
import com.ibm.connect.sdk.rest.response.util.JsonFieldValueExtractorByFieldConfig;
import com.ibm.connect.sdk.rest.response.util.JsonUtils;
import com.ibm.connect.sdk.rest.utils.ObjectMapperUtils;
import com.ibm.connect.sdk.rest.utils.models.EntityType;
import com.ibm.connect.sdk.rest.utils.models.FieldDefinition;
import com.ibm.connect.sdk.rest.utils.models.ResponseFieldConfig;
import com.jayway.jsonpath.JsonPath;


public class JsonResponseHandler implements EntityTypeResponseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonResponseHandler.class);

    private final ClassicHttpResponse response;
    private final JsonNode responseJsonNode;
    private final EntityType entityType;
    private List<FieldDefinition> fieldDefinitions;

    public JsonResponseHandler(ClassicHttpResponse response, EntityType entityType) throws IOException {
        try {
            final String responseBody = response.getEntity() != null
                    ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                    : null;
            this.response = response;
            this.responseJsonNode = ObjectMapperUtils.parse(responseBody);
            this.entityType = entityType;
        } catch (ParseException e) {
            throw new RuntimeException("Unable to parse the json response", e);
        }
    }

    @Override
    public List<FieldDefinition> getFieldDefinitions() {
        calculateFieldDefinition();
        return this.fieldDefinitions;
    }

    @Override
    public List<Map<String, Object>> getFieldValueMap() throws IOException {
        final List<ResponseFieldConfig> fieldConfigs = Optional.ofNullable(this.entityType.getResponseFieldConfigs()).orElse(Collections.emptyList());
        final List<FieldDefinition> fieldDefs = this.getFieldDefinitions();
        if(!fieldConfigs.isEmpty()) {
            final JsonFieldValueExtractorByFieldConfig extractor = new JsonFieldValueExtractorByFieldConfig();
            return extractor.extractRows(this.responseJsonNode, fieldConfigs, fieldDefs);
        } else {
            final JsonFieldValueExtractorByAutoDiscovery extractor = new JsonFieldValueExtractorByAutoDiscovery();
            return extractor.extractRows(this.responseJsonNode, fieldDefs);
       }
    }

    @Override
    public List<Header> getHeaders() {
        return Optional.of(this.response)
                .map(ClassicHttpResponse::getHeaders)
                .map(List::of)
                .orElse(Collections.emptyList());
    }

    private void calculateFieldDefinition() {
        if(this.fieldDefinitions == null) {
            final Map<String, FieldDefinition> uniqueFieldsAndDefinitionMap = new LinkedHashMap<>();
            final List<ResponseFieldConfig> fieldConfigs = Optional.ofNullable(this.entityType.getResponseFieldConfigs()).orElse(Collections.emptyList());

            if(fieldConfigs.isEmpty()) {
                // Auto discovery of fields available in JSON
                collectUniqueFieldsRecursive("",  responseJsonNode, uniqueFieldsAndDefinitionMap);
            } else {
                // Discovery based on the field configured in Yaml configuration
                for (final ResponseFieldConfig fieldConfig : fieldConfigs) {
                    try {
                        // Extract subnode based on jsonPath
                        final Object extracted = JsonPath.read(ObjectMapperUtils.toJsonString(responseJsonNode), fieldConfig.getJsonPath());
                        final JsonNode targetNode = ObjectMapperUtils.toJsonNode(extracted);

                        // Perform discovery from that node
                        collectUniqueFieldsRecursive(fieldConfig.getName(), targetNode, uniqueFieldsAndDefinitionMap);
                    } catch (Exception e) {
                        // Gracefully skip invalid or missing paths
                        LOGGER.warn("Skipping field config: {} due to error: {}", fieldConfig.getName(), e.getMessage());
                    }
                }
            }

            this.fieldDefinitions = new ArrayList<>(uniqueFieldsAndDefinitionMap.values());
        }
    }

    private void collectUniqueFieldsRecursive(String prefix, JsonNode node, Map<String, FieldDefinition>  uniqueFields) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(field -> {
                final JsonNode child = node.get(field);
                final String fullName = prefix.isEmpty() ? field : prefix + "." + field;
                // Only add if it's not an object or array of objects
                if (JsonUtils.isPrimitiveOrNotEmptyPrimitiveArray(child)) {
                    uniqueFields.putIfAbsent(fullName, new FieldDefinition(fullName, child.getNodeType().name()));
                }

                // Recurse into nested objects or arrays
                collectUniqueFieldsRecursive(fullName, child, uniqueFields);
            });
        } else if (node.isArray()) {
            if (isNotEmptyArray(node) && JsonUtils.isPrimitiveArray(node)) {
                // Add the array itself if all elements are primitives
                uniqueFields.putIfAbsent(prefix, new FieldDefinition(prefix, node.getNodeType().name()));
            } else {
                // Traverse into non-primitive elements (arrays of objects)
                for (final JsonNode child : node) {
                    collectUniqueFieldsRecursive(prefix + "[]", child, uniqueFields);
                }
            }
        }
    }

}

