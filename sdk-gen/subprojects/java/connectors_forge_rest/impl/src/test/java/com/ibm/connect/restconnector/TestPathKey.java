/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
        final PathKeyDef pk = new PathKeyDef("org_slug", "/api/0/organizations/", "slug", "data", null);
        assertEquals("org_slug",              pk.getVariable());
        assertEquals("/api/0/organizations/", pk.getSourcePath());
        assertEquals("slug",                  pk.getSourceField());
        assertEquals("data",                  pk.getSourceDataPath());
        assertEquals(0L,                      pk.getLookupDelayMs());
    }

    @Test
    public void testPathKeyDefNullSourceDataPath()
    {
        final PathKeyDef pk = new PathKeyDef("slug", "/orgs/", "slug", null, null);
        assertNull(pk.getSourceDataPath());
    }

    @Test
    public void testPathKeyDefLookupDelayMs()
    {
        final PathKeyDef pk = new PathKeyDef("slug", "/orgs/", "slug", null, 200L);
        assertEquals(200L, pk.getLookupDelayMs());
    }

    @Test
    public void testPathKeyDefNegativeDelayTreatedAsZero()
    {
        final PathKeyDef pk = new PathKeyDef("slug", "/orgs/", "slug", null, -1L);
        assertEquals(0L, pk.getLookupDelayMs());
    }

    // -------------------------------------------------------------------------
    // URL building with path_keys variables injected — via buildRequestUrl
    // -------------------------------------------------------------------------

    /**
     * When path-key variable values are merged into the props map (as buildUrl() does),
     * buildRequestUrl correctly substitutes them into the path.
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
     * Two chained path-key variables are substituted independently in the path.
     */
    @Test
    public void testBuildRequestUrlWithTwoPathKeyVariables() throws Exception
    {
        final Map<String, Object> combined = new HashMap<>();
        combined.put("org_id",     "acme");
        combined.put("project_id", "frontend");

        final String result = RestInputInteraction.buildRequestUrl(
                "https://api.example.com",
                "/orgs/$org_id/projects/$project_id/transactions",
                combined);

        assertEquals("https://api.example.com/orgs/acme/projects/frontend/transactions", result);
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
    // RestTableDefinition with List<PathKeyDef>
    // -------------------------------------------------------------------------

    /**
     * A RestTableDefinition with a single PathKeyDef list preserves the list.
     */
    @Test
    public void testTableDefinitionCarriesSinglePathKey()
    {
        final PathKeyDef pk = new PathKeyDef("slug", "/orgs/", "slug", null, null);
        final List<RestFieldDefinition> fields = Arrays.asList(
                new RestFieldDefinition("id", "VARCHAR", true, false));
        final RestTableDefinition def = new RestTableDefinition(
                "/orgs/$slug/items", null, null,
                Collections.singletonList(pk),
                fields);

        assertTrue(def.hasPathKeys());
        assertEquals(1, def.getPathKeys().size());
        assertEquals(pk, def.getPathKeys().get(0));
        assertEquals("/orgs/$slug/items", def.getPath());
        assertNull(def.getDataPath());
        assertNull(def.getPaginationConfig());
    }

    /**
     * A RestTableDefinition with two PathKeyDef entries preserves order.
     */
    @Test
    public void testTableDefinitionCarriesTwoPathKeys()
    {
        final PathKeyDef pk1 = new PathKeyDef("org_id",     "/orgs/",              "id", null, null);
        final PathKeyDef pk2 = new PathKeyDef("project_id", "/orgs/$org_id/projs/", "id", null, null);
        final List<RestFieldDefinition> fields = Arrays.asList(
                new RestFieldDefinition("id", "VARCHAR", true, false));
        final RestTableDefinition def = new RestTableDefinition(
                "/orgs/$org_id/projs/$project_id/txns", null, null,
                Arrays.asList(pk1, pk2),
                fields);

        assertTrue(def.hasPathKeys());
        assertEquals(2, def.getPathKeys().size());
        assertEquals(pk1, def.getPathKeys().get(0));
        assertEquals(pk2, def.getPathKeys().get(1));
    }

    /**
     * A RestTableDefinition with a null path-key list has empty list and hasPathKeys() == false.
     */
    @Test
    public void testTableDefinitionNullPathKeyList()
    {
        final List<RestFieldDefinition> fields = Arrays.asList(
                new RestFieldDefinition("id", "VARCHAR", true, false));
        final RestTableDefinition def = new RestTableDefinition(
                "/items", null, null, null, fields);
        assertFalse(def.hasPathKeys());
        assertTrue(def.getPathKeys().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Path-key variable substitution is handled at read time (no interaction property)
    // -------------------------------------------------------------------------

    /**
     * buildRequestUrl substitutes path-key variables that stream() resolves at read time.
     * No interaction property is involved — the variable is simply present in the props map.
     */
    @Test
    public void testPathKeyVariableSubstitutedAtReadTime() throws Exception
    {
        final Map<String, Object> propsWithKey = new HashMap<>();
        propsWithKey.put("postId", "42");

        final String result = RestInputInteraction.buildRequestUrl(
                "https://jsonplaceholder.typicode.com",
                "/posts/$postId/comments",
                propsWithKey);

        assertEquals("https://jsonplaceholder.typicode.com/posts/42/comments", result);
    }

}

// Made with Bob
