/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.localfs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.apache.arrow.flight.AsyncPutListener;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightInfo;
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
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

/**
 * Tests a flight producer for the local file system.
 */
public class TestLocalFSFlightProducer extends ConnectorTestSuite
{
    private static final Logger LOGGER = getLogger(TestLocalFSFlightProducer.class);

    private static final String DATASOURCE_TYPE_NAME = LocalFSDatasourceType.DATASOURCE_TYPE_NAME;

    private static final int TEST_FILE_COLUMN_COUNT = 12;
    private static final int TEST_FILE_VALUES_COUNT = 25;

    private static ModelMapper modelMapper = new ModelMapper();

    private static TestFlight testFlight;
    private static FlightClient client;
    private static TimeZone defaultTimeZone;

    /**
     * Verifies that test configuration has been specified before running tests.
     */
    @Before
    public void setUp()
    {
        // If testing on Windows, then spark needs the Hadoop DLLs and winutils.exe.
        assumeTrue(!System.getProperty("os.name").contains("Windows") || System.getenv("HADOOP_HOME") != null);
    }

    /**
     * Setup before tests.
     *
     * @throws Exception
     */
    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        if (Boolean.parseBoolean(TestConfig.get("file_localfs.flight.createLocal", "true"))) {
            final boolean useSSL = Boolean.parseBoolean(TestConfig.get("file_localfs.flight.ssl", "true"));
            testFlight = TestFlight.createLocal(TestConfig.getPort("file_localfs.flight.port"), useSSL, new LocalFSFlightProducer(), null);
        } else {
            final boolean verifyCert = Boolean.parseBoolean(TestConfig.get("file_localfs.flight.ssl_certificate_validation", "true"));
            testFlight
                    = TestFlight.createRemote(TestConfig.get("file_localfs.flight.uri.internal", TestConfig.get("file_localfs.flight.uri")),
                            TestConfig.get("file_localfs.flight.ssl_certificate"), verifyCert, null);
        }
        client = testFlight.getClient();
        defaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    private static ConnectionProperties createLocalFSConnectionProperties()
    {
        return new ConnectionProperties();
    }

    /**
     * Cleanup after tests.
     *
     * @throws Exception
     */
    @AfterClass
    public static void tearDownOnce()
    {
        TimeZone.setDefault(defaultTimeZone);
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
        return createLocalFSConnectionProperties();
    }

    private void createTestFile(String filename) throws Exception
    {
        createTestFile(null, filename, null, null);
    }

    private void createTestFile(String filename, DiscoveredAssetInteractionProperties interactionProperties) throws Exception
    {
        createTestFile(null, filename, null, interactionProperties);
    }

    private void createTestFile(String rootPath, String filename) throws Exception
    {
        createTestFile(rootPath, filename, null, null);
    }

    private void createTestFile(String rootPath, String filename, String fileFormat) throws Exception
    {
        createTestFile(rootPath, filename, fileFormat, null);
    }

