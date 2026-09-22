/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025, 2026                  */
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
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.ibm.connect.sdk.test.TestConfig;
import com.ibm.connect.sdk.test.TestFlight;
import com.ibm.connect.sdk.test.file.FileTestSuite;
import com.ibm.connect.sdk.test.file.TestScenario;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

/**
 * Tests the Arrow Flight producer for the local file system connector.
 *
 * <p>All standard file connector tests (discovery, metadata, read, write,
 * paging) are inherited from {@link FileTestSuite}. This class adds
 * LocalFS-specific tests for individual file formats and write scenarios.
 *
 * <h3>Configuration</h3>
 * <p>All settings are loaded from {@code tests.properties} (gitignored) located in
 * the project working directory, or from the classpath resource of the same name.
 * No properties are required for LocalFS — the connector writes to a per-process
 * temp directory and needs no external infrastructure.
 *
 * <p>Optional properties (all under the {@code file_localfs.*} namespace):
 * <pre>
 *   # Flight server
 *   file_localfs.flight.createLocal=true       # set false to point at a remote server
 *   file_localfs.flight.ssl=true
 *   file_localfs.flight.port=                  # blank = random free port
 *   # Remote server (only when createLocal=false)
 *   file_localfs.flight.uri=grpc+tls://host:port
 *   file_localfs.flight.ssl_certificate=       # PEM content
 *   file_localfs.flight.ssl_certificate_validation=true
 * </pre>
 *
 * <h3>Scenario-based tests</h3>
 * <p>Scenario files placed under {@code src/test/resources/scenarios/localfs/}
 * are automatically discovered and run via {@link FileTestSuite#testScenarios()}.
 * Add a new {@code *.scenario} file and list it in {@link #getScenarioPaths()} to
 * include it in the suite without writing Java code.
 */
public class TestLocalFSFlightProducer extends FileTestSuite
{
    private static final Logger LOGGER = getLogger(TestLocalFSFlightProducer.class);
    private static final String DATASOURCE_TYPE_NAME = LocalFSDatasourceType.DATASOURCE_TYPE_NAME;

    /** Number of columns in the shared test file written by {@link #createTestFile}. */
    static final int TEST_FILE_COLUMN_COUNT = 12;

    /**
     * Total number of non-null cell values across all 3 data rows and 12 columns
     * (row 0=12, row 1=1 varchar + 11 nulls, row 2=12 → 25 non-null cells).
     */
    static final int TEST_FILE_VALUES_COUNT = 25;

    /** Stable file name used by the canonical read tests inherited from {@link FileTestSuite}. */
    private static final String KNOWN_FILE_NAME = "suite_known_read.csv";
    private static final String KNOWN_FILE_PATH = "/" + KNOWN_FILE_NAME;

    // -----------------------------------------------------------------------
    // Flight server / client lifecycle
    // -----------------------------------------------------------------------

    private static TestFlight testFlight;
    private static FlightClient client;
    private static TimeZone defaultTimeZone;
    private static final LocalFSConfig CONFIG = new LocalFSConfig();

