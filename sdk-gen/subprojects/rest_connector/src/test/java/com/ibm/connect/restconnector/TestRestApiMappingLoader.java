/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

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
            + "  \"#hostname\": \"https://api.example.com\",\n"
            + "  \"USERS\": {\n"
            + "    \"#path\": [\"/v1/users\"],\n"
            + "    \"id\": \"VarChar(50),#key\",\n"
            + "    \"name\": \"VarChar(200)\",\n"
            + "    \"age\": \"Integer\",\n"
            + "    \"score\": \"Double\",\n"
            + "    \"active\": \"Boolean\",\n"
            + "    \"created_at\": \"Timestamp\",\n"
            + "    \"birth_date\": \"Date\",\n"
            + "    \"balance\": \"BigInt\",\n"
            + "    \"bio\": \"LongVarChar(5000)\",\n"
            + "    \"metadata\": \"JSON\",\n"
            + "    \"tags[]\": {\"tag\": \"VarChar(50)\"}\n"
            + "  },\n"
            + "  \"PRODUCTS\": {\n"
            + "    \"#path\": [\"/v1/products\"],\n"
            + "    \"id\": \"VarChar(50),#key\",\n"
            + "    \"title\": \"VarChar(500)\"\n"
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
        assertEquals("tags", fields.get(10).getName()); // "tags[]" → "tags"
    }

    /**
     * Test that the #key marker is parsed correctly.
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
     * Test that the type strings are parsed correctly (without #key marker).
     */
    @Test
    public void testTypeStrings() throws Exception
    {
        final RestApiMapping mapping = RestApiMappingLoader.parse(MINIMAL_REST_JSON);
        final List<RestFieldDefinition> fields = mapping.getTable("USERS").getFields();
        assertEquals("VarChar(50)", fields.get(0).getTypeString()); // id: "VarChar(50),#key" → "VarChar(50)"
        assertEquals("VarChar(200)", fields.get(1).getTypeString());
        assertEquals("Integer", fields.get(2).getTypeString());
        assertEquals("Double", fields.get(3).getTypeString());
        assertEquals("Boolean", fields.get(4).getTypeString());
        assertEquals("Timestamp", fields.get(5).getTypeString());
        assertEquals("Date", fields.get(6).getTypeString());
        assertEquals("BigInt", fields.get(7).getTypeString());
        assertEquals("LongVarChar(5000)", fields.get(8).getTypeString());
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
        assertEquals("tags", tagsField.getName());
        assertTrue("tags should be a nested array", tagsField.isNestedArray());
        assertEquals("JSON", tagsField.getTypeString());
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
                + "  \"#hostname\": \"https://api.spacexdata.com\",\n"
                + "  \"ROCKETS\": {\n"
                + "    \"#path\": [\"/v4/rockets\"],\n"
                + "    \"id\": \"VarChar(50),#key\",\n"
                + "    \"name\": \"VarChar(100)\",\n"
                + "    \"active\": \"Boolean\",\n"
                + "    \"cost_per_launch\": \"BigInt\",\n"
                + "    \"first_flight\": \"Date\",\n"
                + "    \"height\": \"JSON\",\n"
                + "    \"engines\": \"JSON\"\n"
                + "  },\n"
                + "  \"LAUNCHES\": {\n"
                + "    \"#path\": [\"/v4/launches\"],\n"
                + "    \"id\": \"VarChar(50),#key\",\n"
                + "    \"name\": \"VarChar(200)\",\n"
                + "    \"date_utc\": \"Timestamp\",\n"
                + "    \"cores[]\": {\n"
                + "      \"core\": \"VarChar(50)\",\n"
                + "      \"flight\": \"Integer\"\n"
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
        // id, name, date_utc, cores (nested array)
        assertEquals(4, launches.getFields().size());

        // Verify the nested array field
        final RestFieldDefinition coresField = launches.getFields().get(3);
        assertEquals("cores", coresField.getName());
        assertTrue(coresField.isNestedArray());
        assertEquals("JSON", coresField.getTypeString());
    }
}

// Made with Bob
