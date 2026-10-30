/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.box;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.AsyncPutListener;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightInfo;
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

import com.google.common.collect.Table;
import com.ibm.connect.sdk.api.ArrowConversions;
import com.ibm.connect.sdk.test.ConnectorTestSuite;
import com.ibm.connect.sdk.test.TestConfig;
import com.ibm.connect.sdk.test.TestFlight;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionRequest;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

/**
 * Tests a Flight producer for Box.
 *
 * <p>
 * To run against a real Box account, create the file
 * {@code sdk-gen/tests.properties} (gitignored) and populate it with the
 * Box application credentials and test file/folder paths:
 *
 * <pre>
 *   file_box.box.client_id=...
 *   file_box.box.client_secret=...
 *   file_box.box.enterprise_id=...
 *   file_box.box.private_key=...
 *   file_box.box.private_key_password=...  (optional)
 *   file_box.box.public_key=...
 *   file_box.box.username=...              (optional; email of Box user to authenticate as)
 *
 *   # Pre-existing files/folders in Box (must be created manually):
 *   file_box.box.test_file_name=folder/file.txt
 *   # test_folder is derived automatically from the first segment of test_file_name
 * </pre>
 *
 * <p>
 * All tests are skipped automatically when {@code file_box.box.client_id} is
 * not set.
 */
public class TestBoxFlightProducer extends ConnectorTestSuite
{
    private static final Logger LOGGER = getLogger(TestBoxFlightProducer.class);

    private static final String DATASOURCE_TYPE_NAME = BoxDatasourceType.DATASOURCE_TYPE_NAME;

    private static final String BOX_CLIENT_ID = TestConfig.get("file_box.box.client_id");
    private static final String BOX_CLIENT_SECRET = TestConfig.get("file_box.box.client_secret");
    private static final String BOX_ENTERPRISE_ID = TestConfig.get("file_box.box.enterprise_id");
    private static final String BOX_PRIVATE_KEY = TestConfig.get("file_box.box.private_key");
    private static final String BOX_PRIVATE_KEY_PASSWORD = TestConfig.get("file_box.box.private_key_password");
    private static final String BOX_PUBLIC_KEY = TestConfig.get("file_box.box.public_key");
    private static final String BOX_USERNAME = TestConfig.get("file_box.box.username");
    private static final String BOX_TEST_FILE_NAME = TestConfig.get("file_box.box.test_file_name");
    private static final String BOX_TEST_FOLDER = deriveTestFolder(BOX_TEST_FILE_NAME);

    private static final ModelMapper MODEL_MAPPER = new ModelMapper();

    private static TestFlight testFlight;
    private static FlightClient client;

    /**
     * Verifies that test configuration has been specified before running tests.
     */
    @Before
    public void setUp()
    {
        assumeNotNull(BOX_CLIENT_ID);
    }