    /**
     * Skip on Windows unless {@code HADOOP_HOME} is set, and write the
     * canonical seed files that each test reads from.
     */
    @Before
    public void setUp() throws Exception
    {
        assumeTrue(!System.getProperty("os.name").contains("Windows") || System.getenv("HADOOP_HOME") != null);
        createTestFile(KNOWN_FILE_PATH);
        createTestFile("/suite_container/seed.csv");
    }

    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        if (CONFIG.createLocal) {
            testFlight = TestFlight.createLocal(CONFIG.port, CONFIG.useSSL, new LocalFSFlightProducer(), null);
        } else {
            testFlight = TestFlight.createRemote(CONFIG.remoteUri, CONFIG.sslCertificate, CONFIG.verifyCert, null);
        }
        client = testFlight.getClient();
        defaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

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
        return new ConnectionProperties();
    }

    /** Root is "/" — lists files and folders directly under the FS root. */
    @Override
    protected String getRootPath()
    {
        return "/";
    }

    /**
     * Container path for listing tests: the same folder that
     * {@link #setUp()} seeds with a file before each test.
     */
    @Override
    protected String getContainerPath()
    {
        return "/suite_container";
    }

    @Override
    protected DiscoveredAssetInteractionProperties createReadInteractionProperties()
    {
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", KNOWN_FILE_PATH);
        return props;
    }

    @Override
    protected String getKnownFilePath()
    {
        return KNOWN_FILE_PATH;
    }

    @Override
    protected String getKnownFileName()
    {
        return KNOWN_FILE_NAME;
    }

    /** LocalFS supports writing — provide a unique target path per test. */
    @Override
    protected DiscoveredAssetInteractionProperties createWriteInteractionProperties(String uniqueSuffix)
    {
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", "/suite_write_" + uniqueSuffix + ".csv");
        props.put("first_line_header", "true");
        return props;
    }

    // -----------------------------------------------------------------------
    // FileTestSuite data-validating hooks
    // -----------------------------------------------------------------------

    /** The canonical test file has 3 rows: Low values / Null values / High values. */
    @Override
    protected int getExpectedRowCount()
    {
        return 3;
    }

    /** Column order matches the field order used by {@link #createTestFile}. */
    @Override
    protected List<String> getExpectedColumnNames()
    {
        return Arrays.asList(
                "varchar_type", "boolean_type", "tinyint_type", "smallint_type",
                "integer_type", "bigint_type", "real_type", "double_type",
                "decimal_4_2_type", "date_type", "timestamp_type", "varbinary_type");
    }

    /**
     * Spot-checks for {@code suite_known_read.csv}.
     * CSV returns all values as strings; null cells are absent from the table.
     */
    @Override
    protected Map<int[], Object> getExpectedCellValues()
    {
        final Map<int[], Object> expected = new LinkedHashMap<>();
        // Row 0 — Low values
        expected.put(new int[]{0, 0}, "Low values");
        expected.put(new int[]{0, 1}, "false");
        expected.put(new int[]{0, 2}, "-128");
        expected.put(new int[]{0, 3}, "-32768");
        // Row 1 — Null values: varchar non-null, everything else null
        expected.put(new int[]{1, 0}, "Null values");
        expected.put(new int[]{1, 1}, null);
        expected.put(new int[]{1, 2}, null);
        // Row 2 — High values
        expected.put(new int[]{2, 0}, "High values");
        expected.put(new int[]{2, 1}, "true");
        expected.put(new int[]{2, 2}, "127");
        expected.put(new int[]{2, 3}, "32767");
        return expected;
    }

    // -----------------------------------------------------------------------
    // Scenario support
    // -----------------------------------------------------------------------

    /**
     * Returns scenario files to run via {@link FileTestSuite#testScenarios()}.
     *
     * <p>Add entries here to enable scenario-based testing without writing Java.
     * Each path is relative to the test classpath root.
     */
    @Override
    protected List<String> getScenarioPaths()
    {
        return Arrays.asList(
                "scenarios/localfs/readwrite_csv.scenario",
                "scenarios/localfs/discover_root.scenario");
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
                    descriptor.addFieldsItem(new CustomFlightAssetField()
                            .name(parts[0].trim()).type(parts[1].trim()).nullable(true).signed(true));
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
                                final String type = schemaSpec.get(ci).split("\\|").length > 1
                                        ? schemaSpec.get(ci).split("\\|")[1].trim() : "varchar";
                                setter.setValue(ci, coerceValue(val, type));
                            }
                        }
                    }
                    root.setRowCount(rows.size());
                    final FlightClient.ClientStreamListener put = flightClient.startPut(
                            FlightDescriptor.command(modelMapper.toBytes(descriptor)), root,
                            new AsyncPutListener());
                    put.putNext();
                    root.clear();
                    put.completed();
                    put.getResult();
                }
            }
        };
    }

    @SuppressWarnings("PMD.CyclomaticComplexity")
    private static Serializable coerceValue(final String value, final String type)
    {
        switch (type.toLowerCase(Locale.ENGLISH)) {
        case "integer":
        case "int":      return Integer.parseInt(value);
        case "bigint":   return Long.parseLong(value);
        case "smallint": return Short.parseShort(value);
        case "tinyint":  return Byte.parseByte(value);
        case "boolean":
        case "bool":     return Boolean.parseBoolean(value);
        case "real":
        case "float":    return Float.parseFloat(value);
        case "double":   return Double.parseDouble(value);
        case "date":     return Date.valueOf(value);
        case "timestamp": return Timestamp.valueOf(value);
        case "varbinary":
        case "binary":   return value.getBytes(StandardCharsets.UTF_8);
        default:         return value;
        }
    }

    // -----------------------------------------------------------------------
    // Helper: write the 3-row, 12-column canonical test file
    // -----------------------------------------------------------------------

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

    /**
     * Creates a test file via {@code startPut} with the 12-column, 3-row
     * canonical schema (low / null / high values).
     */
    private static void createTestFile(String rootPath, String filename, String fileFormat,
            DiscoveredAssetInteractionProperties interactionProperties) throws Exception
    {
        if (interactionProperties == null) {
            interactionProperties = new DiscoveredAssetInteractionProperties();
        }
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        descriptor.setDatasourceTypeName(DATASOURCE_TYPE_NAME);
        descriptor.setConnectionProperties(new ConnectionProperties());
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

                final FlightClient.ClientStreamListener putStream = client
                        .startPut(FlightDescriptor.command(MODEL_MAPPER.toBytes(descriptor)), root, new AsyncPutListener());
                putStream.putNext();
                root.clear();
                putStream.completed();
                putStream.getResult();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Discovery — LocalFS-specific structural assertions
    // -----------------------------------------------------------------------

    /**
     * Discover files and folders at a non-root folder path.
     * Verifies the full asset-type contract for folder entries.
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
        final List<String> files = new ArrayList<>();
        for (final FlightInfo info : getClient()
                .listFlights(new Criteria(MODEL_MAPPER.toBytes(
                        new com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria()
                                .datasourceTypeName(getDatasourceTypeName())
                                .connectionProperties(connectionPropertiesWithRoot(rootFolder))
                                .path(folderPath))))) {
            final CustomFlightAssetDescriptor descriptor
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
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
            assertEquals(folderPath + '/' + descriptor.getId(), descriptor.getPath());
            files.add(descriptor.getId());
        }
        assertEquals(5, files.size());
        assertTrue(files.contains("subfolder1"));
        assertTrue(files.contains("subfolder2"));
        assertTrue(files.contains("subfolder3"));
        assertTrue(files.contains("file2.csv"));
        assertTrue(files.contains("file4.csv"));
    }

    private static ConnectionProperties connectionPropertiesWithRoot(String rootFolder)
    {
        final ConnectionProperties props = new ConnectionProperties();
        props.put("root_path", rootFolder);
        return props;
    }

    // -----------------------------------------------------------------------
    // Metadata — per-format discover-columns tests
    // -----------------------------------------------------------------------

    /** Discover a CSV file: verify mime type, field_delimiter, first_line_header, and column names. */
    @Test
    public void testDiscoverColumnsCsv() throws Exception
    {
        final String filePath = "/discovercolumns.csv";
        createTestFile(filePath);
        final com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria criteria
                = new com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(filePath);
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor d
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            assertEquals("csv", d.getInteractionProperties().get("file_format"));
            assertEquals("true", d.getInteractionProperties().get("first_line_header"));
            assertEquals(",", d.getInteractionProperties().get("field_delimiter_value"));
            assertEquals("text/csv", d.getDetails().get("mime_type"));
            assertNotNull(d.getDetails().get("file_size"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
            assertEquals("varchar_type", schema.getFields().get(0).getName());
            assertEquals("boolean_type", schema.getFields().get(1).getName());
        }
    }

    /** Discover a delimited text file: verify mime type and format. */
    @Test
    public void testDiscoverColumnsDelimited() throws Exception
    {
        final String filePath = "/discovercolumnsdelimited.txt";
        createTestFile(null, filePath, "delimited");
        final com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria criteria
                = new com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(filePath);
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor d
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            assertEquals("delimited", d.getInteractionProperties().get("file_format"));
            assertEquals("text/plain", d.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        }
    }

    /** Discover a JSON file: verify mime type and column count. */
    @Test
    public void testDiscoverColumnsJson() throws Exception
    {
        final String filePath = "/discovercolumns.json";
        createTestFile(null, filePath, "json");
        final com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria criteria
                = new com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(filePath);
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor d
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            assertEquals("json", d.getInteractionProperties().get("file_format"));
            assertEquals("application/json", d.getDetails().get("mime_type"));
            assertEquals(TEST_FILE_COLUMN_COUNT, info.getSchemaOptional().get().getFields().size());
        }
    }

    /** Discover an ORC file: verify mime type and column count. */
    @Test
    public void testDiscoverColumnsOrc() throws Exception
    {
        final String filePath = "/discovercolumns.orc";
        createTestFile(null, filePath, "orc");
        final com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria criteria
                = new com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(filePath);
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor d
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            assertEquals("orc", d.getInteractionProperties().get("file_format"));
            assertEquals("application/octet-stream", d.getDetails().get("mime_type"));
            assertEquals(TEST_FILE_COLUMN_COUNT, info.getSchemaOptional().get().getFields().size());
        }
    }

    /** Discover a Parquet file: verify mime type and column count. */
    @Test
    public void testDiscoverColumnsParquet() throws Exception
    {
        final String filePath = "/discovercolumns.parquet";
        createTestFile(null, filePath, "parquet");
        final com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria criteria
                = new com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(filePath);
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor d
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            assertEquals("parquet", d.getInteractionProperties().get("file_format"));
            assertEquals("application/x-parquet", d.getDetails().get("mime_type"));
            assertEquals(TEST_FILE_COLUMN_COUNT, info.getSchemaOptional().get().getFields().size());
        }
    }

    /** Discover an XML file: verify mime type and column count. */
    @Test
    public void testDiscoverColumnsXml() throws Exception
    {
        final String filePath = "/discovercolumns.xml";
        createTestFile(null, filePath, "xml");
        final com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria criteria
                = new com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(filePath);
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor d
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            assertEquals("xml", d.getInteractionProperties().get("file_format"));
            assertEquals("application/xml", d.getDetails().get("mime_type"));
            assertEquals(TEST_FILE_COLUMN_COUNT, info.getSchemaOptional().get().getFields().size());
        }
    }

    // -----------------------------------------------------------------------
    // Read — per-format getStream tests
    // -----------------------------------------------------------------------

    /** Read a CSV with first_line_header=true: verify column names and cell values. */
    @Test
    public void testGetStreamCsv() throws Exception
    {
        final String filePath = "/getstream.csv";
        createTestFile(filePath);
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", filePath);
        final FlightInfo info = getFlightInfo(props);
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        assertEquals("varchar_type", schema.getFields().get(0).getName());
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
     * Read a CSV written without a header row (first_line_header=false): verify
     * that columns are named _c0, _c1, … and data includes the header as row 0.
     */
    @Test
    public void testGetStreamCsvFirstLineHeader() throws Exception
    {
        final String filePath = "/getstreamfirstlineheader.csv";
        final DiscoveredAssetInteractionProperties writeProps = new DiscoveredAssetInteractionProperties();
        writeProps.put("first_line_header", "false");
        createTestFile(filePath, writeProps);
        final DiscoveredAssetInteractionProperties readProps = new DiscoveredAssetInteractionProperties();
        readProps.put("file_name", filePath);
        final FlightInfo info = getFlightInfo(readProps);
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        assertEquals("_c0", schema.getFields().get(0).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
    }

    /** Read a CSV with infer_schema=false: all values returned as strings. */
    @Test
    public void testGetStreamCsvInferSchemaFalse() throws Exception
    {
        final String filePath = "/getstreaminferschemafalse.csv";
        createTestFile(filePath);
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", filePath);
        props.put("infer_schema", "false");
        final Table<Integer, Integer, Object> data = getTableData(getFlightInfo(props));
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertEquals("false", data.get(0, 1));
        assertEquals("-128", data.get(0, 2));
    }

    /** Read a CSV with infer_schema=true: numeric/boolean values are returned with native types. */
    @Test
    public void testGetStreamCsvInferSchemaTrue() throws Exception
    {
        final String filePath = "/getstreaminferschematrue.csv";
        createTestFile(filePath);
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", filePath);
        props.put("infer_schema", "true");
        final Table<Integer, Integer, Object> data = getTableData(getFlightInfo(props));
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertFalse((Boolean) data.get(0, 1));
        assertEquals(-128, data.get(0, 2));
        assertEquals(-32768, data.get(0, 3));
        assertNull(data.get(1, 1));
        assertTrue((Boolean) data.get(2, 1));
        assertEquals(127, data.get(2, 2));
        assertEquals(32767, data.get(2, 3));
    }

    /** Read a delimited text file: verify column count and cell values. */
    @Test
    public void testGetStreamDelimited() throws Exception
    {
        final String filePath = "/getstreamdelimited.txt";
        createTestFile(null, filePath, "delimited");
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", filePath);
        final Table<Integer, Integer, Object> data = getTableData(getFlightInfo(props));
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertEquals("Null values", data.get(1, 0));
        assertNull(data.get(1, 1));
        assertEquals("High values", data.get(2, 0));
    }

    /** Read a JSON file: verify column ordering (JSON sorts alphabetically) and values. */
    @Test
    public void testGetStreamJson() throws Exception
    {
        final String filePath = "/getstream.json";
        createTestFile(null, filePath, "json");
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", filePath);
        final FlightInfo info = getFlightInfo(props);
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        assertEquals("bigint_type", schema.getFields().get(0).getName());
        assertEquals("boolean_type", schema.getFields().get(1).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals(Long.MIN_VALUE, data.get(0, 0));
        assertFalse((Boolean) data.get(0, 1));
        assertNull(data.get(1, 0));
        assertEquals(Long.MAX_VALUE, data.get(2, 0));
        assertTrue((Boolean) data.get(2, 1));
    }

    /** Read an ORC file: verify typed values for boolean/int columns. */
    @Test
    public void testGetStreamOrc() throws Exception
    {
        final String filePath = "/getstream.orc";
        createTestFile(null, filePath, "orc");
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", filePath);
        final Table<Integer, Integer, Object> data = getTableData(getFlightInfo(props));
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertFalse((Boolean) data.get(0, 1));
        assertEquals(Byte.MIN_VALUE, data.get(0, 2));
        assertEquals(Short.MIN_VALUE, data.get(0, 3));
        assertNull(data.get(1, 1));
        assertTrue((Boolean) data.get(2, 1));
        assertEquals(Byte.MAX_VALUE, data.get(2, 2));
        assertEquals(Short.MAX_VALUE, data.get(2, 3));
    }

    /** Read an ORC file written with Snappy compression. */
    @Test
    public void testGetStreamOrcSnappy() throws Exception
    {
        final String filePath = "/getstream.snappy.orc";
        final DiscoveredAssetInteractionProperties writeProps = new DiscoveredAssetInteractionProperties();
        writeProps.put("compression", "snappy");
        createTestFile(null, filePath, "orc", writeProps);
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", filePath);
        final Table<Integer, Integer, Object> data = getTableData(getFlightInfo(props));
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertFalse((Boolean) data.get(0, 1));
        assertEquals(Byte.MIN_VALUE, data.get(0, 2));
        assertNull(data.get(1, 1));
        assertTrue((Boolean) data.get(2, 1));
    }

    /** Read a Parquet file: verify typed values for boolean/int columns. */
    @Test
    public void testGetStreamParquet() throws Exception
    {
        final String filePath = "/getstream.parquet";
        createTestFile(null, filePath, "parquet");
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", filePath);
        final Table<Integer, Integer, Object> data = getTableData(getFlightInfo(props));
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertFalse((Boolean) data.get(0, 1));
        assertEquals(Byte.MIN_VALUE, data.get(0, 2));
        assertNull(data.get(1, 1));
        assertTrue((Boolean) data.get(2, 1));
    }

    /** Read a Parquet file written with Snappy compression. */
    @Test
    public void testGetStreamParquetSnappy() throws Exception
    {
        final String filePath = "/getstream.snappy.parquet";
        final DiscoveredAssetInteractionProperties writeProps = new DiscoveredAssetInteractionProperties();
        writeProps.put("compression", "snappy");
        createTestFile(null, filePath, "parquet", writeProps);
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", filePath);
        final Table<Integer, Integer, Object> data = getTableData(getFlightInfo(props));
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertFalse((Boolean) data.get(0, 1));
        assertEquals(Byte.MIN_VALUE, data.get(0, 2));
        assertNull(data.get(1, 1));
        assertTrue((Boolean) data.get(2, 1));
    }

    /** Read an XML file: verify column ordering (alphabetical, as with JSON) and cell values. */
    @Test
    public void testGetStreamXml() throws Exception
    {
        final String filePath = "/getstream.xml";
        createTestFile(null, filePath, "xml");
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", filePath);
        final FlightInfo info = getFlightInfo(props);
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(TEST_FILE_COLUMN_COUNT, schema.getFields().size());
        assertEquals("bigint_type", schema.getFields().get(0).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(TEST_FILE_VALUES_COUNT, data.size());
        assertEquals(Double.valueOf(String.valueOf(Long.MIN_VALUE)), data.get(0, 0));
        assertFalse((Boolean) data.get(0, 1));
        assertEquals(Date.valueOf("1970-01-01"), data.get(0, 2));
        assertNull(data.get(1, 0));
        assertEquals("Null values", data.get(1, 11));
        assertEquals(Double.valueOf(String.valueOf(Long.MAX_VALUE)), data.get(2, 0));
        assertTrue((Boolean) data.get(2, 1));
    }

    // -----------------------------------------------------------------------
    // Read — limit tests with exact cell-value assertions
    // -----------------------------------------------------------------------

    /** row_limit=1 must return exactly the first row (12 cells). */
    @Test
    public void testGetStreamRowLimitExact() throws Exception
    {
        final String filePath = "/getstreamrowlimit.csv";
        createTestFile(filePath);
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", filePath);
        props.put("row_limit", "1");
        final Table<Integer, Integer, Object> data = getTableData(getFlightInfo(props));
        assertEquals(TEST_FILE_COLUMN_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertEquals("false", data.get(0, 1));
        assertEquals("-128", data.get(0, 2));
        assertEquals("-32768", data.get(0, 3));
    }

    /** byte_limit=10 must return exactly the first row (truncated at 10 bytes). */
    @Test
    public void testGetStreamByteLimitExact() throws Exception
    {
        final String filePath = "/getstreambytelimit.csv";
        createTestFile(filePath);
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        props.put("file_name", filePath);
        props.put("byte_limit", "10");
        final Table<Integer, Integer, Object> data = getTableData(getFlightInfo(props));
        assertEquals(TEST_FILE_COLUMN_COUNT, data.size());
        assertEquals("Low values", data.get(0, 0));
        assertEquals("false", data.get(0, 1));
        assertEquals("-128", data.get(0, 2));
        assertEquals("-32768", data.get(0, 3));
    }

    // -----------------------------------------------------------------------
    // Flight configuration — all from tests.properties
    // -----------------------------------------------------------------------

    /**
     * All LocalFS Flight-server settings, resolved from {@code tests.properties}.
     *
     * <p>Because LocalFS needs no external credentials, {@link #isConfigured()} is
     * always {@code true}.
     */
    private static final class LocalFSConfig
    {
        final boolean createLocal = TestConfig.getBoolean("file_localfs.flight.createLocal", true);
        final boolean useSSL = TestConfig.getBoolean("file_localfs.flight.ssl", true);
        final int port = TestConfig.getPort("file_localfs.flight.port");

        // Remote-server settings (used when createLocal=false)
        final String remoteUri = TestConfig.get("file_localfs.flight.uri");
        final String sslCertificate = TestConfig.get("file_localfs.flight.ssl_certificate");
        final boolean verifyCert = TestConfig.getBoolean("file_localfs.flight.ssl_certificate_validation", true);

        boolean isConfigured()
        {
            // LocalFS always works — no external service required.
            return true;
        }
    }
}
