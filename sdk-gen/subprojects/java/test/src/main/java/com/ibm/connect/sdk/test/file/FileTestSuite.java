/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.arrow.flight.AsyncPutListener;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;
import org.junit.Test;

import com.google.common.collect.Table;
import com.ibm.connect.sdk.test.ConnectorTestSuite;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

/**
 * Abstract test suite for file connectors.
 *
 * <p>Extends {@link ConnectorTestSuite} (which itself extends
 * {@link com.ibm.connect.sdk.test.FlightTestSuite}), so every subclass
 * automatically inherits:
 * <ul>
 *   <li>health_check, list_actions, list_datasource_types</li>
 *   <li>validate and test-connection actions</li>
 *   <li>error cases (invalid criteria, unsupported action, missing body)</li>
 * </ul>
 *
 * <p><strong>To add a new file connector test class:</strong>
 * <ol>
 *   <li>Extend this class.</li>
 *   <li>Implement the six abstract methods below.</li>
 *   <li>Add a {@code @BeforeClass} that starts a {@link com.ibm.connect.sdk.test.TestFlight}
 *       and a {@code @AfterClass} that closes it.</li>
 *   <li>Override any {@code @Test} method inherited here to customise its
 *       behaviour for your connector, or add connector-specific tests directly
 *       in the subclass.</li>
 * </ol>
 *
 * <p><strong>Write-support:</strong> if {@link #createWriteInteractionProperties(String)}
 * returns {@code null} (the default), all {@code testPutStream*} tests are
 * skipped automatically via {@code assumeNotNull}.
 */
public abstract class FileTestSuite extends ConnectorTestSuite
{
    /** Shared mapper — thread-safe, one instance is fine. */
    protected static final ModelMapper MODEL_MAPPER = new ModelMapper();

    // -----------------------------------------------------------------------
    // Abstract hooks — subclasses must implement these
    // -----------------------------------------------------------------------

    /**
     * Returns the expected number of data rows in the file returned by
     * {@link #createReadInteractionProperties()}, or {@code -1} (the default)
     * to skip row-count assertions in {@link #testGetStream}.
     */
    protected int getExpectedRowCount()
    {
        return -1;
    }

    /**
     * Returns the expected ordered column names for the file returned by
     * {@link #createReadInteractionProperties()}, or {@code null} (the default)
     * to skip column-name assertions in {@link #testGetStream}.
     */
    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    protected List<String> getExpectedColumnNames()
    {
        return null;
    }

    /**
     * Returns a map of {@code (row, col) → expectedValue} spot-checks for the
     * file returned by {@link #createReadInteractionProperties()}.  The map key
     * is a two-element {@code int[]} {@code {row, col}} (0-based).  Cell values
     * may be {@code null} to assert that a cell is null.
     *
     * <p>Returns {@code null} (the default) to skip cell-value assertions in
     * {@link #testGetStream}.
     *
     * <p>Example:
     * <pre>
     *   Map&lt;int[], Object&gt; expected = new LinkedHashMap&lt;&gt;();
     *   expected.put(new int[]{0, 0}, "Tesla");
     *   expected.put(new int[]{0, 4}, null);
     *   return expected;
     * </pre>
     */
    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    protected Map<int[], Object> getExpectedCellValues()
    {
        return null;
    }


    /**
     * Returns the path used to browse the root level of the connector
     * (e.g. {@code "/"} for most connectors, which lists buckets or branches).
     */
    protected abstract String getRootPath();

    /**
     * Returns a path that points to a non-empty container one level below root
     * (e.g. a branch name {@code "/master"} for GitHub, a bucket prefix
     * {@code "/my-bucket"} for S3, or a folder {@code "/folder"} for local FS).
     * Used by folder/container listing tests.
     */
    protected abstract String getContainerPath();

    /**
     * Returns the interaction-properties map needed to read a known file that
     * exists in the test environment.  The map must at minimum contain
     * {@code file_name}.  The file should be a CSV so that the schema and data
     * assertions in the read tests can be expressed in a connector-independent
     * way.
     *
     * <p>Example:
     * <pre>
     *   DiscoveredAssetInteractionProperties p = new DiscoveredAssetInteractionProperties();
     *   p.put("file_name", "/master/sql/core/src/test/resources/test-data/cars.csv");
     *   return p;
     * </pre>
     */
    protected abstract DiscoveredAssetInteractionProperties createReadInteractionProperties();

    /**
     * Returns the full discovery path (used with {@code listFlights}) for the
     * known readable file returned by {@link #createReadInteractionProperties()}.
     * Typically {@code "/" + file_name_value}.
     */
    protected abstract String getKnownFilePath();

