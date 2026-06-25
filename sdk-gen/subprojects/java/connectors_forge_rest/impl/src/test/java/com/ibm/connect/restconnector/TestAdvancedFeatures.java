/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.ibm.wdp.connect.sdk.connector.forge.PaginationConfig;
import com.ibm.wdp.connect.sdk.connector.forge.RestApiMapping;
import com.ibm.wdp.connect.sdk.connector.forge.RestApiMappingLoader;
import com.ibm.wdp.connect.sdk.connector.forge.RestFieldDefinition;
import com.ibm.wdp.connect.sdk.connector.forge.RestTableDefinition;

/**
 * Tests for advanced REST connector features including authentication,
 * pagination, data paths, and object flattening.
 */
public class TestAdvancedFeatures
{
    /**
     * Test parsing authentication configuration - none type.
     */
    @Test
    public void testAuthenticationNone() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$authentication\": \"none\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"id\": \"VARCHAR,$key\"\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        assertEquals("none", mapping.getAuthenticationType());
    }

    /**
     * Test parsing authentication configuration - api_key type.
     */
    @Test
    public void testAuthenticationApiKey() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$authentication\": \"api_key\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"id\": \"VARCHAR,$key\"\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        assertEquals("api_key", mapping.getAuthenticationType());
    }

    /**
     * Test parsing authentication configuration - oauth2 type.
     */
    @Test
    public void testAuthenticationOAuth2() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$authentication\": \"oauth2\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"id\": \"VARCHAR,$key\"\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        assertEquals("oauth2", mapping.getAuthenticationType());
    }

    /**
     * Test parsing authentication configuration - basic type.
     */
    @Test
    public void testAuthenticationBasic() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$authentication\": \"basic\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"id\": \"VARCHAR,$key\"\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        assertEquals("basic", mapping.getAuthenticationType());
    }

    /**
     * Test parsing data path configuration.
     */
    @Test
    public void testDataPath() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"$data_path\": \"data.users\",\n"
                + "      \"id\": \"VARCHAR,$key\",\n"
                + "      \"name\": \"VARCHAR\"\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestTableDefinition table = mapping.getTable("USERS");
        assertNotNull(table);
        assertEquals("data.users", table.getDataPath());
    }

    /**
     * Test parsing pagination configuration - offset type.
     */
    @Test
    public void testPaginationOffset() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"$pagination\": {\n"
                + "        \"type\": \"offset\",\n"
                + "        \"offset_param\": \"offset\",\n"
                + "        \"limit_param\": \"limit\",\n"
                + "        \"page_size\": 100\n"
                + "      },\n"
                + "      \"id\": \"VARCHAR,$key\"\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestTableDefinition table = mapping.getTable("USERS");
        final PaginationConfig pagination = table.getPaginationConfig();
        
        assertNotNull(pagination);
        assertEquals("offset", pagination.getType());
        assertEquals("offset", pagination.getOffsetParam());
        assertEquals("limit", pagination.getLimitParam());
        assertEquals(100, pagination.getPageSize());
    }

    /**
     * Test parsing pagination configuration - page type.
     */
    @Test
    public void testPaginationPage() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"$pagination\": {\n"
                + "        \"type\": \"page\",\n"
                + "        \"page_param\": \"page\",\n"
                + "        \"limit_param\": \"per_page\",\n"
                + "        \"page_size\": 50,\n"
                + "        \"initial_page\": 1\n"
                + "      },\n"
                + "      \"id\": \"VARCHAR,$key\"\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestTableDefinition table = mapping.getTable("USERS");
        final PaginationConfig pagination = table.getPaginationConfig();
        
        assertNotNull(pagination);
        assertEquals("page", pagination.getType());
        assertEquals("page", pagination.getPageParam());
        assertEquals("per_page", pagination.getLimitParam());
        assertEquals(50, pagination.getPageSize());
        assertEquals(1, pagination.getInitialPage());
    }

    /**
     * Test parsing pagination configuration - cursor type.
     */
    @Test
    public void testPaginationCursor() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"$pagination\": {\n"
                + "        \"type\": \"cursor\",\n"
                + "        \"cursor_param\": \"cursor\",\n"
                + "        \"limit_param\": \"limit\",\n"
                + "        \"page_size\": 100,\n"
                + "        \"next_cursor_path\": \"pagination.next_cursor\"\n"
                + "      },\n"
                + "      \"id\": \"VARCHAR,$key\"\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestTableDefinition table = mapping.getTable("USERS");
        final PaginationConfig pagination = table.getPaginationConfig();
        
        assertNotNull(pagination);
        assertEquals("cursor", pagination.getType());
        assertEquals("cursor", pagination.getCursorParam());
        assertEquals("pagination.next_cursor", pagination.getNextCursorPath());
    }

    /**
     * Test parsing pagination configuration - link_header type.
     */
    @Test
    public void testPaginationLinkHeader() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"$pagination\": {\n"
                + "        \"type\": \"link_header\"\n"
                + "      },\n"
                + "      \"id\": \"VARCHAR,$key\"\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestTableDefinition table = mapping.getTable("USERS");
        final PaginationConfig pagination = table.getPaginationConfig();
        
        assertNotNull(pagination);
        assertEquals("link_header", pagination.getType());
    }

    /**
     * Test parsing pagination configuration - next_url type.
     */
    @Test
    public void testPaginationNextUrl() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"$pagination\": {\n"
                + "        \"type\": \"next_url\",\n"
                + "        \"next_url_path\": \"links.next\"\n"
                + "      },\n"
                + "      \"id\": \"VARCHAR,$key\"\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestTableDefinition table = mapping.getTable("USERS");
        final PaginationConfig pagination = table.getPaginationConfig();
        
        assertNotNull(pagination);
        assertEquals("next_url", pagination.getType());
        assertEquals("links.next", pagination.getNextUrlPath());
    }

    /**
     * Test parsing object flattening with [] syntax.
     */
    @Test
    public void testObjectFlattening() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"id\": \"VARCHAR,$key\",\n"
                + "      \"name\": \"VARCHAR\",\n"
                + "      \"address[]\": {\n"
                + "        \"street\": \"VARCHAR\",\n"
                + "        \"city\": \"VARCHAR\",\n"
                + "        \"country\": \"VARCHAR\"\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestTableDefinition table = mapping.getTable("USERS");
        final List<RestFieldDefinition> fields = table.getFields();
        
        // Should have id, name, and flattened address fields (street, city, country)
        assertEquals(5, fields.size());
        assertEquals("id", fields.get(0).getName());
        assertEquals("name", fields.get(1).getName());
        assertEquals("address.street", fields.get(2).getName());
        assertEquals("address.city", fields.get(3).getName());
        assertEquals("address.country", fields.get(4).getName());
    }

    /**
     * Test parsing multi-level object flattening.
     */
    @Test
    public void testMultiLevelObjectFlattening() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"id\": \"VARCHAR,$key\",\n"
                + "      \"profile[]\": {\n"
                + "        \"first_name\": \"VARCHAR\",\n"
                + "        \"last_name\": \"VARCHAR\",\n"
                + "        \"contact[]\": {\n"
                + "          \"email\": \"VARCHAR\",\n"
                + "          \"phone\": \"VARCHAR\"\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestTableDefinition table = mapping.getTable("USERS");
        final List<RestFieldDefinition> fields = table.getFields();
        
        // Should have id and flattened profile fields (first_name, last_name, contact.email, contact.phone)
        assertEquals(5, fields.size());
        assertEquals("id", fields.get(0).getName());
        assertEquals("profile.first_name", fields.get(1).getName());
        assertEquals("profile.last_name", fields.get(2).getName());
        assertEquals("profile.contact.email", fields.get(3).getName());
        assertEquals("profile.contact.phone", fields.get(4).getName());
    }

    /**
     * Test table without pagination (should be null).
     */
    @Test
    public void testNoPagination() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"id\": \"VARCHAR,$key\"\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestTableDefinition table = mapping.getTable("USERS");
        assertNull(table.getPaginationConfig());
    }

    /**
     * Test table without data path (should be null).
     */
    @Test
    public void testNoDataPath() throws Exception
    {
        final String json = "{\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/users\"],\n"
                + "      \"id\": \"VARCHAR,$key\"\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestTableDefinition table = mapping.getTable("USERS");
        assertNull(table.getDataPath());
    }

    /**
     * Test complete configuration with all features.
     */
    @Test
    public void testCompleteConfiguration() throws Exception
    {
        final String json = "{\n"
                + "  \"$connector_name\": \"Test API\",\n"
                + "  \"$connector_label\": \"Test API Connector\",\n"
                + "  \"$connector_description\": \"A test connector\",\n"
                + "  \"$hostname\": \"https://api.example.com\",\n"
                + "  \"$authentication\": \"api_key\",\n"
                + "  \"$tables\": {\n"
                + "    \"USERS\": {\n"
                + "      \"$path\": [\"/v1/users\"],\n"
                + "      \"$data_path\": \"data.users\",\n"
                + "      \"$pagination\": {\n"
                + "        \"type\": \"offset\",\n"
                + "        \"offset_param\": \"offset\",\n"
                + "        \"limit_param\": \"limit\",\n"
                + "        \"page_size\": 100\n"
                + "      },\n"
                + "      \"id\": \"VARCHAR,$key\",\n"
                + "      \"name\": \"VARCHAR\",\n"
                + "      \"address[]\": {\n"
                + "        \"city\": \"VARCHAR\",\n"
                + "        \"country\": \"VARCHAR\"\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        
        // Verify connector metadata
        assertEquals("Test API", mapping.getConnectorName());
        assertEquals("Test API Connector", mapping.getConnectorLabel());
        assertEquals("A test connector", mapping.getConnectorDescription());
        assertEquals("https://api.example.com", mapping.getBaseUrl());
        assertEquals("api_key", mapping.getAuthenticationType());
        
        // Verify table configuration
        final RestTableDefinition table = mapping.getTable("USERS");
        assertNotNull(table);
        assertEquals("/v1/users", table.getPath());
        assertEquals("data.users", table.getDataPath());
        
        // Verify pagination
        final PaginationConfig pagination = table.getPaginationConfig();
        assertNotNull(pagination);
        assertEquals("offset", pagination.getType());
        assertEquals(100, pagination.getPageSize());
        
        // Verify fields including flattened objects
        final List<RestFieldDefinition> fields = table.getFields();
        assertEquals(4, fields.size());
        assertTrue(fields.get(0).isKey());
        assertEquals("address.city", fields.get(2).getName());
        assertEquals("address.country", fields.get(3).getName());
    }
}

// Made with Bob