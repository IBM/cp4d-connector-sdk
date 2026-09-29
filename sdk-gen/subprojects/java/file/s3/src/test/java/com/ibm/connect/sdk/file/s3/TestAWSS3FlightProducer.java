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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.Result;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;

import com.ibm.connect.sdk.test.TestConfig;
import com.ibm.connect.sdk.test.TestFlight;
import com.ibm.connect.sdk.test.file.FileTestSuite;
import com.ibm.connect.sdk.test.file.WorkDirManager;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionRequest;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

/**
 * Tests the Arrow Flight producer for the Amazon S3 connector.
 *
 * <p>All standard file connector tests (discovery contract, metadata, read,
 * write, paging) are inherited from {@link FileTestSuite}.
 * Write tests require {@code file_s3.test_work_dir} in {@code tests.properties}.
 *
 * <h3>Configuration — {@code tests.properties}</h3>
 * <p>Create the file {@code sdk-gen/tests.properties} (gitignored) and populate
 * it with the settings below.  Interpolation with {@code #ref#} tokens is
 * supported so common prefixes can be defined once.
 *
 * <pre>
 * # ── Amazon S3 connection ──────────────────────────────────────────────────
 * file_s3.s3.access_key_id=AKIA...
 * file_s3.s3.secret_access_key=...          # supports encrypted.{{ }} wrapper
 * file_s3.s3.bucket=my-test-bucket
 * file_s3.s3.region=us-east-1
 * # Optional — for S3-compatible stores (MinIO, LocalStack):
 * # file_s3.s3.endpoint_url=http://localhost:9000
 *
 * # ── Test data (objects that must exist in the bucket before running) ──────
 * file_s3.s3.test_base=test-data/
 * file_s3.s3.test_folder=#file_s3.s3.test_base#        # non-empty prefix
 * file_s3.s3.test_csv_key=#file_s3.s3.test_base#cars.csv
 * file_s3.s3.test_binary_key=#file_s3.s3.test_base#logo.png
 *
 * # ── Flight server ─────────────────────────────────────────────────────────
 * file_s3.flight.createLocal=true    # false = point at a deployed server
 * file_s3.flight.ssl=true
 * # file_s3.flight.port=             # blank = random free port
 * # file_s3.flight.uri=grpc+tls://host:port   (used when createLocal=false)
 * # file_s3.flight.ssl_certificate=           (PEM; used when createLocal=false)
 * file_s3.flight.ssl_certificate_validation=true
 * </pre>
 *
 * <p>All tests are skipped automatically when {@code file_s3.s3.access_key_id}
 * is absent from the configuration.
 */
public class TestAWSS3FlightProducer extends FileTestSuite
{
    private static final Logger LOGGER = getLogger(TestAWSS3FlightProducer.class);
    private static final String DATASOURCE_TYPE_NAME = AWSS3DatasourceType.DATASOURCE_TYPE_NAME;

    // -----------------------------------------------------------------------
    // Flight server / client lifecycle
    // -----------------------------------------------------------------------

    private static TestFlight testFlight;
    private static FlightClient client;
    private static final S3Config S3 = new S3Config();