    /**
     * Returns the expected file name (last path segment, no leading slash) of
     * the known readable file, i.e. the value expected in
     * {@code descriptor.getName()} and {@code descriptor.getId()}.
     */
    protected abstract String getKnownFileName();

    /**
     * Returns interaction properties for a writable destination, or
     * {@code null} if the connector does not support write operations.
     * The map must at minimum contain {@code file_name}.
     *
     * <p>When this method returns {@code null}, all {@code testPutStream*}
     * tests are skipped automatically.
     *
     * <p>Example:
     * <pre>
     *   DiscoveredAssetInteractionProperties p = new DiscoveredAssetInteractionProperties();
     *   p.put("file_name", "/output/write_test.csv");
     *   p.put("first_line_header", "true");
     *   return p;
     * </pre>
     */
    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    protected DiscoveredAssetInteractionProperties createWriteInteractionProperties(String uniqueSuffix)
    {
        return null;
    }

    // -----------------------------------------------------------------------
    // Convenience helpers
    // -----------------------------------------------------------------------

    /**
     * Runs {@code listFlights} and returns all descriptors as a list.
     */
    protected List<CustomFlightAssetDescriptor> listAssets(String path) throws Exception
    {
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(path);
        final List<CustomFlightAssetDescriptor> result = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            result.add(MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class));
        }
        return result;
    }

    /**
     * Runs {@code listFlights} with paging and returns all descriptors.
     */
    protected List<CustomFlightAssetDescriptor> listAssets(String path, int offset, int limit) throws Exception
    {
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(path);
        criteria.setOffset(offset);
        criteria.setLimit(limit);
        final List<CustomFlightAssetDescriptor> result = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            result.add(MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class));
        }
        return result;
    }

    /**
     * Returns a {@link FlightInfo} for the given interaction properties.
     */
    protected FlightInfo getFlightInfo(DiscoveredAssetInteractionProperties interactionProperties) throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        descriptor.setDatasourceTypeName(getDatasourceTypeName());
        descriptor.setConnectionProperties(createConnectionProperties());
        descriptor.setInteractionProperties(interactionProperties);
        return getClient().getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(descriptor)));
    }

    // -----------------------------------------------------------------------
    // Discovery tests
    // -----------------------------------------------------------------------

    /**
     * listFlights at the root must return at least one asset, and every
     * descriptor must carry the mandatory id / name / path / assetType fields.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverRoot() throws Exception
    {
        final List<CustomFlightAssetDescriptor> assets = listAssets(getRootPath());
        assertFalse("Expected at least one asset at the root", assets.isEmpty());
        for (final CustomFlightAssetDescriptor d : assets) {
            assertNotNull("descriptor.id must not be null", d.getId());
            assertNotNull("descriptor.name must not be null", d.getName());
            assertNotNull("descriptor.path must not be null", d.getPath());
            assertNotNull("descriptor.assetType must not be null", d.getAssetType());
            assertNotNull("assetType.type must not be null", d.getAssetType().getType());
            assertNotNull("assetType.dataset must not be null", d.getAssetType().isDataset());
            assertNotNull("assetType.datasetContainer must not be null", d.getAssetType().isDatasetContainer());
            // A descriptor must be either a dataset OR a container, not both.
            assertFalse("An asset cannot be both a dataset and a dataset container",
                    Boolean.TRUE.equals(d.getAssetType().isDataset())
                            && Boolean.TRUE.equals(d.getAssetType().isDatasetContainer()));
        }
    }

    /**
     * listFlights at the root with offset=0, limit=1 must return at most 1 entry.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverRootWithPaging() throws Exception
    {
        final List<CustomFlightAssetDescriptor> assets = listAssets(getRootPath(), 0, 1);
        assertTrue("Paging with limit=1 must return at most 1 asset", assets.size() <= 1);
    }

    /**
     * listFlights inside a container must return at least one entry, and all
     * descriptors must have non-null id, name, path, and assetType.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverContainer() throws Exception
    {
        final List<CustomFlightAssetDescriptor> assets = listAssets(getContainerPath());
        assertFalse("Expected at least one asset inside the container", assets.isEmpty());
        for (final CustomFlightAssetDescriptor d : assets) {
            assertNotNull("descriptor.id must not be null", d.getId());
            assertNotNull("descriptor.name must not be null", d.getName());
            assertNotNull("descriptor.assetType must not be null", d.getAssetType());
        }
    }

    /**
     * listFlights inside a container with paging must honour the limit.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverContainerWithPaging() throws Exception
    {
        final List<CustomFlightAssetDescriptor> assets = listAssets(getContainerPath(), 0, 1);
        assertTrue("Paging with limit=1 must return at most 1 asset", assets.size() <= 1);
    }

    /**
     * listFlights pointing directly at a known file must return exactly one
     * descriptor whose assetType is a dataset (not a container), and whose
     * interactionProperties, details, and schema are populated.
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverKnownFile() throws Exception
    {
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(getDatasourceTypeName());
        criteria.setConnectionProperties(createConnectionProperties());
        criteria.setPath(getKnownFilePath());
        final List<CustomFlightAssetDescriptor> files = new ArrayList<>();
        for (final FlightInfo info : getClient().listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor d
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            assertNotNull("assetType must not be null", d.getAssetType());
            assertEquals("file", d.getAssetType().getType());
            assertTrue("dataset flag must be true for a file", d.getAssetType().isDataset());
            assertFalse("datasetContainer flag must be false for a file", d.getAssetType().isDatasetContainer());
            assertNotNull("descriptor.id must not be null", d.getId());
            assertNotNull("descriptor.name must not be null", d.getName());
            assertNotNull("interactionProperties must be present", d.getInteractionProperties());
            assertNotNull("file_name interaction property must be set",
                    d.getInteractionProperties().get("file_name"));
            assertNotNull("details must be present", d.getDetails());
            assertNotNull("file_size detail must be set", d.getDetails().get("file_size"));
            assertNotNull("mime_type detail must be set", d.getDetails().get("mime_type"));
            final Schema schema = info.getSchemaOptional().orElse(null);
            assertNotNull("Schema must be present for a data file", schema);
            assertFalse("Schema must have at least one field", schema.getFields().isEmpty());
            files.add(d);
        }
        assertEquals("Expected exactly one descriptor for a single-file path", 1, files.size());
        assertEquals(getKnownFileName(), files.get(0).getName());
    }

    // -----------------------------------------------------------------------
    // Metadata tests (getFlightInfo)
    // -----------------------------------------------------------------------

    /**
     * getFlightInfo for the known file must return populated interaction
     * properties and a non-empty schema.
     *
     * @throws Exception
     */
    @Test
    public void testGetFlightInfo() throws Exception
    {
        final FlightInfo info = getFlightInfo(createReadInteractionProperties());
        final CustomFlightAssetDescriptor returned
                = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
        assertNotNull("Returned interactionProperties must not be null",
                returned.getInteractionProperties());
        assertNotNull("file_name must be present in returned descriptor",
                returned.getInteractionProperties().get("file_name"));
        final Schema schema = info.getSchemaOptional().orElse(null);
        assertNotNull("Schema must be present", schema);
        assertFalse("Schema must have at least one field", schema.getFields().isEmpty());
    }

    // -----------------------------------------------------------------------
    // Read tests (getStream)
    // -----------------------------------------------------------------------

    /**
     * Reading the known file must produce at least one data row.
     * When optional hooks are provided the test also validates:
     * <ul>
     *   <li>column count and names (via {@link #getExpectedColumnNames()})</li>
     *   <li>row count (via {@link #getExpectedRowCount()})</li>
     *   <li>spot-check cell values (via {@link #getExpectedCellValues()})</li>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testGetStream() throws Exception
    {
        final FlightInfo info = getFlightInfo(createReadInteractionProperties());
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertFalse("Expected at least one data row", data.isEmpty());

        // Optional: column names
        final List<String> expectedColumns = getExpectedColumnNames();
        if (expectedColumns != null) {
            final Schema schema = info.getSchemaOptional().orElse(null);
            assertNotNull("Schema must be present when column names are declared", schema);
            assertEquals("Column count mismatch", expectedColumns.size(), schema.getFields().size());
            for (int i = 0; i < expectedColumns.size(); i++) {
                assertEquals("Column name at index " + i, expectedColumns.get(i),
                        schema.getFields().get(i).getName());
            }
        }

        // Optional: row count
        final int expectedRows = getExpectedRowCount();
        if (expectedRows >= 0) {
            assertEquals("Row count mismatch", expectedRows, data.rowKeySet().size());
        }

        // Optional: cell-value spot-checks
        final Map<int[], Object> expectedCells = getExpectedCellValues();
        if (expectedCells != null) {
            for (final Map.Entry<int[], Object> entry : expectedCells.entrySet()) {
                final int row = entry.getKey()[0];
                final int col = entry.getKey()[1];
                final Object expected = entry.getValue();
                final Object actual = data.get(row, col);
                if (expected == null) {
                    org.junit.Assert.assertNull(
                            "Expected null at (" + row + "," + col + ") but got: " + actual, actual);
                } else {
                    assertEquals("Cell value at (" + row + "," + col + ")", expected, actual);
                }
            }
        }
    }

    /**
     * Setting {@code row_limit=1} must return at most 1 row.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamRowLimit() throws Exception
    {
        final DiscoveredAssetInteractionProperties props = createReadInteractionProperties();
        props.put("row_limit", "1");
        final FlightInfo info = getFlightInfo(props);
        final Table<Integer, Integer, Object> data = getTableData(info);
        assertTrue("row_limit=1 must return at most 1 row", data.rowKeySet().size() <= 1);
    }

    /**
     * Setting {@code byte_limit} to a very small value must reduce the returned
     * rows compared to an unlimited read.
     *
     * @throws Exception
     */
    @Test
    public void testGetStreamByteLimit() throws Exception
    {
        // First: unlimited read to know how many rows there are.
        final int totalRows = getTableData(getFlightInfo(createReadInteractionProperties())).rowKeySet().size();
        if (totalRows <= 1) {
            // Not enough rows to observe truncation — just verify no exception.
            return;
        }
        final DiscoveredAssetInteractionProperties props = createReadInteractionProperties();
        props.put("byte_limit", "1"); // tiny limit — at most 1 row should come back
        final Table<Integer, Integer, Object> limited = getTableData(getFlightInfo(props));
        assertTrue("byte_limit must reduce the number of returned rows",
                limited.rowKeySet().size() < totalRows);
    }

    // -----------------------------------------------------------------------
    // Write tests (putStream) — skipped when createWriteInteractionProperties returns null
    // -----------------------------------------------------------------------

    /**
     * Write a small batch of rows and read them back; verify the row count matches.
     * Skipped when {@link #createWriteInteractionProperties(String)} returns {@code null}.
     *
     * @throws Exception
     */
    @Test
    public void testPutStream() throws Exception
    {
        final DiscoveredAssetInteractionProperties writeProps
                = createWriteInteractionProperties("testPutStream");
        assumeNotNull(writeProps);

        // 1. Obtain a source stream from the known readable file.
        final FlightInfo sourceInfo = getFlightInfo(createReadInteractionProperties());

        // 2. Build target descriptor.
        final CustomFlightAssetDescriptor targetDescriptor = new CustomFlightAssetDescriptor();
        targetDescriptor.setDatasourceTypeName(getDatasourceTypeName());
        targetDescriptor.setConnectionProperties(createConnectionProperties());
        targetDescriptor.setInteractionProperties(writeProps);

        // 3. Stream source → target.
        int writtenRows = 0;
        for (final FlightEndpoint endpoint : sourceInfo.getEndpoints()) {
            try (FlightStream stream = getClient().getStream(endpoint.getTicket());
                    VectorSchemaRoot root = stream.getRoot()) {
                final FlightClient.ClientStreamListener putStream = getClient()
                        .startPut(FlightDescriptor.command(MODEL_MAPPER.toBytes(targetDescriptor)), root,
                                new AsyncPutListener());
                while (stream.next()) {
                    writtenRows += root.getRowCount();
                    putStream.putNext();
                    root.clear();
                }
                putStream.completed();
                putStream.getResult();
            }
        }
        assertTrue("At least one row must have been written", writtenRows > 0);

        // 4. Read the written file back — verify row count and spot-check cell values.
        final DiscoveredAssetInteractionProperties readBackProps
                = createReadBackInteractionProperties(writeProps);
        if (readBackProps != null) {
            final Table<Integer, Integer, Object> written = getTableData(getFlightInfo(readBackProps));
            assertEquals("Read-back row count must equal written row count",
                    writtenRows, written.rowKeySet().size());

            // If the subclass declared expected cell values, verify the round-trip preserved them.
            final Map<int[], Object> expectedCells = getExpectedCellValues();
            if (expectedCells != null) {
                for (final Map.Entry<int[], Object> entry : expectedCells.entrySet()) {
                    final int row = entry.getKey()[0];
                    final int col = entry.getKey()[1];
                    final Object expected = entry.getValue();
                    final Object actual = written.get(row, col);
                    if (expected == null) {
                        org.junit.Assert.assertNull(
                                "Round-trip: expected null at (" + row + "," + col + ") but got: " + actual,
                                actual);
                    } else {
                        assertEquals("Round-trip cell value at (" + row + "," + col + ")",
                                expected.toString(), actual != null ? actual.toString() : null);
                    }
                }
            }
        }
    }

    /**
     * Returns the interaction properties to use when reading back a file that
     * was just written by {@link #testPutStream()}.
     *
     * <p>The default implementation returns {@code writeProps} unchanged, which
     * works when the same properties that were used to write are also valid for
     * reading.  Override this if the read-back path or format differs.
     *
     * @param writeProps
     *            the properties that were used for the write
     * @return properties for the read-back, or {@code null} to skip the
     *         read-back assertion
     */
    protected DiscoveredAssetInteractionProperties createReadBackInteractionProperties(
            DiscoveredAssetInteractionProperties writeProps)
    {
        return writeProps;
    }
}
