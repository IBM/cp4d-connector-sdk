/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Unit tests for {@link RestInputInteraction#buildRequestUrl(String, String, Map)}.
 *
 * <p>The method is package-private to allow direct testing without a full connector stack.
 * Each test exercises a single dimension of the host/port override logic.
 */
public class TestBuildRequestUrl
{
    // -------------------------------------------------------------------------
    // Baseline — no overrides, everything from $hostname
    // -------------------------------------------------------------------------

    /**
     * No connection-property overrides: host and port are taken from baseUrl.
     */
    @Test
    public void testNoOverrides() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com", "/users", Collections.emptyMap());
        assertEquals("https://api.example.com/users", result);
    }

    /**
     * No overrides, baseUrl has an explicit port.
     */
    @Test
    public void testNoOverridesWithExplicitPort() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com:8443", "/items", Collections.emptyMap());
        assertEquals("https://api.example.com:8443/items", result);
    }

    /**
     * No overrides, baseUrl has a path prefix (e.g. "/api/v1").
     */
    @Test
    public void testNoOverridesWithPathPrefix() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com/api/v1", "/products", Collections.emptyMap());
        assertEquals("https://api.example.com/api/v1/products", result);
    }

    // -------------------------------------------------------------------------
    // Host override
    // -------------------------------------------------------------------------

    /**
     * Supplying a "host" property overrides the host from baseUrl.
     */
    @Test
    public void testHostOverride() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com", "/users", props("host", "staging.example.com"));
        assertEquals("https://staging.example.com/users", result);
    }

    /**
     * A blank "host" property is ignored — falls back to baseUrl host.
     */
    @Test
    public void testBlankHostIgnored() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com", "/users", props("host", "   "));
        assertEquals("https://api.example.com/users", result);
    }

    /**
     * An empty-string "host" property is ignored — falls back to baseUrl host.
     */
    @Test
    public void testEmptyHostIgnored() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com", "/users", props("host", ""));
        assertEquals("https://api.example.com/users", result);
    }

    // -------------------------------------------------------------------------
    // Port override
    // -------------------------------------------------------------------------

    /**
     * Supplying a "port" property overrides the port from baseUrl (which has no explicit port).
     */
    @Test
    public void testPortOverride() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com", "/data", props("port", "9443"));
        assertEquals("https://api.example.com:9443/data", result);
    }

    /**
     * Port override replaces an explicit port already in baseUrl.
     */
    @Test
    public void testPortOverridesExistingPort() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com:8080", "/data", props("port", "9090"));
        assertEquals("https://api.example.com:9090/data", result);
    }

    /**
     * A blank "port" property is ignored — falls back to baseUrl port.
     */
    @Test
    public void testBlankPortIgnored() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com:8443", "/data", props("port", "  "));
        assertEquals("https://api.example.com:8443/data", result);
    }

    /**
     * An empty-string "port" property is ignored — falls back to baseUrl port.
     */
    @Test
    public void testEmptyPortIgnored() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com:8443", "/data", props("port", ""));
        assertEquals("https://api.example.com:8443/data", result);
    }

    // -------------------------------------------------------------------------
    // Both host and port overridden
    // -------------------------------------------------------------------------

    /**
     * Both host and port overrides applied together.
     */
    @Test
    public void testHostAndPortOverride() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com", "/users",
                props("host", "dev.example.com", "port", "7070"));
        assertEquals("https://dev.example.com:7070/users", result);
    }

    /**
     * Host override combined with port already in baseUrl (no port property).
     * The port from baseUrl is preserved.
     */
    @Test
    public void testHostOverridePreservesConfigPort() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com:9443", "/data", props("host", "alt.example.com"));
        assertEquals("https://alt.example.com:9443/data", result);
    }

    // -------------------------------------------------------------------------
    // Path prefix preserved
    // -------------------------------------------------------------------------

    /**
     * When both host is overridden and baseUrl has a path prefix, the path prefix is kept.
     */
    @Test
    public void testHostOverridePreservesPathPrefix() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com/api/v2", "/orders",
                props("host", "staging.example.com"));
        assertEquals("https://staging.example.com/api/v2/orders", result);
    }

    /**
     * Full override: host, port, path prefix from baseUrl and table path all combine correctly.
     */
    @Test
    public void testFullOverrideWithPathPrefix() throws Exception
    {
        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com/v1", "/products",
                props("host", "dev.example.com", "port", "8080"));
        assertEquals("https://dev.example.com:8080/v1/products", result);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a {@code Map<String, Object>} from alternating key/value pairs. */
    private static Map<String, Object> props(Object... keyValues)
    {
        final Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }
}

// Made with Bob
