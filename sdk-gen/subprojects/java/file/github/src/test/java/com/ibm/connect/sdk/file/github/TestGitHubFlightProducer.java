/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.github;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

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
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionRequest;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

/**
 * Tests a flight producer for GitHub.
 */
public class TestGitHubFlightProducer extends ConnectorTestSuite
{
    private static final Logger LOGGER = getLogger(TestGitHubFlightProducer.class);

    private static final String DATASOURCE_TYPE_NAME = GitHubDatasourceType.DATASOURCE_TYPE_NAME;

    private static final String GITHUB_HOST = TestConfig.get("file_github.github.host", "github.com");
    private static final String GITHUB_REPOSITORY_OWNER = TestConfig.get("file_github.github.repository_owner", "apache");
    private static final String GITHUB_REPOSITORY_NAME = TestConfig.get("file_github.github.repository_name", "spark");
    private static final String GITHUB_ACCESS_TOKEN = TestConfig.get("file_github.github.access_token");

    private static final String BRANCH_NAME = "master";
    private static final String BRANCH_PATH = "/" + BRANCH_NAME;
    private static final String TEST_DATA_PATH = "sql/core/src/test/resources/test-data";
    private static final String CSV_FILE_NAME = "cars.csv";
    private static final String CSV_FILE_PATH = TEST_DATA_PATH + '/' + CSV_FILE_NAME;
    private static final String CSV_COMMENT_FILE_NAME = "comments.csv";
    private static final String CSV_COMMENT_FILE_PATH = TEST_DATA_PATH + '/' + CSV_COMMENT_FILE_NAME;
    private static final String CSV_ENCODING_FILE_NAME = "cars_iso-8859-1.csv";
    private static final String CSV_ENCODING_FILE_PATH = TEST_DATA_PATH + '/' + CSV_ENCODING_FILE_NAME;
    private static final String CSV_INFER_SCHEMA_FILE_NAME = "date-infer-schema.csv";
    private static final String CSV_INFER_SCHEMA_FILE_PATH = TEST_DATA_PATH + '/' + CSV_INFER_SCHEMA_FILE_NAME;
    private static final String CSV_NULL_VALUE_FILE_NAME = "cars-null.csv";
    private static final String CSV_NULL_VALUE_FILE_PATH = TEST_DATA_PATH + '/' + CSV_NULL_VALUE_FILE_NAME;
    private static final String CSV_ROW_DELIMITER_FILE_NAME = "cars-crlf.csv";
    private static final String CSV_ROW_DELIMITER_FILE_PATH = TEST_DATA_PATH + '/' + CSV_ROW_DELIMITER_FILE_NAME;
    private static final String CSV_BOOLEAN_FILE_NAME = "bool.csv";
    private static final String CSV_BOOLEAN_FILE_PATH = TEST_DATA_PATH + '/' + CSV_BOOLEAN_FILE_NAME;
    private static final String CSV_DECIMAL_FILE_NAME = "decimal.csv";
    private static final String CSV_DECIMAL_FILE_PATH = TEST_DATA_PATH + '/' + CSV_DECIMAL_FILE_NAME;
    private static final String CSV_NUMERIC_FILE_NAME = "numbers.csv";
    private static final String CSV_NUMERIC_FILE_PATH = TEST_DATA_PATH + '/' + CSV_NUMERIC_FILE_NAME;
    private static final String DELIMITED_FILE_NAME = "text-suite.txt";
    private static final String DELIMITED_FILE_PATH = TEST_DATA_PATH + '/' + DELIMITED_FILE_NAME;
    private static final String DELIMITED_PIPE_FILE_NAME = "cars-alternative.csv";
    private static final String DELIMITED_PIPE_FILE_PATH = TEST_DATA_PATH + '/' + DELIMITED_PIPE_FILE_NAME;
    private static final String JSON_FILE_NAME = "with-map-fields.json";
    private static final String JSON_FILE_PATH = TEST_DATA_PATH + '/' + JSON_FILE_NAME;
    private static final String ORC_FILE_NAME = "TestStringDictionary.testRowIndex.orc";
    private static final String ORC_FILE_PATH = TEST_DATA_PATH + '/' + ORC_FILE_NAME;
    private static final String ORC_SNAPPY_FILE_NAME = "before_1582_date_v2_4.snappy.orc";
    private static final String ORC_SNAPPY_FILE_PATH = TEST_DATA_PATH + '/' + ORC_SNAPPY_FILE_NAME;
    private static final String PARQUET_FILE_NAME = "dec-in-fixed-len.parquet";
    private static final String PARQUET_FILE_PATH = TEST_DATA_PATH + '/' + PARQUET_FILE_NAME;
    private static final String PARQUET_SNAPPY_FILE_NAME = "before_1582_date_v3_2_0.snappy.parquet";
    private static final String PARQUET_SNAPPY_FILE_PATH = TEST_DATA_PATH + '/' + PARQUET_SNAPPY_FILE_NAME;
    private static final String XML_FILE_NAME = "cars.xml";
    private static final String XML_FILE_PATH = TEST_DATA_PATH + "/xml-resources/" + XML_FILE_NAME;

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
        assumeNotNull(GITHUB_ACCESS_TOKEN);
    }

    /**
     * Setup before tests.
     *
     * @throws Exception
     */
    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        if (Boolean.parseBoolean(TestConfig.get("file_github.flight.createLocal", "true"))) {
            final boolean useSSL = Boolean.parseBoolean(TestConfig.get("file_github.flight.ssl", "true"));
            testFlight = TestFlight.createLocal(TestConfig.getPort("file_github.flight.port"), useSSL, new GitHubFlightProducer(), null);
        } else {
            final boolean verifyCert = Boolean.parseBoolean(TestConfig.get("file_github.flight.ssl_certificate_validation", "true"));
            testFlight
                    = TestFlight.createRemote(TestConfig.get("file_github.flight.uri.internal", TestConfig.get("file_github.flight.uri")),
                            TestConfig.get("file_github.flight.ssl_certificate"), verifyCert, null);
        }
        client = testFlight.getClient();
        defaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    private static ConnectionProperties createGitHubConnectionProperties()
    {
        final ConnectionProperties connectionProperties = new ConnectionProperties();
        connectionProperties.put("host", GITHUB_HOST);
        connectionProperties.put("repository_owner", GITHUB_REPOSITORY_OWNER);
        connectionProperties.put("repository_name", GITHUB_REPOSITORY_NAME);
        if (GITHUB_ACCESS_TOKEN != null) {
            connectionProperties.put("access_token", GITHUB_ACCESS_TOKEN);
        }
        return connectionProperties;
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
        return createGitHubConnectionProperties();
    }

    /**
     * Test validate action with invalid properties.
     *
     * @throws Exception
     */
    @Test
    public void testConnectionMissingHost() throws Exception
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        request.getConnectionProperties().remove("host");
        try {
            getClient().doAction(new Action("validate", modelMapper.toBytes(request))).next();
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("Missing host"));
        }
    }

    /**
     * Test discover branches.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverBranches() throws Exception
    {
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath("/");
        final List<String> branches = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            assertNotNull(descriptor.getAssetType());
            assertEquals("branch", descriptor.getAssetType().getType());
            assertFalse(descriptor.getAssetType().isDataset());
            assertTrue(descriptor.getAssetType().isDatasetContainer());
            assertNotNull(descriptor.getId());
            assertNotNull(descriptor.getName());
            final String expectedPath = "/" + descriptor.getId();
            assertEquals(expectedPath, descriptor.getPath());
            branches.add(descriptor.getId());
        }
        assertTrue(branches.contains(BRANCH_NAME));
    }

    /**
     * Test discover branches with paging options.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverBranchesWithPaging() throws Exception
    {
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath("/");
        criteria.setOffset(2);
        criteria.setLimit(3);
        final List<String> branches = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(modelMapper.toBytes(criteria)))) {
            final FlightDescriptor flightDescriptor = info.getDescriptor();
            final CustomFlightAssetDescriptor descriptor
                    = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
            branches.add(descriptor.getId());
        }
        assertEquals(3, branches.size());
    }

    /**
     * Test discover files.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverFiles() throws Exception
    {
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(BRANCH_PATH);
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
            final String expectedPath = BRANCH_PATH + '/' + descriptor.getId();
            assertEquals(expectedPath, descriptor.getPath());
            files.add(descriptor.getId());
        }
        assertTrue(files.contains("sql"));
    }

    /**
     * Test discover files with paging options.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverFilesWithPaging() throws Exception
    {
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(BRANCH_PATH);
        criteria.setOffset(2);
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
        final String filePath = BRANCH_PATH + '/' + CSV_FILE_PATH;
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
            assertEquals(BRANCH_NAME, descriptor.getInteractionProperties().get("branch_name"));
            assertEquals(CSV_FILE_PATH, descriptor.getInteractionProperties().get("file_name"));
            assertEquals("csv", descriptor.getInteractionProperties().get("file_format"));
            assertEquals("true", descriptor.getInteractionProperties().get("first_line_header"));
            assertEquals(",", descriptor.getInteractionProperties().get("field_delimiter_value"));
            assertEquals("\n", descriptor.getInteractionProperties().get("row_delimiter_value"));
            assertNotNull(descriptor.getDetails());
            assertNotNull(descriptor.getDetails().get("file_size"));
            assertEquals("text/csv", descriptor.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(5, schema.getFields().size());
            assertEquals("year", schema.getFields().get(0).getName());
            assertEquals("make", schema.getFields().get(1).getName());
            assertEquals("model", schema.getFields().get(2).getName());
            assertEquals("comment", schema.getFields().get(3).getName());
            assertEquals("blank", schema.getFields().get(4).getName());
            files.add(descriptor.getId());
        }
        assertEquals(1, files.size());
        assertTrue(files.contains(CSV_FILE_NAME));
    }

    /**
     * Test discover delimited columns.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumnsDelimited() throws Exception
    {
        final String filePath = BRANCH_PATH + '/' + DELIMITED_FILE_PATH;
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
            assertEquals(BRANCH_NAME, descriptor.getInteractionProperties().get("branch_name"));
            assertEquals(DELIMITED_FILE_PATH, descriptor.getInteractionProperties().get("file_name"));
            assertEquals("delimited", descriptor.getInteractionProperties().get("file_format"));
            assertEquals("true", descriptor.getInteractionProperties().get("first_line_header"));
            assertEquals("\n", descriptor.getInteractionProperties().get("row_delimiter_value"));
            assertNotNull(descriptor.getDetails());
            assertNotNull(descriptor.getDetails().get("file_size"));
            assertEquals("text/plain", descriptor.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(1, schema.getFields().size());
            assertEquals("This is a test file for the text data source", schema.getFields().get(0).getName());
            files.add(descriptor.getId());
        }
        assertEquals(1, files.size());
        assertTrue(files.contains(DELIMITED_FILE_NAME));
    }

    /**
     * Test discover json columns.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumnsJson() throws Exception
    {
        final String filePath = BRANCH_PATH + '/' + JSON_FILE_PATH;
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
            assertEquals(BRANCH_NAME, descriptor.getInteractionProperties().get("branch_name"));
            assertEquals(JSON_FILE_PATH, descriptor.getInteractionProperties().get("file_name"));
            assertEquals("json", descriptor.getInteractionProperties().get("file_format"));
            assertNotNull(descriptor.getDetails());
            assertNotNull(descriptor.getDetails().get("file_size"));
            assertEquals("application/json", descriptor.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(2, schema.getFields().size());
            files.add(descriptor.getId());
        }
        assertEquals(1, files.size());
        assertTrue(files.contains(JSON_FILE_NAME));
    }

    /**
     * Test discover orc columns.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumnsOrc() throws Exception
    {
        final String filePath = BRANCH_PATH + '/' + ORC_FILE_PATH;
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
            assertEquals(BRANCH_NAME, descriptor.getInteractionProperties().get("branch_name"));
            assertEquals(ORC_FILE_PATH, descriptor.getInteractionProperties().get("file_name"));
            assertEquals("orc", descriptor.getInteractionProperties().get("file_format"));
            assertNotNull(descriptor.getDetails());
            assertNotNull(descriptor.getDetails().get("file_size"));
            assertEquals("application/octet-stream", descriptor.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(1, schema.getFields().size());
            files.add(descriptor.getId());
        }
        assertEquals(1, files.size());
        assertTrue(files.contains(ORC_FILE_NAME));
    }

    /**
     * Test discover parquet columns.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumnsParquet() throws Exception
    {
        final String filePath = BRANCH_PATH + '/' + PARQUET_FILE_PATH;
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
            assertEquals(BRANCH_NAME, descriptor.getInteractionProperties().get("branch_name"));
            assertEquals(PARQUET_FILE_PATH, descriptor.getInteractionProperties().get("file_name"));
            assertEquals("parquet", descriptor.getInteractionProperties().get("file_format"));
            assertNotNull(descriptor.getDetails());
            assertNotNull(descriptor.getDetails().get("file_size"));
            assertEquals("application/x-parquet", descriptor.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(1, schema.getFields().size());
            files.add(descriptor.getId());
        }
        assertEquals(1, files.size());
        assertTrue(files.contains(PARQUET_FILE_NAME));
    }

    /**
     * Test discover xml columns.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverColumnsXml() throws Exception
    {
        final String filePath = BRANCH_PATH + '/' + XML_FILE_PATH;
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
            assertEquals(BRANCH_NAME, descriptor.getInteractionProperties().get("branch_name"));
            assertEquals(XML_FILE_PATH, descriptor.getInteractionProperties().get("file_name"));
            assertEquals("xml", descriptor.getInteractionProperties().get("file_format"));
            assertNotNull(descriptor.getDetails());
            assertNotNull(descriptor.getDetails().get("file_size"));
            assertEquals("application/xml", descriptor.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().get();
            assertEquals(4, schema.getFields().size());
            files.add(descriptor.getId());
        }
        assertEquals(1, files.size());
        assertTrue(files.contains(XML_FILE_NAME));
    }

    /**
     * Test getFlightInfo.
     *
     * @throws Exception
     */
    @Test
    public void testGetFlightInfo() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", CSV_FILE_PATH);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(BRANCH_NAME, returnedDescriptor.getInteractionProperties().get("branch_name"));
        assertEquals(CSV_FILE_PATH, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("csv", returnedDescriptor.getInteractionProperties().get("file_format"));
        assertEquals("true", returnedDescriptor.getInteractionProperties().get("first_line_header"));
        assertEquals(",", returnedDescriptor.getInteractionProperties().get("field_delimiter_value"));
        assertEquals("\n", returnedDescriptor.getInteractionProperties().get("row_delimiter_value"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(5, schema.getFields().size());
        assertEquals("year", schema.getFields().get(0).getName());
        assertEquals("make", schema.getFields().get(1).getName());
        assertEquals("model", schema.getFields().get(2).getName());
        assertEquals("comment", schema.getFields().get(3).getName());
        assertEquals("blank", schema.getFields().get(4).getName());
    }

    /**
     * Test getStream with csv.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsv() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", CSV_FILE_PATH);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(BRANCH_NAME, returnedDescriptor.getInteractionProperties().get("branch_name"));
        assertEquals(CSV_FILE_PATH, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("csv", returnedDescriptor.getInteractionProperties().get("file_format"));
        assertEquals("true", returnedDescriptor.getInteractionProperties().get("first_line_header"));
        assertEquals(",", returnedDescriptor.getInteractionProperties().get("field_delimiter_value"));
        assertEquals("\n", returnedDescriptor.getInteractionProperties().get("row_delimiter_value"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(5, schema.getFields().size());
        assertEquals("year", schema.getFields().get(0).getName());
        assertEquals("make", schema.getFields().get(1).getName());
        assertEquals("model", schema.getFields().get(2).getName());
        assertEquals("comment", schema.getFields().get(3).getName());
        assertEquals("blank", schema.getFields().get(4).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(11, data.size());
        assertEquals("2012", data.get(0, 0));
        assertEquals("Tesla", data.get(0, 1));
        assertEquals("S", data.get(0, 2));
        assertEquals("No comment", data.get(0, 3));
        assertNull(data.get(0, 4));
        assertEquals("2015", data.get(2, 0));
        assertEquals("Chevy", data.get(2, 1));
        assertEquals("Volt", data.get(2, 2));
        assertNull(data.get(2, 3));
        assertNull(data.get(2, 4));
    }

    /**
     * Test getStream with csv and first_line_header.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsvFirstLineHeader() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", CSV_FILE_PATH);
        interactionProperties.put("first_line_header", "false");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(BRANCH_NAME, returnedDescriptor.getInteractionProperties().get("branch_name"));
        assertEquals(CSV_FILE_PATH, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("csv", returnedDescriptor.getInteractionProperties().get("file_format"));
        assertEquals("false", returnedDescriptor.getInteractionProperties().get("first_line_header"));
        assertEquals(",", returnedDescriptor.getInteractionProperties().get("field_delimiter_value"));
        assertEquals("\n", returnedDescriptor.getInteractionProperties().get("row_delimiter_value"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(5, schema.getFields().size());
        assertEquals("_c0", schema.getFields().get(0).getName());
        assertEquals("_c1", schema.getFields().get(1).getName());
        assertEquals("_c2", schema.getFields().get(2).getName());
        assertEquals("_c3", schema.getFields().get(3).getName());
        assertEquals("_c4", schema.getFields().get(4).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(16, data.size());
        assertEquals("year", data.get(0, 0));
        assertEquals("make", data.get(0, 1));
        assertEquals("model", data.get(0, 2));
        assertEquals("comment", data.get(0, 3));
        assertEquals("blank", data.get(0, 4));
        assertEquals("2015", data.get(3, 0));
        assertEquals("Chevy", data.get(3, 1));
        assertEquals("Volt", data.get(3, 2));
        assertNull(data.get(3, 3));
        assertNull(data.get(3, 4));
    }

    /**
     * Test getStream with csv and comment_character_value.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsvComment() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", CSV_COMMENT_FILE_PATH);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(BRANCH_NAME, returnedDescriptor.getInteractionProperties().get("branch_name"));
        assertEquals(CSV_COMMENT_FILE_PATH, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("csv", returnedDescriptor.getInteractionProperties().get("file_format"));
        assertEquals("~", returnedDescriptor.getInteractionProperties().get("comment_character_value"));
        assertEquals("false", returnedDescriptor.getInteractionProperties().get("first_line_header"));
        assertEquals(",", returnedDescriptor.getInteractionProperties().get("field_delimiter_value"));
        assertEquals("\n", returnedDescriptor.getInteractionProperties().get("row_delimiter_value"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(6, schema.getFields().size());
        assertEquals("_c0", schema.getFields().get(0).getName());
        assertEquals("_c1", schema.getFields().get(1).getName());
        assertEquals("_c2", schema.getFields().get(2).getName());
        assertEquals("_c3", schema.getFields().get(3).getName());
        assertEquals("_c4", schema.getFields().get(4).getName());
        assertEquals("_c5", schema.getFields().get(5).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(18, data.size());
        assertEquals("1", data.get(0, 0));
        assertEquals("2", data.get(0, 1));
        assertEquals("3", data.get(0, 2));
        assertEquals("4", data.get(0, 3));
        assertEquals("5.01", data.get(0, 4));
        assertEquals("2015-08-20 15:57:00", data.get(0, 5));
        assertEquals("1", data.get(2, 0));
        assertEquals("2", data.get(2, 1));
        assertEquals("3", data.get(2, 2));
        assertEquals("4", data.get(2, 3));
        assertEquals("5", data.get(2, 4));
        assertEquals("2015-08-23 18:00:42", data.get(2, 5));
    }

    /**
     * Test getStream with csv and encoding.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsvEncoding() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", CSV_ENCODING_FILE_PATH);
        interactionProperties.put("encoding", "iso-8859-1");
        interactionProperties.put("first_line_header", "true");
        interactionProperties.put("field_delimiter_value", "\u00FE");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(BRANCH_NAME, returnedDescriptor.getInteractionProperties().get("branch_name"));
        assertEquals(CSV_ENCODING_FILE_PATH, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("csv", returnedDescriptor.getInteractionProperties().get("file_format"));
        assertEquals("iso-8859-1", returnedDescriptor.getInteractionProperties().get("encoding"));
        assertEquals("true", returnedDescriptor.getInteractionProperties().get("first_line_header"));
        assertEquals("\u00FE", returnedDescriptor.getInteractionProperties().get("field_delimiter_value"));
        assertEquals("\n", returnedDescriptor.getInteractionProperties().get("row_delimiter_value"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(5, schema.getFields().size());
        assertEquals("year", schema.getFields().get(0).getName());
        assertEquals("make", schema.getFields().get(1).getName());
        assertEquals("model", schema.getFields().get(2).getName());
        assertEquals("comment", schema.getFields().get(3).getName());
        assertEquals("blank", schema.getFields().get(4).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(11, data.size());
        assertEquals("2012", data.get(0, 0));
        assertEquals("Tesla", data.get(0, 1));
        assertEquals("S", data.get(0, 2));
        assertEquals("No comment", data.get(0, 3));
        assertNull(data.get(0, 4));
        assertEquals("2015", data.get(2, 0));
        assertEquals("Chevy", data.get(2, 1));
        assertEquals("Volt", data.get(2, 2));
        assertNull(data.get(2, 3));
        assertNull(data.get(2, 4));
    }

    /**
     * Test getStream with csv and infer_schema=false.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsvInferSchemaFalse() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", CSV_INFER_SCHEMA_FILE_PATH);
        interactionProperties.put("infer_schema", "false");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(3, schema.getFields().size());
        assertEquals("date", schema.getFields().get(0).getName());
        assertEquals("timestamp-date", schema.getFields().get(1).getName());
        assertEquals("date-timestamp", schema.getFields().get(2).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(9, data.size());
        assertEquals("2001-09-08", data.get(0, 0));
        assertEquals("2014-10-27T18:30:00", data.get(0, 1));
        assertEquals("1765-03-28", data.get(0, 2));
        assertEquals("0293-11-07", data.get(2, 0));
        assertEquals("1995-06-25", data.get(2, 1));
        assertEquals("2016-01-28T20:00:00", data.get(2, 2));
    }

    /**
     * Test getStream with csv and infer_schema=true.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsvInferSchemaTrue() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", CSV_INFER_SCHEMA_FILE_PATH);
        interactionProperties.put("infer_schema", "true");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(3, schema.getFields().size());
        assertEquals("date", schema.getFields().get(0).getName());
        assertEquals("timestamp-date", schema.getFields().get(1).getName());
        assertEquals("date-timestamp", schema.getFields().get(2).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(9, data.size());
        assertEquals(Date.valueOf("2001-09-08"), data.get(0, 0));
        assertEquals(Timestamp.valueOf("2014-10-27 18:30:00.0"), data.get(0, 1));
        assertEquals(Timestamp.valueOf("1765-03-28 00:00:00.0"), data.get(0, 2));
        assertEquals(Date.valueOf("0293-11-07"), data.get(2, 0));
        assertEquals(Timestamp.valueOf("1995-06-25 00:00:00.0"), data.get(2, 1));
        assertEquals(Timestamp.valueOf("2016-01-28 20:00:00.0"), data.get(2, 2));
    }

    /**
     * Test getStream with csv and null_value.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsvNullValue() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", CSV_NULL_VALUE_FILE_PATH);
        interactionProperties.put("null_value", "null");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(5, schema.getFields().size());
        assertEquals("year", schema.getFields().get(0).getName());
        assertEquals("make", schema.getFields().get(1).getName());
        assertEquals("model", schema.getFields().get(2).getName());
        assertEquals("comment", schema.getFields().get(3).getName());
        assertEquals("blank", schema.getFields().get(4).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(9, data.size());
        assertEquals("2012", data.get(0, 0));
        assertEquals("Tesla", data.get(0, 1));
        assertEquals("S", data.get(0, 2));
        assertNull(data.get(0, 3));
        assertNull(data.get(0, 4));
        assertNull(data.get(2, 0));
        assertEquals("Chevy", data.get(2, 1));
        assertEquals("Volt", data.get(2, 2));
        assertNull(data.get(2, 3));
        assertNull(data.get(2, 4));
    }

    /**
     * Test getStream with csv and row_delimiter_value.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsvRowDelimiter() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", CSV_ROW_DELIMITER_FILE_PATH);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(BRANCH_NAME, returnedDescriptor.getInteractionProperties().get("branch_name"));
        assertEquals(CSV_ROW_DELIMITER_FILE_PATH, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("csv", returnedDescriptor.getInteractionProperties().get("file_format"));
        assertEquals("true", returnedDescriptor.getInteractionProperties().get("first_line_header"));
        assertEquals(",", returnedDescriptor.getInteractionProperties().get("field_delimiter_value"));
        assertEquals("\r\n", returnedDescriptor.getInteractionProperties().get("row_delimiter_value"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(5, schema.getFields().size());
        assertEquals("year", schema.getFields().get(0).getName());
        assertEquals("make", schema.getFields().get(1).getName());
        assertEquals("model", schema.getFields().get(2).getName());
        assertEquals("comment", schema.getFields().get(3).getName());
        assertEquals("blank", schema.getFields().get(4).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(11, data.size());
        assertEquals("2012", data.get(0, 0));
        assertEquals("Tesla", data.get(0, 1));
        assertEquals("S", data.get(0, 2));
        assertEquals("No comment", data.get(0, 3));
        assertNull(data.get(0, 4));
        assertEquals("2015", data.get(2, 0));
        assertEquals("Chevy", data.get(2, 1));
        assertEquals("Volt", data.get(2, 2));
        assertNull(data.get(2, 3));
        assertNull(data.get(2, 4));
    }

    /**
     * Test getStream with delimited and field_delimiter_value.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamDelimited() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", DELIMITED_PIPE_FILE_PATH);
        interactionProperties.put("quote_character_value", "'");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(BRANCH_NAME, returnedDescriptor.getInteractionProperties().get("branch_name"));
        assertEquals(DELIMITED_PIPE_FILE_PATH, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("csv", returnedDescriptor.getInteractionProperties().get("file_format"));
        assertEquals("true", returnedDescriptor.getInteractionProperties().get("first_line_header"));
        assertEquals("|", returnedDescriptor.getInteractionProperties().get("field_delimiter_value"));
        assertEquals("'", returnedDescriptor.getInteractionProperties().get("quote_character_value"));
        assertEquals("\n", returnedDescriptor.getInteractionProperties().get("row_delimiter_value"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(5, schema.getFields().size());
        assertEquals("year", schema.getFields().get(0).getName());
        assertEquals("make", schema.getFields().get(1).getName());
        assertEquals("model", schema.getFields().get(2).getName());
        assertEquals("comment", schema.getFields().get(3).getName());
        assertEquals("blank", schema.getFields().get(4).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(11, data.size());
        assertEquals("2012", data.get(0, 0));
        assertEquals("Tesla", data.get(0, 1));
        assertEquals("S", data.get(0, 2));
        assertEquals(" 'No comment'", data.get(0, 3));
        assertNull(data.get(0, 4));
        assertEquals("2015", data.get(2, 0));
        assertEquals("Chevy", data.get(2, 1));
        assertEquals("Volt", data.get(2, 2));
        assertNull(data.get(2, 3));
        assertNull(data.get(2, 4));
    }

    /**
     * Test getStream with json.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamJson() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", JSON_FILE_PATH);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(2, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(9, data.size());
        assertEquals(Long.valueOf("1"), data.get(0, 0));
        assertEquals("[[211,111],[221,121]]", data.get(0, 1));
        assertEquals(Long.valueOf("5"), data.get(4, 0));
        assertNull(data.get(4, 1));
    }

    /**
     * Test getStream with orc.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamOrc() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", ORC_FILE_PATH);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(1, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(32768, data.size());
        assertEquals("row 000000", data.get(0, 0));
        assertEquals("row 032767", data.get(32767, 0));
    }

    /**
     * Test getStream with orc and snappy.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamOrcSnappy() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", ORC_SNAPPY_FILE_PATH);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(BRANCH_NAME, returnedDescriptor.getInteractionProperties().get("branch_name"));
        assertEquals(ORC_SNAPPY_FILE_PATH, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("application/octet-stream", returnedDescriptor.getDetails().get("mime_type"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(1, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(1, data.size());
        assertEquals(Date.valueOf("1200-01-01"), data.get(0, 0));
    }

    /**
     * Test getStream with parquet.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamParquet() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", PARQUET_FILE_PATH);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(1, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(16, data.size());
        assertEquals(new BigDecimal("0.00"), data.get(0, 0));
        assertEquals(new BigDecimal("5.00"), data.get(15, 0));
    }

    /**
     * Test getStream with parquet and snappy.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamParquetSnappy() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", PARQUET_SNAPPY_FILE_PATH);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final FlightDescriptor flightDescriptor = info.getDescriptor();
        final CustomFlightAssetDescriptor returnedDescriptor
                = modelMapper.fromBytes(flightDescriptor.getCommand(), CustomFlightAssetDescriptor.class);
        assertEquals(BRANCH_NAME, returnedDescriptor.getInteractionProperties().get("branch_name"));
        assertEquals(PARQUET_SNAPPY_FILE_PATH, returnedDescriptor.getInteractionProperties().get("file_name"));
        assertEquals("application/x-parquet", returnedDescriptor.getDetails().get("mime_type"));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(2, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(16, data.size());
        assertEquals(Date.valueOf("1001-01-01"), data.get(0, 0));
        assertEquals(Date.valueOf("1001-01-01"), data.get(0, 1));
        assertEquals(Date.valueOf("1001-01-01"), data.get(7, 0));
        assertEquals(Date.valueOf("1001-01-08"), data.get(7, 1));
    }

    /**
     * Test getStream with xml.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamXml() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", XML_FILE_PATH);
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(4, schema.getFields().size());
        assertEquals("comment", schema.getFields().get(0).getName());
        assertEquals("make", schema.getFields().get(1).getName());
        assertEquals("model", schema.getFields().get(2).getName());
        assertEquals("year", schema.getFields().get(3).getName());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(12, data.size());
        assertEquals("No comment", data.get(0, 0));
        assertEquals("Tesla", data.get(0, 1));
        assertEquals("S", data.get(0, 2));
        assertEquals(Long.valueOf("2012"), data.get(0, 3));
        assertEquals("No", data.get(2, 0));
        assertEquals("Chevy", data.get(2, 1));
        assertEquals("Volt", data.get(2, 2));
        assertEquals(Long.valueOf("2015"), data.get(2, 3));
    }

    /**
     * Test getStream with csv boolean.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsvBoolean() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", CSV_BOOLEAN_FILE_PATH);
        interactionProperties.put("infer_schema", "true");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(1, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(3, data.size());
        assertTrue((Boolean) data.get(0, 0));
        assertFalse((Boolean) data.get(1, 0));
        assertTrue((Boolean) data.get(2, 0));
    }

    /**
     * Test getStream with csv decimal.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsvDecimal() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", CSV_DECIMAL_FILE_PATH);
        interactionProperties.put("infer_schema", "true");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(3, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(9, data.size());
        assertEquals(BigDecimal.ONE, data.get(0, 0));
        assertEquals(Long.valueOf("1"), data.get(0, 1));
        assertEquals(Double.valueOf("0.1"), data.get(0, 2));
        assertEquals(BigDecimal.ONE, data.get(1, 0));
        assertEquals(Long.valueOf("9223372036854775807"), data.get(1, 1));
        assertEquals(Double.valueOf("1.0"), data.get(1, 2));
        assertEquals(new BigDecimal("92233720368547758070"), data.get(2, 0));
        assertEquals(Long.valueOf("1"), data.get(2, 1));
        assertEquals(Double.valueOf("92233720368547758070"), data.get(2, 2));
    }

    /**
     * Test getStream with csv numeric.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamCsvNumeric() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", CSV_NUMERIC_FILE_PATH);
        interactionProperties.put("infer_schema", "true");
        interactionProperties.put("null_value", "--");
        interactionProperties.put("nan_value", "NAN");
        interactionProperties.put("negative_infinity_value", "-INF");
        interactionProperties.put("positive_infinity_value", "INF");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(4, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(28, data.size());
        assertEquals(Integer.valueOf("8"), data.get(0, 0));
        assertEquals(Integer.valueOf("1000000"), data.get(0, 1));
        assertEquals("1.042", data.get(0, 2));
        assertEquals(Double.valueOf("23848545.0374"), data.get(0, 3));
        assertNull(data.get(1, 0));
        assertEquals(Integer.valueOf("34232323"), data.get(1, 1));
        assertEquals("98.343", data.get(1, 2));
        assertEquals(Double.valueOf("184721.23987223"), data.get(1, 3));
    }

    /**
     * Test getStream with row_limit.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamRowLimit() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", ORC_FILE_PATH);
        interactionProperties.put("row_limit", "1000");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(1, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(1000, data.size());
        assertEquals("row 000000", data.get(0, 0));
        assertEquals("row 000999", data.get(999, 0));
    }

    /**
     * Test getStream with byte_limit.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamByteLimit() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("branch_name", BRANCH_NAME);
        interactionProperties.put("file_name", ORC_FILE_PATH);
        interactionProperties.put("byte_limit", "1000");
        final FlightInfo info = getClient().getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        final Schema schema = info.getSchemaOptional().get();
        assertEquals(1, schema.getFields().size());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertEquals(100, data.size());
        assertEquals("row 000000", data.get(0, 0));
        assertEquals("row 000099", data.get(99, 0));
    }
}
