/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * Tests for {@link RestApiMappingLoader}.
 */
public class TestRestApiMappingLoader
{
    /** Minimal .rest JSON for testing */
    private static final String MINIMAL_REST_JSON = "{\n"
            + "  \"$hostname\": \"https://api.example.com\",\n"
            + "  \"$tables\": {\n"
            + "    \"USERS\": {\n"
            + "      \"$path\": [\"/v1/users\"],\n"
            + "      \"id\": \"VARCHAR,$key\",\n"
            + "      \"name\": \"VARCHAR\",\n"
            + "      \"age\": \"INTEGER\",\n"
            + "      \"score\": \"DOUBLE\",\n"
            + "      \"active\": \"BOOLEAN\",\n"
            + "      \"created_at\": \"TIMESTAMP\",\n"
            + "      \"birth_date\": \"DATE\",\n"
            + "      \"balance\": \"BIGINT\",\n"
            + "      \"bio\": \"VARCHAR\",\n"
            + "      \"metadata\": \"JSON\",\n"
            + "      \"tags[]\": {\"tag\": \"VARCHAR\"}\n"
            + "    },\n"
            + "    \"PRODUCTS\": {\n"
            + "      \"$path\": [\"/v1/products\"],\n"
            + "      \"id\": \"VARCHAR,$key\",\n"
            + "      \"title\": \"VARCHAR\"\n"
            + "    }\n"
            + "  }\n"
            + "}";

    /**
     * Test that the base URL is parsed correctly.
     */
    @Test
    public void testBaseUrl() throws Exception
    {
        final RestApiMapping mapping = RestApiMappingLoader.parse(MINIMAL_REST_JSON);
        assertEquals("https://api.example.com", mapping.getBaseUrl());
    }

    /**
     * Test that the correct number of tables is parsed.
     */
    @Test
    public void testTableCount() throws Exception
    {
        final RestApiMapping mapping = RestApiMappingLoader.parse(MINIMAL_REST_JSON);
        assertEquals(2, mapping.getTables().size());
        assertTrue(mapping.getTables().containsKey("USERS"));
        assertTrue(mapping.getTables().containsKey("PRODUCTS"));
    }

    /**
     * Test that the table path is parsed correctly.
     */
    @Test
    public void testTablePath() throws Exception
    {
        final RestApiMapping mapping = RestApiMappingLoader.parse(MINIMAL_REST_JSON);
        assertEquals("/v1/users", mapping.getTable("USERS").getPath());
        assertEquals("/v1/products", mapping.getTable("PRODUCTS").getPath());
    }

    /**
     * Test that field names are parsed correctly.
     */
    @Test
    public void testFieldNames() throws Exception
    {
        final RestApiMapping mapping = RestApiMappingLoader.parse(MINIMAL_REST_JSON);
        final List<RestFieldDefinition> fields = mapping.getTable("USERS").getFields();
        assertEquals("id", fields.get(0).getName());
        assertEquals("name", fields.get(1).getName());
        assertEquals("age", fields.get(2).getName());
        assertEquals("tags.tag", fields.get(10).getName()); // "tags[]" with nested object → "tags.tag"
    }

    /**
     * Test that the $key marker is parsed correctly.
     */
    @Test
    public void testKeyField() throws Exception
    {
        final RestApiMapping mapping = RestApiMappingLoader.parse(MINIMAL_REST_JSON);
        final List<RestFieldDefinition> fields = mapping.getTable("USERS").getFields();
        assertTrue("id should be a key field", fields.get(0).isKey());
        assertFalse("name should not be a key field", fields.get(1).isKey());
    }

