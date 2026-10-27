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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.ibm.connect.sdk.test.file.TestScenario;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionRequest;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Tests the Arrow Flight producer for the Amazon S3 connector.
 *
 * <p>
 * All standard file connector tests (discovery contract, metadata, read, write,
 * paging) are inherited from {@link FileTestSuite}. The S3 connector is
 * currently read-only (no {@code putStream} support), so write tests are
 * skipped automatically.
 *
 * <p>
 * Test data is managed directly via the AWS SDK — a seed CSV is uploaded to a
 * unique prefix inside the configured bucket in {@code @BeforeClass} and
 * deleted in {@code @AfterClass}. This requires only {@code s3:PutObject},
 * {@code s3:GetObject}, {@code s3:ListBucket}, and {@code s3:DeleteObject}
 * permissions — no {@code s3:CreateBucket}.
 *
 * <h3>Configuration — {@code tests.properties}</h3>
 * 
 * <pre>
 * # ── Amazon S3 connection ──────────────────────────────────────────────────
 * file.s3.access_key_id=AKIA...
 * file.s3.secret_access_key=...
 * file.s3.bucket=my-test-bucket
 * file.s3.region=us-east-1
 * # Optional — for S3-compatible stores (MinIO, LocalStack):
 * # file.s3.endpoint_url=http://localhost:9000
 *
 * # ── Flight server ─────────────────────────────────────────────────────────
 * flight.createLocal=true
 * flight.ssl=true
 * </pre>
 *
 * <p>
 * All tests are skipped automatically when {@code file.s3.access_key_id} is
 * absent from the configuration.
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

    /**
     * AWS SDK client used to upload/delete test data directly — bypasses the Flight
     * connector (which is read-only).
     */
    private static S3Client s3Client;

    /**
     * Unique prefix used for all test objects in this run, e.g.
     * {@code sdk-test-runs/a1b2c3d4/}. Deleted in {@code @AfterClass}.
     */
    private static String workPrefix;

    /**
     * Key of the seed CSV uploaded in {@code @BeforeClass}. Used as the "known
     * readable file" for all inherited read tests.
     */
    private static String seedCsvKey;

    /** Skip every test when S3 credentials are absent from tests.properties. */
    @Before
    public void setUp()
    {
        assumeNotNull("S3 credentials not configured — set file.s3.access_key_id in tests.properties", S3.accessKeyId);
    }

    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        if (S3.flight.createLocal) {
            testFlight = TestFlight.createLocal(S3.flight.port, S3.flight.useSSL, new AWSS3FlightProducer(), null);
        } else {
            testFlight = TestFlight.createRemote(S3.flight.remoteUri, S3.flight.sslCertificate, S3.flight.verifyCertificate, null);
        }
        client = testFlight.getClient();

        if (S3.isConfigured()) {
            s3Client = buildS3Client();
            // Allocate a unique prefix so this run is isolated from any other data.
            workPrefix = "sdk-test-runs/" + UUID.randomUUID();
            seedCsvKey = workPrefix + "/suite_known_read.csv";
            // Upload seed CSV directly via AWS SDK — no Flight putStream needed.
            s3Client.putObject(b -> b.bucket(S3.bucket).key(seedCsvKey),
                    RequestBody.fromString("year,make,model\n2012,Tesla,S\n2015,Chevy,Volt", StandardCharsets.UTF_8));
            LOGGER.info("Uploaded seed CSV to s3://{}/{}", S3.bucket, seedCsvKey);
        }
    }

    @AfterClass
    public static void tearDownOnce()
    {
        if (s3Client != null) {
            // Delete every object under the work prefix.
            try {
                deletePrefix(s3Client, S3.bucket, workPrefix + "/");
            }
            catch (Exception e) {
                LOGGER.error("Failed to clean S3 work prefix {}: {}", workPrefix, e.getMessage(), e);
            }
            s3Client.close();
        }
        try {
            testFlight.close();
        }
        catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /** Deletes all objects under {@code prefix} (inclusive) in the given bucket. */
    private static void deletePrefix(S3Client s3, String bucket, String prefix)
    {
        String token = null;
        do {
            final String t = token;
            final ListObjectsV2Response resp = s3.listObjectsV2(b -> {
                b.bucket(bucket).prefix(prefix);
                if (t != null) {
                    b.continuationToken(t);
                }
            });
            if (!resp.contents().isEmpty()) {
                final List<ObjectIdentifier> ids = new ArrayList<>();
                for (final S3Object obj : resp.contents()) {
                    ids.add(ObjectIdentifier.builder().key(obj.key()).build());
                }
                s3.deleteObjects(b -> b.bucket(bucket).delete(Delete.builder().objects(ids).build()));
            }
            token = resp.isTruncated() ? resp.nextContinuationToken() : null;
        } while (token != null);
    }

    /** Builds an S3Client from the test configuration. */
    private static S3Client buildS3Client()
    {
        final S3ClientBuilder builder = S3Client.builder();
        builder.region(S3.region != null ? Region.of(S3.region) : Region.US_EAST_1);
        if (S3.endpointUrl != null) {
            builder.endpointOverride(URI.create(S3.endpointUrl));
            builder.forcePathStyle(true);
        }
        if (S3.accessKeyId != null && S3.secretAccessKey != null) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(S3.accessKeyId, S3.secretAccessKey)));
        }
        return builder.build();
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

    /**
     * Container path points at the work prefix so listing finds the seed file.
     */
    @Override
    protected String getContainerPath()
    {
        return workPrefix != null ? "/" + workPrefix : "/";
    }

    @Override
    protected DiscoveredAssetInteractionProperties createReadInteractionProperties()
    {
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        if (seedCsvKey != null) {
            props.put("file_name", "/" + seedCsvKey);
        }
        return props;
    }

    @Override
    protected String getKnownFilePath()
    {
        return seedCsvKey != null ? "/" + seedCsvKey : null;
    }

    @Override
    protected String getKnownFileName()
    {
        return "suite_known_read.csv";
    }

    /**
     * S3 connector is read-only — write tests are always skipped.
     */
    @Override
    protected DiscoveredAssetInteractionProperties createWriteInteractionProperties(String uniqueSuffix)
    {
        return null;
    }

    // -----------------------------------------------------------------------
    // Scenario support
    // -----------------------------------------------------------------------

    /**
     * Returns a {@link TestScenario} pre-wired with the S3 put hook so that
     * {@code Type=Write} steps in scenario files are executed via the AWS SDK
     * instead of the Flight connector (which is read-only).
     */
    @Override
    protected TestScenario scenario()
    {
        final Map<String, String> vars = new LinkedHashMap<>();
        if (workPrefix != null) {
            vars.put("work_dir", workPrefix);
        }
        return super.scenario().withVars(vars).withPutHook(createS3PutHook());
    }

    /**
     * Creates a {@link com.ibm.connect.sdk.test.file.TestScenario.PutHook} that
     * uploads CSV or binary content directly to S3 via the AWS SDK.
     *
     * <p>
     * Supports {@code file_format=binary} (base-64 decoded) and all CSV-like
     * formats (columns serialised as a header row + data rows).
     */
    private TestScenario.PutHook createS3PutHook()
    {
        return (iprops, schemaSpec, rows, nullToken) -> {
            // file_name is "/<key>" — strip the leading slash to get the S3 key.
            final String rawName = (String) iprops.get("file_name");
            final String key = rawName != null && rawName.startsWith("/") ? rawName.substring(1) : rawName;
            final String fileFormat = (String) iprops.getOrDefault("file_format", "csv");

            final byte[] body;
            if ("binary".equalsIgnoreCase(fileFormat) && !rows.isEmpty() && rows.get(0).length > 0) {
                // Single varbinary column — value is base-64 encoded in the scenario file.
                body = Base64.getDecoder().decode(rows.get(0)[0]);
            } else {
                body = toCsvBytes(schemaSpec, rows, nullToken);
            }

            s3Client.putObject(b -> b.bucket(S3.bucket).key(key), RequestBody.fromBytes(body));
            LOGGER.info("S3PutHook: uploaded s3://{}/{} ({} bytes)", S3.bucket, key, body.length);
        };
    }

    /**
     * Serialises a typed row set to UTF-8 CSV bytes. The header is derived from the
     * schema spec ({@code "name|type"} pairs). Null values (matching
     * {@code nullToken}) are emitted as empty fields.
     */
    private static byte[] toCsvBytes(List<String> schemaSpec, List<String[]> rows, String nullToken)
    {
        final StringJoiner csv = new StringJoiner("\n", "", "\n");

        // Header row — extract column name (before the '|') from each spec entry.
        csv.add(schemaSpec.stream().map(spec -> spec.split("\\|")[0].trim()).collect(Collectors.joining(",")));

        // Data rows — null-token values become empty fields.
        for (final String[] row : rows) {
            csv.add(Arrays.stream(row).map(v -> nullToken.equals(v) ? "" : v).collect(Collectors.joining(",")));
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected String getScenarioPrefix()
    {
        return "s3";
    }

    @Override
    protected List<String> getScenarioPaths()
    {
        if (!S3.isConfigured()) {
            return Collections.emptyList();
        }
        return scenarioPaths(
                "discover_root.scenario",
                "negative_missing_key.scenario",
                "discover_folder.scenario",
                "metadata_csv.scenario",
                "read_csv.scenario",
                "read_csv_no_header.scenario",
                "read_csv_null_value.scenario",
                "read_csv_row_delimiter.scenario",
                "read_csv_infer_schema.scenario",
                "read_csv_encoding.scenario",
                "read_delimited_pipe.scenario",
                "read_binary.scenario",
                "readwrite_csv.scenario");
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
     */
    @Test
    public void testGetAclSuccess() throws Exception
    {
        assumeNotNull(seedCsvKey);
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, bucketPrefixedPath(seedCsvKey));
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter = getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse actionResponse = MODEL_MAPPER.fromBytes(iter.next().getBody(), CustomFlightActionResponse.class);
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
        final Map<String, Object> inheritance = (Map<String, Object>) actionResponse.getResponseProperties().get("inheritance");
        assertNotNull(inheritance);
        assertTrue(inheritance.containsKey("enabled"));
        assertTrue(inheritance.containsKey("parent_precedence"));
        assertFalse((Boolean) inheritance.get("enabled"));
        assertEquals("parent", inheritance.get("parent_precedence"));
        assertEquals("deny", actionResponse.getResponseProperties().get("precedence"));

        final String returnedPath = (String) actionResponse.getResponseProperties().get("path");
        assertTrue("path must start with '/" + S3.bucket + "/'", returnedPath != null && returnedPath.startsWith("/" + S3.bucket + "/"));
    }

    /**
     * get_acl when the bucket has no policy must return a valid (empty) ACL
     * structure.
     */
    @Test
    public void testGetAclNoPolicyReturnsValidStructure() throws Exception
    {
        assumeNotNull(seedCsvKey);
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, bucketPrefixedPath(seedCsvKey));
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter = getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse actionResponse = MODEL_MAPPER.fromBytes(iter.next().getBody(), CustomFlightActionResponse.class);
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
     */
    @Test
    public void testGetFileMetadataSuccess() throws Exception
    {
        assumeNotNull(seedCsvKey);
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(AWSS3Connector.ACTION_PATH_PROP, seedCsvKey);
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter
                = getClient().doAction(new Action(AWSS3DatasourceType.ACTION_GET_FILE_METADATA, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse actionResponse = MODEL_MAPPER.fromBytes(iter.next().getBody(), CustomFlightActionResponse.class);
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

    private static final class S3Config
    {
        final String accessKeyId = TestConfig.get("file.s3.access_key_id");
        final String secretAccessKey = TestConfig.get("file.s3.secret_access_key");
        final String bucket = TestConfig.get("file.s3.bucket");
        final String region = TestConfig.get("file.s3.region");
        final String endpointUrl = TestConfig.get("file.s3.endpoint_url");

        final TestConfig.FlightConfig flight = TestConfig.flightConfig("file.s3");

        boolean isConfigured()
        {
            return accessKeyId != null;
        }
    }
}
