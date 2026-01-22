/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.discovery;

import static com.ibm.connect.sdk.rest.utils.RestUtils.mapToFlightAssetFields;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import com.ibm.connect.sdk.rest.httpclient.RestExecutorImpl;
import com.ibm.connect.sdk.rest.request.EntityTypeRequestHandler;
import com.ibm.connect.sdk.rest.response.EntityTypeResponseHandler;
import com.ibm.connect.sdk.rest.utils.ObjectMapperUtils;
import com.ibm.connect.sdk.rest.utils.RestConnectionProperties;
import com.ibm.connect.sdk.rest.utils.RestSourceInteractionProperties;
import com.ibm.connect.sdk.rest.utils.models.EntityType;
import com.ibm.connect.sdk.rest.utils.models.EntityTypeNode;
import com.ibm.connect.sdk.rest.utils.models.FieldDefinition;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetType;

public class RestDiscoveryHandler {
    private final EntityTypeRequestHandler entityTypeRequestHandler;

    public RestDiscoveryHandler(RestExecutorImpl restApiExecutor, RestConnectionProperties restConnectionProperties) {
        this.entityTypeRequestHandler = new EntityTypeRequestHandler(restApiExecutor, restConnectionProperties);
    }

    public List<CustomFlightAssetDescriptor> listContainerAssets(String selectedPath, EntityType discoverEntityType,
                                                                 IntFunction<EntityTypeNode> getNthLevelNode,
                                                                 Integer offset, Integer limit) throws IOException {
        final Map<String, String> entityNameAndSelectedAssetIdMap = calculateSelectedAssetIdentifierFromPath(selectedPath, getNthLevelNode);
        final List<CustomFlightAssetDescriptor> assetDescriptors =  new ArrayList<>();
        final List<EntityTypeResponseHandler> responseHandlers = this.entityTypeRequestHandler.executeRequest(discoverEntityType, entityNameAndSelectedAssetIdMap, offset, limit);
        for(final EntityTypeResponseHandler responseHandler : responseHandlers) {
            final List<Map<String, Object>> flattenedFieldItemsMap = responseHandler.getFieldValueMap();

            for (final Map<String, Object> flattenedFieldItemMap : flattenedFieldItemsMap) {
                final String path = calculateNewPath(selectedPath, discoverEntityType, flattenedFieldItemMap);
                assetDescriptors.add(new CustomFlightAssetDescriptor()
                        .id(String.valueOf(flattenedFieldItemMap.getOrDefault(discoverEntityType.getUniqueIdField(), "")))
                        .name(String.valueOf(flattenedFieldItemMap.getOrDefault(discoverEntityType.getLabelField(), "")))
                        .path(path)
                        .assetType(new DiscoveredAssetType()
                                .type(discoverEntityType.getName())
                                .dataset(false)
                                .datasetContainer(true)));
            }
        }
        return assetDescriptors;
    }

    private static String calculateNewPath(String selectedPath, EntityType discoverEntityType, Map<String, Object> flattenedFieldItemMap) {
        final String suffix = String.valueOf(flattenedFieldItemMap.getOrDefault(discoverEntityType.getUniqueIdField(), ""));
        // Remove trailing slash from selectedPath if present
        if (selectedPath.endsWith("/")) {
            selectedPath = selectedPath.substring(0, selectedPath.length() - 1);
        }
        return selectedPath + "/" + suffix;
    }

    public CustomFlightAssetDescriptor listLeafAsset(String selectedPath, EntityType discoverEntityType, IntFunction<EntityTypeNode> getNthLevelNode) throws IOException {
        final Map<String, String> entityNameAndSelectedAssetIdMap = calculateSelectedAssetIdentifierFromPath(selectedPath, getNthLevelNode);
        final EntityTypeResponseHandler responseHandler = this.entityTypeRequestHandler.executeRequest(discoverEntityType, entityNameAndSelectedAssetIdMap);
        //TODO: known limitation : As this response only for first page, it could only discover fields from first page.
        // This may need to loop for all the pages. Consider configuring 'numOfRecordsRequiredForFieldDiscovery' in configuration to allow users to choose based on there response.
        // Ticket : https://github.ibm.com/wdp-gov/tracker/issues/281955
        final List<FieldDefinition> fieldDefinition = responseHandler.getFieldDefinitions();
        final List<CustomFlightAssetField> fields = mapToFlightAssetFields(fieldDefinition);

        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        interactionProperties.put(RestSourceInteractionProperties.PROPERTIES_SELECTED_PARENT_ENTITY_VALUES, ObjectMapperUtils.toJsonString(entityNameAndSelectedAssetIdMap));

        return new CustomFlightAssetDescriptor()
                .name(discoverEntityType.getName())
                .path(selectedPath)
                .fields(fields)
                .assetType(new DiscoveredAssetType()
                        .type(discoverEntityType.getName())
                        .dataset(true)
                        .datasetContainer(false))
                .interactionProperties(interactionProperties);
    }

    private Map<String, String> calculateSelectedAssetIdentifierFromPath(String path, IntFunction<EntityTypeNode> getNthLevelNode) {
        final Map<String, String> result = new LinkedHashMap<>();

        if (path == null || path.isBlank() || "/".equals(path.trim())) {
            return result; // empty map for root or empty path
        }

        // Normalize path, remove leading/trailing slashes and split
        final String[] parts = Arrays.stream(path.split("/"))
                .filter(p -> !p.isBlank())
                .toArray(String[]::new);

        for (int i = 0; i < parts.length; i++) {
            // Level starts from 1
            final EntityTypeNode node = getNthLevelNode.apply(i + 1);
            if (node != null && node.getName() != null) {
                result.put(node.getName().toLowerCase(java.util.Locale.ROOT), parts[i]);
            }
        }
        return result;
    }
}
