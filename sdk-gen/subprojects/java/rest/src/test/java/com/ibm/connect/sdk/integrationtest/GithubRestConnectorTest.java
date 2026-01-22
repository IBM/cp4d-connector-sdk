/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */

/**
 * Integration test class for testing the REST connector with real GitHub API calls.
 *
 * <p><strong>Important:</strong> This test class makes actual HTTP calls to the GitHub API
 * (https://api.github.ibm.com/api/v3) and is NOT using mocked responses. All tests in this
 * class are currently marked with {@code @Ignore} to prevent them from running in regular
 * test suites.</p>
 *
 * <p><strong>Purpose:</strong> Use this test class when you want to gain full confidence
 * that your changes work correctly with real HTTP calls instead of relying solely on the
 * mocked tests in {@code RestConnectorTest.java}.</p>
 *
 * <p><strong>Setup Requirements:</strong></p>
 * <ul>
 *   <li>Update the {@code PROPERTY_AUTH_USERNAME} with your GitHub username</li>
 *   <li>Update the {@code PROPERTY_AUTH_PASSWORD} with your GitHub credentials/token</li>
 *   <li>Remove the {@code @Ignore} annotation from the test you want to run</li>
 *   <li>Ensure you have network access to the GitHub API endpoint</li>
 * </ul>
 *
 * <p><strong>Test Coverage:</strong></p>
 * <ul>
 *   <li>{@link #testGithubRepos2Level()} - Tests 2-level hierarchy (Organizations → Repositories)</li>
 *   <li>{@link #testGithubBranch3Level()} - Tests 3-level hierarchy (Organizations → Repositories → Branches)</li>
 * </ul>
 *
 * @see RestConnectorTest for mocked unit tests
 */
package com.ibm.connect.sdk.integrationtest;

import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_AUTH_PASSWORD;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_AUTH_TYPE;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_AUTH_USERNAME;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_BASE_URL;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_CUSTOM_REST_CONFIG_YAML;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;

import com.ibm.connect.sdk.rest.RestConnector;
import com.ibm.connect.sdk.rest.RestSourceInteraction;
import com.ibm.connect.sdk.rest.utils.RestSourceInteractionProperties;
import com.ibm.connect.sdk.rest.utils.RestUtils;
import com.ibm.connect.sdk.test.helper.MockConnectionProperties;
import com.ibm.connect.sdk.test.helper.RestTestUtils;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

@SuppressWarnings("PMD")
public class GithubRestConnectorTest {
    private final Properties properties = new Properties();

    /**
     * Sets up the connection properties for GitHub API authentication.
     *
     * <p><strong>Note:</strong> You must update the username and password/token
     * with your actual GitHub credentials before running these tests.</p>
     */
    @Before
    public void setup() {
        properties.put(PROPERTY_BASE_URL, "https://api.github.ibm.com/api/v3");
        properties.put(PROPERTY_AUTH_TYPE, "basic");
        properties.put(PROPERTY_AUTH_USERNAME, "<Add your Username>");
        properties.put(PROPERTY_AUTH_PASSWORD, "<Add your credentials here>");
    }