    /** Skip every test when S3 credentials are absent from tests.properties. */
    @Before
    public void setUp()
    {
        assumeNotNull("S3 credentials not configured — set file_s3.s3.access_key_id in tests.properties",
                S3.accessKeyId);
    }

    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        if (S3.createLocal) {
            testFlight = TestFlight.createLocal(S3.port, S3.useSSL, new AWSS3FlightProducer(), null);
        } else {
            testFlight = TestFlight.createRemote(S3.remoteUri, S3.sslCert, S3.verifyCert, null);
        }
        client = testFlight.getClient();
    }

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

    // -----------------------------------------------------------------------
    // FileTestSuite abstract hooks
    // -----------------------------------------------------------------------

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
        if (S3.bucket != null) {
            props.put("bucket", S3.bucket);
        }
        if (S3.region != null) {
            props.put("region", S3.region);
        }
        if (S3.endpointUrl != null) {
            props.put("endpoint_url", S3.endpointUrl);
        }
        if (S3.accessKeyId != null) {
            props.put("access_key_id", S3.accessKeyId);
        }
        if (S3.secretAccessKey != null) {
            props.put("secret_access_key", S3.secretAccessKey);
        }
        return props;
    }

    /** Root "/" lists all objects and common-prefix folders at the bucket root. */
    @Override
    protected String getRootPath()
    {
        return "/";
    }

    /** A non-empty folder path used by container listing tests. */
    @Override
    protected String getContainerPath()
    {
        return S3.testFolder != null ? "/" + S3.testFolder : "/";
    }

    @Override
    protected DiscoveredAssetInteractionProperties createReadInteractionProperties()
    {
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        if (S3.testCsvKey != null) {
            props.put("file_name", "/" + S3.testCsvKey);
        }
        return props;
    }

    @Override
    protected String getKnownFilePath()
    {
        return S3.testCsvKey != null ? "/" + S3.testCsvKey : null;
    }

    @Override
    protected String getKnownFileName()
    {
        if (S3.testCsvKey == null) {
            return null;
        }
        final int slash = S3.testCsvKey.lastIndexOf('/');
        return slash >= 0 ? S3.testCsvKey.substring(slash + 1) : S3.testCsvKey;
    }

    /**
     * S3 write tests are enabled when a test work bucket is configured.
     * Returns {@code null} (skip) when credentials or work dir are absent.
     */
    @Override
    protected DiscoveredAssetInteractionProperties createWriteInteractionProperties(String uniqueSuffix)
    {
        final String workDir = getWorkDir();
        if (workDir == null) {
            return null;
        }
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", "/" + workDir + "/suite_write_" + uniqueSuffix + ".csv");
        props.put("first_line_header", "true");
        return props;
    }

    /** Opts into the work-directory isolation feature (test bucket). */
    @Override
    protected String getWorkDirConfigKey()
    {
        return "file_s3.test_work_dir";
    }

    // -----------------------------------------------------------------------
    // Scenario support
    // -----------------------------------------------------------------------

    /**
     * Returns scenario files for S3-specific behaviour.
     * Tests are skipped automatically when credentials are absent
     * (the {@link #setUp()} guard fires before {@code testScenarios} runs).
     * Write scenarios additionally require a configured test work bucket.
     */
    @Override
    protected List<String> getScenarioPaths()
    {
        if (!S3.isConfigured()) {
            return java.util.Collections.emptyList();
        }
        final List<String> paths = new ArrayList<>(Arrays.asList(
                "scenarios/s3/discover_root.scenario",
                "scenarios/s3/negative_missing_key.scenario"));
        // Write-dependent scenarios require a test work bucket
        if (getWorkDir() != null) {
            paths.addAll(Arrays.asList(
                    "scenarios/s3/discover_folder.scenario",
                    "scenarios/s3/metadata_csv.scenario",
                    "scenarios/s3/read_csv.scenario",
                    "scenarios/s3/read_binary.scenario",
                    "scenarios/s3/readwrite_csv.scenario"));
        }
        return paths;
    }

    // -----------------------------------------------------------------------
    // S3 WorkDirOperations — creates/empties/destroys a dedicated test bucket
    // -----------------------------------------------------------------------

    /**
     * {@link WorkDirManager.WorkDirOperations} implementation that uses the
     * AWS SDK v2 S3 client already created for the connector under test.
     *
     * <p>
     * This is called by {@link com.ibm.connect.sdk.test.file.VerifyWorkDir} and
     * {@link com.ibm.connect.sdk.test.file.CleanTestWorkDir} via a subclass-provided
     * factory registered in the build-specific Gradle tasks.
     * For the default case (HIERARCHICAL_FS / no custom operations) the
     * {@link WorkDirManager} handles everything itself. This inner class is
     * provided so that future Gradle task configuration for S3 can supply it.
     */
    static final class S3WorkDirOps implements WorkDirManager.WorkDirOperations
    {
        private final software.amazon.awssdk.services.s3.S3Client s3;

        S3WorkDirOps(software.amazon.awssdk.services.s3.S3Client s3)
        {
            this.s3 = s3;
        }

        @Override
        public void create(String name) throws Exception
        {
            s3.createBucket(b -> b.bucket(name));
        }

        @Override
        public boolean isEmpty(String name) throws Exception
        {
            final software.amazon.awssdk.services.s3.model.ListObjectsV2Response resp
                    = s3.listObjectsV2(b -> b.bucket(name).maxKeys(1));
            return resp.contents().isEmpty();
        }

        @Override
        public void emptyContainer(String name) throws Exception
        {
            deleteAllObjects(name);
        }

        @Override
        public void destroyContainer(String name) throws Exception
        {
            deleteAllObjects(name);
            s3.deleteBucket(b -> b.bucket(name));
        }

        private void deleteAllObjects(String bucket)
        {
            String continuationToken = null;
            do {
                final String token = continuationToken;
                final software.amazon.awssdk.services.s3.model.ListObjectsV2Response resp = s3.listObjectsV2(
                        b -> {
                            b.bucket(bucket);
                            if (token != null) {
                                b.continuationToken(token);
                            }
                        });
                if (!resp.contents().isEmpty()) {
                    final List<software.amazon.awssdk.services.s3.model.ObjectIdentifier> ids
                            = new ArrayList<>();
                    for (final software.amazon.awssdk.services.s3.model.S3Object obj : resp.contents()) {
                        ids.add(software.amazon.awssdk.services.s3.model.ObjectIdentifier.builder()
                                .key(obj.key()).build());
                    }
                    s3.deleteObjects(b -> b.bucket(bucket)
                            .delete(d -> d.objects(ids)));
                }
                continuationToken = resp.isTruncated() ? resp.nextContinuationToken() : null;
            }
            while (continuationToken != null);
        }
    }

    // -----------------------------------------------------------------------
    // Validation tests
    // -----------------------------------------------------------------------

    /** Validate action must fail when the required bucket property is missing. */
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
    // get_acl action tests
    // -----------------------------------------------------------------------

    /** Builds a bucket-prefixed path: {@code /<bucket>/<key>}. */
    private String bucketPrefixedPath(String key)
    {
        return "/" + S3.bucket + "/" + key;
    }

    /**
     * get_acl with a valid bucket-prefixed path must return a structurally complete
     * ACL response.
     * Requires {@code file_s3.s3.test_csv_key} in tests.properties.
     */
    @Test
    public void testGetAclSuccess() throws Exception
    {
        assumeNotNull(S3.testCsvKey);
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, bucketPrefixedPath(S3.testCsvKey));
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter = getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse actionResponse
                = MODEL_MAPPER.fromBytes(iter.next().getBody(), CustomFlightActionResponse.class);
        assertNotNull("Response properties must not be null", actionResponse.getResponseProperties());

        assertTrue(actionResponse.getResponseProperties().containsKey("path"));
        assertTrue(actionResponse.getResponseProperties().containsKey("allow"));
        assertTrue(actionResponse.getResponseProperties().containsKey("deny"));
        assertTrue(actionResponse.getResponseProperties().containsKey("inheritance"));
        assertTrue(actionResponse.getResponseProperties().containsKey("precedence"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> allow = (Map<String, Object>) actionResponse.getResponseProperties().get("allow");
        assertNotNull(allow);
        assertTrue(allow.containsKey("users"));
        assertTrue(allow.containsKey("groups"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> deny = (Map<String, Object>) actionResponse.getResponseProperties().get("deny");
        assertNotNull(deny);
        assertTrue(deny.containsKey("users"));
        assertTrue(deny.containsKey("groups"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> inheritance
                = (Map<String, Object>) actionResponse.getResponseProperties().get("inheritance");
        assertNotNull(inheritance);
        assertTrue(inheritance.containsKey("enabled"));
        assertTrue(inheritance.containsKey("parent_precedence"));
        assertFalse((Boolean) inheritance.get("enabled"));
        assertEquals("parent", inheritance.get("parent_precedence"));
        assertEquals("deny", actionResponse.getResponseProperties().get("precedence"));

        final String returnedPath = (String) actionResponse.getResponseProperties().get("path");
        assertTrue("path must start with '/" + S3.bucket + "/'",
                returnedPath != null && returnedPath.startsWith("/" + S3.bucket + "/"));
    }

    /**
     * get_acl when the bucket has no policy must return a valid (empty) ACL structure.
     */
    @Test
    public void testGetAclNoPolicyReturnsValidStructure() throws Exception
    {
        assumeNotNull(S3.testCsvKey);
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, bucketPrefixedPath(S3.testCsvKey));
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter = getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse actionResponse
                = MODEL_MAPPER.fromBytes(iter.next().getBody(), CustomFlightActionResponse.class);
        assertNotNull(actionResponse.getResponseProperties());
        assertTrue(actionResponse.getResponseProperties().containsKey("allow"));
        assertTrue(actionResponse.getResponseProperties().containsKey("deny"));
    }

    /** get_acl with a missing path property must return an error. */
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

    /** get_acl with a bare key (no leading bucket segment) must return an error. */
    @Test
    public void testGetAclPathNoBucketSegment()
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, "folder/file.csv");
        request.setRequestProperties(inputProps);
        try {
            getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected for path with no bucket segment");
        }
        catch (Exception e) {
            assertNotNull(e.getMessage());
        }
    }

    /** get_acl with a mismatched bucket segment must return an error. */
    @Test
    public void testGetAclPathBucketMismatch()
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, "/wrong-bucket/folder/file.csv");
        request.setRequestProperties(inputProps);
        try {
            getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected for bucket mismatch in path");
        }
        catch (Exception e) {
            assertNotNull(e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // get_file_metadata action tests
    // -----------------------------------------------------------------------

    /**
     * get_file_metadata must return {@code last_modified} and {@code size}.
     * Requires {@code file_s3.s3.test_csv_key} in tests.properties.
     */
    @Test
    public void testGetFileMetadataSuccess() throws Exception
    {
        assumeNotNull(S3.testCsvKey);
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, S3.testCsvKey);
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter
                = getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_FILE_METADATA, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse actionResponse
                = MODEL_MAPPER.fromBytes(iter.next().getBody(), CustomFlightActionResponse.class);
        assertNotNull(actionResponse.getResponseProperties());

        assertTrue(actionResponse.getResponseProperties().containsKey("last_modified"));
        assertTrue(actionResponse.getResponseProperties().containsKey("size"));

        final String lastModified = (String) actionResponse.getResponseProperties().get("last_modified");
        assertNotNull(lastModified);
        assertFalse(lastModified.isEmpty());
        assertTrue("last_modified must be an ISO-8601 UTC string", lastModified.contains("T"));

        final Number size = (Number) actionResponse.getResponseProperties().get("size");
        assertNotNull(size);
        assertTrue("size must be non-negative", size.longValue() >= 0);
    }

    /** get_file_metadata with a missing path property must return an error. */
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

    /** get_file_metadata on a non-existent key must propagate an S3 error. */
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
            assertNotNull(e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Connector-scoped configuration — all sourced from tests.properties
    // -----------------------------------------------------------------------

    /**
     * All S3 test settings, resolved from {@code tests.properties} at class-load
     * time.  Keeping them in one place makes it easy to see what keys are required
     * and which are optional.
     */
    private static final class S3Config
    {
        // Connection
        final String accessKeyId     = TestConfig.get("file_s3.s3.access_key_id");
        final String secretAccessKey = TestConfig.get("file_s3.s3.secret_access_key");
        final String bucket          = TestConfig.get("file_s3.s3.bucket");
        final String region          = TestConfig.get("file_s3.s3.region");
        final String endpointUrl     = TestConfig.get("file_s3.s3.endpoint_url");   // null = real AWS

        // Test data — objects that must exist before running
        final String testFolder    = TestConfig.get("file_s3.s3.test_folder");     // non-empty prefix
        final String testCsvKey    = TestConfig.get("file_s3.s3.test_csv_key");
        final String testBinaryKey = TestConfig.get("file_s3.s3.test_binary_key");

        // Flight server
        final boolean createLocal = TestConfig.getBoolean("file_s3.flight.createLocal", true);
        final boolean useSSL      = TestConfig.getBoolean("file_s3.flight.ssl", true);
        final int     port        = TestConfig.getPort("file_s3.flight.port");

        // Remote server (only when createLocal=false)
        final String remoteUri    = TestConfig.get("file_s3.flight.uri");
        final String sslCert      = TestConfig.get("file_s3.flight.ssl_certificate");
        final boolean verifyCert  = TestConfig.getBoolean("file_s3.flight.ssl_certificate_validation", true);

        /** Returns true when the minimum required S3 credentials are present. */
        boolean isConfigured()
        {
            return accessKeyId != null;
        }
    }
}
