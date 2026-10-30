/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.dropbox;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
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
 * Tests the Arrow Flight producer for the Dropbox connector.
 *
 * <p>
 * All standard file connector tests (discovery contract, metadata, read, write,
 * paging) are inherited from {@link FileTestSuite}.
 *
 * <h3>Configuration — {@code tests.properties}</h3>
 *
 * <pre>
 * # ── Dropbox connection ────────────────────────────────────────────────────
 * file.dropbox.access_token=
 * file.dropbox.refresh_token=      # optional
 * file.dropbox.app_key=
 * file.dropbox.app_secret=
 *
 * # ── Test work folder (isolated write/delete sandbox) ─────────────────────
 * # A Dropbox folder path, e.g. "/sdk-test-runs".
 * # When absent, write scenarios and write tests are skipped automatically.
 * file.dropbox.test_work_dir=/sdk-test-runs
 *
 * # ── Flight server ─────────────────────────────────────────────────────────
 * flight.createLocal=true
 * flight.ssl=true
 * </pre>
 *
 * <p>
 * All tests are skipped automatically when {@code file.dropbox.access_token} is
 * absent from the configuration.
 */
public class TestDropboxFlightProducer extends FileTestSuite
{
    private static final Logger LOGGER = getLogger(TestDropboxFlightProducer.class);
    private static final String DATASOURCE_TYPE_NAME = DropboxDatasourceType.DATASOURCE_TYPE_NAME;

    private static TestFlight testFlight;
    private static FlightClient client;

    private static final String DROPBOX_ACCESS_TOKEN = TestConfig.get("file.dropbox.access_token");
    private static final String DROPBOX_REFRESH_TOKEN = TestConfig.get("file.dropbox.refresh_token");
    private static final String DROPBOX_APP_KEY = TestConfig.get("file.dropbox.app_key");
    private static final String DROPBOX_APP_SECRET = TestConfig.get("file.dropbox.app_secret");

    /** Skip every test when Dropbox credentials are absent from tests.properties. */
    @Before
    public void setUp()
    {
        assumeNotNull("Dropbox credentials not configured — set file.dropbox.access_token in tests.properties",
                DROPBOX_ACCESS_TOKEN);
    }

    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        final boolean createLocal = Boolean.parseBoolean(TestConfig.get("file.dropbox.flight.createLocal",
                TestConfig.get("flight.createLocal", "true")));
        if (createLocal) {
            final boolean useSSL = Boolean.parseBoolean(TestConfig.get("file.dropbox.flight.ssl",
                    TestConfig.get("flight.ssl", "true")));
            testFlight = TestFlight.createLocal(TestConfig.getPort("file.dropbox.flight.port"), useSSL,
                    new DropboxFlightProducer(), null);
        } else {
            final boolean verifyCert = Boolean.parseBoolean(
                    TestConfig.get("file.dropbox.flight.ssl_certificate_validation",
                            TestConfig.get("flight.ssl_certificate_validation", "true")));
            final String uri = TestConfig.get("file.dropbox.flight.uri.internal",
                    TestConfig.get("file.dropbox.flight.uri"));
            final String cert = TestConfig.get("file.dropbox.flight.ssl_certificate",
                    TestConfig.get("flight.ssl_certificate"));
            testFlight = TestFlight.createRemote(uri, cert, verifyCert, null);
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
        if (DROPBOX_ACCESS_TOKEN != null) {
            props.put("access_token", DROPBOX_ACCESS_TOKEN);
        }
        if (DROPBOX_REFRESH_TOKEN != null) {
            props.put("refresh_token", DROPBOX_REFRESH_TOKEN);
        }
        if (DROPBOX_APP_KEY != null) {
            props.put("app_key", DROPBOX_APP_KEY);
        }
        if (DROPBOX_APP_SECRET != null) {
            props.put("app_secret", DROPBOX_APP_SECRET);
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
        return workDir != null ? workDir : "/";
    }

    @Override
    protected DiscoveredAssetInteractionProperties createReadInteractionProperties()
    {
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        final String workDir = getWorkDir();
        if (workDir != null) {
            props.put("file_name", workDir + "/suite_known_read.csv");
        }
        return props;
    }

    @Override
    protected String getKnownFilePath()
    {
        final String workDir = getWorkDir();
        return workDir != null ? workDir + "/suite_known_read.csv" : null;
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
        props.put("file_name", workDir + "/suite_write_" + uniqueSuffix + ".csv");
        props.put("first_line_header", "true");
        return props;
    }

    @Override
    protected String getWorkDirConfigKey()
    {
        return "file.dropbox.test_work_dir";
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
        return "dropbox";
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
    // Dropbox-specific action tests
    // -----------------------------------------------------------------------

    /**
     * {@code get_acl} on a valid path must return the standard ACL response
     * structure: allow / deny / inheritance / precedence.
     */
    @Test
    public void testGetAcl() throws Exception
    {
        assumeNotNull(getWorkDir());
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(DropboxConnector.ACTION_PATH_PROP, getWorkDir());
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter = getClient().doAction(
                new Action(DropboxDatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse response = MODEL_MAPPER.fromBytes(
                iter.next().getBody(), CustomFlightActionResponse.class);
        assertNotNull(response.getResponseProperties());
        assertTrue("allow key required", response.getResponseProperties().containsKey("allow"));
        assertTrue("deny key required", response.getResponseProperties().containsKey("deny"));
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
            getClient().doAction(new Action(DropboxDatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request))).next();
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
        inputProps.put(DropboxConnector.ACTION_PATH_PROP, filePath);
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter = getClient().doAction(
                new Action(DropboxDatasourceType.ACTION_GET_FILE_METADATA, MODEL_MAPPER.toBytes(request)));
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
                    new Action(DropboxDatasourceType.ACTION_GET_FILE_METADATA, MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected for missing path");
        }
        catch (Exception e) {
            assertTrue("Error must mention 'path'",
                    e.getMessage() != null && e.getMessage().contains("path"));
        }
    }
}
