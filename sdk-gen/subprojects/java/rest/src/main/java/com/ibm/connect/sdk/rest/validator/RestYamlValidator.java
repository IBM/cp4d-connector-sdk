/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.validator;

import static com.ibm.connect.sdk.rest.RestMsgs.INTERNAL_ERROR;
import static com.ibm.connect.sdk.rest.RestMsgs.INVALID_YAML_CONFIG;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ibm.connect.sdk.rest.utils.RestUtils;
import com.ibm.connect.sdk.rest.utils.models.EntityType;
import com.ibm.connect.sdk.rest.utils.models.RestConfiguration;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

public class RestYamlValidator {
    public static final String REST_CONFIGURATION_SCHEMA_RESOURCE_PATH = "com/ibm/connect/sdk/rest/schema/rest-configuration-schema.json";

    public static void validateSchema(String restConfigYaml) {
        try {
            final ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
            final JsonNode yamlNode = yamlReader.readTree(restConfigYaml);

            // Parse JSON Schema
            try (InputStream schemaStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(REST_CONFIGURATION_SCHEMA_RESOURCE_PATH)) {
                if (schemaStream == null) {
                    throw new IllegalArgumentException(INTERNAL_ERROR.format());
                }
                final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
                final JsonSchema schema = schemaFactory.getSchema(schemaStream);
                final Set<ValidationMessage>  errors = schema.validate(yamlNode);
                if (!errors.isEmpty()) {
                    final String combined = errors.stream()
                            .map(ValidationMessage::getMessage)
                            .collect(Collectors.joining("; "));
                    throw new IllegalArgumentException(INVALID_YAML_CONFIG.format(combined));
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(INVALID_YAML_CONFIG.format(e.getMessage()), e);
        }
    }

    public static void validateEntityTypeTree(RestConfiguration restConfiguration) {
        final List<EntityType> entities = Optional.ofNullable(restConfiguration.getEntityTypes())
                .orElseThrow(() -> new IllegalArgumentException("entityTypes must not be null or empty"));
        final Set<String> allEntityNames = new HashSet<>();
        final Map<String, String> parentMap = new HashMap<>();

        if (entities.isEmpty()) {
            throw new IllegalArgumentException("entityTypes must not be empty");
        }

        //Rule 1: Ensure config contain only one root entity
        final long rootCount = entities.stream()
                .filter(e -> e.getParentEntity() == null)
                .count();

        if (rootCount == 0) {
            throw new IllegalArgumentException("At least one root entity (with null parentEntity) must exist");
        }
        if (rootCount > 1) {
            throw new IllegalArgumentException("Multiple root entities found — only one is allowed");
        }

        //Rule 2: Avoid duplicate entity names
        for (final EntityType entity : entities) {
            final String name = Optional.ofNullable(entity.getName())
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .orElseThrow(() -> new IllegalArgumentException("Entity name must not be null or empty."));

            if (!allEntityNames.add(name)) {
                throw new IllegalArgumentException("Duplicate entity name found: " + name);
            }
            parentMap.put(name, entity.getParentEntity());
        }

        //Rule 3: Parent existence and cycle check
        checkParentExistenceAndCycleCheck(entities, allEntityNames, parentMap);

        //Rule 4: Ensure the dynamic parameter with entity name present on endpoint Path and requestQueryParams. (TODO: Extend also for Request body if any)
        validateDynamicParameters(entities, allEntityNames);

    }

    private static void checkParentExistenceAndCycleCheck(List<EntityType> entities, Set<String> allEntityNames, Map<String, String> parentMap) {
        for (final EntityType entity : entities) {
            final String current = entity.getName();
            final String parent = entity.getParentEntity();

            if (parent == null) {
                continue; // root entity — skip checks
            }

            if (!allEntityNames.contains(parent)) {
                throw new IllegalArgumentException("Parent entity " + parent + " of " + current + " does not exist");
            }

            detectCycle(current, parent, parentMap);
        }
    }

    private static void validateDynamicParameters(List<EntityType> entities, Set<String> allEntityNames) {
        for (final EntityType entity : entities) {
            final Set<String> dynamicParams = new HashSet<>();
            if (!RestUtils.isNullOrEmpty(entity.getEndpointPath())) {
                dynamicParams.addAll(RestUtils.extractPlaceholders(entity.getEndpointPath()));
            }
            if (entity.getRequestQueryParams() != null) {
                entity.getRequestQueryParams().values().forEach(value -> {
                    dynamicParams.addAll(RestUtils.extractPlaceholders(value));
                });
            }

            if (!allEntityNames.containsAll(dynamicParams)) {
                final Set<String> missing = new HashSet<>(dynamicParams);
                missing.removeAll(allEntityNames);
                throw new IllegalArgumentException("Following entity names are not found, it used as dynamic param on 'endpoint' or `requestQueryParam`: " + missing);
            }
        }
    }

    private static void detectCycle(String current, String parent, Map<String, String> parentMap) {
        final Set<String> visited = new HashSet<>();
        String node = current;
        while (parent != null) {
            if (!visited.add(node)) {
                throw new IllegalArgumentException("Cycle detected in hierarchy at entity: " + node);
            }
            node = parent;
            parent = parentMap.get(parent);
        }
    }

}
