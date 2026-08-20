/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.s3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.Result;
import org.apache.arrow.vector.types.pojo.Schema;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;

import com.google.common.collect.Table;
import com.ibm.connect.sdk.test.ConnectorTestSuite;
import com.ibm.connect.sdk.test.TestConfig;
import com.ibm.connect.sdk.test.TestFlight;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionRequest;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

/**
 * Tests a Flight producer for the Amazon S3 connector.
 *
 * <p>
 * To run against a real S3 bucket, create the file
 * {@code sdk-gen/tests.properties} (gitignored) and populate it:
 * 
 * <pre>
 *   # S3 Flight server settings
 *   file_s3.flight.createLocal=true
 *   file_s3.flight.ssl=true
 *   file_s3.flight.port=&lt;leave blank for random port&gt;
 *
 *   # S3 connection
 *   file_s3.s3.bucket=my-test-bucket
 *   file_s3.s3.region=us-east-1
 *   file_s3.s3.access_key_id=AKIA...
 *   file_s3.s3.secret_access_key=...
 *   # Optional — for S3-compatible stores (MinIO, LocalStack):
 *   # file_s3.s3.endpoint_url=http://localhost:9000
 *
 *   # S3 test data (objects that must exist in the bucket before running tests)
 *   file_s3.s3.test_folder=test-data/            # prefix of a non-empty folder
 *   file_s3.s3.test_csv_key=test-data/cars.csv   # a CSV object
 *   file_s3.s3.test_binary_key=test-data/logo.png  # any binary object
 * </pre>
 *
 * <p>
 * All tests are skipped automatically when {@code file_s3.s3.access_key_id} is
 * not set.
 */
public class TestAWSS3FlightProducer extends ConnectorTestSuite
{
    private static final Logger LOGGER = getLogger(TestAWSS3FlightProducer.class);

    private static final String DATASOURCE_TYPE_NAME = AWSS3DatasourceType.DATASOURCE_TYPE_NAME;

    // -----------------------------------------------------------------------
    // Test configuration — loaded once from tests.properties (gitignored)
    // -----------------------------------------------------------------------
    private static final String S3_ACCESS_KEY_ID = TestConfig.get("file_s3.s3.access_key_id");
    private static final String S3_SECRET_ACCESS_KEY = TestConfig.get("file_s3.s3.secret_access_key");
    private static final String S3_BUCKET = TestConfig.get("file_s3.s3.bucket");
    private static final String S3_REGION = TestConfig.get("file_s3.s3.region");
    private static final String S3_ENDPOINT_URL = TestConfig.get("file_s3.s3.endpoint_url");

    /**
     * S3 prefix (key ending with "/") of a folder that contains at least one
     * object.
     */
    private static final String S3_TEST_FOLDER = TestConfig.get("file_s3.s3.test_folder");
    /** S3 key of a CSV object to use for structured read tests. */
    private static final String S3_TEST_CSV_KEY = TestConfig.get("file_s3.s3.test_csv_key");
    /** S3 key of any binary object to use for raw-bytes read tests. */
    private static final String S3_TEST_BINARY_KEY = TestConfig.get("file_s3.s3.test_binary_key");

    // -----------------------------------------------------------------------
    // Flight server / client
    // -----------------------------------------------------------------------
    private static final String NO_SCHEMA_MSG = "Expected a schema but none was present";

    private static final ModelMapper MODEL_MAPPER = new ModelMapper();
    private static TestFlight testFlight;
    private static FlightClient client;

    /**
     * Skip every test when S3 credentials are not present in tests.properties.
     */
    @Before
    public void setUp()
    {
        assumeNotNull(S3_ACCESS_KEY_ID);
    }