    /**
     * Tests the REST connector with a 2-level hierarchy using the real GitHub API.
     *
     * <p>This test validates:</p>
     * <ul>
     *   <li>Discovery of organizations at the root level</li>
     *   <li>Discovery of repositories under a specific organization</li>
     *   <li>Reading repository data with all fields</li>
     *   <li>Pagination and batch processing of results</li>
     * </ul>
     *
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Discovers organizations (expects 100 organizations)</li>
     *   <li>Discovers repositories for the first organization</li>
     *   <li>Validates repository field definitions (expects 107 fields)</li>
     *   <li>Reads actual repository data for the "Whitewater" organization</li>
     *   <li>Validates batch processing with 100 rows per batch</li>
     * </ol>
     *
     * <p><strong>Configuration:</strong> Uses {@code github_repos.yml} configuration file.</p>
     *
     * @throws Exception if the test fails due to connection issues or assertion failures
     */
    @Ignore
    public void testGithubRepos2Level() throws Exception {
        properties.put(PROPERTY_CUSTOM_REST_CONFIG_YAML, RestTestUtils.readResourceFile("com/ibm/connect/sdk/rest/config/github/github_repos.yml"));
        final ConnectionProperties connectionProperties = MockConnectionProperties.getConnectionProperties(properties);
        try (RestConnector connector = new RestConnector(connectionProperties); BufferAllocator rootAllocator = new RootAllocator();) {
            connector.connect();

            //Organisation
            final CustomFlightAssetsCriteria criteria1 = new CustomFlightAssetsCriteria();
            criteria1.setPath("/");
            final List<CustomFlightAssetDescriptor> orgAssets = connector.discoverAssets(criteria1);
            Assert.assertNotNull(orgAssets);
            Assert.assertEquals(100, orgAssets.size());
            orgAssets.forEach(org -> {
                Assert.assertTrue(!RestUtils.isNullOrEmpty(org.getPath()));
                Assert.assertTrue(!RestUtils.isNullOrEmpty(org.getName()));
                Assert.assertTrue(!RestUtils.isNullOrEmpty(org.getId()));
                Assert.assertNull(org.getFields());
            });

            //Repos
            final CustomFlightAssetsCriteria criteria2 = new CustomFlightAssetsCriteria();
            criteria2.setPath(orgAssets.get(0).getPath());
            final List<CustomFlightAssetDescriptor> repoAssets = connector.discoverAssets(criteria2);
            Assert.assertNotNull(repoAssets);
            Assert.assertEquals(1, repoAssets.size()); //As its leaf node, only one asset with field definition
            Assert.assertEquals(107, repoAssets.get(0).getFields().size()); //It might need to adjust later depends on Rest API.
            repoAssets.forEach(repo -> {
                Assert.assertTrue(!RestUtils.isNullOrEmpty(repo.getPath()));
                Assert.assertTrue(!RestUtils.isNullOrEmpty(repo.getName()));
            });

            //Read Data
            final CustomFlightAssetDescriptor asset = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            interactionProperties.put(RestSourceInteractionProperties.PROPERTIES_SELECTED_PARENT_ENTITY_VALUES, "{\"organization\":\"Whitewater\"}");
            asset.setInteractionProperties(interactionProperties);
            try (RestSourceInteraction sourceInteraction = connector.getSourceInteraction(asset, null)) {
                asset.setFields(sourceInteraction.getFields());
                sourceInteraction.beginStream(rootAllocator);

                Assert.assertTrue(sourceInteraction.hasNextBatch());
                while (sourceInteraction.hasNextBatch()) {
                    try (VectorSchemaRoot batch = sourceInteraction.nextBatch()) {
                        Assert.assertEquals(127, batch.getRowCount());

                        // Read data from the current batch
                        final int rowCount = batch.getRowCount();
                        final List<FieldVector> fieldVectors = batch.getFieldVectors();
                        for (int row = 0; row < rowCount; row++) {
                            Assert.assertEquals(107, fieldVectors.size());
                            Assert.assertEquals("repos[].id, repos[].node_id, repos[].name, repos[].full_name, repos[].private, repos[].owner.login, repos[].owner.id, repos[].owner.node_id, repos[].owner.avatar_url, repos[].owner.gravatar_id, repos[].owner.url, repos[].owner.html_url, repos[].owner.followers_url, repos[].owner.following_url, repos[].owner.gists_url, repos[].owner.starred_url, repos[].owner.subscriptions_url, repos[].owner.organizations_url, repos[].owner.repos_url, repos[].owner.events_url, repos[].owner.received_events_url, repos[].owner.type, repos[].owner.user_view_type, repos[].owner.site_admin, repos[].html_url, repos[].description, repos[].fork, repos[].url, repos[].forks_url, repos[].keys_url, repos[].collaborators_url, repos[].teams_url, repos[].hooks_url, repos[].issue_events_url, repos[].events_url, repos[].assignees_url, repos[].branches_url, repos[].tags_url, repos[].blobs_url, repos[].git_tags_url, repos[].git_refs_url, repos[].trees_url, repos[].statuses_url, repos[].languages_url, repos[].stargazers_url, repos[].contributors_url, repos[].subscribers_url, repos[].subscription_url, repos[].commits_url, repos[].git_commits_url, repos[].comments_url, repos[].issue_comment_url, repos[].contents_url, repos[].compare_url, repos[].merges_url, repos[].archive_url, repos[].downloads_url, repos[].issues_url, repos[].pulls_url, repos[].milestones_url, repos[].notifications_url, repos[].labels_url, repos[].releases_url, repos[].deployments_url, repos[].created_at, repos[].updated_at, repos[].pushed_at, repos[].git_url, repos[].ssh_url, repos[].clone_url, repos[].svn_url, repos[].homepage, repos[].size, repos[].stargazers_count, repos[].watchers_count, repos[].language, repos[].has_issues, repos[].has_projects, repos[].has_downloads, repos[].has_wiki, repos[].has_pages, repos[].has_discussions, repos[].forks_count, repos[].mirror_url, repos[].archived, repos[].disabled, repos[].open_issues_count, repos[].license, repos[].allow_forking, repos[].is_template, repos[].web_commit_signoff_required, repos[].visibility, repos[].forks, repos[].open_issues, repos[].watchers, repos[].default_branch, repos[].permissions.admin, repos[].permissions.maintain, repos[].permissions.push, repos[].permissions.triage, repos[].permissions.pull, repos[].topics, repos[].license.key, repos[].license.name, repos[].license.spdx_id, repos[].license.url, repos[].license.node_id", fieldVectors.stream().map(field -> field.getName()).collect(Collectors.joining(", ")));
                        }
                    }
                }
            }
        }
    }

