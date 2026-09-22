/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;

/**
 * Scenario-based test runner for file connectors.
 *
 * <p>
 * A scenario is a {@code *.scenario} properties file loaded from the test
 * classpath. Each scenario file describes a sequence of steps using a
 * human-readable properties format inspired by the {@code wdp-connect-library}
 * SCAPI test framework.
 *
 * <h3>Scenario step types</h3>
 * <ul>
 * <li>{@code Type=Write} — write rows to the connector using a typed schema and
 * inline data values.</li>
 * <li>{@code Type=Read} — read from the connector and verify schema, row count,
 * and/or cell values.</li>
 * <li>{@code Type=Discover} — list assets at a path and verify the descriptor
 * contract (presence and optionally exact values).</li>
 * <li>{@code Type=FileCompare} — read a file connector object as raw bytes and
 * compare it to a classpath reference file.</li>
 * </ul>
 *
 * <h3>Scenario file format</h3>
 * 
 * <pre>
 * # Each step is introduced by [Step] and ends at the next [Step] or EOF.
 * [Step]
 * Type=Write
 * # Interaction properties (key=value)
 * Interaction.file_name=/test_write.csv
 * Interaction.first_line_header=true
 * # Schema: name|type (e.g. integer, varchar, boolean, bigint, real, double, decimal, date, timestamp, varbinary)
 * Schema=id|integer; name|varchar; active|boolean
 * # Data rows: column values separated by |; use &lt;NULL&gt; for null
 * Row=1|Alice|true
 * Row=2|Bob|false
 * Row=3|&lt;NULL&gt;|true
 *
 * [Step]
 * Type=Read
 * Interaction.file_name=/test_write.csv
 * # Expected columns (optional)
 * ExpectedColumns=id|name|active
 * # Expected row count (optional)
 * ExpectedRowCount=3
 * # Expected cells: row,col=value  (0-based, &lt;NULL&gt; for null)
 * ExpectedCell.0.0=1
 * ExpectedCell.0.1=Alice
 * ExpectedCell.1.0=2
 * ExpectedCell.2.1=&lt;NULL&gt;
 *
 * [Step]
 * Type=Discover
 * Path=/
 * ExpectedContains=/test_write.csv
 *
 * [Step]
 * Type=FileCompare
 * Interaction.file_name=/test_write.csv
 * ReferenceFile=scenarios/reference/test_write.ref
 * ReferenceEncoding=UTF-8
 * </pre>
 *
 * <p>
 * Leading/trailing whitespace around keys and values is trimmed. Lines starting
 * with {@code #} are comments and are ignored. Property keys beginning with
 * {@code Interaction.} are stripped of that prefix and collected into the
 * interaction-properties map for that step.
 */
public final class TestScenario
{
    private static final ModelMapper MODEL_MAPPER = new ModelMapper();
    private static final String NULL_TOKEN = "<NULL>";
    private static final String COL_DELIM = "\\|";

    private WriteHook writeHook;

    private final FlightClient client;
    private final String datasourceTypeName;
    private final ConnectionProperties connectionProperties;

    /**
     * Creates a scenario runner bound to the given connector context.
     *
     * @param client
     *            the live Flight client
     * @param datasourceTypeName
     *            the datasource type name constant for this connector
     * @param connectionProperties
     *            the connection properties to inject into every step
     */
    public TestScenario(FlightClient client, String datasourceTypeName, ConnectionProperties connectionProperties)
    {
        this.client = client;
        this.datasourceTypeName = datasourceTypeName;
        this.connectionProperties = connectionProperties;
    }

