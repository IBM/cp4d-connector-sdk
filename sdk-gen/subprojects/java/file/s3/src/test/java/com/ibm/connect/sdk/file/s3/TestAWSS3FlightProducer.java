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
import java.util.List;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightInfo;
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
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

/**
 * Tests a Flight producer for the Amazon S3 connector.
 *
 * <p>To run against a real S3 bucket, create the file
 * {@code sdk-gen/tests.properties} (gitignored) and populate it:
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
 * <p>All tests are skipped automatically when {@code file_s3.s3.access_key_id}
 * is not set.
 */
public class TestAWSS3FlightProducer extends ConnectorTestSuite
{
    private static final Logger LOGGER = getLogger(TestAWSS3FlightProducer.class);

    private static final String DATASOURCE_TYPE_NAME = AWSS3DatasourceType.DATASOURCE_TYPE_NAME;

    // -----------------------------------------------------------------------
    // Test configuration — loaded once from tests.properties (gitignored)
    // -----------------------------------------------------------------------
    private static final String S3_ACCESS_KEY_ID   = TestConfig.get("file_s3.s3.access_key_id");
    private static final String S3_SECRET_ACCESS_KEY = TestConfig.get("file_s3.s3.secret_access_key");
    private static final String S3_BUCKET         = TestConfig.get("file_s3.s3.bucket");
    private static final String S3_REGION         = TestConfig.get("file_s3.s3.region");
    private static final String S3_ENDPOINT_URL   = TestConfig.get("file_s3.s3.endpoint_url");

    /** S3 prefix (key ending with "/") of a folder that contains at least one object. */
    private static final String S3_TEST_FOLDER    = TestConfig.get("file_s3.s3.test_folder");
    /** S3 key of a CSV object to use for structured read tests. */
    private static final String S3_TEST_CSV_KEY   = TestConfig.get("file_s3.s3.test_csv_key");
    /** S3 key of any binary object to use for raw-bytes read tests. */
    private static final String S3_TEST_BINARY_KEY = TestConfig.get("file_s3.s3.test_binary_key");

    // -----------------------------------------------------------------------
    // Flight server / client
    // -----------------------------------------------------------------------
    private static ModelMapper modelMapper = new ModelMapper();
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
            testFlight = TestFlight.createLocal(
                    TestConfig.getPort("file_s3.flight.port"), useSSL, new AWSS3FlightProducer(), null);
        } else {
            final boolean verifyCert = Boolean.parseBoolean(
                    TestConfig.get("file_s3.flight.ssl_certificate_validation", "true"));
            testFlight = TestFlight.createRemote(
                    TestConfig.get("file_s3.flight.uri.internal", TestConfig.get("file_s3.flight.uri")),
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
     *
     * @throws Exception
     */
    @Test
    public void testConnectionMissingBucket() throws Exception
    {
        final com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionRequest request
                = new com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        request.getConnectionProperties().remove("bucket");
        try {
            getClient().doAction(new Action("validate", modelMapper.toBytes(request))).next();
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
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
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
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            names.add(descriptor.getName());
        }
        assertTrue("Expected at most 2 results", names.size() <= 2);
    }

    /**
     * List the contents of a specific folder (prefix).
     * Requires {@code file_s3.s3.test_folder} to be set in tests.properties.
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
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
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
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
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
            final Schema schema = info.getSchemaOptional().get();
            assertFalse("Schema must have at least one field", schema.getFields().isEmpty());
            assets.add(descriptor);
        }
        assertEquals(1, assets.size());
    }

    // -----------------------------------------------------------------------
    // Structured read (CSV)
    // -----------------------------------------------------------------------

    /**
     * getFlightInfo for a CSV object — verify schema and interaction properties
     * are populated.
     * Requires {@code file_s3.s3.test_csv_key} to be set in tests.properties.
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
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final CustomFlightAssetDescriptor returned
                = modelMapper.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals("/" + S3_TEST_CSV_KEY, returned.getInteractionProperties().get("file_name"));
        assertEquals("csv", returned.getInteractionProperties().get("file_format"));
        final Schema schema = info.getSchemaOptional().get();
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
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertFalse("Expected at least one data row", data.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Raw / unstructured read (binary)
    // -----------------------------------------------------------------------

    /**
     * getFlightInfo for a binary object — verify the schema has a single
     * {@code content} field of type varbinary.
     * Requires {@code file_s3.s3.test_binary_key} to be set in tests.properties.
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
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final CustomFlightAssetDescriptor returned
                = modelMapper.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals("/" + S3_TEST_BINARY_KEY, returned.getInteractionProperties().get("file_name"));
        assertEquals(AWSS3DatasourceType.FILE_FORMAT_BINARY, returned.getInteractionProperties().get("file_format"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals("Schema for binary mode must have exactly one field", 1, schema.getFields().size());
        assertEquals("content", schema.getFields().get(0).getName());
    }

    /**
     * getStream for a binary object — verify one record is returned that contains
     * non-empty raw bytes.
     * Requires {@code file_s3.s3.test_binary_key} to be set in tests.properties.
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
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals("Binary read must produce exactly one record", 1, data.rowKeySet().size());
        final Object content = data.get(0, 0);
        assertNotNull("content field must not be null", content);
        assertTrue("content field must be a byte array", content instanceof byte[]);
        assertTrue("content must be non-empty", ((byte[]) content).length > 0);
    }
}
