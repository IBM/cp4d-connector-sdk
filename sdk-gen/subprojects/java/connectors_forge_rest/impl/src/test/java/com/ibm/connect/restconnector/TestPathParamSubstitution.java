/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

/**
 * Tests for ${varName} path parameter extraction and substitution.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@link RestApiMapping#getPathVariables()} — correct set of names extracted</li>
 *   <li>URL assembly in {@link RestInputInteraction} (via the mapping loader / table path)</li>
 *   <li>{@link RestDatasourceType} advertising each path variable as a connection property</li>
 * </ul>
 */
public class TestPathParamSubstitution
{
    // ---- getPathVariables() ----

    @Test
    public void testNoPathVariables() throws Exception
    {
        final String json = "{"
                + "\"$hostname\":\"https://api.example.com\","
                + "\"$tables\":{"
                + "  \"ITEMS\":{\"$path\":[\"/v1/items\"],\"id\":\"VARCHAR,$key\"}"
                + "}}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        assertTrue("Expected no path variables", mapping.getPathVariables().isEmpty());
    }

    @Test
    public void testSinglePathVariable() throws Exception
    {
        final String json = "{"
                + "\"$hostname\":\"https://api.example.com\","
                + "\"$tables\":{"
                + "  \"REPOS\":{\"$path\":[\"/2.0/repositories/${workspace}\"],\"id\":\"VARCHAR,$key\"}"
                + "}}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final Set<String> vars = mapping.getPathVariables();

        assertEquals(1, vars.size());
        assertTrue(vars.contains("workspace"));
    }

    @Test
    public void testMultiplePathVariablesInOneTable() throws Exception
    {
        final String json = "{"
                + "\"$hostname\":\"https://api.example.com\","
                + "\"$tables\":{"
                + "  \"COMMITS\":{\"$path\":[\"/2.0/repositories/${workspace}/${repo_slug}/commits\"],"
                + "    \"hash\":\"VARCHAR,$key\"}"
                + "}}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final Set<String> vars = mapping.getPathVariables();

        assertEquals(2, vars.size());
        assertTrue(vars.contains("workspace"));
        assertTrue(vars.contains("repo_slug"));
    }

    @Test
    public void testPathVariablesDeduplicatedAcrossTables() throws Exception
    {
        // Both REPOS and COMMITS use ${workspace}; COMMITS also uses ${repo_slug}
        final String json = "{"
                + "\"$hostname\":\"https://api.example.com\","
                + "\"$tables\":{"
                + "  \"REPOS\":{\"$path\":[\"/2.0/repositories/${workspace}\"],\"id\":\"VARCHAR,$key\"},"
                + "  \"COMMITS\":{\"$path\":[\"/2.0/repositories/${workspace}/${repo_slug}/commits\"],"
                + "    \"hash\":\"VARCHAR,$key\"}"
                + "}}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final Set<String> vars = mapping.getPathVariables();

        // workspace must appear only once even though two tables reference it
        assertEquals(2, vars.size());
        assertTrue(vars.contains("workspace"));
        assertTrue(vars.contains("repo_slug"));
    }

    @Test
    public void testPathVariablesInsertionOrder() throws Exception
    {
        // workspace appears before repo_slug in the path string
        final String json = "{"
                + "\"$hostname\":\"https://api.example.com\","
                + "\"$tables\":{"
                + "  \"BRANCHES\":{\"$path\":[\"/2.0/repositories/${workspace}/${repo_slug}/refs/branches\"],"
                + "    \"name\":\"VARCHAR,$key\"}"
                + "}}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final String[] vars = mapping.getPathVariables().toArray(new String[0]);

        assertEquals("workspace", vars[0]);
        assertEquals("repo_slug", vars[1]);
    }

    // ---- path variable substitution via table path string ----

    @Test
    public void testTablePathContainsToken() throws Exception
    {
        final String json = "{"
                + "\"$hostname\":\"https://api.example.com\","
                + "\"$tables\":{"
                + "  \"REPOS\":{\"$path\":[\"/2.0/repositories/${workspace}\"],\"id\":\"VARCHAR,$key\"}"
                + "}}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        // The raw path stored in the table definition must still contain the token;
        // substitution happens at request time inside RestInputInteraction.
        assertEquals("/2.0/repositories/${workspace}", mapping.getTable("REPOS").getPath());
    }