    /**
     * Setup before tests.
     *
     * @throws Exception
     */
    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        if (Boolean.parseBoolean(TestConfig.get("file_box.flight.createLocal", "true"))) {
            final boolean useSSL = Boolean.parseBoolean(TestConfig.get("file_box.flight.ssl", "true"));
            testFlight = TestFlight.createLocal(TestConfig.getPort("file_box.flight.port"), useSSL, new BoxFlightProducer(), null);
        } else {
            final boolean verifyCert = Boolean.parseBoolean(TestConfig.get("file_box.flight.ssl_certificate_validation", "true"));
            testFlight = TestFlight.createRemote(
                    TestConfig.get("file_box.flight.uri.internal", TestConfig.get("file_box.flight.uri")),
                    TestConfig.get("file_box.flight.ssl_certificate"), verifyCert, null);
        }
        client = testFlight.getClient();
    }

    private static ConnectionProperties createBoxConnectionProperties()
    {
        final ConnectionProperties connectionProperties = new ConnectionProperties();
        connectionProperties.put("client_id", BOX_CLIENT_ID);
        connectionProperties.put("client_secret", BOX_CLIENT_SECRET);
        connectionProperties.put("enterprise_id", BOX_ENTERPRISE_ID);
        connectionProperties.put("private_key", BOX_PRIVATE_KEY);
        if (BOX_PRIVATE_KEY_PASSWORD != null) {
            connectionProperties.put("private_key_password", BOX_PRIVATE_KEY_PASSWORD);
        }
        connectionProperties.put("public_key", BOX_PUBLIC_KEY);
        if (BOX_USERNAME != null) {
            connectionProperties.put("username", BOX_USERNAME);
        }
        return connectionProperties;
    }

    /**
     * Derives the test folder name from the first path segment of the given
     * file name. For example, {@code "folder/file.txt"} yields {@code "folder"}.
     * Returns {@code null} when the file name is null or has no path separator.
     *
     * @param testFileName
     *            the value of {@code file_box.box.test_file_name}
     * @return the first path segment, or {@code null}
     */
    private static String deriveTestFolder(String testFileName)
    {
        if (testFileName == null) {
            return null;
        }
        final String normalized = testFileName.startsWith("/") ? testFileName.substring(1) : testFileName;
        final int slash = normalized.indexOf('/');
        return slash > 0 ? normalized.substring(0, slash) : null;
    }

    /**
     * Cleanup after tests.
     *
     * @throws Exception
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
        return createBoxConnectionProperties();
    }

    // -----------------------------------------------------------------------
    // Binary read tests
    // -----------------------------------------------------------------------

    /**
     * Test getFlightInfo returns a schema with one field named "content".
     *
     * @throws Exception
     */
    @Test
    public void testGetFlightInfoBinary() throws Exception
    {
        assumeNotNull(BOX_TEST_FILE_NAME);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", BOX_TEST_FILE_NAME);
        interactionProperties.put("file_format", "binary");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(1, schema.getFields().size());
        assertEquals("content", schema.getFields().get(0).getName());
    }

    /**
     * Test getStream returns one record with non-empty binary content.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamBinary() throws Exception
    {
        assumeNotNull(BOX_TEST_FILE_NAME);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", BOX_TEST_FILE_NAME);
        interactionProperties.put("file_format", "binary");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(descriptor)));
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(1, data.rowKeySet().size());
        final Object contentValue = data.get(0, 0);
        assertNotNull(contentValue);
        assertTrue(contentValue instanceof byte[]);
        assertTrue(((byte[]) contentValue).length > 0);
    }

    // -----------------------------------------------------------------------
    // Discovery tests
    // -----------------------------------------------------------------------

    /**
     * List items at the root — verify at least one entry is returned.
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
     * List items at the root with paging — offset 0, limit 2 should return at most
     * 2 entries.
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
     * List the contents of a specific folder. Requires {@code file_box.box.test_folder}
     * to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverFolder() throws Exception
    {
        assumeNotNull(BOX_TEST_FOLDER);
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath("/" + BOX_TEST_FOLDER);
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
     * ListFlights at the root must return descriptors with non-null id, name,
     * assetType, and path as required by the discovery contract.
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
            assertNotNull("descriptor.id must not be null", descriptor.getId());
            assertNotNull("descriptor.name must not be null", descriptor.getName());
            assertNotNull("descriptor.path must not be null", descriptor.getPath());
            assertNotNull("descriptor.assetType must not be null", descriptor.getAssetType());
            assertNotNull("assetType.type must not be null", descriptor.getAssetType().getType());
            assertNotNull("assetType.dataset must not be null", descriptor.getAssetType().isDataset());
            assertNotNull("assetType.datasetContainer must not be null", descriptor.getAssetType().isDatasetContainer());
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
     * get_acl with a valid file path must return a response matching the
     * ACLProvider contract: path / allow{users,groups} / deny{users,groups} /
     * inheritance / precedence. Requires {@code file_box.box.test_file_name} to be
     * set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testGetAclSuccess() throws Exception
    {
        assumeNotNull(BOX_TEST_FILE_NAME);
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(BoxConnector.ACTION_PATH_PROP, BOX_TEST_FILE_NAME);
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter = getClient().doAction(new Action(BoxDatasourceType.ACTION_GET_ACL, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse actionResponse = MODEL_MAPPER.fromBytes(iter.next().getBody(), CustomFlightActionResponse.class);
        assertNotNull("Response properties must not be null", actionResponse.getResponseProperties());

        assertTrue("Response must contain 'path'", actionResponse.getResponseProperties().containsKey("path"));
        assertTrue("Response must contain 'allow'", actionResponse.getResponseProperties().containsKey("allow"));
        assertTrue("Response must contain 'deny'", actionResponse.getResponseProperties().containsKey("deny"));
        assertTrue("Response must contain 'inheritance'", actionResponse.getResponseProperties().containsKey("inheritance"));
        assertTrue("Response must contain 'precedence'", actionResponse.getResponseProperties().containsKey("precedence"));

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

        @SuppressWarnings("unchecked")
        final Map<String, Object> inheritance = (Map<String, Object>) actionResponse.getResponseProperties().get("inheritance");
        assertNotNull("inheritance must not be null", inheritance);
        assertTrue("inheritance must contain 'enabled'", inheritance.containsKey("enabled"));
        assertTrue("inheritance must contain 'parent_precedence'", inheritance.containsKey("parent_precedence"));
        assertFalse("inheritance.enabled must be false", (Boolean) inheritance.get("enabled"));
        assertEquals("inheritance.parent_precedence must be 'parent'", "parent", inheritance.get("parent_precedence"));

        assertEquals("precedence must be 'deny'", "deny", actionResponse.getResponseProperties().get("precedence"));
    }

    /**
     * get_acl with a missing path property must return an error.
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
            assertTrue("Error must mention 'path'", e.getMessage() != null && e.getMessage().contains("path"));
        }
    }

    // -----------------------------------------------------------------------
    // get_file_metadata action tests
    // -----------------------------------------------------------------------

    /**
     * get_file_metadata with a valid file path must return last_modified
     * (non-null, non-empty ISO-8601 string) and size (non-negative long). Requires
     * {@code file_box.box.test_file_name} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testGetFileMetadataSuccess() throws Exception
    {
        assumeNotNull(BOX_TEST_FILE_NAME);
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(BoxConnector.ACTION_PATH_PROP, BOX_TEST_FILE_NAME);
        request.setRequestProperties(inputProps);

        final Iterator<Result> iter
                = getClient().doAction(new Action(BoxDatasourceType.ACTION_GET_FILE_METADATA, MODEL_MAPPER.toBytes(request)));
        assertTrue("Expected a result", iter.hasNext());
        final CustomFlightActionResponse actionResponse = MODEL_MAPPER.fromBytes(iter.next().getBody(), CustomFlightActionResponse.class);
        assertNotNull("Response properties must not be null", actionResponse.getResponseProperties());

        assertTrue("Response must contain 'last_modified'", actionResponse.getResponseProperties().containsKey("last_modified"));
        assertTrue("Response must contain 'size'", actionResponse.getResponseProperties().containsKey("size"));

        final String lastModified = (String) actionResponse.getResponseProperties().get("last_modified");
        assertNotNull("last_modified must not be null", lastModified);
        assertFalse("last_modified must not be empty", lastModified.isEmpty());
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
            getClient().doAction(new Action(BoxDatasourceType.ACTION_GET_FILE_METADATA, MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected for missing path");
        }
        catch (Exception e) {
            assertTrue("Error must mention 'path'", e.getMessage() != null && e.getMessage().contains("path"));
        }
    }

    /**
     * get_file_metadata on a non-existent path must propagate an error.
     */
    @Test
    public void testGetFileMetadataNonExistentPath()
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
        inputProps.put(BoxConnector.ACTION_PATH_PROP, "this/path/does/not/exist.csv");
        request.setRequestProperties(inputProps);
        try {
            getClient().doAction(new Action(BoxDatasourceType.ACTION_GET_FILE_METADATA, MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected for non-existent path");
        }
        catch (Exception e) {
            assertNotNull("Exception message must not be null", e.getMessage());
        }
    }

    /**
     * An unknown action with no path property must fail with an "unsupported" error,
     * not a "missing path" error. Validates that action-name validation happens before
     * path-property validation.
     */
    @Override
    @Test
    public void testUnsupportedAction()
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        request.setRequestProperties(new ConnectionActionConfiguration());
        try {
            getClient().doAction(new Action("doAction_unknown_action", MODEL_MAPPER.toBytes(request))).next();
            fail("Exception expected for unsupported action");
        }
        catch (Exception e) {
            assertTrue("Error must mention the action name",
                    e.getMessage() != null && e.getMessage().contains("unknown_action"));
        }
    }

    // -----------------------------------------------------------------------
    // Binary write tests
    // -----------------------------------------------------------------------

    /**
     * startPut with file_format=binary uploads a single record's raw bytes to Box.
     * The uploaded file must be readable back with getStream (binary mode) and
     * return the same bytes.
     *
     * <p>Requires {@code file_box.box.test_file_name} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testPutBinary() throws Exception
    {
        assumeNotNull(BOX_TEST_FILE_NAME);
        final byte[] payload = "Hello Box binary write".getBytes(StandardCharsets.UTF_8);

        // Derive a unique target path in the same folder as the test file.
        final String folder = BOX_TEST_FOLDER != null ? BOX_TEST_FOLDER : "";
        final String targetPath = (folder.isEmpty() ? "" : folder + "/") + "put_binary_test.bin";

        // --- Write ---
        final CustomFlightAssetDescriptor writeDescriptor = new CustomFlightAssetDescriptor();
        writeDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        writeDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties writeProps = new DiscoveredAssetInteractionProperties();
        writeProps.put("file_name", targetPath);
        writeProps.put("file_format", "binary");
        writeDescriptor.setInteractionProperties(writeProps);
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("content").type("varbinary").nullable(false));

        try (BufferAllocator allocator = new RootAllocator()) {
            final Schema schema = ArrowConversions.toArrow(writeDescriptor.getFields());
            try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
                final ArrowConversions.ArrowFieldSetter setter = new ArrowConversions.ArrowFieldSetter(root);
                setter.setVectorIndex(0);
                setter.setValue(0, payload);
                root.setRowCount(1);

                final FlightClient.ClientStreamListener putStream
                        = getClient().startPut(FlightDescriptor.command(MODEL_MAPPER.toBytes(writeDescriptor)), root, new AsyncPutListener());
                putStream.putNext();
                root.clear();
                putStream.completed();
                putStream.getResult();
            }
        }

        // --- Read back and verify ---
        final CustomFlightAssetDescriptor readDescriptor = new CustomFlightAssetDescriptor();
        readDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        readDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties readProps = new DiscoveredAssetInteractionProperties();
        readProps.put("file_name", targetPath);
        readProps.put("file_format", "binary");
        readDescriptor.setInteractionProperties(readProps);

        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(readDescriptor)));
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals("Expected exactly one row", 1, data.rowKeySet().size());
        final Object contentValue = data.get(0, 0);
        assertNotNull("content field must not be null", contentValue);
        assertTrue("content field must be byte[]", contentValue instanceof byte[]);
        final byte[] returned = (byte[]) contentValue;
        assertEquals("Byte length must match", payload.length, returned.length);
        for (int i = 0; i < payload.length; i++) {
            assertEquals("Byte at index " + i + " must match", payload[i], returned[i]);
        }
    }

    // -----------------------------------------------------------------------
    // Structured write tests
    // -----------------------------------------------------------------------

    /**
     * startPut with file_format=csv writes tabular records to Box as a CSV file.
     * The written file must be readable back with getStream (csv mode) and return
     * the same row count.
     *
     * <p>Requires {@code file_box.box.test_file_name} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testPutStructuredCsv() throws Exception
    {
        assumeNotNull(BOX_TEST_FILE_NAME);
        final String folder = BOX_TEST_FOLDER != null ? BOX_TEST_FOLDER : "";
        final String targetPath = (folder.isEmpty() ? "" : folder + "/") + "put_structured_test.csv";

        // --- Write ---
        final CustomFlightAssetDescriptor writeDescriptor = new CustomFlightAssetDescriptor();
        writeDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        writeDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties writeProps = new DiscoveredAssetInteractionProperties();
        writeProps.put("file_name", targetPath);
        writeProps.put("file_format", "csv");
        writeProps.put("first_line_header", "true");
        writeDescriptor.setInteractionProperties(writeProps);
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("id").type("integer").nullable(false).signed(true));
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("name").type("varchar").nullable(true));

        try (BufferAllocator allocator = new RootAllocator()) {
            final Schema schema = ArrowConversions.toArrow(writeDescriptor.getFields());
            try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
                final ArrowConversions.ArrowFieldSetter setter = new ArrowConversions.ArrowFieldSetter(root);
                setter.setVectorIndex(0);
                setter.setValue(0, 1);
                setter.setValue(1, "Alice");
                setter.setVectorIndex(1);
                setter.setValue(0, 2);
                setter.setValue(1, "Bob");
                root.setRowCount(2);

                final FlightClient.ClientStreamListener putStream
                        = getClient().startPut(FlightDescriptor.command(MODEL_MAPPER.toBytes(writeDescriptor)), root, new AsyncPutListener());
                putStream.putNext();
                root.clear();
                putStream.completed();
                putStream.getResult();
            }
        }

        // --- Read back and verify row count ---
        final CustomFlightAssetDescriptor readDescriptor = new CustomFlightAssetDescriptor();
        readDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        readDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties readProps = new DiscoveredAssetInteractionProperties();
        readProps.put("file_name", targetPath);
        readProps.put("file_format", "csv");
        readProps.put("first_line_header", "true");
        readDescriptor.setInteractionProperties(readProps);

        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(readDescriptor)));
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals("Expected 2 rows written and read back", 2, data.rowKeySet().size());
    }

    /**
     * startPut with file_format=delimited writes tabular records to Box as a
     * delimited text file. The written file must be readable back and return the
     * same row count.
     *
     * <p>Requires {@code file_box.box.test_file_name} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testPutStructuredDelimited() throws Exception
    {
        assumeNotNull(BOX_TEST_FILE_NAME);
        final String folder = BOX_TEST_FOLDER != null ? BOX_TEST_FOLDER : "";
        final String targetPath = (folder.isEmpty() ? "" : folder + "/") + "put_delimited_test.txt";

        // --- Write ---
        final CustomFlightAssetDescriptor writeDescriptor = new CustomFlightAssetDescriptor();
        writeDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        writeDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties writeProps = new DiscoveredAssetInteractionProperties();
        writeProps.put("file_name", targetPath);
        writeProps.put("file_format", "delimited");
        writeProps.put("first_line_header", "true");
        writeDescriptor.setInteractionProperties(writeProps);
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("id").type("integer").nullable(false).signed(true));
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("value").type("varchar").nullable(true));

        try (BufferAllocator allocator = new RootAllocator()) {
            final Schema schema = ArrowConversions.toArrow(writeDescriptor.getFields());
            try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
                final ArrowConversions.ArrowFieldSetter setter = new ArrowConversions.ArrowFieldSetter(root);
                setter.setVectorIndex(0);
                setter.setValue(0, 1);
                setter.setValue(1, "alpha");
                setter.setVectorIndex(1);
                setter.setValue(0, 2);
                setter.setValue(1, "beta");
                root.setRowCount(2);

                final FlightClient.ClientStreamListener putStream
                        = getClient().startPut(FlightDescriptor.command(MODEL_MAPPER.toBytes(writeDescriptor)), root, new AsyncPutListener());
                putStream.putNext();
                root.clear();
                putStream.completed();
                putStream.getResult();
            }
        }

        // --- Read back and verify row count ---
        final CustomFlightAssetDescriptor readDescriptor = new CustomFlightAssetDescriptor();
        readDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        readDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties readProps = new DiscoveredAssetInteractionProperties();
        readProps.put("file_name", targetPath);
        readProps.put("file_format", "delimited");
        readProps.put("first_line_header", "true");
        readDescriptor.setInteractionProperties(readProps);

        final FlightInfo info2 = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(readDescriptor)));
        final Table<Integer, Integer, Object> data2 = getTableData(info2);
        assertEquals("Expected 2 rows written and read back", 2, data2.rowKeySet().size());
    }

    /**
     * startPut with file_format=json writes tabular records to Box as a JSON file.
     * The written file must be readable back with getStream (json mode) and return
     * the same row count.
     *
     * <p>Requires {@code file_box.box.test_file_name} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testPutStructuredJson() throws Exception
    {
        assumeNotNull(BOX_TEST_FILE_NAME);
        final String folder = BOX_TEST_FOLDER != null ? BOX_TEST_FOLDER : "";
        final String targetPath = (folder.isEmpty() ? "" : folder + "/") + "put_json_test.json";

        // --- Write ---
        final CustomFlightAssetDescriptor writeDescriptor = new CustomFlightAssetDescriptor();
        writeDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        writeDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties writeProps = new DiscoveredAssetInteractionProperties();
        writeProps.put("file_name", targetPath);
        writeProps.put("file_format", "json");
        writeDescriptor.setInteractionProperties(writeProps);
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("id").type("integer").nullable(false).signed(true));
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("label").type("varchar").nullable(true));

        try (BufferAllocator allocator = new RootAllocator()) {
            final Schema schema = ArrowConversions.toArrow(writeDescriptor.getFields());
            try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
                final ArrowConversions.ArrowFieldSetter setter = new ArrowConversions.ArrowFieldSetter(root);
                setter.setVectorIndex(0);
                setter.setValue(0, 10);
                setter.setValue(1, "foo");
                setter.setVectorIndex(1);
                setter.setValue(0, 20);
                setter.setValue(1, "bar");
                root.setRowCount(2);

                final FlightClient.ClientStreamListener putStream
                        = getClient().startPut(FlightDescriptor.command(MODEL_MAPPER.toBytes(writeDescriptor)), root, new AsyncPutListener());
                putStream.putNext();
                root.clear();
                putStream.completed();
                putStream.getResult();
            }
        }

        // --- Read back and verify row count ---
        final CustomFlightAssetDescriptor readDescriptor = new CustomFlightAssetDescriptor();
        readDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        readDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties readProps = new DiscoveredAssetInteractionProperties();
        readProps.put("file_name", targetPath);
        readProps.put("file_format", "json");
        readDescriptor.setInteractionProperties(readProps);

        final FlightInfo info2 = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(readDescriptor)));
        final Table<Integer, Integer, Object> data2 = getTableData(info2);
        assertEquals("Expected 2 rows written and read back", 2, data2.rowKeySet().size());
    }

    /**
     * startPut with file_format=parquet writes tabular records to Box as a Parquet
     * file. The written file must be readable back with getStream (parquet mode) and
     * return the same row count.
     *
     * <p>Requires {@code file_box.box.test_file_name} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testPutStructuredParquet() throws Exception
    {
        assumeNotNull(BOX_TEST_FILE_NAME);
        final String folder = BOX_TEST_FOLDER != null ? BOX_TEST_FOLDER : "";
        final String targetPath = (folder.isEmpty() ? "" : folder + "/") + "put_parquet_test.parquet";

        // --- Write ---
        final CustomFlightAssetDescriptor writeDescriptor = new CustomFlightAssetDescriptor();
        writeDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        writeDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties writeProps = new DiscoveredAssetInteractionProperties();
        writeProps.put("file_name", targetPath);
        writeProps.put("file_format", "parquet");
        writeDescriptor.setInteractionProperties(writeProps);
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("id").type("integer").nullable(false).signed(true));
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("score").type("double").nullable(true).signed(true));

        try (BufferAllocator allocator = new RootAllocator()) {
            final Schema schema = ArrowConversions.toArrow(writeDescriptor.getFields());
            try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
                final ArrowConversions.ArrowFieldSetter setter = new ArrowConversions.ArrowFieldSetter(root);
                setter.setVectorIndex(0);
                setter.setValue(0, 1);
                setter.setValue(1, 3.14);
                setter.setVectorIndex(1);
                setter.setValue(0, 2);
                setter.setValue(1, 2.71);
                root.setRowCount(2);

                final FlightClient.ClientStreamListener putStream
                        = getClient().startPut(FlightDescriptor.command(MODEL_MAPPER.toBytes(writeDescriptor)), root, new AsyncPutListener());
                putStream.putNext();
                root.clear();
                putStream.completed();
                putStream.getResult();
            }
        }

        // --- Read back and verify row count ---
        final CustomFlightAssetDescriptor readDescriptor = new CustomFlightAssetDescriptor();
        readDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        readDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties readProps = new DiscoveredAssetInteractionProperties();
        readProps.put("file_name", targetPath);
        readProps.put("file_format", "parquet");
        readDescriptor.setInteractionProperties(readProps);

        final FlightInfo info2 = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(readDescriptor)));
        final Table<Integer, Integer, Object> data2 = getTableData(info2);
        assertEquals("Expected 2 rows written and read back", 2, data2.rowKeySet().size());
    }

    /**
     * startPut with file_format=avro writes tabular records to Box as an Avro file.
     * The written file must be readable back with getStream (avro mode) and return
     * the same row count.
     *
     * <p>Requires {@code file_box.box.test_file_name} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testPutStructuredAvro() throws Exception
    {
        assumeNotNull(BOX_TEST_FILE_NAME);
        final String folder = BOX_TEST_FOLDER != null ? BOX_TEST_FOLDER : "";
        final String targetPath = (folder.isEmpty() ? "" : folder + "/") + "put_avro_test.avro";

        // --- Write ---
        final CustomFlightAssetDescriptor writeDescriptor = new CustomFlightAssetDescriptor();
        writeDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        writeDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties writeProps = new DiscoveredAssetInteractionProperties();
        writeProps.put("file_name", targetPath);
        writeProps.put("file_format", "avro");
        writeDescriptor.setInteractionProperties(writeProps);
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("id").type("integer").nullable(false).signed(true));
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("tag").type("varchar").nullable(true));

        try (BufferAllocator allocator = new RootAllocator()) {
            final Schema schema = ArrowConversions.toArrow(writeDescriptor.getFields());
            try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
                final ArrowConversions.ArrowFieldSetter setter = new ArrowConversions.ArrowFieldSetter(root);
                setter.setVectorIndex(0);
                setter.setValue(0, 100);
                setter.setValue(1, "x");
                setter.setVectorIndex(1);
                setter.setValue(0, 200);
                setter.setValue(1, "y");
                root.setRowCount(2);

                final FlightClient.ClientStreamListener putStream
                        = getClient().startPut(FlightDescriptor.command(MODEL_MAPPER.toBytes(writeDescriptor)), root, new AsyncPutListener());
                putStream.putNext();
                root.clear();
                putStream.completed();
                putStream.getResult();
            }
        }

        // --- Read back and verify row count ---
        final CustomFlightAssetDescriptor readDescriptor = new CustomFlightAssetDescriptor();
        readDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        readDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties readProps = new DiscoveredAssetInteractionProperties();
        readProps.put("file_name", targetPath);
        readProps.put("file_format", "avro");
        readDescriptor.setInteractionProperties(readProps);

        final FlightInfo info2 = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(readDescriptor)));
        final Table<Integer, Integer, Object> data2 = getTableData(info2);
        assertEquals("Expected 2 rows written and read back", 2, data2.rowKeySet().size());
    }

    /**
     * startPut with file_format=orc writes tabular records to Box as an ORC file.
     * The written file must be readable back with getStream (orc mode) and return
     * the same row count.
     *
     * <p>Requires {@code file_box.box.test_file_name} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testPutStructuredOrc() throws Exception
    {
        assumeNotNull(BOX_TEST_FILE_NAME);
        final String folder = BOX_TEST_FOLDER != null ? BOX_TEST_FOLDER : "";
        final String targetPath = (folder.isEmpty() ? "" : folder + "/") + "put_orc_test.orc";

        // --- Write ---
        final CustomFlightAssetDescriptor writeDescriptor = new CustomFlightAssetDescriptor();
        writeDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        writeDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties writeProps = new DiscoveredAssetInteractionProperties();
        writeProps.put("file_name", targetPath);
        writeProps.put("file_format", "orc");
        writeDescriptor.setInteractionProperties(writeProps);
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("id").type("integer").nullable(false).signed(true));
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("flag").type("boolean").nullable(true));

        try (BufferAllocator allocator = new RootAllocator()) {
            final Schema schema = ArrowConversions.toArrow(writeDescriptor.getFields());
            try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
                final ArrowConversions.ArrowFieldSetter setter = new ArrowConversions.ArrowFieldSetter(root);
                setter.setVectorIndex(0);
                setter.setValue(0, 1);
                setter.setValue(1, Boolean.TRUE);
                setter.setVectorIndex(1);
                setter.setValue(0, 2);
                setter.setValue(1, Boolean.FALSE);
                root.setRowCount(2);

                final FlightClient.ClientStreamListener putStream
                        = getClient().startPut(FlightDescriptor.command(MODEL_MAPPER.toBytes(writeDescriptor)), root, new AsyncPutListener());
                putStream.putNext();
                root.clear();
                putStream.completed();
                putStream.getResult();
            }
        }

        // --- Read back and verify row count ---
        final CustomFlightAssetDescriptor readDescriptor = new CustomFlightAssetDescriptor();
        readDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        readDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties readProps = new DiscoveredAssetInteractionProperties();
        readProps.put("file_name", targetPath);
        readProps.put("file_format", "orc");
        readDescriptor.setInteractionProperties(readProps);

        final FlightInfo info2 = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(readDescriptor)));
        final Table<Integer, Integer, Object> data2 = getTableData(info2);
        assertEquals("Expected 2 rows written and read back", 2, data2.rowKeySet().size());
    }

    /**
     * startPut with file_format=xml writes tabular records to Box as an XML file.
     * The written file must be readable back with getStream (xml mode) and return
     * the same row count.
     *
     * <p>Requires {@code file_box.box.test_file_name} to be set in tests.properties.
     *
     * @throws Exception
     */
    @Test
    public void testPutStructuredXml() throws Exception
    {
        assumeNotNull(BOX_TEST_FILE_NAME);
        final String folder = BOX_TEST_FOLDER != null ? BOX_TEST_FOLDER : "";
        final String targetPath = (folder.isEmpty() ? "" : folder + "/") + "put_xml_test.xml";

        // --- Write ---
        final CustomFlightAssetDescriptor writeDescriptor = new CustomFlightAssetDescriptor();
        writeDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        writeDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties writeProps = new DiscoveredAssetInteractionProperties();
        writeProps.put("file_name", targetPath);
        writeProps.put("file_format", "xml");
        writeProps.put("row_tag", "record");
        writeDescriptor.setInteractionProperties(writeProps);
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("id").type("integer").nullable(false).signed(true));
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("name").type("varchar").nullable(true));

        try (BufferAllocator allocator = new RootAllocator()) {
            final Schema schema = ArrowConversions.toArrow(writeDescriptor.getFields());
            try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
                final ArrowConversions.ArrowFieldSetter setter = new ArrowConversions.ArrowFieldSetter(root);
                setter.setVectorIndex(0);
                setter.setValue(0, 1);
                setter.setValue(1, "Alice");
                setter.setVectorIndex(1);
                setter.setValue(0, 2);
                setter.setValue(1, "Bob");
                root.setRowCount(2);

                final FlightClient.ClientStreamListener putStream
                        = getClient().startPut(FlightDescriptor.command(MODEL_MAPPER.toBytes(writeDescriptor)), root, new AsyncPutListener());
                putStream.putNext();
                root.clear();
                putStream.completed();
                putStream.getResult();
            }
        }

        // --- Read back and verify row count ---
        final CustomFlightAssetDescriptor readDescriptor = new CustomFlightAssetDescriptor();
        readDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        readDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties readProps = new DiscoveredAssetInteractionProperties();
        readProps.put("file_name", targetPath);
        readProps.put("file_format", "xml");
        readProps.put("row_tag", "record");
        readDescriptor.setInteractionProperties(readProps);

        final FlightInfo info2 = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(readDescriptor)));
        final Table<Integer, Integer, Object> data2 = getTableData(info2);
        assertEquals("Expected 2 rows written and read back", 2, data2.rowKeySet().size());
    }

    // -----------------------------------------------------------------------
    // Text (plain-text) read tests
    // -----------------------------------------------------------------------

    /**
     * getStream with file_format=binary on a plain text file (.txt) must return
     * a single record whose content field contains the non-empty file bytes.
     * This covers the "text" file format case: plain text files are read as raw
     * binary through Box.
     *
     * <p>Requires {@code file_box.box.test_file_name} to be set in tests.properties.
     * The test writes a small text payload first, then reads it back as binary.
     *
     * @throws Exception
     */
    @Test
    public void testReadTextFileAsBinary() throws Exception
    {
        assumeNotNull(BOX_TEST_FILE_NAME);
        final String folder = BOX_TEST_FOLDER != null ? BOX_TEST_FOLDER : "";
        final String targetPath = (folder.isEmpty() ? "" : folder + "/") + "put_text_test.txt";
        final byte[] textPayload = "Hello, Box text file!\nLine 2\nLine 3\n".getBytes(StandardCharsets.UTF_8);

        // --- Write the text file as binary ---
        final CustomFlightAssetDescriptor writeDescriptor = new CustomFlightAssetDescriptor();
        writeDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        writeDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties writeProps = new DiscoveredAssetInteractionProperties();
        writeProps.put("file_name", targetPath);
        writeProps.put("file_format", "binary");
        writeDescriptor.setInteractionProperties(writeProps);
        writeDescriptor.addFieldsItem(new CustomFlightAssetField().name("content").type("varbinary").nullable(false));

        try (BufferAllocator allocator = new RootAllocator()) {
            final Schema schema = ArrowConversions.toArrow(writeDescriptor.getFields());
            try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
                final ArrowConversions.ArrowFieldSetter setter = new ArrowConversions.ArrowFieldSetter(root);
                setter.setVectorIndex(0);
                setter.setValue(0, textPayload);
                root.setRowCount(1);

                final FlightClient.ClientStreamListener putStream
                        = getClient().startPut(FlightDescriptor.command(MODEL_MAPPER.toBytes(writeDescriptor)), root, new AsyncPutListener());
                putStream.putNext();
                root.clear();
                putStream.completed();
                putStream.getResult();
            }
        }

        // --- Read back as binary and validate content ---
        final CustomFlightAssetDescriptor readDescriptor = new CustomFlightAssetDescriptor();
        readDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        readDescriptor.setConnectionProperties(createBoxConnectionProperties());
        final DiscoveredAssetInteractionProperties readProps = new DiscoveredAssetInteractionProperties();
        readProps.put("file_name", targetPath);
        readProps.put("file_format", "binary");
        readDescriptor.setInteractionProperties(readProps);

        final FlightInfo info2 = getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(readDescriptor)));
        final Table<Integer, Integer, Object> data2 = getTableData(info2);
        assertEquals("Text file read: expected exactly one row", 1, data2.rowKeySet().size());
        final Object contentValue = data2.get(0, 0);
        assertNotNull("Text file read: content must not be null", contentValue);
        assertTrue("Text file read: content must be byte[]", contentValue instanceof byte[]);
        assertEquals("Text file read: byte length must match", textPayload.length, ((byte[]) contentValue).length);
    }
}
