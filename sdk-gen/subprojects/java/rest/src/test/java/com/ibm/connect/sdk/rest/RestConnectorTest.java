/*
  ***************************************************

  (C) Copyright IBM Corp. ${YEAR}

  ***************************************************
 */
package com.ibm.connect.sdk.rest;

import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_AUTH_PASSWORD;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_AUTH_TYPE;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_AUTH_USERNAME;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_BASE_URL;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_CUSTOM_REST_CONFIG_YAML;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_PREDEFINED_REST_CONFIG_IDENTIFIER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import com.ibm.connect.sdk.rest.httpclient.RestExecutorImpl;
import com.ibm.connect.sdk.rest.utils.RestSourceInteractionProperties;
import com.ibm.connect.sdk.rest.utils.RestUtils;
import com.ibm.connect.sdk.test.helper.MockConnectionProperties;
import com.ibm.connect.sdk.test.helper.RestTestUtils;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

public class RestConnectorTest {
    private final Properties properties = new Properties();
    private CloseableHttpClient mockHttpClient;

    @Before
    public void setup() {
        properties.put(PROPERTY_BASE_URL, "https://api.github.ibm.com/api/v3");
        properties.put(PROPERTY_AUTH_TYPE, "basic");
        properties.put(PROPERTY_AUTH_USERNAME, "test-user");
        properties.put(PROPERTY_AUTH_PASSWORD, "test-password");
        properties.put(PROPERTY_PREDEFINED_REST_CONFIG_IDENTIFIER, "custom_model");

        mockHttpClient = mock(CloseableHttpClient.class);
    }