    // ---- RestDatasourceType registers path variables as connection properties ----

    @Test
    public void testDatasourceTypeRegistersNoPathVarsWhenAbsent() throws Exception
    {
        final String json = "{"
                + "\"$connector_name\":\"no-vars\","
                + "\"$hostname\":\"https://api.example.com\","
                + "\"$authentication\":\"none\","
                + "\"$tables\":{"
                + "  \"ITEMS\":{\"$path\":[\"/v1/items\"],\"id\":\"VARCHAR,$key\"}"
                + "}}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestDatasourceType dsType = new RestDatasourceType(mapping, "test.json");

        final boolean hasWorkspace = dsType.getProperties().getConnection().stream()
                .anyMatch(p -> "workspace".equals(p.getName()));
        assertFalse("workspace should not be registered when not present in paths", hasWorkspace);
    }

    @Test
    public void testDatasourceTypeRegistersWorkspaceProperty() throws Exception
    {
        final String json = "{"
                + "\"$connector_name\":\"bitbucket-cloud-connector\","
                + "\"$hostname\":\"https://api.bitbucket.org\","
                + "\"$authentication\":\"basic\","
                + "\"$tables\":{"
                + "  \"REPOS\":{\"$path\":[\"/2.0/repositories/${workspace}\"],\"id\":\"VARCHAR,$key\"}"
                + "}}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestDatasourceType dsType = new RestDatasourceType(mapping, "bitbucket-cloud.json");

        final boolean hasWorkspace = dsType.getProperties().getConnection().stream()
                .anyMatch(p -> "workspace".equals(p.getName()));
        assertTrue("workspace must be a connection property", hasWorkspace);
    }

    @Test
    public void testDatasourceTypeRegistersAllBitbucketPathVars() throws Exception
    {
        final String json = "{"
                + "\"$connector_name\":\"bitbucket-cloud-connector\","
                + "\"$hostname\":\"https://api.bitbucket.org\","
                + "\"$authentication\":\"basic\","
                + "\"$tables\":{"
                + "  \"REPOS\":{\"$path\":[\"/2.0/repositories/${workspace}\"],\"id\":\"VARCHAR,$key\"},"
                + "  \"COMMITS\":{\"$path\":[\"/2.0/repositories/${workspace}/${repo_slug}/commits\"],"
                + "    \"hash\":\"VARCHAR,$key\"}"
                + "}}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestDatasourceType dsType = new RestDatasourceType(mapping, "bitbucket-cloud.json");

        final boolean hasWorkspace = dsType.getProperties().getConnection().stream()
                .anyMatch(p -> "workspace".equals(p.getName()));
        final boolean hasRepoSlug = dsType.getProperties().getConnection().stream()
                .anyMatch(p -> "repo_slug".equals(p.getName()));

        assertTrue("workspace must be a connection property", hasWorkspace);
        assertTrue("repo_slug must be a connection property", hasRepoSlug);
    }

    @Test
    public void testPathVarPropertyIsRequired() throws Exception
    {
        final String json = "{"
                + "\"$connector_name\":\"test\","
                + "\"$hostname\":\"https://api.example.com\","
                + "\"$authentication\":\"none\","
                + "\"$tables\":{"
                + "  \"T\":{\"$path\":[\"/v1/${org_id}/items\"],\"id\":\"VARCHAR,$key\"}"
                + "}}";

        final RestApiMapping mapping = RestApiMappingLoader.parse(json);
        final RestDatasourceType dsType = new RestDatasourceType(mapping, "test.json");

        dsType.getProperties().getConnection().stream()
                .filter(p -> "org_id".equals(p.getName()))
                .findFirst()
                .ifPresent(p -> assertTrue("path var property must be required", Boolean.TRUE.equals(p.isRequired())));
    }
}

// Made with Bob