    private void createTestFile(String rootPath, String filename, String fileFormat,
            DiscoveredAssetInteractionProperties interactionProperties) throws Exception
    {
        if (interactionProperties == null) {
            interactionProperties = new DiscoveredAssetInteractionProperties();
        }
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        if (rootPath != null) {
            descriptor.getConnectionProperties().put("root_path", rootPath);
        }
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filename);
        if (fileFormat != null) {
            interactionProperties.put("file_format", fileFormat);
        }
        if ((fileFormat == null || "csv".equals(fileFormat) || "delimited".equals(fileFormat))
                && interactionProperties.get("first_line_header") == null) {
            interactionProperties.put("first_line_header", "true");
        }
        descriptor.addFieldsItem(new CustomFlightAssetField().name("varchar_type").type("varchar").nullable(true));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("boolean_type").type("boolean").nullable(true));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("tinyint_type").type("tinyint").nullable(true).signed(true));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("smallint_type").type("smallint").nullable(true).signed(true));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("integer_type").type("integer").nullable(true).signed(true));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("bigint_type").type("bigint").nullable(true).signed(true));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("real_type").type("real").nullable(true).signed(true));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("double_type").type("double").nullable(true).signed(true));
        descriptor.addFieldsItem(
                new CustomFlightAssetField().name("decimal_4_2_type").type("decimal").nullable(true).signed(true).length(4).scale(2));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("date_type").type("date").nullable(true));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("timestamp_type").type("timestamp").nullable(true));
        descriptor.addFieldsItem(new CustomFlightAssetField().name("varbinary_type").type("varbinary").nullable(true));
        try (BufferAllocator rootAllocator = new RootAllocator()) {
            final Schema schema = ArrowConversions.toArrow(descriptor.getFields());
            try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, rootAllocator)) {
                final ArrowConversions.ArrowFieldSetter fieldSetter = new ArrowConversions.ArrowFieldSetter(root);

                fieldSetter.setVectorIndex(0);
                fieldSetter.setValue(0, "Low values");
                fieldSetter.setValue(1, Boolean.FALSE);
                fieldSetter.setValue(2, Byte.MIN_VALUE);
                fieldSetter.setValue(3, Short.MIN_VALUE);
                fieldSetter.setValue(4, Integer.MIN_VALUE);
                fieldSetter.setValue(5, Long.MIN_VALUE);
                fieldSetter.setValue(6, Float.MIN_VALUE);
                fieldSetter.setValue(7, Double.MIN_VALUE);
                fieldSetter.setValue(8, new BigDecimal("-99.99"));
                fieldSetter.setValue(9, Date.valueOf("1970-01-01"));
                fieldSetter.setValue(10, Timestamp.valueOf("0001-01-01 00:00:00.000"));
                fieldSetter.setValue(11, "Low values".getBytes(StandardCharsets.UTF_8));

                fieldSetter.setVectorIndex(1);
                fieldSetter.setValue(0, "Null values");
                fieldSetter.setNull(1);
                fieldSetter.setNull(2);
                fieldSetter.setNull(3);
                fieldSetter.setNull(4);
                fieldSetter.setNull(5);
                fieldSetter.setNull(6);
                fieldSetter.setNull(7);
                fieldSetter.setNull(8);
                fieldSetter.setNull(9);
                fieldSetter.setNull(10);
                fieldSetter.setNull(11);

                fieldSetter.setVectorIndex(2);
                fieldSetter.setValue(0, "High values");
                fieldSetter.setValue(1, Boolean.TRUE);
                fieldSetter.setValue(2, Byte.MAX_VALUE);
                fieldSetter.setValue(3, Short.MAX_VALUE);
                fieldSetter.setValue(4, Integer.MAX_VALUE);
                fieldSetter.setValue(5, Long.MAX_VALUE);
                fieldSetter.setValue(6, Float.MAX_VALUE);
                fieldSetter.setValue(7, Double.MAX_VALUE);
                fieldSetter.setValue(8, new BigDecimal("99.99"));
                fieldSetter.setValue(9, Date.valueOf("9999-12-31"));
                fieldSetter.setValue(10, Timestamp.valueOf("9999-12-31 23:59:59.999"));
                fieldSetter.setValue(11, "High values".getBytes(StandardCharsets.UTF_8));

                root.setRowCount(3);

                final FlightClient.ClientStreamListener putStream
                        = getClient().startPut(FlightDescriptor.command(modelMapper.toBytes(descriptor)), root, new AsyncPutListener());
                putStream.putNext();
                root.clear();
                putStream.completed();
                putStream.getResult();
            }
        }
    }

    /**
     * Test discover files and folders at the root.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverRoot() throws Exception
    {
        final String rootFolder = "testDiscoverRoot";
        createTestFile(rootFolder, "file1.csv");
        createTestFile(rootFolder, "folder/file2.csv");
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.getConnectionProperties().put("root_path", rootFolder);
        criteria.setPath("/");
        final List<String> files = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            assertNotNull(descriptor.getAssetType());
            if ("folder".equals(descriptor.getAssetType().getType())) {
                assertFalse(descriptor.getAssetType().isDataset());
                assertTrue(descriptor.getAssetType().isDatasetContainer());
                assertNotNull(descriptor.getId());
                assertEquals("folder", descriptor.getName());
                final String expectedPath = "/" + descriptor.getId();
                assertEquals(expectedPath, descriptor.getPath());
            } else {
                assertEquals("file", descriptor.getAssetType().getType());
                assertTrue(descriptor.getAssetType().isDataset());
                assertFalse(descriptor.getAssetType().isDatasetContainer());
                assertNotNull(descriptor.getId());
                assertEquals("file1.csv", descriptor.getName());
                final String expectedPath = "/" + descriptor.getId();
                assertEquals(expectedPath, descriptor.getPath());
            }
            files.add(descriptor.getId());
        }
        assertEquals(2, files.size());
    }

    /**
     * Test discover root with paging options.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverRootWithPaging() throws Exception
    {
        final String rootFolder = "testDiscoverRootWithPaging";
        createTestFile(rootFolder, "file1.csv");
        createTestFile(rootFolder, "file2.csv");
        createTestFile(rootFolder, "file3.csv");
        createTestFile(rootFolder, "file4.csv");
        createTestFile(rootFolder, "file5.csv");
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.getConnectionProperties().put("root_path", rootFolder);
        criteria.setPath("/");
        criteria.setOffset(1);
        criteria.setLimit(3);
        final List<String> files = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            files.add(descriptor.getId());
        }
        assertEquals(3, files.size());
    }

    /**
     * Test discover folder files.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverFolderFiles() throws Exception
    {
        final String rootFolder = "testDiscoverFolderFiles";
        final String folderPath = "/folder";
        createTestFile(rootFolder, folderPath + "/subfolder1/file1.csv");
        createTestFile(rootFolder, folderPath + "/file2.csv");
        createTestFile(rootFolder, folderPath + "/subfolder2/file3.csv");
        createTestFile(rootFolder, folderPath + "/file4.csv");
        createTestFile(rootFolder, folderPath + "/subfolder3/file5.csv");
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.getConnectionProperties().put("root_path", rootFolder);
        criteria.setPath(folderPath);
        final List<String> files = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            assertNotNull(descriptor.getAssetType());
            if ("folder".equals(descriptor.getAssetType().getType())) {
                assertFalse(descriptor.getAssetType().isDataset());
                assertTrue(descriptor.getAssetType().isDatasetContainer());
            } else {
                assertEquals("file", descriptor.getAssetType().getType());
                assertTrue(descriptor.getAssetType().isDataset());
                assertFalse(descriptor.getAssetType().isDatasetContainer());
            }
            assertNotNull(descriptor.getId());
            assertNotNull(descriptor.getName());
            final String expectedPath = folderPath + '/' + descriptor.getId();
            assertEquals(expectedPath, descriptor.getPath());
            files.add(descriptor.getId());
        }
        assertEquals(5, files.size());
        assertTrue(files.contains("subfolder1"));
        assertTrue(files.contains("subfolder2"));
        assertTrue(files.contains("subfolder3"));
        assertTrue(files.contains("file2.csv"));
        assertTrue(files.contains("file4.csv"));
    }

    /**
     * Test discover folder files with paging options.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverFolderFilesWithPaging() throws Exception
    {
        final String rootFolder = "testDiscoverFolderFilesWithPaging";
        final String folderPath = "/folder";
        createTestFile(rootFolder, folderPath + "/file1.csv");
        createTestFile(rootFolder, folderPath + "/file2.csv");
        createTestFile(rootFolder, folderPath + "/file3.csv");
        createTestFile(rootFolder, folderPath + "/file4.csv");
        createTestFile(rootFolder, folderPath + "/file5.csv");
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.getConnectionProperties().put("root_path", rootFolder);
        criteria.setPath(folderPath);
        criteria.setOffset(1);
        criteria.setLimit(3);
        final List<String> files = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            files.add(descriptor.getId());
        }
        assertEquals(3, files.size());
    }

    /**
     * Test discover csv columns.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumnsCsv() throws Exception
    {
        final String filename = "discovercolumns.csv";
        final String filePath = "/" + filename;
        createTestFile(filePath);
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(filePath);
        final List<String> files = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            assertNotNull(descriptor.getAssetType());
            assertEquals("file", descriptor.getAssetType().getType());
            assertTrue(descriptor.getAssetType().isDataset());
            assertFalse(descriptor.getAssetType().isDatasetContainer());
            assertNotNull(descriptor.getId());
            assertNotNull(descriptor.getName());
            assertEquals(filePath, descriptor.getPath());
            assertNotNull(descriptor.getInteractionProperties());
            assertEquals(filePath, descriptor.getInteractionProperties().get("file_name"));
            assertEquals("csv", descriptor.getInteractionProperties().get("file_format"));
            assertEquals("true", descriptor.getInteractionProperties().get("first_line_header"));
            assertEquals(",", descriptor.getInteractionProperties().get("field_delimiter_value"));
            assertNotNull(descriptor.getDetails());
            assertNotNull(descriptor.getDetails().get("file_size"));
            assertEquals("text/csv", descriptor.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
            assertEquals("varchar_type", schema.getFields().get(0).getName());
            assertEquals("boolean_type", schema.getFields().get(1).getName());
            assertEquals("tinyint_type", schema.getFields().get(2).getName());
            assertEquals("smallint_type", schema.getFields().get(3).getName());
            assertEquals("integer_type", schema.getFields().get(4).getName());
            files.add(descriptor.getId());
        }
        assertEquals(1, files.size());
        assertTrue(files.contains(filename));
    }

    /**
     * Test discover delimited columns.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumnsDelimited() throws Exception
    {
        final String filename = "discovercolumnsdelimited.txt";
        final String filePath = "/" + filename;
        createTestFile(null, filePath, "delimited");
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(filePath);
        final List<String> files = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            assertNotNull(descriptor.getAssetType());
            assertEquals("file", descriptor.getAssetType().getType());
            assertTrue(descriptor.getAssetType().isDataset());
            assertFalse(descriptor.getAssetType().isDatasetContainer());
            assertNotNull(descriptor.getId());
            assertNotNull(descriptor.getName());
            assertEquals(filePath, descriptor.getPath());
            assertNotNull(descriptor.getInteractionProperties());
            assertEquals(filePath, descriptor.getInteractionProperties().get("file_name"));
            assertEquals("delimited", descriptor.getInteractionProperties().get("file_format"));
            assertEquals("true", descriptor.getInteractionProperties().get("first_line_header"));
            assertNotNull(descriptor.getDetails());
            assertNotNull(descriptor.getDetails().get("file_size"));
            assertEquals("text/plain", descriptor.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
            assertEquals("varchar_type", schema.getFields().get(0).getName());
            assertEquals("boolean_type", schema.getFields().get(1).getName());
            assertEquals("tinyint_type", schema.getFields().get(2).getName());
            assertEquals("smallint_type", schema.getFields().get(3).getName());
            assertEquals("integer_type", schema.getFields().get(4).getName());
            files.add(descriptor.getId());
        }
        assertEquals(1, files.size());
        assertTrue(files.contains(filename));
    }

    /**
     * Test discover json columns.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumnsJson() throws Exception
    {
        final String filename = "discovercolumns.json";
        final String filePath = "/" + filename;
        createTestFile(null, filePath, "json");
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(filePath);
        final List<String> files = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            assertNotNull(descriptor.getAssetType());
            assertEquals("file", descriptor.getAssetType().getType());
            assertTrue(descriptor.getAssetType().isDataset());
            assertFalse(descriptor.getAssetType().isDatasetContainer());
            assertNotNull(descriptor.getId());
            assertNotNull(descriptor.getName());
            assertEquals(filePath, descriptor.getPath());
            assertNotNull(descriptor.getInteractionProperties());
            assertEquals(filePath, descriptor.getInteractionProperties().get("file_name"));
            assertEquals("json", descriptor.getInteractionProperties().get("file_format"));
            assertNotNull(descriptor.getDetails());
            assertNotNull(descriptor.getDetails().get("file_size"));
            assertEquals("application/json", descriptor.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
            files.add(descriptor.getId());
        }
        assertEquals(1, files.size());
        assertTrue(files.contains(filename));
    }

    /**
     * Test discover orc columns.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumnsOrc() throws Exception
    {
        final String filename = "discovercolumns.orc";
        final String filePath = "/" + filename;
        createTestFile(null, filePath, "orc");
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(filePath);
        final List<String> files = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            assertNotNull(descriptor.getAssetType());
            assertEquals("file", descriptor.getAssetType().getType());
            assertTrue(descriptor.getAssetType().isDataset());
            assertFalse(descriptor.getAssetType().isDatasetContainer());
            assertNotNull(descriptor.getId());
            assertNotNull(descriptor.getName());
            assertEquals(filePath, descriptor.getPath());
            assertNotNull(descriptor.getInteractionProperties());
            assertEquals(filePath, descriptor.getInteractionProperties().get("file_name"));
            assertEquals("orc", descriptor.getInteractionProperties().get("file_format"));
            assertNotNull(descriptor.getDetails());
            assertNotNull(descriptor.getDetails().get("file_size"));
            assertEquals("application/octet-stream", descriptor.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
            files.add(descriptor.getId());
        }
        assertEquals(1, files.size());
        assertTrue(files.contains(filename));
    }

    /**
     * Test discover parquet columns.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumnsParquet() throws Exception
    {
        final String filename = "discovercolumns.parquet";
        final String filePath = "/" + filename;
        createTestFile(null, filePath, "parquet");
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(filePath);
        final List<String> files = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            assertNotNull(descriptor.getAssetType());
            assertEquals("file", descriptor.getAssetType().getType());
            assertTrue(descriptor.getAssetType().isDataset());
            assertFalse(descriptor.getAssetType().isDatasetContainer());
            assertNotNull(descriptor.getId());
            assertNotNull(descriptor.getName());
            assertEquals(filePath, descriptor.getPath());
            assertNotNull(descriptor.getInteractionProperties());
            assertEquals(filePath, descriptor.getInteractionProperties().get("file_name"));
            assertEquals("parquet", descriptor.getInteractionProperties().get("file_format"));
            assertNotNull(descriptor.getDetails());
            assertNotNull(descriptor.getDetails().get("file_size"));
            assertEquals("application/x-parquet", descriptor.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
            files.add(descriptor.getId());
        }
        assertEquals(1, files.size());
        assertTrue(files.contains(filename));
    }

    /**
     * Test discover xml columns.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumnsXml() throws Exception
    {
        final String filename = "discovercolumns.xml";
        final String filePath = "/" + filename;
        createTestFile(null, filePath, "xml");
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(filePath);
        final List<String> files = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            assertNotNull(descriptor.getAssetType());
            assertEquals("file", descriptor.getAssetType().getType());
            assertTrue(descriptor.getAssetType().isDataset());
            assertFalse(descriptor.getAssetType().isDatasetContainer());
            assertNotNull(descriptor.getId());
            assertNotNull(descriptor.getName());
            assertEquals(filePath, descriptor.getPath());
            assertNotNull(descriptor.getInteractionProperties());
            assertEquals(filePath, descriptor.getInteractionProperties().get("file_name"));
            assertEquals("xml", descriptor.getInteractionProperties().get("file_format"));
            assertNotNull(descriptor.getDetails());
            assertNotNull(descriptor.getDetails().get("file_size"));
            assertEquals("application/xml", descriptor.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
            files.add(descriptor.getId());
        }
        assertEquals(1, files.size());
        assertTrue(files.contains(filename));
    }

    /**
     * Test getFlightInfo.
     *
     * @throws Exception
     */
    @Test
    public void testGetFlightInfo() throws Exception
    {
        final String filename = "getflightinfo.csv";
        final String filePath = "/" + filename;
        createTestFile(filePath);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(filePath, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("csv", returnedDescriptor.getInteractionProperties().get("file_format"));
        assertEquals("true", returnedDescriptor.getInteractionProperties().get("first_line_header"));
        assertEquals(",", returnedDescriptor.getInteractionProperties().get("field_delimiter_value"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        assertEquals("varchar_type", schema.getFields().get(0).getName());
        assertEquals("boolean_type", schema.getFields().get(1).getName());
        assertEquals("tinyint_type", schema.getFields().get(2).getName());
        assertEquals("smallint_type", schema.getFields().get(3).getName());
        assertEquals("integer_type", schema.getFields().get(4).getName());
    }

    /**
     * Test getStream with csv.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsv() throws Exception
    {
        final String filename = "getstream.csv";
        final String filePath = "/" + filename;
        createTestFile(filePath);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(filePath, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("csv", returnedDescriptor.getInteractionProperties().get("file_format"));
        assertEquals("true", returnedDescriptor.getInteractionProperties().get("first_line_header"));
        assertEquals(",", returnedDescriptor.getInteractionProperties().get("field_delimiter_value"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        assertEquals("varchar_type", schema.getFields().get(0).getName());
        assertEquals("boolean_type", schema.getFields().get(1).getName());
        assertEquals("tinyint_type", schema.getFields().get(2).getName());
        assertEquals("smallint_type", schema.getFields().get(3).getName());
        assertEquals("integer_type", schema.getFields().get(4).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertEquals("false", data.get(0, 1));
        assertEquals("-128", data.get(0, 2));
        assertEquals("-32768", data.get(0, 3));
        assertEquals("Null values", data.get(1, 0));
        assertNull(data.get(1, 1));
        assertEquals("High values", data.get(2, 0));
        assertEquals("true", data.get(2, 1));
        assertEquals("127", data.get(2, 2));
        assertEquals("32767", data.get(2, 3));
    }

    /**
     * Test getStream with csv and first_line_header.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsvFirstLineHeader() throws Exception
    {
        final String filename = "getstreamfirstlineheader.csv";
        final String filePath = "/" + filename;
        final DiscoveredAssetInteractionProperties targetInteractionProperties = new DiscoveredAssetInteractionProperties();
        targetInteractionProperties.put("first_line_header", "false");
        createTestFile(filePath, targetInteractionProperties);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(filePath, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("csv", returnedDescriptor.getInteractionProperties().get("file_format"));
        assertEquals("false", returnedDescriptor.getInteractionProperties().get("first_line_header"));
        assertEquals(",", returnedDescriptor.getInteractionProperties().get("field_delimiter_value"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        assertEquals("_c0", schema.getFields().get(0).getName());
        assertEquals("_c1", schema.getFields().get(1).getName());
        assertEquals("_c2", schema.getFields().get(2).getName());
        assertEquals("_c3", schema.getFields().get(3).getName());
        assertEquals("_c4", schema.getFields().get(4).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertEquals("false", data.get(0, 1));
        assertEquals("-128", data.get(0, 2));
        assertEquals("-32768", data.get(0, 3));
        assertEquals("Null values", data.get(1, 0));
        assertNull(data.get(1, 1));
        assertEquals("High values", data.get(2, 0));
        assertEquals("true", data.get(2, 1));
        assertEquals("127", data.get(2, 2));
        assertEquals("32767", data.get(2, 3));
    }

    /**
     * Test getStream with csv and infer_schema=false.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsvInferSchemaFalse() throws Exception
    {
        final String filename = "getstreaminferschemafalse.csv";
        final String filePath = "/" + filename;
        createTestFile(filePath);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        interactionProperties.put("infer_schema", "false");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        assertEquals("varchar_type", schema.getFields().get(0).getName());
        assertEquals("boolean_type", schema.getFields().get(1).getName());
        assertEquals("tinyint_type", schema.getFields().get(2).getName());
        assertEquals("smallint_type", schema.getFields().get(3).getName());
        assertEquals("integer_type", schema.getFields().get(4).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertEquals("false", data.get(0, 1));
        assertEquals("-128", data.get(0, 2));
        assertEquals("-32768", data.get(0, 3));
        assertEquals("Null values", data.get(1, 0));
        assertNull(data.get(1, 1));
        assertEquals("High values", data.get(2, 0));
        assertEquals("true", data.get(2, 1));
        assertEquals("127", data.get(2, 2));
        assertEquals("32767", data.get(2, 3));
    }

    /**
     * Test getStream with csv and infer_schema=true.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsvInferSchemaTrue() throws Exception
    {
        final String filename = "getstreaminferschematrue.csv";
        final String filePath = "/" + filename;
        createTestFile(filePath);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        interactionProperties.put("infer_schema", "true");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        assertEquals("varchar_type", schema.getFields().get(0).getName());
        assertEquals("boolean_type", schema.getFields().get(1).getName());
        assertEquals("tinyint_type", schema.getFields().get(2).getName());
        assertEquals("smallint_type", schema.getFields().get(3).getName());
        assertEquals("integer_type", schema.getFields().get(4).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertFalse((Boolean) data.get(0, 1));
        assertEquals(-128, data.get(0, 2));
        assertEquals(-32768, data.get(0, 3));
        assertEquals("Null values", data.get(1, 0));
        assertNull(data.get(1, 1));
        assertEquals("High values", data.get(2, 0));
        assertTrue((Boolean) data.get(2, 1));
        assertEquals(127, data.get(2, 2));
        assertEquals(32767, data.get(2, 3));
    }

    /**
     * Test getStream with delimited and field_delimiter_value.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamDelimited() throws Exception
    {
        final String filename = "getstreamdelimited.txt";
        final String filePath = "/" + filename;
        createTestFile(null, filePath, "delimited");
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(filePath, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("delimited", returnedDescriptor.getInteractionProperties().get("file_format"));
        assertEquals("true", returnedDescriptor.getInteractionProperties().get("first_line_header"));
        assertEquals(",", returnedDescriptor.getInteractionProperties().get("field_delimiter_value"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        assertEquals("varchar_type", schema.getFields().get(0).getName());
        assertEquals("boolean_type", schema.getFields().get(1).getName());
        assertEquals("tinyint_type", schema.getFields().get(2).getName());
        assertEquals("smallint_type", schema.getFields().get(3).getName());
        assertEquals("integer_type", schema.getFields().get(4).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertEquals("false", data.get(0, 1));
        assertEquals("-128", data.get(0, 2));
        assertEquals("-32768", data.get(0, 3));
        assertEquals("Null values", data.get(1, 0));
        assertNull(data.get(1, 1));
        assertEquals("High values", data.get(2, 0));
        assertEquals("true", data.get(2, 1));
        assertEquals("127", data.get(2, 2));
        assertEquals("32767", data.get(2, 3));
    }

    /**
     * Test getStream with json.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamJson() throws Exception
    {
        final String filename = "getstream.json";
        final String filePath = "/" + filename;
        createTestFile(null, filePath, "json");
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        assertEquals("bigint_type", schema.getFields().get(0).getName());
        assertEquals("boolean_type", schema.getFields().get(1).getName());
        assertEquals("date_type", schema.getFields().get(2).getName());
        assertEquals("decimal_4_2_type", schema.getFields().get(3).getName());
        assertEquals("double_type", schema.getFields().get(4).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals(Long.MIN_VALUE, data.get(0, 0));
        assertFalse((Boolean) data.get(0, 1));
        assertEquals("1970-01-01", data.get(0, 2));
        assertEquals(Double.valueOf("-99.99"), data.get(0, 3));
        assertNull(data.get(1, 0));
        assertEquals("Null values", data.get(1, 11));
        assertEquals(Long.MAX_VALUE, data.get(2, 0));
        assertTrue((Boolean) data.get(2, 1));
        assertEquals("9999-12-31", data.get(2, 2));
        assertEquals(Double.valueOf("99.99"), data.get(2, 3));
    }

    /**
     * Test getStream with orc.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamOrc() throws Exception
    {
        final String filename = "getstream.orc";
        final String filePath = "/" + filename;
        createTestFile(null, filePath, "orc");
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertFalse((Boolean) data.get(0, 1));
        assertEquals(Byte.MIN_VALUE, data.get(0, 2));
        assertEquals(Short.MIN_VALUE, data.get(0, 3));
        assertEquals("Null values", data.get(1, 0));
        assertNull(data.get(1, 1));
        assertEquals("High values", data.get(2, 0));
        assertTrue((Boolean) data.get(2, 1));
        assertEquals(Byte.MAX_VALUE, data.get(2, 2));
        assertEquals(Short.MAX_VALUE, data.get(2, 3));
    }

    /**
     * Test getStream with orc and snappy.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamOrcSnappy() throws Exception
    {
        final String filename = "getstream.snappy.orc";
        final String filePath = "/" + filename;
        final DiscoveredAssetInteractionProperties targetInteractionProperties = new DiscoveredAssetInteractionProperties();
        targetInteractionProperties.put("compression", "snappy");
        createTestFile(null, filePath, "orc", targetInteractionProperties);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(filePath, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("application/octet-stream", returnedDescriptor.getDetails().get("mime_type"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertFalse((Boolean) data.get(0, 1));
        assertEquals(Byte.MIN_VALUE, data.get(0, 2));
        assertEquals(Short.MIN_VALUE, data.get(0, 3));
        assertEquals("Null values", data.get(1, 0));
        assertNull(data.get(1, 1));
        assertEquals("High values", data.get(2, 0));
        assertTrue((Boolean) data.get(2, 1));
        assertEquals(Byte.MAX_VALUE, data.get(2, 2));
        assertEquals(Short.MAX_VALUE, data.get(2, 3));
    }

    /**
     * Test getStream with parquet.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamParquet() throws Exception
    {
        final String filename = "getstream.parquet";
        final String filePath = "/" + filename;
        createTestFile(null, filePath, "parquet");
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertFalse((Boolean) data.get(0, 1));
        assertEquals(Byte.MIN_VALUE, data.get(0, 2));
        assertEquals(Short.MIN_VALUE, data.get(0, 3));
        assertEquals("Null values", data.get(1, 0));
        assertNull(data.get(1, 1));
        assertEquals("High values", data.get(2, 0));
        assertTrue((Boolean) data.get(2, 1));
        assertEquals(Byte.MAX_VALUE, data.get(2, 2));
        assertEquals(Short.MAX_VALUE, data.get(2, 3));
    }

    /**
     * Test getStream with parquet and snappy.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamParquetSnappy() throws Exception
    {
        final String filename = "getstream.snappy.parquet";
        final String filePath = "/" + filename;
        final DiscoveredAssetInteractionProperties targetInteractionProperties = new DiscoveredAssetInteractionProperties();
        targetInteractionProperties.put("compression", "snappy");
        createTestFile(null, filePath, "parquet", targetInteractionProperties);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(filePath, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("application/x-parquet", returnedDescriptor.getDetails().get("mime_type"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertFalse((Boolean) data.get(0, 1));
        assertEquals(Byte.MIN_VALUE, data.get(0, 2));
        assertEquals(Short.MIN_VALUE, data.get(0, 3));
        assertEquals("Null values", data.get(1, 0));
        assertNull(data.get(1, 1));
        assertEquals("High values", data.get(2, 0));
        assertTrue((Boolean) data.get(2, 1));
        assertEquals(Byte.MAX_VALUE, data.get(2, 2));
        assertEquals(Short.MAX_VALUE, data.get(2, 3));
    }

    /**
     * Test getStream with xml.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamXml() throws Exception
    {
        final String filename = "getstream.xml";
        final String filePath = "/" + filename;
        createTestFile(null, filePath, "xml");
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        assertEquals("bigint_type", schema.getFields().get(0).getName());
        assertEquals("boolean_type", schema.getFields().get(1).getName());
        assertEquals("date_type", schema.getFields().get(2).getName());
        assertEquals("decimal_4_2_type", schema.getFields().get(3).getName());
        assertEquals("double_type", schema.getFields().get(4).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals(Double.valueOf(String.valueOf(Long.MIN_VALUE)), data.get(0, 0));
        assertFalse((Boolean) data.get(0, 1));
        assertEquals(Date.valueOf("1970-01-01"), data.get(0, 2));
        assertEquals(Double.valueOf("-99.99"), data.get(0, 3));
        assertNull(data.get(1, 0));
        assertEquals("Null values", data.get(1, 11));
        assertEquals(Double.valueOf(String.valueOf(Long.MAX_VALUE)), data.get(2, 0));
        assertTrue((Boolean) data.get(2, 1));
        assertEquals(Date.valueOf("9999-12-31"), data.get(2, 2));
        assertEquals(Double.valueOf("99.99"), data.get(2, 3));
    }

    /**
     * Test getStream with row_limit.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamRowLimit() throws Exception
    {
        final String filename = "getstreamrowlimit.csv";
        final String filePath = "/" + filename;
        createTestFile(filePath);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        interactionProperties.put("row_limit", "1");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_COLUMN_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertEquals("false", data.get(0, 1));
        assertEquals("-128", data.get(0, 2));
        assertEquals("-32768", data.get(0, 3));
    }

    /**
     * Test getStream with byte_limit.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamByteLimit() throws Exception
    {
        final String filename = "getstreambytelimit.csv";
        final String filePath = "/" + filename;
        createTestFile(filePath);
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("file_name", filePath);
        interactionProperties.put("byte_limit", "10");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_COLUMN_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertEquals("false", data.get(0, 1));
        assertEquals("-128", data.get(0, 2));
        assertEquals("-32768", data.get(0, 3));
    }
}