    /**
     * Test that the type strings are parsed correctly (without $key marker).
     */
    @Test
    public void testTypeStrings() throws Exception
    {
        final RestApiMapping mapping = RestApiMappingLoader.parse(MINIMAL_REST_JSON);
        final List<RestFieldDefinition> fields = mapping.getTable("USERS").getFields();
        assertEquals("VARCHAR", fields.get(0).getTypeString()); // id: "VARCHAR,$key" → "VARCHAR"
        assertEquals("VARCHAR", fields.get(1).getTypeString());
        assertEquals("INTEGER", fields.get(2).getTypeString());
        assertEquals("DOUBLE", fields.get(3).getTypeString());
        assertEquals("BOOLEAN", fields.get(4).getTypeString());
        assertEquals("TIMESTAMP", fields.get(5).getTypeString());
        assertEquals("DATE", fields.get(6).getTypeString());
        assertEquals("BIGINT", fields.get(7).getTypeString());
        assertEquals("VARCHAR", fields.get(8).getTypeString());
        assertEquals("JSON", fields.get(9).getTypeString());
    }

    /**
     * Test that nested array fields are parsed correctly.
     */
    @Test
    public void testNestedArrayField() throws Exception
    {
        final RestApiMapping mapping = RestApiMappingLoader.parse(MINIMAL_REST_JSON);
        final List<RestFieldDefinition> fields = mapping.getTable("USERS").getFields();
        final RestFieldDefinition tagsField = fields.get(10);
        assertEquals("tags.tag", tagsField.getName());
    }

    /**
     * Test case-insensitive table lookup.
     */
    @Test
    public void testCaseInsensitiveLookup() throws Exception
    {
        final RestApiMapping mapping = RestApiMappingLoader.parse(MINIMAL_REST_JSON);
        assertNotNull(mapping.getTable("users"));
        assertNotNull(mapping.getTable("USERS"));
        assertNotNull(mapping.getTable("Users"));
    }

    /**
     * Test that null is returned for unknown table names.
     */
    @Test
    public void testUnknownTable() throws Exception
    {
        final RestApiMapping mapping = RestApiMappingLoader.parse(MINIMAL_REST_JSON);
        assertNull(mapping.getTable("UNKNOWN_TABLE"));
    }

    /**
     * Test parsing the SpaceX-style .rest file structure.
     */
    @Test
    public void testSpaceXStyleParsing() throws Exception
    {
        final String spacexJson = "{\n"
                + "  \"$hostname\": \"https://api.spacexdata.com\",\n"
                + "  \"$tables\": {\n"
                + "    \"ROCKETS\": {\n"
                + "      \"$path\": [\"/v4/rockets\"],\n"
                + "      \"id\": \"VARCHAR,$key\",\n"
                + "      \"name\": \"VARCHAR\",\n"
                + "      \"active\": \"BOOLEAN\",\n"
                + "      \"cost_per_launch\": \"BIGINT\",\n"
                + "      \"first_flight\": \"DATE\",\n"
                + "      \"height\": \"JSON\",\n"
                + "      \"engines\": \"JSON\"\n"
                + "    },\n"
                + "    \"LAUNCHES\": {\n"
                + "      \"$path\": [\"/v4/launches\"],\n"
                + "      \"id\": \"VARCHAR,$key\",\n"
                + "      \"name\": \"VARCHAR\",\n"
                + "      \"date_utc\": \"TIMESTAMP\",\n"
                + "      \"cores[]\": {\"core\": \"VARCHAR\", \"flight\": \"INTEGER\"}\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(spacexJson);
        assertEquals("https://api.spacexdata.com", mapping.getBaseUrl());
        assertEquals(2, mapping.getTables().size());

        final RestTableDefinition rockets = mapping.getTable("ROCKETS");
        assertNotNull(rockets);
        assertEquals("/v4/rockets", rockets.getPath());
        assertEquals(7, rockets.getFields().size());

        final RestTableDefinition launches = mapping.getTable("LAUNCHES");
        assertNotNull(launches);
        assertEquals("/v4/launches", launches.getPath());
        // id, name, date_utc, cores.core, cores.flight (flattened from cores[])
        assertEquals(5, launches.getFields().size());

        // Verify the flattened object fields
        final RestFieldDefinition coreField = launches.getFields().get(3);
        assertEquals("cores.core", coreField.getName());
        
        final RestFieldDefinition flightField = launches.getFields().get(4);
        assertEquals("cores.flight", flightField.getName());
    }
}

// Made with Bob
