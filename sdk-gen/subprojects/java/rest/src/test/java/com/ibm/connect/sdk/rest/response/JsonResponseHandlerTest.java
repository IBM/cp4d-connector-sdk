/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.Assert;
import org.junit.Test;

import com.ibm.connect.sdk.rest.response.impl.JsonResponseHandler;
import com.ibm.connect.sdk.rest.utils.models.EntityType;
import com.ibm.connect.sdk.rest.utils.models.FieldDefinition;
import com.ibm.connect.sdk.rest.utils.models.ResponseFieldConfig;
import com.ibm.connect.sdk.test.helper.RestTestUtils;

public class JsonResponseHandlerTest {

    @Test
    public void testJsonResponseHandlerWithNoFieldConfig() throws IOException {
        final String responseBody = "{\n" +
                "  \"users\": [\n" +
                "    { \"name\": \"Tom\", \"age\": 30 },\n" +
                "    { \"name\": \"John\", \"age\": 25 }\n" +
                "  ],\n" +
                "  \"metadata\": { \"count\": 2 }\n" +
                "}\n";

        final String expectedColumns = "users[].name, users[].age, metadata.count";
        final EntityType entityType = new EntityType();
        entityType.setName("entityName");
        final JsonResponseHandler jsonResponseHandler = new JsonResponseHandler(createHttpResponse(responseBody), entityType);
        Assert.assertNotNull(jsonResponseHandler.getFieldDefinitions());

        Assert.assertEquals(3, jsonResponseHandler.getFieldDefinitions().size());
        Assert.assertEquals(expectedColumns, jsonResponseHandler.getFieldDefinitions().stream()
                                                        .map(FieldDefinition::getName)
                                                        .collect(Collectors.joining(", ")));

        final List<Map<String, Object>> fieldValueMap = jsonResponseHandler.getFieldValueMap();
        Assert.assertEquals(2, fieldValueMap.size());
        Assert.assertEquals(expectedColumns, String.join(", ", fieldValueMap.stream().map(Map::keySet)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new))));
        Assert.assertEquals("Tom, 30, 2, John, 25, null", String.join(", ", fieldValueMap.stream().map(Map::values)
                .flatMap(Collection::stream).map(String::valueOf).collect(Collectors.toCollection(LinkedHashSet::new))));
    }

    @Test
    public void testJsonResponseHandlerWithNoFieldConfigJson2() throws IOException {
        final String responseBody = "{\"name\": \"Tom\", \"age\": 30 }";

        final String expectedColumns = "name, age";
        final EntityType entityType = new EntityType();
        entityType.setName("entityName");
        final JsonResponseHandler jsonResponseHandler = new JsonResponseHandler(createHttpResponse(responseBody), entityType);
        Assert.assertNotNull(jsonResponseHandler.getFieldDefinitions());


        Assert.assertEquals(2, jsonResponseHandler.getFieldDefinitions().size());
        Assert.assertEquals(expectedColumns, jsonResponseHandler.getFieldDefinitions().stream()
                .map(FieldDefinition::getName)
                .collect(Collectors.joining(", ")));

        final List<Map<String, Object>> fieldValueMap = jsonResponseHandler.getFieldValueMap();
        Assert.assertEquals(1, fieldValueMap.size());
        Assert.assertEquals(expectedColumns, String.join(", ", fieldValueMap.stream().map(Map::keySet)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new))));
        Assert.assertEquals("Tom, 30", String.join(", ", fieldValueMap.stream().map(Map::values)
                .flatMap(Collection::stream).map(String::valueOf).collect(Collectors.toCollection(LinkedHashSet::new))));
    }

    @Test
    public void testJsonResponseHandlerWithFieldConfigContainsArray() throws IOException {
        final String responseBody = "{\n" +
                "  \"users\": [\n" +
                "    { \"name\": \"Tom\", \"age\": 30 },\n" +
                "    { \"name\": \"John\", \"age\": 25 }\n" +
                "  ],\n" +
                "  \"metadata\": { \"count\": 2 }\n" +
                "}\n";
        final EntityType entityType = new EntityType();
        entityType.setName("entityName");
        entityType.setResponseFieldConfigs(List.of(new ResponseFieldConfig("usersField","$.users[*]")));
        final JsonResponseHandler jsonResponseHandler = new JsonResponseHandler(createHttpResponse(responseBody), entityType);
        Assert.assertNotNull(jsonResponseHandler.getFieldDefinitions());

        Assert.assertEquals(2, jsonResponseHandler.getFieldDefinitions().size());
        Assert.assertEquals("usersField[].name, usersField[].age", jsonResponseHandler.getFieldDefinitions().stream()
                .map(FieldDefinition::getName)
                .collect(Collectors.joining(", ")));

        final List<Map<String, Object>> fieldValueMap = jsonResponseHandler.getFieldValueMap();
        Assert.assertEquals(2, fieldValueMap.size());
        Assert.assertEquals("usersField[].name, usersField[].age", String.join(", ", fieldValueMap.stream().map(Map::keySet)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new))));
        Assert.assertEquals("Tom, 30, John, 25", String.join(", ", fieldValueMap.stream().map(Map::values)
                .flatMap(Collection::stream).map(String::valueOf).collect(Collectors.toCollection(LinkedHashSet::new))));
    }

    @Test
    public void testJsonResponseHandlerWithFieldConfigContainsProjections() throws IOException {
        final String responseBody = "{\n" +
                "  \"users\": [\n" +
                "    { \"name\": \"Tom\", \"age\": 30, \"postcode\": 30403 },\n" +
                "    { \"name\": \"John\", \"age\": 25, \"postcode\": 37080 }\n" +
                "  ],\n" +
                "  \"metadata\": { \"count\": 2 }\n" +
                "}\n";
        final EntityType entityType = new EntityType();
        entityType.setName("entityName");
        entityType.setResponseFieldConfigs(List.of(new ResponseFieldConfig("user_name","$.users[*].name"),
                                                   new ResponseFieldConfig("user_age","$.users[*].age")));
        final JsonResponseHandler jsonResponseHandler = new JsonResponseHandler(createHttpResponse(responseBody), entityType);
        Assert.assertNotNull(jsonResponseHandler.getFieldDefinitions());

        Assert.assertEquals(2, jsonResponseHandler.getFieldDefinitions().size());
        Assert.assertEquals("user_name, user_age", jsonResponseHandler.getFieldDefinitions().stream()
                .map(FieldDefinition::getName)
                .collect(Collectors.joining(", ")));

        final List<Map<String, Object>> fieldValueMap = jsonResponseHandler.getFieldValueMap();
        Assert.assertEquals(2, fieldValueMap.size());
        Assert.assertEquals("user_name, user_age", String.join(", ", fieldValueMap.stream().map(Map::keySet)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new))));
        Assert.assertEquals("Tom, 30, John, 25", String.join(", ", fieldValueMap.stream().map(Map::values)
                .flatMap(Collection::stream).map(String::valueOf).collect(Collectors.toCollection(LinkedHashSet::new))));
    }

    @Test
    public void testObjectArrayJsonResponseHandlerWithFieldConfig() throws IOException {
        final String responseBody = RestTestUtils.readResourceFile("com/ibm/connect/sdk/rest/response/object_array.json");
        final EntityType entityType = new EntityType();
        entityType.setName("entityName");
        entityType.setResponseFieldConfigs(List.of(new ResponseFieldConfig("users","$.users[*]")));
        final JsonResponseHandler jsonResponseHandler = new JsonResponseHandler(createHttpResponse(responseBody), entityType);
        Assert.assertNotNull(jsonResponseHandler.getFieldDefinitions());

        Assert.assertEquals(12, jsonResponseHandler.getFieldDefinitions().size());
        Assert.assertEquals("users[].id, users[].name, users[].age, users[].address.street, users[].address.city, users[].address.geo.lat, users[].address.geo.lng, users[].roles, users[].isActive, users[].projects[].id, users[].projects[].name, users[].projects[].budget", jsonResponseHandler.getFieldDefinitions().stream()
                .map(FieldDefinition::getName)
                .collect(Collectors.joining(", ")));

        final List<Map<String, Object>> fieldValueMap = jsonResponseHandler.getFieldValueMap();
        Assert.assertEquals(3, fieldValueMap.size()); //Subarrays present on project
        Assert.assertEquals("users[].id, users[].name, users[].age, users[].address.street, users[].address.city, users[].address.geo.lat, users[].address.geo.lng, users[].roles, users[].isActive, users[].projects[].id, users[].projects[].name, users[].projects[].budget", String.join(", ", fieldValueMap.stream().map(Map::keySet)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new))));

        Assert.assertEquals("101, Tom, 30, 123 Main St, New York, 40.7128, -74.006, admin, editor, true, P100, Project A, 5000.75", fieldValueMap.get(0).values().stream().map(String::valueOf).collect(Collectors.joining(", ")));
        Assert.assertEquals("101, Tom, 30, 123 Main St, New York, 40.7128, -74.006, admin, editor, true, P101, Project B, 12000.0", fieldValueMap.get(1).values().stream().map(String::valueOf).collect(Collectors.joining(", ")));
        Assert.assertEquals("102, John, 25, 456 Market St, San Francisco, 37.7749, -122.4194, viewer, false, null, null, null", fieldValueMap.get(2).values().stream().map(String::valueOf).collect(Collectors.joining(", ")));
    }

    public static ClassicHttpResponse createHttpResponse(String body) throws IOException {
        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        try(HttpEntity entity = new StringEntity(body, StandardCharsets.UTF_8)) {
            response.setEntity(entity);
        }
        return response;
    }
}