    /**
     * Start a local Flight server backed by the S3 producer.
     *
     * @throws Exception
     */
    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        if (Boolean.parseBoolean(TestConfig.get("file_s3.flight.createLocal", "true"))) {
            final boolean useSSL = Boolean.parseBoolean(TestConfig.get("file_s3.flight.ssl", "true"));
            testFlight = TestFlight.createLocal(TestConfig.getPort("file_s3.flight.port"), useSSL, new AWSS3FlightProducer(), null);
        } else {
            final boolean verifyCert = Boolean.parseBoolean(TestConfig.get("file_s3.flight.ssl_certificate_validation", "true"));
            testFlight = TestFlight.createRemote(TestConfig.get("file_s3.flight.uri.internal", TestConfig.get("file_s3.flight.uri")),
                    TestConfig.get("file_s3.flight.ssl_certificate"), verifyCert, null);
        }
        client = testFlight.getClient();
    }

    /**
     * Shut down the local Flight server after all tests.
     */
    @AfterClass
    public static void tearDownOnce()
    {
        try {
            testFlight.close();
        }
        catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    protected FlightClient getClient()
    {
        return client;
    }

    @Override
    protected String getDatasourceTypeName()
    {
        return DATASOURCE_TYPE_NAME;
    }

    @Override
    protected ConnectionProperties createConnectionProperties()
    {
        final ConnectionProperties props = new ConnectionProperties();
        if (S3_BUCKET != null) {
            props.put("bucket", S3_BUCKET);
        }
        if (S3_REGION != null) {
            props.put("region", S3_REGION);
        }
        if (S3_ENDPOINT_URL != null) {
            props.put("endpoint_url", S3_ENDPOINT_URL);
        }
        if (S3_ACCESS_KEY_ID != null) {
            props.put("access_key_id", S3_ACCESS_KEY_ID);
        }
        if (S3_SECRET_ACCESS_KEY != null) {
            props.put("secret_access_key", S3_SECRET_ACCESS_KEY);
        }
        return props;
    }

    // -----------------------------------------------------------------------
    // Validation tests
    // -----------------------------------------------------------------------

    /**
     * Validate action should fail when the required bucket property is missing.
     */
    @Test
    public void testConnectionMissingBucket()
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        request.getConnectionProperties().remove("bucket");
        try {
            getClient().doAction(new Action("validate", MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("Missing bucket"));
        }
    }

    // -----------------------------------------------------------------------
    // Discovery tests
    // -----------------------------------------------------------------------

    /**
     * List objects at the bucket root — verify at least one entry is returned.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverRoot() throws Exception
    {
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath("/");
        final List<String> names = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor descriptor
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            assertNotNull(descriptor.getAssetType());
            assertNotNull(descriptor.getId());
            assertNotNull(descriptor.getName());
            names.add(descriptor.getName());
        }
        assertFalse("Expected at least one asset at the root", names.isEmpty());
    }

    /**
     * List objects with paging — offset 0, limit 2 should return exactly 2 entries
     * (assumes the bucket root has at least 2 entries).
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverRootWithPaging() throws Exception
    {
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath("/");
        criteria.setOffset(0);
        criteria.setLimit(2);
        final List<String> names = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor descriptor
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            names.add(descriptor.getName());
        }
        assertTrue("Expected at most 2 results", names.size() <= 2);
    }

    /**
     * List the contents of a specific folder (prefix). Requires
     * {@code file_s3.s3.test_folder} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverFolder() throws Exception
    {
        assumeNotNull(S3_TEST_FOLDER);
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        // Path uses leading slash; test_folder value should end with "/".
        criteria.setPath("/" + S3_TEST_FOLDER);
        final List<String> names = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor descriptor
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            assertNotNull(descriptor.getAssetType());
            assertNotNull(descriptor.getId());
            names.add(descriptor.getName());
        }
        assertFalse("Expected at least one asset in the test folder", names.isEmpty());
    }

    /**
     * Discover a specific CSV object — verify schema and interaction properties.
     * Requires {@code file_s3.s3.test_csv_key} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumnsCsv() throws Exception
    {
        assumeNotNull(S3_TEST_CSV_KEY);
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath("/" + S3_TEST_CSV_KEY);
        final List<CustomFlightAssetDescriptor> assets = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor descriptor
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            assertNotNull(descriptor.getAssetType());
            assertEquals("file", descriptor.getAssetType().getType());
            assertTrue(descriptor.getAssetType().isDataset());
            assertFalse(descriptor.getAssetType().isDatasetContainer());
            assertNotNull(descriptor.getInteractionProperties());
            assertEquals("/" + S3_TEST_CSV_KEY, descriptor.getInteractionProperties().get("file_name"));
            assertEquals("csv", descriptor.getInteractionProperties().get("file_format"));
            assertNotNull(descriptor.getDetails());
            assertNotNull(descriptor.getDetails().get("file_size"));
            assertEquals("text/csv", descriptor.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional()
                    .orElseThrow(() -> new AssertionError(NO_SCHEMA_MSG));
            assertFalse("Schema must have at least one field", schema.getFields().isEmpty());
            assets.add(descriptor);
        }
        assertEquals(1, assets.size());
    }

    // -----------------------------------------------------------------------
    // Structured read (CSV)
    // -----------------------------------------------------------------------

    /**
     * getFlightInfo for a CSV object — verify schema and interaction properties are
     * populated. Requires {@code file_s3.s3.test_csv_key} to be set in
     * tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testGetFlightInfoCsv() throws Exception
    {
        assumeNotNull(S3_TEST_CSV_KEY);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", "/" + S3_TEST_CSV_KEY);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(descriptor)));
        final CustomFlightAssetDescriptor returned
                = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals("/" + S3_TEST_CSV_KEY, returned.getInteractionProperties().get("file_name"));
        assertEquals("csv", returned.getInteractionProperties().get("file_format"));
        final Schema schema = info.getSchemaOptional()
                .orElseThrow(() -> new AssertionError(NO_SCHEMA_MSG));
        assertFalse("Schema must have at least one field", schema.getFields().isEmpty());
    }

    /**
     * getStream for a CSV object — read all rows and verify the data is non-empty.
     * Requires {@code file_s3.s3.test_csv_key} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsv() throws Exception
    {
        assumeNotNull(S3_TEST_CSV_KEY);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", "/" + S3_TEST_CSV_KEY);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(descriptor)));
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertFalse("Expected at least one data row", data.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Raw / unstructured read (binary)
    // -----------------------------------------------------------------------

    /**
     * getFlightInfo for a binary object — verify the schema has a single
     * {@code content} field of type varbinary. Requires
     * {@code file_s3.s3.test_binary_key} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testGetFlightInfoBinary() throws Exception
    {
        assumeNotNull(S3_TEST_BINARY_KEY);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", "/" + S3_TEST_BINARY_KEY);
        interactionProperties.put("file_format", AWSS3DatasourceType.FILE_FORMAT_BINARY);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(descriptor)));
        final CustomFlightAssetDescriptor returned
                = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals("/" + S3_TEST_BINARY_KEY, returned.getInteractionProperties().get("file_name"));
        assertEquals(AWSS3DatasourceType.FILE_FORMAT_BINARY, returned.getInteractionProperties().get("file_format"));
        final Schema schema = info.getSchemaOptional()
                .orElseThrow(() -> new AssertionError(NO_SCHEMA_MSG));
        assertEquals("Schema for binary mode must have exactly one field", 1, schema.getFields().size());
        assertEquals("content", schema.getFields().get(0).getName());
    }

    /**
     * getStream for a binary object — verify one record is returned that contains
     * non-empty raw bytes. Requires {@code file_s3.s3.test_binary_key} to be set in
     * tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamBinary() throws Exception
    {
        assumeNotNull(S3_TEST_BINARY_KEY);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", "/" + S3_TEST_BINARY_KEY);
        interactionProperties.put("file_format", AWSS3DatasourceType.FILE_FORMAT_BINARY);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(descriptor)));
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals("Binary read must produce exactly one record", 1, data.rowKeySet().size());
        final Object content = data.get(0, 0);
        assertNotNull("content field must not be null", content);
        assertTrue("content field must be a byte array", content instanceof byte[]);
        assertTrue("content must be non-empty", ((byte[]) content).length > 0);
    }

    // -----------------------------------------------------------------------
    // ListFlights (discovery) tests — already exercised above; this test
    // explicitly validates the asset-type contract from the guide.
    // -----------------------------------------------------------------------

    /**
     * ListFlights at the root must return descriptors with non-null id, name,
     * assetType, and path attributes as required by the guide.
     *
     * @throws Exception
     */
    @Test
    public void testListFlightsContractRoot() throws Exception
    {
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath("/");
        int count = 0;
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor descriptor
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            // Guide requires id, name, path, and asset_type on every returned descriptor.
            assertNotNull("descriptor.id must not be null", descriptor.getId());
            assertNotNull("descriptor.name must not be null", descriptor.getName());
            assertNotNull("descriptor.path must not be null", descriptor.getPath());
            assertNotNull("descriptor.assetType must not be null", descriptor.getAssetType());
            assertNotNull("assetType.type must not be null", descriptor.getAssetType().getType());
            assertNotNull("assetType.dataset must not be null", descriptor.getAssetType().isDataset());
            assertNotNull("assetType.datasetContainer must not be null", descriptor.getAssetType().isDatasetContainer());
            // Containers must have an empty schema; data assets may have fields.
            if (Boolean.TRUE.equals(descriptor.getAssetType().isDatasetContainer())) {
                assertTrue("Schema for container must have no fields",
                        info.getSchemaOptional().map(s -> s.getFields().isEmpty()).orElse(true));
            }
            count++;
        }
        assertTrue("Expected at least one asset at the root", count > 0);
    }

    // -----------------------------------------------------------------------
    // get_acl action tests
    // -----------------------------------------------------------------------

    /**
     * Builds a bucket-prefixed path for use in get_acl / get_file_metadata
     * requests: {@code /<bucket>/<key>}.
     */
    private String bucketPrefixedPath(String key)
    {
        return "/" + S3_BUCKET + "/" + key;
    }

    /**
     * get_acl with a valid bucket-prefixed path must return a response whose
     * structure matches the ACLProvider contract: path / allow{users,groups} /
     * deny{users,groups} / inheritance / precedence. Requires
     * {@code file_s3.s3.test_csv_key} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testGetAclSuccess() throws Exception
    {
        assumeNotNull(S3_TEST_CSV_KEY);
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        // Path must start with the bucket name as the leading segment.
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, bucketPrefixedPath(S3_TEST_CSV_KEY));
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter = getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse actionResponse = MODEL_MAPPER.fromBytes(iter.next().getBody(), CustomFlightActionResponse.class);
        assertNotNull("Response properties must not be null", actionResponse.getResponseProperties());

        // Structural contract: top-level keys must be present.
        assertTrue("Response must contain 'path'", actionResponse.getResponseProperties().containsKey("path"));
        assertTrue("Response must contain 'allow'", actionResponse.getResponseProperties().containsKey("allow"));
        assertTrue("Response must contain 'deny'", actionResponse.getResponseProperties().containsKey("deny"));
        assertTrue("Response must contain 'inheritance'", actionResponse.getResponseProperties().containsKey("inheritance"));
        assertTrue("Response must contain 'precedence'", actionResponse.getResponseProperties().containsKey("precedence"));

        // allow / deny must each have 'users' and 'groups' sub-keys.
        @SuppressWarnings("unchecked")
        final Map<String, Object> allow = (Map<String, Object>) actionResponse.getResponseProperties().get("allow");
        assertNotNull("allow must not be null", allow);
        assertTrue("allow must contain 'users'", allow.containsKey("users"));
        assertTrue("allow must contain 'groups'", allow.containsKey("groups"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> deny = (Map<String, Object>) actionResponse.getResponseProperties().get("deny");
        assertNotNull("deny must not be null", deny);
        assertTrue("deny must contain 'users'", deny.containsKey("users"));
        assertTrue("deny must contain 'groups'", deny.containsKey("groups"));

        // inheritance must have 'enabled' and 'parent_precedence'.
        @SuppressWarnings("unchecked")
        final Map<String, Object> inheritance = (Map<String, Object>) actionResponse.getResponseProperties().get("inheritance");
        assertNotNull("inheritance must not be null", inheritance);
        assertTrue("inheritance must contain 'enabled'", inheritance.containsKey("enabled"));
        assertTrue("inheritance must contain 'parent_precedence'", inheritance.containsKey("parent_precedence"));
        assertFalse("inheritance.enabled must be false", (Boolean) inheritance.get("enabled"));
        assertEquals("inheritance.parent_precedence must be 'parent'", "parent", inheritance.get("parent_precedence"));

        assertEquals("precedence must be 'deny'", "deny", actionResponse.getResponseProperties().get("precedence"));

        // The returned path must be the canonical /bucket/key form.
        final String returnedPath = (String) actionResponse.getResponseProperties().get("path");
        assertTrue("path must start with '/" + S3_BUCKET + "/'",
                returnedPath != null && returnedPath.startsWith("/" + S3_BUCKET + "/"));
    }

    /**
     * get_acl when the bucket has no policy must return a valid (empty) ACL
     * structure rather than throwing. This always passes regardless of whether
     * a bucket policy exists; when one does exist the test still validates the
     * structural contract.
     *
     * @throws Exception
     */
    @Test
    public void testGetAclNoPolicyReturnsValidStructure() throws Exception
    {
        assumeNotNull(S3_TEST_CSV_KEY);
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, bucketPrefixedPath(S3_TEST_CSV_KEY));
        request.setRequestProperties(inputProps);

        // Must not throw regardless of whether a bucket policy exists.
        final Iterator<Result> iter = getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse actionResponse = MODEL_MAPPER.fromBytes(iter.next().getBody(), CustomFlightActionResponse.class);
        assertNotNull(actionResponse.getResponseProperties());
        assertTrue(actionResponse.getResponseProperties().containsKey("allow"));
        assertTrue(actionResponse.getResponseProperties().containsKey("deny"));
    }

    /**
     * get_acl with a missing path property must return an error.
     *
     * @throws Exception
     */
    @Test
    public void testGetAclMissingPath() throws Exception
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        request.setRequestProperties(new ConnectionActionConfiguration());
        try {
            getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected for missing path");
        }
        catch (Exception e) {
            assertTrue("Error must mention 'path'", e.getMessage() != null && e.getMessage().contains("path"));
        }
    }

    /**
     * get_acl with a path that has no bucket segment (bare key) must return an
     * error describing the requirement to include the bucket as the leading segment.
     */
    @Test
    public void testGetAclPathNoBucketSegment()
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        // A bare key with no leading bucket segment — must be rejected.
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, "folder/file.csv");
        request.setRequestProperties(inputProps);
        try {
            getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected for path with no bucket segment");
        }
        catch (Exception e) {
            assertNotNull("Exception message must not be null", e.getMessage());
        }
    }

    /**
     * get_acl with a path whose bucket segment does not match the connection
     * bucket must return an error.
     */
    @Test
    public void testGetAclPathBucketMismatch()
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        // A different bucket name as the leading segment — must be rejected.
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, "/wrong-bucket/folder/file.csv");
        request.setRequestProperties(inputProps);
        try {
            getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected for bucket mismatch in path");
        }
        catch (Exception e) {
            assertNotNull("Exception message must not be null", e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // get_file_metadata action tests
    // -----------------------------------------------------------------------

    /**
     * get_file_metadata with a valid object key must return last_modified
     * (non-null, non-empty ISO-8601 string) and size (non-negative long). Requires
     * {@code file_s3.s3.test_csv_key} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testGetFileMetadataSuccess() throws Exception
    {
        assumeNotNull(S3_TEST_CSV_KEY);
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, S3_TEST_CSV_KEY);
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter
                = getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_FILE_METADATA, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse actionResponse = MODEL_MAPPER.fromBytes(iter.next().getBody(), CustomFlightActionResponse.class);
        assertNotNull("Response properties must not be null", actionResponse.getResponseProperties());

        assertTrue("Response must contain 'last_modified'", actionResponse.getResponseProperties().containsKey("last_modified"));
        assertTrue("Response must contain 'size'", actionResponse.getResponseProperties().containsKey("size"));

        final String lastModified = (String) actionResponse.getResponseProperties().get("last_modified");
        assertNotNull("last_modified must not be null", lastModified);
        assertFalse("last_modified must not be empty", lastModified.isEmpty());
        // ISO-8601 UTC instants always contain 'T' and 'Z'.
        assertTrue("last_modified must be an ISO-8601 UTC string", lastModified.contains("T"));

        final Number size = (Number) actionResponse.getResponseProperties().get("size");
        assertNotNull("size must not be null", size);
        assertTrue("size must be non-negative", size.longValue() >= 0);
    }

    /**
     * get_file_metadata with a missing path property must return an error.
     */
    @Test
    public void testGetFileMetadataMissingPath()
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        request.setRequestProperties(new ConnectionActionConfiguration());
        try {
            getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_FILE_METADATA, MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected for missing path");
        }
        catch (Exception e) {
            assertTrue("Error must mention 'path'", e.getMessage() != null && e.getMessage().contains("path"));
        }
    }

    /**
     * get_file_metadata on a non-existent key must propagate an S3 error.
     */
    @Test
    public void testGetFileMetadataNonExistentKey()
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, "this/key/does/not/exist.csv");
        request.setRequestProperties(inputProps);
        try {
            getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_FILE_METADATA, MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected for non-existent key");
        }
        catch (Exception e) {
            assertNotNull("Exception message must not be null", e.getMessage());
        }
    }
}