    /**
     * Tests the REST connector with a 3-level hierarchy using the real GitHub API.
     *
     * <p>This test validates:</p>
     * <ul>
     *   <li>Discovery of organizations at the root level</li>
     *   <li>Discovery of repositories under a specific organization</li>
     *   <li>Discovery of branches under a specific repository</li>
     *   <li>Reading branch data with all fields</li>
     *   <li>Pagination and batch processing of results</li>
     * </ul>
     *
     * <p><strong>Test Flow:</strong></p>
     * <ol>
     *   <li>Discovers organizations (expects 100 organizations)</li>
     *   <li>Discovers repositories for the first organization (expects 100 repositories)</li>
     *   <li>Discovers branches for the first repository</li>
     *   <li>Validates branch field definitions (expects 4 fields)</li>
     *   <li>Reads actual branch data for "Analytics/shared-discovery-platform" repository</li>
     *   <li>Validates batch processing with 30 rows (may need adjustment if branches change)</li>
     * </ol>
     *
     * <p><strong>Configuration:</strong> Uses {@code github_branches.yml} configuration file.</p>
     *
     * <p><strong>Note:</strong> The expected row count (30) may need to be updated if new
     * branches are created or deleted in the target repository.</p>
     *
     * @throws Exception if the test fails due to connection issues or assertion failures
     */
    @Ignore
    public void testGithubBranch3Level() throws Exception {
        properties.put(PROPERTY_CUSTOM_REST_CONFIG_YAML, RestTestUtils.readResourceFile("com/ibm/connect/sdk/rest/config/github/github_branches.yml"));
        final ConnectionProperties connectionProperties = MockConnectionProperties.getConnectionProperties(properties);
        try (RestConnector connector = new RestConnector(connectionProperties); BufferAllocator rootAllocator = new RootAllocator();) {
            connector.connect();

            //Organisation
            final CustomFlightAssetsCriteria criteria1 = new CustomFlightAssetsCriteria();
            criteria1.setPath("/");
            criteria1.offset(0);
            criteria1.limit(1000);
            final List<CustomFlightAssetDescriptor> orgAssets = connector.discoverAssets(criteria1);
            Assert.assertNotNull(orgAssets);
            Assert.assertEquals(30, orgAssets.size());
            orgAssets.forEach(org -> {
                Assert.assertTrue(!RestUtils.isNullOrEmpty(org.getPath()));
                Assert.assertTrue(!RestUtils.isNullOrEmpty(org.getName()));
                Assert.assertTrue(!RestUtils.isNullOrEmpty(org.getId()));
                Assert.assertNull(org.getFields());
            });

            //Repos
            final CustomFlightAssetsCriteria criteria2 = new CustomFlightAssetsCriteria();
            criteria2.setPath(orgAssets.get(0).getPath());
            criteria2.offset(0);
            criteria2.limit(1000);
            final List<CustomFlightAssetDescriptor> repoAssets = connector.discoverAssets(criteria2);
            Assert.assertNotNull(repoAssets);
            Assert.assertEquals(30, repoAssets.size());
            repoAssets.forEach(repo -> {
                Assert.assertTrue(!RestUtils.isNullOrEmpty(repo.getPath()));
                Assert.assertTrue(!RestUtils.isNullOrEmpty(repo.getName()));
                Assert.assertTrue(!RestUtils.isNullOrEmpty(repo.getId()));
                Assert.assertNull(repo.getFields());
            });

            //Branches
            final CustomFlightAssetsCriteria criteria3 = new CustomFlightAssetsCriteria();
            criteria3.setPath(repoAssets.get(0).getPath());
            final List<CustomFlightAssetDescriptor> branchAssets = connector.discoverAssets(criteria3);
            Assert.assertNotNull(branchAssets);
            Assert.assertEquals(1, branchAssets.size()); //As its leaf node, only one asset with field definition
            Assert.assertEquals(4, branchAssets.get(0).getFields().size()); //It might need to adjust later depends on Rest API.
            branchAssets.forEach(repo -> {
                Assert.assertTrue(!RestUtils.isNullOrEmpty(repo.getPath()));
                Assert.assertTrue(!RestUtils.isNullOrEmpty(repo.getName()));
            });

            //Read Data
            final CustomFlightAssetDescriptor asset = new CustomFlightAssetDescriptor();
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            interactionProperties.put(RestSourceInteractionProperties.PROPERTIES_SELECTED_PARENT_ENTITY_VALUES, "{\"organization\":\"Analytics\", \"repository\": \"shared-discovery-platform\"}");
            asset.setInteractionProperties(interactionProperties);
            try (RestSourceInteraction sourceInteraction = connector.getSourceInteraction(asset, null)) {
                asset.setFields(sourceInteraction.getFields());
                sourceInteraction.beginStream(rootAllocator);

                Assert.assertTrue(sourceInteraction.hasNextBatch());
                while (sourceInteraction.hasNextBatch()) {
                    try (VectorSchemaRoot batch = sourceInteraction.nextBatch()) {
                        Assert.assertEquals(30, batch.getRowCount()); //Need to adjust if new branch created on this repo. Feel free to update this value

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
}