    @Test
    public void testGithubRepos2LevelMocked() throws Exception {
        properties.put(PROPERTY_CUSTOM_REST_CONFIG_YAML, RestTestUtils.readResourceFile("com/ibm/connect/sdk/rest/config/github/github_repos.yml"));
        final ConnectionProperties connectionProperties = MockConnectionProperties.getConnectionProperties(properties);
        
        // Setup mock responses
        setupMockHttpClient();
        
        // Create connector with mocked HTTP client
        try (RestConnector connector = createMockedConnector(connectionProperties); 
             BufferAllocator rootAllocator = new RootAllocator();) {
            connector.connect();

            // Test Organization discovery
            final CustomFlightAssetsCriteria criteria1 = new CustomFlightAssetsCriteria();
            criteria1.setPath("/");
            final List<CustomFlightAssetDescriptor> orgAssets = connector.discoverAssets(criteria1);
            Assert.assertNotNull(orgAssets);
            Assert.assertEquals(100, orgAssets.size()); // Mock returns 2 orgs
            orgAssets.forEach(org -> {
                Assert.assertTrue(!RestUtils.isNullOrEmpty(org.getPath()));
                Assert.assertTrue(!RestUtils.isNullOrEmpty(org.getName()));
                Assert.assertTrue(!RestUtils.isNullOrEmpty(org.getId()));
                Assert.assertNull(org.getFields());
            });

            // Test Repos discovery
            final CustomFlightAssetsCriteria criteria2 = new CustomFlightAssetsCriteria();
            criteria2.setPath(orgAssets.get(0).getPath());
            final List<CustomFlightAssetDescriptor> repoAssets = connector.discoverAssets(criteria2);
            Assert.assertNotNull(repoAssets);
            Assert.assertEquals(1, repoAssets.size()); // Leaf node
            Assert.assertEquals(106, repoAssets.get(0).getFields().size());
            repoAssets.forEach(repo -> {
                Assert.assertTrue(!RestUtils.isNullOrEmpty(repo.getPath()));
                Assert.assertTrue(!RestUtils.isNullOrEmpty(repo.getName()));
            });

            // Test Read Data
            final CustomFlightAssetDescriptor asset = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            interactionProperties.put(RestSourceInteractionProperties.PROPERTIES_SELECTED_PARENT_ENTITY_VALUES, "{\"organization\":\"TestOrg\"}");
            asset.setInteractionProperties(interactionProperties);
            try (RestSourceInteraction sourceInteraction = connector.getSourceInteraction(asset, null)) {
                asset.setFields(sourceInteraction.getFields());
                sourceInteraction.beginStream(rootAllocator);

                Assert.assertTrue(sourceInteraction.hasNextBatch());
                while (sourceInteraction.hasNextBatch()) {
                    try (VectorSchemaRoot batch = sourceInteraction.nextBatch()) {
                        Assert.assertEquals(100, batch.getRowCount());
                        
                        final int rowCount = batch.getRowCount();
                        final List<FieldVector> fieldVectors = batch.getFieldVectors();
                        for (int row = 0; row < rowCount; row++) {
                            Assert.assertEquals(106, fieldVectors.size());
                            Assert.assertEquals("repos[].id, repos[].node_id, repos[].name, repos[].full_name, repos[].private, repos[].owner.login, repos[].owner.id, repos[].owner.node_id, repos[].owner.avatar_url, repos[].owner.gravatar_id, repos[].owner.url, repos[].owner.html_url, repos[].owner.followers_url, repos[].owner.following_url, repos[].owner.gists_url, repos[].owner.starred_url, repos[].owner.subscriptions_url, repos[].owner.organizations_url, repos[].owner.repos_url, repos[].owner.events_url, repos[].owner.received_events_url, repos[].owner.type, repos[].owner.user_view_type, repos[].owner.site_admin, repos[].html_url, repos[].description, repos[].fork, repos[].url, repos[].forks_url, repos[].keys_url, repos[].collaborators_url, repos[].teams_url, repos[].hooks_url, repos[].issue_events_url, repos[].events_url, repos[].assignees_url, repos[].branches_url, repos[].tags_url, repos[].blobs_url, repos[].git_tags_url, repos[].git_refs_url, repos[].trees_url, repos[].statuses_url, repos[].languages_url, repos[].stargazers_url, repos[].contributors_url, repos[].subscribers_url, repos[].subscription_url, repos[].commits_url, repos[].git_commits_url, repos[].comments_url, repos[].issue_comment_url, repos[].contents_url, repos[].compare_url, repos[].merges_url, repos[].archive_url, repos[].downloads_url, repos[].issues_url, repos[].pulls_url, repos[].milestones_url, repos[].notifications_url, repos[].labels_url, repos[].releases_url, repos[].deployments_url, repos[].created_at, repos[].updated_at, repos[].pushed_at, repos[].git_url, repos[].ssh_url, repos[].clone_url, repos[].svn_url, repos[].homepage, repos[].size, repos[].stargazers_count, repos[].watchers_count, repos[].language, repos[].has_issues, repos[].has_projects, repos[].has_downloads, repos[].has_wiki, repos[].has_pages, repos[].has_discussions, repos[].forks_count, repos[].mirror_url, repos[].archived, repos[].disabled, repos[].open_issues_count, repos[].license, repos[].allow_forking, repos[].is_template, repos[].web_commit_signoff_required, repos[].visibility, repos[].forks, repos[].open_issues, repos[].watchers, repos[].default_branch, repos[].permissions.admin, repos[].permissions.maintain, repos[].permissions.push, repos[].permissions.triage, repos[].permissions.pull, repos[].license.key, repos[].license.name, repos[].license.spdx_id, repos[].license.url, repos[].license.node_id", fieldVectors.stream().map(field -> field.getName()).collect(Collectors.joining(", ")));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testGithubBranch3LevelMocked() throws Exception {
        properties.put(PROPERTY_CUSTOM_REST_CONFIG_YAML, RestTestUtils.readResourceFile("com/ibm/connect/sdk/rest/config/github/github_branches.yml"));
        final ConnectionProperties connectionProperties = MockConnectionProperties.getConnectionProperties(properties);
        
        // Setup mock responses
        setupMockHttpClient();
        
        try (RestConnector connector = createMockedConnector(connectionProperties); 
             BufferAllocator rootAllocator = new RootAllocator();) {
            connector.connect();

            // Test Organization discovery
            final CustomFlightAssetsCriteria criteria1 = new CustomFlightAssetsCriteria();
            criteria1.setPath("/");
            criteria1.setOffset(0);
            criteria1.setLimit(1000);
            final List<CustomFlightAssetDescriptor> orgAssets = connector.discoverAssets(criteria1);
            Assert.assertNotNull(orgAssets);
            Assert.assertEquals(1000, orgAssets.size());
            orgAssets.forEach(org -> {
                Assert.assertFalse(RestUtils.isNullOrEmpty(org.getPath()));
                Assert.assertFalse(RestUtils.isNullOrEmpty(org.getName()));
                Assert.assertFalse(RestUtils.isNullOrEmpty(org.getId()));
                Assert.assertNull(org.getFields());
            });

            // Test Repos discovery
            final CustomFlightAssetsCriteria criteria2 = new CustomFlightAssetsCriteria();
            criteria2.setPath(orgAssets.get(0).getPath());
            criteria2.setOffset(0);
            criteria2.setLimit(1000);
            final List<CustomFlightAssetDescriptor> repoAssets = connector.discoverAssets(criteria2);
            Assert.assertNotNull(repoAssets);
            Assert.assertEquals(1000, repoAssets.size()); // Not leaf, returns multiple repos
            repoAssets.forEach(repo -> {
                Assert.assertFalse(RestUtils.isNullOrEmpty(repo.getPath()));
                Assert.assertFalse(RestUtils.isNullOrEmpty(repo.getName()));
                Assert.assertFalse(RestUtils.isNullOrEmpty(repo.getId()));
                Assert.assertNull(repo.getFields());
            });

            // Test Branches discovery
            final CustomFlightAssetsCriteria criteria3 = new CustomFlightAssetsCriteria();
            criteria3.setPath(repoAssets.get(0).getPath());
            final List<CustomFlightAssetDescriptor> branchAssets = connector.discoverAssets(criteria3);
            Assert.assertNotNull(branchAssets);
            Assert.assertEquals(1, branchAssets.size()); // Leaf node
            Assert.assertNotNull(branchAssets.get(0).getFields());
            Assert.assertEquals(4, branchAssets.get(0).getFields().size());
            branchAssets.forEach(repo -> {
                Assert.assertFalse(RestUtils.isNullOrEmpty(repo.getPath()));
                Assert.assertFalse(RestUtils.isNullOrEmpty(repo.getName()));
            });

            // Test Read Data
            final CustomFlightAssetDescriptor asset = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            interactionProperties.put(RestSourceInteractionProperties.PROPERTIES_SELECTED_PARENT_ENTITY_VALUES, 
                "{\"organization\":\"TestOrg\", \"repository\": \"test-repo\"}");
            asset.setInteractionProperties(interactionProperties);
            try (RestSourceInteraction sourceInteraction = connector.getSourceInteraction(asset, null)) {
                asset.setFields(sourceInteraction.getFields());
                sourceInteraction.beginStream(rootAllocator);

                Assert.assertTrue(sourceInteraction.hasNextBatch());
                while (sourceInteraction.hasNextBatch()) {
                    try (VectorSchemaRoot batch = sourceInteraction.nextBatch()) {
                        Assert.assertEquals(30, batch.getRowCount());

                        // Read data from the current batch
                        final int rowCount = batch.getRowCount();
                        final List<FieldVector> fieldVectors = batch.getFieldVectors();
                        for (int row = 0; row < rowCount; row++) {
                            Assert.assertEquals(4, fieldVectors.size());
                            Assert.assertEquals("branches[].name, branches[].commit.sha, branches[].commit.url, branches[].protected", fieldVectors.stream().map(field -> field.getName()).collect(Collectors.joining(", ")));
                        }
                    }
                }
            }
        }
    }

    /**
     * Setup mock HTTP client to return predefined responses based on URL patterns
     */
    private void setupMockHttpClient() throws IOException {
        when(mockHttpClient.execute(any(), any(HttpClientResponseHandler.class)))
            .thenAnswer((Answer<Object>) invocation -> {
                final HttpClientResponseHandler<?> handler = invocation.getArgument(1);
                final ClassicHttpResponse mockResponse = mock(ClassicHttpResponse.class);
                final HttpEntity mockEntity = mock(HttpEntity.class);
                
                // Get the request to determine which mock response to return
                final Object request = invocation.getArgument(0);
                final String requestUri = request.toString();
                
                // Mock response based on URL pattern
                final String responseBody;
                if (requestUri.contains("/organizations")) {
                    responseBody = getMockOrganizationsResponse();
                    when(mockResponse.getCode()).thenReturn(200);
                } else if (requestUri.contains("/orgs/") && requestUri.contains("/repos")) {
                    responseBody = getMockRepositoriesResponse();
                    when(mockResponse.getCode()).thenReturn(200);
                } else if (requestUri.contains("/repos/") && requestUri.contains("/branches")) {
                    responseBody = getMockBranchesResponse();
                    when(mockResponse.getCode()).thenReturn(200);
                } else {
                    // Default response for connection check (HEAD request)
                    responseBody = "";
                    when(mockResponse.getCode()).thenReturn(200);
                }
                
                // Set Content-Type header for JSON responses
                when(mockEntity.getContentType()).thenReturn("application/json");
                when(mockEntity.getContent()).thenReturn(
                    new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8))
                );
                when(mockResponse.getEntity()).thenReturn(mockEntity);
                
                return handler.handleResponse(mockResponse);
            });
    }

    /**
     * Create a RestConnector with mocked HTTP client
     */
    private RestConnector createMockedConnector(ConnectionProperties connectionProperties) {
        return new RestConnector(connectionProperties) {
            @Override
            public void connect() throws Exception {
                // Override to inject mocked RestExecutorImpl
                final RestExecutorImpl mockedExecutor = new RestExecutorImpl(mockHttpClient);
                // Use reflection to set the private field
                final java.lang.reflect.Field field = RestConnector.class.getDeclaredField("restApiExecutor");
                field.setAccessible(true);
                field.set(this, mockedExecutor);
            }
        };
    }

    /**
     * Mock response for organizations endpoint
     */
    private String getMockOrganizationsResponse() {
        return RestTestUtils.readResourceFile("com/ibm/connect/sdk/rest/response/github/orgs.json");
    }

    /**
     * Mock response for repositories endpoint
     */
    private String getMockRepositoriesResponse() {
        return RestTestUtils.readResourceFile("com/ibm/connect/sdk/rest/response/github/repos.json");
    }

    /**
     * Mock response for branches endpoint
     */
    private String getMockBranchesResponse() {
        return RestTestUtils.readResourceFile("com/ibm/connect/sdk/rest/response/github/branches.json");
    }
}