    /**
     * Loads and runs all steps defined in {@code resourcePath} from the classpath.
     *
     * @param resourcePath
     *            classpath-relative path (e.g.
     *            {@code "scenarios/s3/readwrite_csv.scenario"})
     * @throws Exception
     *             if any step fails
     */
    public void run(String resourcePath) throws Exception
    {
        final List<Properties> steps = parseScenario(resourcePath);
        if (steps.isEmpty()) {
            fail("Scenario file '" + resourcePath + "' contains no steps");
        }
        int stepIdx = 0;
        for (final Properties step : steps) {
            stepIdx++;
            final String context = "[" + resourcePath + " step " + stepIdx + "]";
            try {
                runStep(step, context);
            }
            catch (AssertionError | Exception e) {
                fail(context + " failed: " + e.getMessage());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Step dispatch
    // -----------------------------------------------------------------------

    private void runStep(Properties step, String context) throws Exception
    {
        final String type = required(step, "Type", context);
        switch (type) {
        case "Write":
            runWrite(step);
            break;
        case "Read":
            runRead(step, context);
            break;
        case "Discover":
            runDiscover(step, context);
            break;
        case "FileCompare":
            runFileCompare(step, context);
            break;
        default:
            fail(context + ": unknown step type '" + type + "'");
            break;
        }
    }

    // -----------------------------------------------------------------------
    // Write step — delegates to the pluggable writer hook
    // -----------------------------------------------------------------------

    /**
     * Runs a {@code Write} step by delegating to {@link #executeWrite}. The default
     * implementation calls {@link #executeWrite} which subclasses of
     * {@link FileTestSuite} can override to supply an Arrow-aware writer. The base
     * {@link TestScenario} implementation skips writes gracefully when no write
     * hook is registered.
     */
    private void runWrite(Properties step) throws Exception
    {
        if (writeHook == null) {
            // No write hook registered — skip silently (test suite will log a warning).
            return;
        }
        final DiscoveredAssetInteractionProperties iprops = collectInteractionProps(step);
        final List<String> schemaSpec = parseSchemaSpec(step);
        final List<String[]> rows = parseRows(step);
        writeHook.execute(client, MODEL_MAPPER, datasourceTypeName, connectionProperties, iprops, schemaSpec, rows, NULL_TOKEN);
    }

    // -----------------------------------------------------------------------
    // Read step
    // -----------------------------------------------------------------------

    private void runRead(Properties step, String context) throws Exception
    {
        final DiscoveredAssetInteractionProperties iprops = collectInteractionProps(step);
        final FlightInfo info = getFlightInfo(iprops);

        // -- column names
        final String colSpec = step.getProperty("ExpectedColumns");
        if (colSpec != null) {
            final Schema schema = info.getSchemaOptional().orElseThrow(() -> new AssertionError(context + ": no schema returned"));
            final List<String> expectedCols = Arrays.asList(colSpec.split("\\|"));
            assertEquals(context + ": column count", expectedCols.size(), schema.getFields().size());
            for (int i = 0; i < expectedCols.size(); i++) {
                assertEquals(context + ": column[" + i + "]", expectedCols.get(i).trim(), schema.getFields().get(i).getName());
            }
        }

        final Table<Integer, Integer, Object> data = getTableData(info);

        // -- row count
        final String rowCountStr = step.getProperty("ExpectedRowCount");
        if (rowCountStr != null) {
            final int expected = Integer.parseInt(rowCountStr.trim());
            assertEquals(context + ": row count", expected, data.rowKeySet().size());
        } else {
            assertFalse(context + ": expected at least one row", data.isEmpty());
        }

        // -- individual cell checks: ExpectedCell.<row>.<col>=<value>
        for (final String key : step.stringPropertyNames()) {
            if (key.startsWith("ExpectedCell.")) {
                final String[] parts = key.split("\\.");
                if (parts.length == 3) {
                    final int row = Integer.parseInt(parts[1]);
                    final int col = Integer.parseInt(parts[2]);
                    final String expected = step.getProperty(key).trim();
                    if (NULL_TOKEN.equals(expected)) {
                        assertNull(context + ": cell[" + row + "][" + col + "] should be null", data.get(row, col));
                    } else {
                        final Object actual = data.get(row, col);
                        assertNotNull(context + ": cell[" + row + "][" + col + "] should not be null", actual);
                        assertEquals(context + ": cell[" + row + "][" + col + "]", expected, actual.toString());
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Discover step
    // -----------------------------------------------------------------------

    private void runDiscover(Properties step, String context) throws Exception
    {
        final String path = required(step, "Path", context);
        final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
        criteria.setDatasourceTypeName(datasourceTypeName);
        criteria.setConnectionProperties(connectionProperties);
        criteria.setPath(path);

        final List<CustomFlightAssetDescriptor> descriptors = new ArrayList<>();
        for (final FlightInfo info : client.listFlights(new Criteria(MODEL_MAPPER.toBytes(criteria)))) {
            final CustomFlightAssetDescriptor d
                    = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
            descriptors.add(d);
        }

        final String expectedRowCount = step.getProperty("ExpectedCount");
        if (expectedRowCount != null) {
            assertEquals(context + ": discovery count at '" + path + "'", Integer.parseInt(expectedRowCount.trim()), descriptors.size());
        }

        final String contains = step.getProperty("ExpectedContains");
        if (contains != null) {
            final boolean found = descriptors.stream().anyMatch(
                    d -> contains.trim().equals(d.getId()) || contains.trim().equals(d.getName()) || contains.trim().equals(d.getPath()));
            assertTrue(context + ": expected descriptor '" + contains + "' not found at '" + path + "'", found);
        }

        final String notEmpty = step.getProperty("ExpectedNotEmpty");
        if ("true".equalsIgnoreCase(notEmpty)) {
            assertFalse(context + ": expected at least one descriptor at '" + path + "'", descriptors.isEmpty());
        }

        // Validate descriptor contract for every returned entry
        for (final CustomFlightAssetDescriptor d : descriptors) {
            assertNotNull(context + ": descriptor.id must not be null", d.getId());
            assertNotNull(context + ": descriptor.name must not be null", d.getName());
            assertNotNull(context + ": descriptor.assetType must not be null", d.getAssetType());
        }
    }

    // -----------------------------------------------------------------------
    // FileCompare step
    // -----------------------------------------------------------------------

    private void runFileCompare(Properties step, String context) throws Exception
    {
        final DiscoveredAssetInteractionProperties iprops = collectInteractionProps(step);
        // Force binary / raw-read mode so we get the exact bytes
        iprops.putIfAbsent("file_format", "binary");

        final FlightInfo info = getFlightInfo(iprops);
        final Table<Integer, Integer, Object> data = getTableData(info);

        // The binary reader returns a single row with the content in column 0
        assertFalse(context + ": FileCompare: no content returned", data.isEmpty());
        final Object contentObj = data.get(0, 0);
        assertNotNull(context + ": FileCompare: content must not be null", contentObj);
        assertTrue(context + ": FileCompare: content must be byte[]", contentObj instanceof byte[]);
        final byte[] actual = (byte[]) contentObj;

        final String refResource = required(step, "ReferenceFile", context);
        final String encName = step.getProperty("ReferenceEncoding", "UTF-8");
        final Charset charset = Charset.forName(encName);
        final byte[] expected = loadResource(refResource, context);

        // Normalise line endings for text-mode comparison when encoding is specified
        final String actualStr = new String(actual, charset).replace("\r\n", "\n").stripTrailing();
        final String expectedStr = new String(expected, charset).replace("\r\n", "\n").stripTrailing();
        assertEquals(context + ": file content mismatch (reference: " + refResource + ")", expectedStr, actualStr);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private FlightInfo getFlightInfo(DiscoveredAssetInteractionProperties iprops) throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        descriptor.setDatasourceTypeName(datasourceTypeName);
        descriptor.setConnectionProperties(connectionProperties);
        descriptor.setInteractionProperties(iprops);
        return client.getInfo(FlightDescriptor.command(MODEL_MAPPER.toBytes(descriptor)));
    }

    private Table<Integer, Integer, Object> getTableData(FlightInfo info) throws Exception
    {
        final Table<Integer, Integer, Object> data = HashBasedTable.create();
        int tableRowIdx = 0;
        for (final FlightEndpoint endpoint : info.getEndpoints()) {
            try (FlightStream stream = client.getStream(endpoint.getTicket()); VectorSchemaRoot root = stream.getRoot()) {
                while (stream.next()) {
                    for (int ci = 0; ci < root.getFieldVectors().size(); ci++) {
                        try (FieldVector vec = root.getFieldVectors().get(ci)) {
                            for (int ri = 0; ri < root.getRowCount(); ri++) {
                                if (!vec.isNull(ri)) {
                                    data.put(tableRowIdx + ri, ci, vec.getObject(ri));
                                }
                            }
                        }
                    }
                    tableRowIdx += root.getRowCount();
                }
            }
        }
        return data;
    }

    /**
     * Functional interface for the Write step — allows connector test classes
     * (which do have the full Arrow + SDK classpath) to inject a write
     * implementation without this class needing to depend on
     * {@code ArrowConversions}.
     */
    @FunctionalInterface
    public interface WriteHook
    {
        /**
         * Writes the given rows to the connector.
         *
         * @param client
         *            the Flight client
         * @param modelMapper
         *            the model mapper
         * @param datasourceTypeName
         *            the datasource type name
         * @param connectionProperties
         *            connection properties
         * @param iprops
         *            interaction properties
         * @param schemaSpec
         *            list of {@code "name|type"} strings
         * @param rows
         *            list of column-value arrays; {@code nullToken} marks SQL NULL
         * @param nullToken
         *            the NULL sentinel string
         * @throws Exception
         *             if the write fails
         */
        void execute(FlightClient client, ModelMapper modelMapper, String datasourceTypeName, ConnectionProperties connectionProperties,
                DiscoveredAssetInteractionProperties iprops, List<String> schemaSpec, List<String[]> rows, String nullToken)
                throws Exception;
    }

    /**
     * Registers a write hook. Call this before {@link #run(String)} when scenarios
     * include {@code Type=Write} steps.
     *
     * @param hook
     *            the write implementation
     * @return {@code this} for fluent chaining
     */
    public TestScenario withWriteHook(WriteHook hook)
    {
        this.writeHook = hook;
        return this;
    }

    private static DiscoveredAssetInteractionProperties collectInteractionProps(Properties step)
    {
        final DiscoveredAssetInteractionProperties props = new DiscoveredAssetInteractionProperties();
        for (final String key : step.stringPropertyNames()) {
            if (key.startsWith("Interaction.")) {
                props.put(key.substring("Interaction.".length()), step.getProperty(key).trim());
            }
        }
        return props;
    }

    /**
     * Returns the raw schema spec list (each entry is {@code "name|type"}).
     */
    private static List<String> parseSchemaSpec(Properties step)
    {
        final String schemaStr = step.getProperty("Schema", "");
        final List<String> specs = new ArrayList<>();
        if (schemaStr == null || schemaStr.chars().allMatch(Character::isWhitespace)) {
            return specs;
        }
        for (final String colDef : schemaStr.split(";")) {
            specs.add(colDef.trim());
        }
        return specs;
    }

    private static List<String[]> parseRows(Properties step)
    {
        final List<String[]> rows = new ArrayList<>();
        // Rows can be Row=v1|v2|v3 or Row.0=..., Row.1=..., etc.
        // Support both styles.
        int idx = 0;
        while (true) {
            String rowVal = step.getProperty("Row." + idx);
            if (rowVal == null && idx == 0) {
                // Try bare Row= key (single row)
                rowVal = step.getProperty("Row");
            }
            if (rowVal == null) {
                break;
            }
            rows.add(rowVal.trim().split(COL_DELIM, -1));
            idx++;
        }
        return rows;
    }

    private static byte[] loadResource(String resourcePath, String context) throws IOException
    {
        try (InputStream is = TestScenario.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                fail(context + ": reference file not found on classpath: " + resourcePath);
            }
            return is.readAllBytes();
        }
    }

    // -----------------------------------------------------------------------
    // Scenario file parser
    // -----------------------------------------------------------------------

    /**
     * Parses a scenario file from the classpath into a list of step
     * {@link Properties} objects. Each {@code [Step]} marker begins a new step.
     */
    private static List<Properties> parseScenario(String resourcePath) throws IOException
    {
        final List<Properties> steps = new ArrayList<>();
        try (InputStream is = TestScenario.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                fail("Scenario file not found on classpath: " + resourcePath);
            }
            Properties current = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                String pendingKey = null;
                StringBuilder pendingValue = null;
                while ((line = reader.readLine()) != null) {
                    final String trimmed = line.trim();
                    // Flush pending continuation
                    if (pendingKey != null && !trimmed.endsWith("\\")) {
                        // last continuation line
                        pendingValue.append(trimmed);
                        current.setProperty(pendingKey, pendingValue.toString().trim());
                        pendingKey = null;
                        pendingValue = null;
                        continue;
                    }
                    if (pendingKey != null) {
                        // more continuation
                        pendingValue.append(trimmed, 0, trimmed.length() - 1);
                        continue;
                    }

                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    if ("[Step]".equalsIgnoreCase(trimmed)) {
                        current = new Properties();
                        steps.add(current);
                        continue;
                    }
                    if (current == null) {
                        continue; // ignore content before first [Step]
                    }
                    final int eq = trimmed.indexOf('=');
                    if (eq > 0) {
                        final String key = trimmed.substring(0, eq).trim();
                        final String value = trimmed.substring(eq + 1).trim();
                        if (value.endsWith("\\")) {
                            // continuation
                            pendingKey = key;
                            pendingValue = new StringBuilder(value.substring(0, value.length() - 1));
                        } else {
                            current.setProperty(key, value);
                        }
                    }
                }
                // flush any trailing continuation
                if (pendingKey != null && current != null) {
                    current.setProperty(pendingKey, pendingValue.toString().trim());
                }
            }
        }
        return steps;
    }

    private static String required(Properties step, String key, String context)
    {
        final String value = step.getProperty(key);
        assertNotNull(context + ": required property '" + key + "' is missing", value);
        return value.trim();
    }
}
