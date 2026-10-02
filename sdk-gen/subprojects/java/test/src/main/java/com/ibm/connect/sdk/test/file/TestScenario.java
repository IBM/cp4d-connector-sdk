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
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.text.StringEscapeUtils;

import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.TimeMilliVector;
import org.apache.arrow.vector.TimeStampVector;
import org.apache.arrow.vector.VarCharVector;
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
 * <li>{@code Type=Metadata} — call {@code getFlightInfo} and assert returned
 * interaction properties, details, and schema fields.</li>
 * <li>{@code Type=FileCompare} — read a file connector object as raw bytes and
 * compare it to a classpath reference file.</li>
 * <li>{@code Type=NegativeRead} — assert that {@code getFlightInfo} throws an
 * exception whose message contains {@code ExpectedError}.</li>
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
    private PutHook putHook;
    private java.util.Map<String, String> scenarioVars = java.util.Collections.emptyMap();

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
                runStep(resolveVars(step), context);
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
        case "Metadata":
            runMetadata(step, context);
            break;
        case "FileCompare":
            runFileCompare(step, context);
            break;
        case "NegativeRead":
            runNegativeRead(step, context);
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
        final DiscoveredAssetInteractionProperties iprops = collectInteractionProps(step);
        final List<String> schemaSpec = parseSchemaSpec(step);
        final List<String[]> rows = parseRows(step);
        if (putHook != null) {
            // Connector doesn't support Flight putStream — delegate to the out-of-band put
            // hook.
            putHook.execute(iprops, schemaSpec, rows, NULL_TOKEN);
        } else if (writeHook != null) {
            writeHook.execute(client, MODEL_MAPPER, datasourceTypeName, connectionProperties, iprops, schemaSpec, rows, NULL_TOKEN);
        }
        // If neither hook is registered, skip silently.
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
                    final String expected = step.getProperty(key);
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

        // -- multi-row checks: ExpectedRows.N=val0|val1|val2 (0-based row index)
        for (final String key : step.stringPropertyNames()) {
            if (key.startsWith("ExpectedRows.")) {
                final int row = Integer.parseInt(key.substring("ExpectedRows.".length()).trim());
                final String[] colVals = step.getProperty(key).split(COL_DELIM, -1);
                for (int ci = 0; ci < colVals.length; ci++) {
                    final String expected = colVals[ci];
                    if (NULL_TOKEN.equals(expected)) {
                        assertNull(context + ": row[" + row + "][" + ci + "] should be null", data.get(row, ci));
                    } else {
                        final Object actual = data.get(row, ci);
                        assertNotNull(context + ": row[" + row + "][" + ci + "] should not be null", actual);
                        assertEquals(context + ": row[" + row + "][" + ci + "]", expected, actual.toString());
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
    // Metadata step — getFlightInfo + assert returned descriptor fields
    // -----------------------------------------------------------------------

    private void runMetadata(Properties step, String context) throws Exception
    {
        final DiscoveredAssetInteractionProperties iprops = collectInteractionProps(step);
        final FlightInfo info = getFlightInfo(iprops);
        final CustomFlightAssetDescriptor returned
                = MODEL_MAPPER.fromBytes(info.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);

        // -- ExpectedInteractionProperty.<key>=<value>
        for (final String key : step.stringPropertyNames()) {
            if (key.startsWith("ExpectedInteractionProperty.")) {
                final String propKey = key.substring("ExpectedInteractionProperty.".length());
                final String expected = step.getProperty(key);
                assertNotNull(context + ": interactionProperties must not be null", returned.getInteractionProperties());
                assertEquals(context + ": interactionProperty[" + propKey + "]", expected,
                        returned.getInteractionProperties().get(propKey));
            }
        }

        // -- ExpectedDetail.<key>=<value>
        for (final String key : step.stringPropertyNames()) {
            if (key.startsWith("ExpectedDetail.")) {
                final String propKey = key.substring("ExpectedDetail.".length());
                final String expected = step.getProperty(key).trim();
                assertNotNull(context + ": details must not be null", returned.getDetails());
                assertEquals(context + ": detail[" + propKey + "]", expected, returned.getDetails().get(propKey));
            }
        }

        // -- ExpectedSchemaFieldCount=N
        final String fieldCount = step.getProperty("ExpectedSchemaFieldCount");
        if (fieldCount != null) {
            final Schema schema = info.getSchemaOptional().orElseThrow(() -> new AssertionError(context + ": schema must be present"));
            assertEquals(context + ": schema field count", Integer.parseInt(fieldCount.trim()), schema.getFields().size());
        }

        // -- ExpectedSchemaFields=col0|col1|col2
        final String schemaFields = step.getProperty("ExpectedSchemaFields");
        if (schemaFields != null) {
            final Schema schema = info.getSchemaOptional().orElseThrow(() -> new AssertionError(context + ": schema must be present"));
            final String[] names = schemaFields.split("\\|", -1);
            assertEquals(context + ": schema field count", names.length, schema.getFields().size());
            for (int i = 0; i < names.length; i++) {
                assertEquals(context + ": schema field[" + i + "]", names[i].trim(), schema.getFields().get(i).getName());
            }
        }
    }

    // -----------------------------------------------------------------------
    // NegativeRead step — assert exception is thrown with expected message
    // -----------------------------------------------------------------------

    private void runNegativeRead(Properties step, String context) throws Exception
    {
        final DiscoveredAssetInteractionProperties iprops = collectInteractionProps(step);
        final String expectedError = step.getProperty("ExpectedError", "");
        try {
            getFlightInfo(iprops);
            fail(context + ": expected an exception but none was thrown");
        }
        catch (AssertionError e) {
            throw e; // re-throw our own assertion failures
        }
        catch (Exception e) {
            if (!expectedError.isEmpty()) {
                assertTrue(context + ": expected error message to contain '" + expectedError + "' but got: " + e.getMessage(),
                        e.getMessage() != null && e.getMessage().contains(expectedError));
            }
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
                                    final Object value;
                                    if (vec instanceof VarCharVector) {
                                        value = vec.getObject(ri).toString();
                                    } else if (vec instanceof DateDayVector) {
                                        value = new Date(TimeUnit.DAYS.toMillis(((DateDayVector) vec).get(ri)));
                                    } else if (vec instanceof TimeMilliVector) {
                                        value = Time.valueOf(((TimeMilliVector) vec).getObject(ri).toLocalTime());
                                    } else if (vec instanceof TimeStampVector) {
                                        value = new Timestamp(((TimeStampVector) vec).get(ri));
                                    } else {
                                        value = vec.getObject(ri);
                                    }
                                    data.put(tableRowIdx + ri, ci, value);
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

    /**
     * Functional interface for out-of-band write steps — used when the connector
     * does not support Flight {@code putStream} and writes must be performed via an
     * external API (e.g. direct AWS SDK upload for a read-only S3 connector).
     *
     * <p>
     * When a {@code PutHook} is registered via {@link #withPutHook(PutHook)}, it
     * takes precedence over {@link WriteHook} for {@code Type=Write} scenario
     * steps. The hook receives the resolved interaction properties and the parsed
     * row data — it is responsible for serialising them and uploading the result.
     */
    @FunctionalInterface
    public interface PutHook
    {
        /**
         * Uploads the given rows out-of-band (not via the Flight connector).
         *
         * @param iprops
         *            interaction properties (contains {@code file_name} etc.)
         * @param schemaSpec
         *            list of {@code "name|type"} strings
         * @param rows
         *            list of column-value arrays; {@code nullToken} marks SQL NULL
         * @param nullToken
         *            the NULL sentinel string
         * @throws Exception
         *             if the upload fails
         */
        void execute(DiscoveredAssetInteractionProperties iprops, List<String> schemaSpec, List<String[]> rows, String nullToken)
                throws Exception;
    }

    /**
     * Registers an out-of-band put hook for connectors that do not support Flight
     * {@code putStream}. Takes precedence over {@link WriteHook} when both are
     * registered.
     *
     * @param hook
     *            the put implementation
     * @return {@code this} for fluent chaining
     */
    public TestScenario withPutHook(PutHook hook)
    {
        this.putHook = hook;
        return this;
    }

    /**
     * Registers runtime variable substitutions applied to every property value in
     * the scenario file before the step is executed. Variables are written as
     * {@code ${key}} in the scenario file and replaced with the corresponding
     * value.
     *
     * <p>
     * Example — S3 path resolved from {@code tests.properties}:
     * 
     * <pre>
     *   Interaction.file_name=/${file_s3.s3.test_csv_key}
     * </pre>
     * 
     * with {@code vars = {"file_s3.s3.test_csv_key": "test-data/cars.csv"}} becomes
     * {@code Interaction.file_name=/test-data/cars.csv}.
     *
     * @param vars
     *            map of variable name → replacement value
     * @return {@code this} for fluent chaining
     */
    public TestScenario withVars(java.util.Map<String, String> vars)
    {
        this.scenarioVars = vars != null ? vars : java.util.Collections.emptyMap();
        return this;
    }

    /**
     * Returns a copy of {@code step} with every {@code ${key}} token in property
     * values replaced by the corresponding entry in {@link #scenarioVars}. Tokens
     * whose key is absent in the map are left unchanged.
     */
    private Properties resolveVars(Properties step)
    {
        if (scenarioVars.isEmpty()) {
            return step;
        }
        final Properties resolved = new Properties();
        for (final String key : step.stringPropertyNames()) {
            String value = step.getProperty(key);
            for (final java.util.Map.Entry<String, String> entry : scenarioVars.entrySet()) {
                if (entry.getValue() != null) {
                    value = value.replace("${" + entry.getKey() + "}", entry.getValue());
                }
            }
            resolved.setProperty(key, value);
        }
        return resolved;
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
                            current.setProperty(key, unescape(value));
                        }
                    }
                }
                // flush any trailing continuation
                if (pendingKey != null && current != null) {
                    current.setProperty(pendingKey, unescape(pendingValue.toString().trim()));
                }
            }
        }
        return steps;
    }

    /**
     * Unescapes Java-style escape sequences
     */
    private static String unescape(String value)
    {
        return StringEscapeUtils.unescapeJava(value);
    }

    private static String required(Properties step, String key, String context)
    {
        final String value = step.getProperty(key);
        assertNotNull(context + ": required property '" + key + "' is missing", value);
        return value.trim();
    }
}
