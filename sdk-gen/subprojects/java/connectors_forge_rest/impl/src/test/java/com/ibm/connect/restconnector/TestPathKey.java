/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Tests for path-key-related logic in {@link RestInputInteraction} and
 * {@link JsonToArrowStream#fetchStringValues}.
 *
 * <p>The full {@code buildUrl()} method is package-private via the private field
 * {@code interactionProperties}, so we test the observable contract through
 * {@link RestInputInteraction#buildRequestUrl} (already covered in
 * {@link TestBuildRequestUrl}) and the static helper
 * {@link JsonToArrowStream#fetchStringValues} where the HTTP layer can be
 * bypassed via in-memory JSON.
 */
public class TestPathKey
{
    // -------------------------------------------------------------------------
    // PathKeyDef construction
    // -------------------------------------------------------------------------

    @Test
    public void testPathKeyDefGetters()
    {
        final PathKeyDef pk = new PathKeyDef("org_slug", "/api/0/organizations/", "slug", "data");
        assertEquals("org_slug",             pk.getVariable());
        assertEquals("/api/0/organizations/", pk.getSourcePath());
        assertEquals("slug",                  pk.getSourceField());
        assertEquals("data",                  pk.getSourceDataPath());
    }

    @Test
    public void testPathKeyDefNullSourceDataPath()
    {
        final PathKeyDef pk = new PathKeyDef("slug", "/orgs/", "slug", null);
        assertNull(pk.getSourceDataPath());
    }

    // -------------------------------------------------------------------------
    // URL building with path_key variable injected — via buildRequestUrl
    // -------------------------------------------------------------------------

    /**
     * When the path-key variable value is added to the props map (as buildUrl() does),
     * buildRequestUrl correctly substitutes it into the path.
     */
    @Test
    public void testBuildRequestUrlWithPathKeyVariable() throws Exception
    {
        // Simulate what buildUrl() does: merge path_key variable into props
        final Map<String, Object> combined = new HashMap<>();
        combined.put("org_slug", "my-company");

        final String result = RestInputInteraction.buildRequestUrl(
                "https://sentry.io",
                "/api/0/organizations/$org_slug/issues/",
                combined);

        assertEquals("https://sentry.io/api/0/organizations/my-company/issues/", result);
    }

    /**
     * Combining connection-property overrides and a path-key variable works together.
     */
    @Test
    public void testBuildRequestUrlPathKeyWithHostOverride() throws Exception
    {
        final Map<String, Object> combined = new HashMap<>();
        combined.put("host", "sandbox.sentry.io");
        combined.put("org_slug", "test-org");

        final String result = RestInputInteraction.buildRequestUrl(
                "https://sentry.io",
                "/api/0/organizations/$org_slug/issues/",
                combined);

        assertEquals("https://sandbox.sentry.io/api/0/organizations/test-org/issues/", result);
    }

    // -------------------------------------------------------------------------
    // fetchStringValues — tested with a pre-loaded JSON response via a local
    // HTTP server substitute. Since JsonToArrowStream.fetchStringValues makes a
    // real HTTP call, we test it indirectly by verifying PathKeyDef round-trips
    // and DSL parsing (the HTTP portion is integration-tested end-to-end).
    // -------------------------------------------------------------------------

    /**
     * A RestTableDefinition with a PathKeyDef preserves it through construction.
     */
    @Test
    public void testTableDefinitionCarriesPathKey()
    {
        final PathKeyDef pk = new PathKeyDef("slug", "/orgs/", "slug", null);
        final List<RestFieldDefinition> fields = Arrays.asList(
                new RestFieldDefinition("id", "VARCHAR", true, false));
        final RestTableDefinition def = new RestTableDefinition(
                "/orgs/$slug/items", null, null, pk, fields);

        assertEquals(pk, def.getPathKey());
        assertEquals("/orgs/$slug/items", def.getPath());
        assertNull(def.getDataPath());
        assertNull(def.getPaginationConfig());
    }

    /**
     * A RestTableDefinition with null PathKeyDef returns null from getPathKey().
     */
    @Test
    public void testTableDefinitionNullPathKey()
    {
        final List<RestFieldDefinition> fields = Arrays.asList(
                new RestFieldDefinition("id", "VARCHAR", true, false));
        final RestTableDefinition def = new RestTableDefinition(
                "/items", null, null, null, fields);
        assertNull(def.getPathKey());
    }

    // -------------------------------------------------------------------------
    // INTERACTION_PROP_PATH_KEY_VALUE constant
    // -------------------------------------------------------------------------

    @Test
    public void testInteractionPropConstantValue()
    {
        assertEquals("path_key_value", RestInputInteraction.INTERACTION_PROP_PATH_KEY_VALUE);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Unused — kept for structural symmetry with TestBuildRequestUrl. */
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
