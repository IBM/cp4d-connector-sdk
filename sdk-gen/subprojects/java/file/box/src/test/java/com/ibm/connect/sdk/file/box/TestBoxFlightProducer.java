/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.box;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.AsyncPutListener;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.Result;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;

import com.ibm.connect.sdk.api.ArrowConversions;
import com.ibm.connect.sdk.test.TestConfig;
import com.ibm.connect.sdk.test.TestFlight;
import com.ibm.connect.sdk.test.file.FileTestSuite;
import com.ibm.connect.sdk.test.file.TestScenario;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionRequest;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

/**
 * Tests the Arrow Flight producer for the Box connector.
 *
 * <p>
 * All standard file connector tests (discovery contract, metadata, read, write,
 * paging) are inherited from {@link FileTestSuite}.
 *
 * <h3>Configuration — {@code tests.properties}</h3>
 *
 * <pre>
 * # ── Box connection ────────────────────────────────────────────────────────
 * file.box.client_id=
 * file.box.client_secret=
 * file.box.enterprise_id=
 * file.box.private_key=
 * file.box.private_key_password=   # optional
 * file.box.public_key=
 * file.box.username=               # optional — email of user to impersonate
 *
 * # ── Test work folder (isolated write/delete sandbox) ─────────────────────
 * # A pre-existing Box folder path, e.g. "sdk-test-runs".
 * # When absent, write scenarios and write tests are skipped automatically.
 * file.box.test_work_dir=sdk-test-runs
 *
 * # ── Flight server ─────────────────────────────────────────────────────────
 * flight.createLocal=true
 * flight.ssl=true
 * </pre>
 *
 * <p>
 * All tests are skipped automatically when {@code file.box.client_id} is
 * absent from the configuration.
 */
public class TestBoxFlightProducer extends FileTestSuite
{
    private static final Logger LOGGER = getLogger(TestBoxFlightProducer.class);
    private static final String DATASOURCE_TYPE_NAME = BoxDatasourceType.DATASOURCE_TYPE_NAME;

    private static TestFlight testFlight;
    private static FlightClient client;

    private static final String BOX_CLIENT_ID = TestConfig.get("file.box.client_id");
    private static final String BOX_CLIENT_SECRET = TestConfig.get("file.box.client_secret");
    private static final String BOX_ENTERPRISE_ID = TestConfig.get("file.box.enterprise_id");
    private static final String BOX_PRIVATE_KEY = TestConfig.get("file.box.private_key");
    private static final String BOX_PRIVATE_KEY_PASSWORD = TestConfig.get("file.box.private_key_password");
    private static final String BOX_PUBLIC_KEY = TestConfig.get("file.box.public_key");
    private static final String BOX_USERNAME = TestConfig.get("file.box.username");

    /** Content of the seed CSV that the inherited read tests use as the "known readable file". */
    private static final String SEED_CSV = "year,make,model\n2012,Tesla,S\n2015,Chevy,Volt";

    private static boolean seeded;

    /**
     * Skip every test when Box credentials are absent from tests.properties, and
     * make sure the known read file exists in the work folder.
     */
    @Before
    public void setUp() throws Exception
    {
        assumeNotNull("Box credentials not configured — set file.box.client_id in tests.properties", BOX_CLIENT_ID);
        seedKnownFile();
    }

    /**
     * Uploads {@code suite_known_read.csv} to the Box work folder once per run,
     * directly through the Box SDK (no Flight put needed). Existing files are
     * replaced with a new version, so repeated runs do not accumulate files.
     */
    private void seedKnownFile() throws Exception
    {
        final String knownFilePath = getKnownFilePath();
        if (seeded || knownFilePath == null) {
            return;
        }
        try (BoxConnector connector = new BoxConnector(createConnectionProperties())) {
            connector.connect();
            connector.uploadFile(knownFilePath, new ByteArrayInputStream(SEED_CSV.getBytes(StandardCharsets.UTF_8)));
        }
        seeded = true;
        LOGGER.info("Seeded Box file {}", knownFilePath);
    }

    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        final boolean createLocal = Boolean.parseBoolean(TestConfig.get("file.box.flight.createLocal",
                TestConfig.get("flight.createLocal", "true")));
        if (createLocal) {
            final boolean useSSL = Boolean.parseBoolean(TestConfig.get("file.box.flight.ssl",
                    TestConfig.get("flight.ssl", "true")));
            testFlight = TestFlight.createLocal(TestConfig.getPort("file.box.flight.port"), useSSL,
                    new BoxFlightProducer(), null);
        } else {
            final boolean verifyCert = Boolean.parseBoolean(
                    TestConfig.get("file.box.flight.ssl_certificate_validation",
                            TestConfig.get("flight.ssl_certificate_validation", "true")));
            final String uri = TestConfig.get("file.box.flight.uri.internal",
                    TestConfig.get("file.box.flight.uri"));
            final String cert = TestConfig.get("file.box.flight.ssl_certificate",
                    TestConfig.get("flight.ssl_certificate"));
            testFlight = TestFlight.createRemote(uri, cert, verifyCert, null);
        }
        client = testFlight.getClient();
    }

    @AfterClass
    public static void tearDownOnce()
    {
        cleanUpWorkDir();
        try {
            testFlight.close();
        }
        catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Names of the files and folders that the scenarios and inherited tests create
     * in the work folder. Only these are removed, because the work folder itself
     * is pre-existing and may hold other content.
     */
    private static boolean isTestArtifact(String name)
    {
        return name.startsWith("suite_") || name.endsWith("_test.csv") || "test_binary.bin".equals(name) || "test_folder".equals(name);
    }

    /** Deletes the test artifacts from the Box work folder. Failures are only logged. */
    private static void cleanUpWorkDir()
    {
        final String configured = TestConfig.get("file.box.test_work_dir");
        if (BOX_CLIENT_ID == null || StringUtils.isBlank(configured)) {
            return;
        }
        final ConnectionProperties props = new ConnectionProperties();
        props.put("client_id", BOX_CLIENT_ID);
        props.put("client_secret", BOX_CLIENT_SECRET);
        props.put("enterprise_id", BOX_ENTERPRISE_ID);
        props.put("private_key", BOX_PRIVATE_KEY);
        if (BOX_PRIVATE_KEY_PASSWORD != null) {
            props.put("private_key_password", BOX_PRIVATE_KEY_PASSWORD);
        }
        props.put("public_key", BOX_PUBLIC_KEY);
        if (BOX_USERNAME != null) {
            props.put("username", BOX_USERNAME);
        }
        try (BoxConnector connector = new BoxConnector(props)) {
            connector.connect();
            final int deleted = connector.deleteChildren(configured.trim(), TestBoxFlightProducer::isTestArtifact);
            LOGGER.info("Removed {} test artifact(s) from Box folder {}", deleted, configured.trim());
        }
        catch (Exception e) {
            LOGGER.error("Failed to clean Box work folder {}: {}", configured, e.getMessage(), e);
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
        if (BOX_CLIENT_ID != null) {
            props.put("client_id", BOX_CLIENT_ID);
        }
        if (BOX_CLIENT_SECRET != null) {
            props.put("client_secret", BOX_CLIENT_SECRET);
        }
        if (BOX_ENTERPRISE_ID != null) {
            props.put("enterprise_id", BOX_ENTERPRISE_ID);
        }
        if (BOX_PRIVATE_KEY != null) {
            props.put("private_key", BOX_PRIVATE_KEY);
        }
        if (BOX_PRIVATE_KEY_PASSWORD != null) {
            props.put("private_key_password", BOX_PRIVATE_KEY_PASSWORD);
        }
        if (BOX_PUBLIC_KEY != null) {
            props.put("public_key", BOX_PUBLIC_KEY);
        }
        if (BOX_USERNAME != null) {
            props.put("username", BOX_USERNAME);
        }
        return props;
    }

    @Override
    protected String getRootPath()
    {
        return "/";
    }

    @Override
    protected String getContainerPath()
    {
        final String workDir = getWorkDir();
        return workDir != null ? "/" + workDir : "/";
    }

    @Override
    protected DiscoveredAssetInteractionProperties createReadInteractionProperties()
    {
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        final String workDir = getWorkDir();
        if (workDir != null) {
            props.put("file_name", "/" + workDir + "/suite_known_read.csv");
        }
        return props;
    }

    @Override
    protected String getKnownFilePath()
    {
        final String workDir = getWorkDir();
        return workDir != null ? "/" + workDir + "/suite_known_read.csv" : null;
    }

    @Override
    protected String getKnownFileName()
    {
        return "suite_known_read.csv";
    }

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

    @Override
    protected String getWorkDirConfigKey()
    {
        return "file.box.test_work_dir";
    }

    @Override
    protected TestScenario.WriteHook createScenarioWriteHook()
    {
        return (flightClient, modelMapper, dsTypeName, connProps, iprops, schemaSpec, rows, nullToken) -> {
            final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
            descriptor.setDatasourceTypeName(dsTypeName);
            descriptor.setConnectionProperties(connProps);
            descriptor.setInteractionProperties(iprops);
            for (final String colDef : schemaSpec) {
                final String[] parts = colDef.split("\\|");
                if (parts.length >= 2) {
                    descriptor.addFieldsItem(
                            new CustomFlightAssetField().name(parts[0].trim()).type(parts[1].trim()).nullable(true).signed(true));
                }
            }
            try (BufferAllocator alloc = new RootAllocator()) {
                final Schema arrowSchema = ArrowConversions.toArrow(descriptor.getFields());
                try (VectorSchemaRoot root = VectorSchemaRoot.create(arrowSchema, alloc)) {
                    final ArrowConversions.ArrowFieldSetter setter = new ArrowConversions.ArrowFieldSetter(root);
                    for (int ri = 0; ri < rows.size(); ri++) {
                        setter.setVectorIndex(ri);
                        final String[] cols = rows.get(ri);
                        for (int ci = 0; ci < cols.length; ci++) {
                            final String val = cols[ci];
                            if (nullToken.equals(val)) {
                                setter.setNull(ci);
                            } else {
                                setter.setValue(ci, coerceValue(val, schemaSpec.get(ci)));
                            }
                        }
                    }
                    root.setRowCount(rows.size());
                    final FlightClient.ClientStreamListener put = flightClient
                            .startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)), root, new AsyncPutListener());
                    put.putNext();
                    root.clear();
                    put.completed();
                    put.getResult();
                }
            }
        };
    }

    @Override
    protected String getScenarioPrefix()
    {
        return "box";
    }

    @Override
    protected List<String> getScenarioPaths()
    {
        if (getWorkDir() == null) {
            return Collections.emptyList();
        }
        return scenarioPaths(
                "discover_root.scenario",
                "discover_folder.scenario",
                "negative_missing_file.scenario",
                "metadata_csv.scenario",
                "read_csv.scenario",
                "read_binary.scenario",
                "readwrite_csv.scenario");
    }

    // -----------------------------------------------------------------------
    // Box-specific action tests
    // -----------------------------------------------------------------------

    /**
     * {@code get_acl} on a valid path must return the standard ACL response
     * structure: path / allow / deny / inheritance / precedence.
     */
    @Test
    public void testGetAcl() throws Exception
    {
        assumeNotNull(getWorkDir());
        final String path = "/" + getWorkDir();
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(BoxConnector.ACTION_PATH_PROP, path);
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter = getClient().doAction(
                new Action(BoxDatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse response = MODEL_MAPPER.fromBytes(
                iter.next().getBody(), CustomFlightActionResponse.class);
        assertNotNull(response.getResponseProperties());
        assertTrue("allow key required", response.getResponseProperties().containsKey("allow"));
        assertTrue("deny key required", response.getResponseProperties().containsKey("deny"));
        assertTrue("inheritance key required", response.getResponseProperties().containsKey("inheritance"));
        assertTrue("precedence key required", response.getResponseProperties().containsKey("precedence"));
    }

    /**
     * {@code get_acl} with a missing {@code path} property must surface an error.
     */
    @Test
    public void testGetAclMissingPath()
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        request.setRequestProperties(new ConnectionActionConfiguration());
        try {
            getClient().doAction(new Action(BoxDatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected for missing path");
        }
        catch (Exception e) {
            assertTrue("Error must mention 'path'",
                    e.getMessage() != null && e.getMessage().contains("path"));
        }
    }

    /**
     * {@code get_file_metadata} on a valid file path must return
     * {@code last_modified} and {@code size}.
     */
    @Test
    public void testGetFileMetadata() throws Exception
    {
        final String filePath = getKnownFilePath();
        assumeNotNull(filePath);
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(BoxConnector.ACTION_PATH_PROP, filePath);
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter = getClient().doAction(
                new Action(BoxDatasourceType.ACTION_GET_FILE_METADATA, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse response = MODEL_MAPPER.fromBytes(
                iter.next().getBody(), CustomFlightActionResponse.class);
        assertNotNull(response.getResponseProperties());
        assertTrue("last_modified required", response.getResponseProperties().containsKey("last_modified"));
        assertTrue("size required", response.getResponseProperties().containsKey("size"));
        final Number size = (Number) response.getResponseProperties().get("size");
        assertTrue("size must be non-negative", size.longValue() >= 0);
    }

    /**
     * {@code get_file_metadata} with a missing {@code path} property must surface
     * an error.
     */
    @Test
    public void testGetFileMetadataMissingPath()
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        request.setRequestProperties(new ConnectionActionConfiguration());
        try {
            getClient().doAction(
                    new Action(BoxDatasourceType.ACTION_GET_FILE_METADATA, MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected for missing path");
        }
        catch (Exception e) {
            assertTrue("Error must mention 'path'",
                    e.getMessage() != null && e.getMessage().contains("path"));
        }
    }

    // unsupported action is already tested in ConnectorTestSuite
}
