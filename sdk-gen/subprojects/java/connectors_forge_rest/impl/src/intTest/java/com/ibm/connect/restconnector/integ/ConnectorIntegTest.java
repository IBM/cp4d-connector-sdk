/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector.integ;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.Result;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.junit.AfterClass;
import org.junit.internal.AssumptionViolatedException;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.connect.restconnector.AuthenticationType;
import com.ibm.connect.restconnector.PaginationConfig;
import com.ibm.connect.restconnector.RestApiMapping;
import com.ibm.connect.restconnector.RestApiMappingLoader;
import com.ibm.connect.restconnector.RestConnector;
import com.ibm.connect.restconnector.RestConnectorFactory;
import com.ibm.connect.restconnector.RestFieldDefinition;
import com.ibm.connect.restconnector.RestFlightProducer;
import com.ibm.connect.restconnector.RestTableDefinition;
import com.ibm.connect.sdk.test.TestFlight;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetType;

/**
 * Parameterized integration test for Connector Forge REST connectors.
 *
 * <p>Each {@code .properties} file supplied via {@code -Pinteg.configs} becomes one
 * independent JUnit parameter group. All groups share a single in-process
 * Flight server started once for the entire test run.
 *
 * <p>Design: every {@code @Test} method does its own targeted Flight call,
 * collects all failures into a {@code List<String>}, then asserts once at the
 * end. This gives full visibility (all failing tables reported in one shot)
 * without the overhead of re-running assertions per table per test method.
 *
 * <p>Run from {@code sdk-gen/}:
 * <pre>
 *   ./gradlew :wdp-connect-sdk-gen-java-connectors_forge_rest-impl:integTestForge \
 *       -Pinteg.configs=path/to/openbrewery.properties
 * </pre>
 */
@RunWith(Parameterized.class)
public class ConnectorIntegTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorIntegTest.class);

    // ── Fields ────────────────────────────────────────────────────────────────
    // flight and client are static — one Flight server for the entire test run,
    // shared across all @Parameterized connector groups (started in @BeforeClass).

    private static TestFlight flight;
    private static FlightClient client;

    private final String configPath;
    private ConnectorTestSession session;

    // ── @Parameters ───────────────────────────────────────────────────────────

    @Parameters(name = "{0}")
    public static Collection<Object[]> connectors() {
        final String csv = System.getProperty("integ.configs", "");
        if (csv.isEmpty()) {
            throw new IllegalArgumentException("No configuration files found. "
                    + "Please specify configurations via -Pinteg.configs or create *.properties files in src/intTest/resources/");
        }
        return Arrays.stream(csv.split(","))
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .map(p -> new Object[] { p })
                .collect(Collectors.toList());
    }

    public ConnectorIntegTest(String configPath) {
        this.configPath = configPath;
    }

    // ── Setup / teardown ──────────────────────────────────────────────────────

    /**
     * Starts one in-process Flight server for the entire test run.
     * Port 0 lets the OS assign a free ephemeral port.
     */
    @BeforeClass
    public static void startServer() throws Exception {
        flight = TestFlight.createLocal(0, false, new RestFlightProducer(), null);
        client = flight.getClient();
    }

    /** Closes the shared Flight server after all connectors have been tested. */
    @AfterClass
    @SuppressWarnings("PMD.NonThreadSafeSingleton") // @AfterClass runs once, single-threaded, after all tests finish
    public static void stopServer() throws Exception {
        if (flight != null) {
            flight.close();
            flight = null;
            client = null;
        }
    }

    /**
     * Loads the connector config and registers the mapping for this test method.
     */
    @Before
    public void setUp() throws Exception {
        final IntegTestConfig config = IntegTestConfig.load(configPath);
        if (config == null) {
            LOGGER.warn("No integ config at '{}' — skipping. "
                    + "Run generateIntegProperties to create a template.", configPath);
            Assume.assumeTrue("No integ config at '" + configPath + "' — skipping", false);
            return; // unreachable — satisfies null-safety analysis
        }

        final RestApiMapping mapping = RestApiMappingLoader.load(config.connectorJson());

        final AuthenticationType dslAuth  = mapping.getAuthConfig().getType();
        final AuthenticationType propAuth = config.authType();
        if (propAuth != AuthenticationType.NONE && propAuth != dslAuth) {
            LOGGER.warn("Auth type mismatch: properties file declares '{}' but DSL declares '{}'"
                    + " — skipping (fix {})", propAuth, dslAuth, configPath);
            Assume.assumeTrue("Auth type mismatch: '" + propAuth + "' vs DSL '" + dslAuth + "'", false);
        }

        LOGGER.info("Testing connector '{}' (json={}, auth={}, url={})",
                mapping.getConnectorName(), config.connectorJson(), propAuth, mapping.getBaseUrl());

        RestConnectorFactory.getInstance().register(mapping);

        try {
            session = new ConnectorTestSession(config, mapping);
        } catch (IllegalStateException ex) {
            LOGGER.error("Test setup failed for '{}': {}", configPath, ex.getMessage());
            throw ex;
        }
    }

    // ── Convenience delegates ─────────────────────────────────────────────────

    private Schema readLiveSchema(String tableName) throws Exception {
        delayIfConfigured();
        return FlightTableReader.readLiveSchema(
                client, session.mapping().getConnectorName(),
                tableName, session.connectionProps());
    }

    private List<Map<String, Object>> readRows(String tableName, int rowLimit) throws Exception {
        delayIfConfigured();
        return FlightTableReader.readRows(
                client, session.mapping().getConnectorName(),
                tableName, session.connectionProps(), rowLimit);
    }

    private void delayIfConfigured() throws InterruptedException {
        final int delayMs = session.config().requestDelayMs();
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
    }

    private Set<Entry<String, RestTableDefinition>> tablesToTest() {
        return session.tablesToTest();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Joins a failure list into a readable multi-line message. */
    private static String join(List<String> failures) {
        return String.join("\n", failures);
    }

    /** Returns a description of {@code ex} suitable for a failure message. */
    private static String describeException(Exception ex) {
        if (ex == null) { return "null"; }
        return ex.toString();
    }

    private static boolean looksLikeJson(String s) {
        if (s.isEmpty()) {
            return false;
        }
        final char first = s.charAt(0);
        final char last = s.charAt(s.length() - 1);
        if ((first == '{' && last == '}') || (first == '[' && last == ']')) {
            return true;
        }
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return true;
        }
        if ("true".equals(s) || "false".equals(s) || "null".equals(s)) {
            return true;
        }
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /**
     * Local Flight server starts and responds to {@code health_check} (C-01).
     */
    @Test
    public void testHealthCheckReturnsOk() throws Exception {
        final Iterator<Result> it = client.doAction(new Action("health_check"));
        assertTrue("health_check returned no result", it.hasNext());
        final Result result = it.next();
        assertNotNull("health_check result body is null", result.getBody());
        final CustomFlightActionResponse response =
                new ModelMapper().fromBytes(result.getBody(), CustomFlightActionResponse.class);
        assertEquals("OK", response.getResponseProperties().get("status"));
    }

    /**
     * The live Arrow schema has at least one field per table (R-01).
     *
     * <p>This test makes a real HTTP call; it fails if the API is unreachable.
     */
    @Test
    public void testSchemaHasFields() throws Exception {
        final List<String> failures = new ArrayList<>();
        for (final Entry<String, RestTableDefinition> e : tablesToTest()) {
            try {
                final Schema schema = readLiveSchema(e.getKey());
                if (schema.getFields().isEmpty()) {
                    failures.add("  " + e.getKey() + ": [R-01] live Arrow schema has 0 fields");
                }
            } catch (Exception ex) {
                failures.add("  " + e.getKey() + ": [R-01] exception: " + describeException(ex));
            }
        }
        assertTrue("Schema empty in table(s):\n" + join(failures), failures.isEmpty());
    }

    /**
     * The live Arrow schema column set exactly matches the connector JSON field list (R-02, R-03),
     * and live Arrow field types satisfy {@link ArrowTypeChecker} for every column (R-04).
     *
     * <p>This test makes a real HTTP call; it fails if the API is unreachable.
     * R-03 catches cases where the API returns fields the JSON does not declare.
     * R-04 catches cases where the connector maps a live JSON value to the wrong Arrow type.
     */
    @Test
    public void testSchemaMatchesDescriptor() throws Exception {
        final List<String> failures = new ArrayList<>();
        for (final Entry<String, RestTableDefinition> e : tablesToTest()) {
            final String tableName = e.getKey();
            final List<RestFieldDefinition> fieldDefs = e.getValue().getFields();

            final Schema schema;
            try {
                schema = readLiveSchema(tableName);
            } catch (Exception ex) {
                failures.add("  " + tableName + ": exception: " + describeException(ex));
                continue;
            }

            final Map<String, RestFieldDefinition> byName = new HashMap<>();
            final Set<String> jsonNames = new HashSet<>();
            for (final RestFieldDefinition fd : fieldDefs) {
                byName.put(fd.getName(), fd);
                jsonNames.add(fd.getName());
            }

            final Set<String> arrowNames = new HashSet<>();
            for (final Field f : schema.getFields()) {
                arrowNames.add(f.getName());
            }

            // R-02: every JSON field must appear in the live schema
            for (final RestFieldDefinition fd : fieldDefs) {
                if (!arrowNames.contains(fd.getName())) {
                    failures.add("  " + tableName + ": [R-02] column missing from live schema: " + fd.getName());
                }
            }
            // R-03: every live field must be declared in connector JSON
            // R-04: live Arrow type must match the DSL-declared type
            for (final Field f : schema.getFields()) {
                if (!jsonNames.contains(f.getName())) {
                    failures.add("  " + tableName + ": [R-03] unexpected live column not in JSON: " + f.getName());
                    continue;
                }
                final RestFieldDefinition fd = byName.get(f.getName());
                if (fd != null && !ArrowTypeChecker.isAcceptable(f, fd)) {
                    failures.add("  " + tableName + "  [R-04] " + f.getName()
                            + " : expected " + fd.getTypeString()
                            + " (" + ArrowTypeChecker.expectedArrowTypeName(fd) + ")"
                            + ", got " + f.getFieldType().getType().getClass().getSimpleName());
                }
            }
        }
        assertTrue("Schema mismatches:\n" + join(failures), failures.isEmpty());
    }

    /**
     * Real API returns at least one row for each table (R-05).
     *
     * <p>By default, tables returning 0 rows produce a warning but do not fail — the real
     * API may legitimately have empty tables. Set {@code fail.on.empty.tables=true} in the
     * {@code .properties} file to make empty tables a hard failure (useful against a sidecar
     * with seeded data).
     */
    @Test
    public void testTableReturnsRows() throws Exception {
        final List<String> failures = new ArrayList<>();
        for (final Entry<String, RestTableDefinition> e : tablesToTest()) {
            try {
                final List<Map<String, Object>> rows = readRows(e.getKey(), session.config().rowLimit());
                if (rows.isEmpty()) {
                    if (session.config().failOnEmptyTables()) {
                        failures.add("  " + e.getKey() + ": [R-05] returned 0 rows");
                    } else {
                        LOGGER.warn("[R-05] Table '{}' returned 0 rows",
                                e.getKey());
                    }
                }
            } catch (Exception ex) {
                failures.add("  " + e.getKey() + ": exception: " + describeException(ex));
            }
        }
        assertTrue("Tables returned no rows:\n" + join(failures), failures.isEmpty());
    }

    /**
     * Each Arrow record batch returned by the server contains at most 50 rows (R-06).
     *
     * <p>Sends {@code batchSize=50} in the Flight command; the server is expected to
     * respect it when sizing its output batches. This test fails when the connector
     * ignores {@code batchSize} and returns oversized batches.
     */
    @Test
    public void testBatchSizeRespected() throws Exception {
        final int requestedBatchSize = 50;
        final List<String> failures = new ArrayList<>();
        for (final Entry<String, RestTableDefinition> e : tablesToTest()) {
            final String tableName = e.getKey();
            try {
                delayIfConfigured();
                final List<Integer> batchSizes = FlightTableReader.readBatchSizes(
                        client, session.mapping().getConnectorName(),
                        tableName, session.connectionProps(), requestedBatchSize);
                for (int i = 0; i < batchSizes.size(); i++) {
                    if (batchSizes.get(i) > requestedBatchSize) {
                        failures.add("  " + tableName + ": [R-06] batch " + i
                                + " has " + batchSizes.get(i)
                                + " rows, requested batchSize was " + requestedBatchSize);
                    }
                }
            } catch (Exception ex) {
                failures.add("  " + tableName + ": exception: " + describeException(ex));
            }
        }
        assertTrue("Batch size exceeded:\n" + join(failures), failures.isEmpty());
    }

    /**
     * {@code $notnull} columns have no null values (R-07).
     * {@code $key} columns have no duplicate values (R-08) and no null values (R-09).
     */
    @Test
    public void testKeyAndNotNullConstraints() throws Exception {
        final List<String> failures = new ArrayList<>();
        for (final Entry<String, RestTableDefinition> e : tablesToTest()) {
            final String tableName = e.getKey();
            final List<RestFieldDefinition> fieldDefs = e.getValue().getFields();

            final List<Map<String, Object>> rows;
            try {
                rows = readRows(tableName, session.config().rowLimit());
            } catch (Exception ex) {
                failures.add("  " + tableName + ": exception reading rows: " + describeException(ex));
                continue;
            }
            if (rows.isEmpty()) {
                continue; // R-07/R-08/R-09 → SKIP when no rows
            }

            for (final RestFieldDefinition fd : fieldDefs) {
                // R-07: $notnull columns must have no nulls
                if (fd.isNotNull()) {
                    for (int i = 0; i < rows.size(); i++) {
                        if (rows.get(i).get(fd.getName()) == null) {
                            failures.add("  " + tableName + "." + fd.getName()
                                    + ": [R-07] null in $notnull column at row " + i);
                            break;
                        }
                    }
                }
                // R-08/R-09: $key columns must have no duplicates and no nulls
                if (fd.isKey()) {
                    final Set<Object> seen = new HashSet<>();
                    for (int i = 0; i < rows.size(); i++) {
                        final Object val = rows.get(i).get(fd.getName());
                        // R-09: null in key column
                        if (val == null) {
                            failures.add("  " + tableName + "." + fd.getName()
                                    + ": [R-09] null in $key column at row " + i);
                            break;
                        }
                        // R-08: duplicate key value
                        if (!seen.add(val)) {
                            failures.add("  " + tableName + "." + fd.getName()
                                    + ": [R-08] duplicate $key value '" + val + "' at row " + i);
                            break;
                        }
                    }
                }
            }
        }
        assertTrue("Data constraint violations:\n" + join(failures), failures.isEmpty());
    }

    /**
     * BOOLEAN columns store {@link Boolean}, not {@link Integer} 0/1 (V-01).
     * {@code $key} and {@code $notnull} VARCHAR columns contain no empty strings (V-02).
     */
    @Test
    public void testBooleanAndVarcharFieldValues() throws Exception {
        final List<String> failures = new ArrayList<>();
        for (final Entry<String, RestTableDefinition> e : tablesToTest()) {
            final String tableName = e.getKey();
            final List<Map<String, Object>> rows;
            try {
                rows = readRows(tableName, session.config().rowLimit());
            } catch (Exception ex) {
                failures.add("  " + tableName + ": exception: " + describeException(ex));
                continue;
            }
            for (final RestFieldDefinition fd : e.getValue().getFields()) {
                final String type = fd.getTypeString() == null ? "" : fd.getTypeString().toLowerCase(Locale.ENGLISH);
                for (int i = 0; i < rows.size(); i++) {
                    final Object val = rows.get(i).get(fd.getName());
                    if (val == null) {
                        continue;
                    }

                    // V-01: boolean fields must hold Boolean, not Integer 0/1
                    if ("boolean".equals(type) || "bool".equals(type) || "bit".equals(type)) {
                        if (val instanceof Integer) {
                            failures.add("  " + tableName + "." + fd.getName()
                                    + " row " + i + ": [V-01] boolean stored as Integer: " + val);
                        }
                    }
                    // V-02: key/notnull VARCHAR fields must not be empty strings
                    if ((fd.isKey() || fd.isNotNull())
                            && ("varchar".equals(type) || "nvarchar".equals(type)
                                || "char".equals(type) || "nchar".equals(type))) {
                        if (val.toString().isEmpty()) {
                            failures.add("  " + tableName + "." + fd.getName()
                                    + " row " + i + ": [V-02] key/notnull VARCHAR is empty string");
                        }
                    }
                }
            }
        }
        assertTrue("Scalar value type violations:\n" + join(failures), failures.isEmpty());
    }

    /**
     * JSON/OBJECT/ARRAY fields contain parseable JSON strings (V-03).
     */
    @Test
    public void testJsonFieldValues() throws Exception {
        final List<String> failures = new ArrayList<>();
        for (final Entry<String, RestTableDefinition> e : tablesToTest()) {
            final String tableName = e.getKey();
            final List<Map<String, Object>> rows;
            try {
                rows = readRows(tableName, session.config().rowLimit());
            } catch (Exception ex) {
                failures.add("  " + tableName + ": exception: " + describeException(ex));
                continue;
            }
            if (rows.isEmpty()) {
                continue;
            }

            for (final RestFieldDefinition fd : e.getValue().getFields()) {
                final String type = fd.getTypeString() == null ? "" : fd.getTypeString().toLowerCase(Locale.ENGLISH);

                // V-03: json/array/object columns must contain parseable JSON strings
                if ("json".equals(type) || "jsonb".equals(type)
                        || "array".equals(type) || "object".equals(type)) {
                    for (int i = 0; i < rows.size(); i++) {
                        final Object val = rows.get(i).get(fd.getName());
                        if (val == null) {
                            continue;
                        }
                        final String s = val.toString().strip();
                        if (!looksLikeJson(s)) {
                            failures.add("  " + tableName + "." + fd.getName()
                                    + " row " + i + ": [V-03] not valid JSON: "
                                    + (s.length() > 80 ? s.substring(0, 80) + "…" : s));
                        }
                    }
                }
            }
        }
        assertTrue("Complex field value violations:\n" + join(failures), failures.isEmpty());
    }

    /**
     * {@code discoverAssets("/")} returns one entry per table with
     * {@code hasChildren=true} and {@code dataset=false} (D-01).
     */
    @Test
    public void testDiscoveryRoot() throws Exception {
        try (RestConnector connector = new RestConnector(
                session.mapping().getConnectorName(), session.connectionProps())) {
            connector.connect();

            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            criteria.setPath("/");
            criteria.setConnectionProperties(session.connectionProps());

            final List<CustomFlightAssetDescriptor> assets =
                    connector.getDiscoveryInteraction(criteria).discoverAssets(criteria);
            final Set<String> expectedTables = tablesToTest().stream()
                    .map(Entry::getKey).collect(Collectors.toSet());

            final List<String> failures = new ArrayList<>();
            final Set<String> foundNames = new HashSet<>();
            for (final CustomFlightAssetDescriptor asset : assets) {
                final String name = asset.getName() != null ? asset.getName()
                        : (asset.getPath() != null ? asset.getPath().replaceFirst("^/+", "") : "");
                foundNames.add(name);

                final DiscoveredAssetType assetType = asset.getAssetType();
                if (assetType != null && Boolean.TRUE.equals(assetType.isDataset())) {
                    failures.add("  " + name + ": [D-01] dataset=true at root (expected false)");
                }
                if (!Boolean.TRUE.equals(asset.isHasChildren())) {
                    failures.add("  " + name + ": [D-01] hasChildren=false at root (expected true)");
                }
            }
            for (final String table : expectedTables) {
                if (!foundNames.contains(table)) {
                    failures.add("  [D-01] table '" + table + "' missing from root discovery");
                }
            }
            assertTrue("Root discovery failures:\n" + join(failures), failures.isEmpty());
        }
    }

    /**
     * {@code discoverAssets("/<TABLE>")} returns one descriptor with
     * {@code dataset=true}, {@code hasChildren=false}, and a matching field count (D-02, D-03).
     */
    @Test
    public void testDiscoveryTable() throws Exception {
        try (RestConnector connector = new RestConnector(
                session.mapping().getConnectorName(), session.connectionProps())) {
            connector.connect();

            final List<String> failures = new ArrayList<>();
            for (final Entry<String, RestTableDefinition> e : tablesToTest()) {
                final String tableName = e.getKey();

                final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
                criteria.setPath("/" + tableName);
                criteria.setConnectionProperties(session.connectionProps());

                final List<CustomFlightAssetDescriptor> assets =
                        connector.getDiscoveryInteraction(criteria).discoverAssets(criteria);
                if (assets == null || assets.isEmpty()) {
                    failures.add("  " + tableName + ": [D-02] no descriptor returned");
                    continue;
                }
                final CustomFlightAssetDescriptor asset = assets.get(0);

                final DiscoveredAssetType assetType2 = asset.getAssetType();
                if (assetType2 == null || !Boolean.TRUE.equals(assetType2.isDataset())) {
                    failures.add("  " + tableName + ": [D-02] dataset=false (expected true)");
                }
                if (Boolean.TRUE.equals(asset.isHasChildren())) {
                    failures.add("  " + tableName + ": [D-02] hasChildren=true (expected false)");
                }
                // D-03: field count must match the connector JSON
                if (asset.getFields() != null) {
                    final int want = e.getValue().getFields().size();
                    final int got  = asset.getFields().size();
                    if (want != got) {
                        failures.add("  " + tableName + ": [D-03] field count mismatch: want "
                                + want + ", got " + got);
                    }
                }
            }
            assertTrue("Table discovery failures:\n" + join(failures), failures.isEmpty());
        }
    }

    /**
     * {@code discoverAssets("/NONEXISTENT")} returns an empty list (D-04).
     */
    @Test
    public void testDiscoveryUnknownPathReturnsEmpty() throws Exception {
        try (RestConnector connector = new RestConnector(
                session.mapping().getConnectorName(), session.connectionProps())) {
            connector.connect();

            final CustomFlightAssetsCriteria criteria = new CustomFlightAssetsCriteria();
            criteria.setPath("/NONEXISTENT_TABLE_THAT_DOES_NOT_EXIST_99999");
            criteria.setConnectionProperties(session.connectionProps());

            final List<CustomFlightAssetDescriptor> assets =
                    connector.getDiscoveryInteraction(criteria).discoverAssets(criteria);
            assertTrue("[D-04] expected empty list for unknown table, got: " + assets,
                    assets == null || assets.isEmpty());
        }
    }

    /**
     * The offset parameter advances correctly: rows on page 2 are disjoint from rows on page 1 (P-01).
     *
     * <p>Reads two explicit pages using {@code page_size} from the DSL directly:
     * first reads exactly {@code page_size} rows, then reads {@code page_size + 1}
     * rows, and checks that the extra row from page 2 does not appear in page 1.
     */
    @Test
    public void testPaginationOffsetAdvances() throws Exception {
        final List<String> failures = new ArrayList<>();
        boolean anyTableTested = false;

        for (final Entry<String, RestTableDefinition> entry : tablesToTest()) {
            final String tableName = entry.getKey();
            final PaginationConfig pagination = entry.getValue().getPaginationConfig();
            if (pagination == null || !"offset".equals(pagination.getType())) {
                continue; // only offset pagination has a deterministic page boundary we can probe
            }
            anyTableTested = true;
            final int pageSize = pagination.getPageSize();

            // Page 1 only: read exactly page_size rows
            final List<Map<String, Object>> page1;
            try {
                page1 = readRows(tableName, pageSize);
            } catch (Exception ex) {
                failures.add("  " + tableName + ": exception reading page 1: " + describeException(ex));
                continue;
            }
            if (page1.size() < pageSize) {
                LOGGER.info("[P-01] {}: only {} rows available (< page_size {}), skipping offset check",
                        tableName, page1.size(), pageSize);
                continue;
            }

            // Two pages: read page_size + 1 rows — the last row must not appear in page 1
            final List<Map<String, Object>> twoPages;
            try {
                twoPages = readRows(tableName, pageSize + 1);
            } catch (Exception ex) {
                failures.add("  " + tableName + ": exception reading two pages: " + describeException(ex));
                continue;
            }
            if (twoPages.size() <= pageSize) {
                failures.add("  " + tableName + ": [P-01] two-page read returned only "
                        + twoPages.size() + " rows — page 2 was not fetched");
                continue;
            }

            // Collect page-1 key values
            for (final RestFieldDefinition fd : entry.getValue().getFields()) {
                if (!fd.isKey()) { continue; }
                final Set<Object> page1Keys = new HashSet<>();
                for (final Map<String, Object> row : page1) {
                    final Object k = row.get(fd.getName());
                    if (k != null) { page1Keys.add(k); }
                }
                // The extra row (first row of page 2) must NOT be in page 1
                final Object page2FirstKey = twoPages.get(pageSize).get(fd.getName());
                if (page2FirstKey != null && page1Keys.contains(page2FirstKey)) {
                    failures.add("  " + tableName + "." + fd.getName()
                            + ": [P-01] key '" + page2FirstKey
                            + "' from page 2 was already in page 1 — offset not advancing");
                }
            }
        }

        if (!anyTableTested) {
            LOGGER.info("[P-01] skipped — no offset-paginated tables found");
        }
        assertTrue("Offset-advance failures:\n" + join(failures), failures.isEmpty());
    }

    /**
     * Reading a table name that does not exist in the connector JSON throws an exception (N-01).
     *
     * <p>The connector must not silently return an empty result for an unknown table.
     */
    @Test
    public void testUnknownTableReadThrows() throws Exception {
        final String bogus = "__NO_SUCH_TABLE_XYZ__";
        try {
            readLiveSchema(bogus);
            fail("[N-01] expected an exception reading unknown table '" + bogus + "', got none");
        } catch (AssumptionViolatedException ex) {
            throw ex; // propagate skip — do not mistake it for a successful rejection
        } catch (Exception ex) {
            // Any exception is acceptable — just confirm the connector rejected the request
            LOGGER.info("[N-01] unknown table correctly rejected: {}", describeException(ex));
        }
    }

    /**
     * Reading with a deliberately unreachable host fails with a connectivity error (N-02).
     *
     * <p>The connector must not silently return an empty result when the host is wrong.
     * Uses {@code localhost:1} (port 1 is reserved and always refuses connections immediately)
     * rather than an unresolvable DNS name, which avoids the DNS timeout.
     */
    @Test
    public void testUnreachableHostThrows() throws Exception {
        // localhost:1 is reserved and always produces an immediate "Connection refused"
        final ConnectionProperties badProps = new ConnectionProperties();
        badProps.put("host", "localhost");
        badProps.put("port", 1);

        final Entry<String, RestTableDefinition> first = tablesToTest().iterator().next();
        final FlightDescriptor desc = FlightTableReader.descriptor(
                session.mapping().getConnectorName(), first.getKey(), badProps, 1);

        try {
            final FlightInfo info = client.getInfo(desc);
            for (final FlightEndpoint ep : info.getEndpoints()) {
                try (FlightStream stream = client.getStream(ep.getTicket())) {
                    while (stream.next()) { /* drain */ }
                    stream.getRoot();
                }
            }
            fail("[N-02] expected a connectivity exception for host 'localhost:1', got none");
        } catch (Exception ex) {
            LOGGER.info("[N-02] unreachable host correctly rejected: {}", describeException(ex));
        }
    }

    /**
     * Attempting to write to the connector is rejected with an unsupported-operation error (N-03).
     *
     * <p>The REST connector is read-only; {@code getOutputInteraction()} must throw.
     */
    @Test
    public void testWriteOperationRejected() throws Exception {
        final Entry<String, RestTableDefinition> first = tablesToTest().iterator().next();
        final CustomFlightAssetDescriptor asset = new CustomFlightAssetDescriptor();
        asset.setDatasourceTypeName(session.mapping().getConnectorName());
        asset.setPath("/" + first.getKey());
        asset.setConnectionProperties(session.connectionProps());

        try (RestConnector connector = new RestConnector(
                session.mapping().getConnectorName(), session.connectionProps())) {
            connector.connect();
            connector.getOutputInteraction(asset);
            fail("[N-03] expected write to be rejected, but getOutputInteraction() returned normally");
        } catch (UnsupportedOperationException ex) {
            LOGGER.info("[N-03] write correctly rejected: {}", describeException(ex));
        }
    }

    /**
     * Reading with deliberately invalid credentials is rejected by the API (N-04).
     *
     * <p>Skipped when {@code auth.type=none} — there are no credentials to invalidate.
     * For all other auth types, the connector must propagate the 401/403 from the API
     * as a failure rather than silently returning empty data.
     */
    @Test
    public void testInvalidCredentialsRejected() throws Exception {
        Assume.assumeTrue("[N-04] skipping — auth.type is NONE, no credentials to invalidate",
                session.config().authType() != AuthenticationType.NONE);

        final ConnectionProperties badProps = new ConnectionProperties();
        badProps.put("host",     session.connectionProps().get("host"));
        badProps.put("port",     session.connectionProps().get("port"));

        switch (session.config().authType()) {
            case BASIC:
                badProps.put("username", "__invalid_user__");
                badProps.put("password", "__invalid_pass__");
                break;
            case API_KEY:
                badProps.put("api_key", "__invalid_api_key__");
                break;
            case OAUTH2:
                badProps.put("bearer_token", "__invalid_bearer_token__");
                break;
            default:
                break;
        }

        final Entry<String, RestTableDefinition> first = tablesToTest().iterator().next();
        final FlightDescriptor desc = FlightTableReader.descriptor(
                session.mapping().getConnectorName(), first.getKey(), badProps, 1);

        try {
            final FlightInfo info = client.getInfo(desc);
            for (final FlightEndpoint ep : info.getEndpoints()) {
                try (FlightStream stream = client.getStream(ep.getTicket())) {
                    while (stream.next()) { /* drain */ }
                    stream.getRoot();
                }
            }
            fail("[N-04] expected an auth failure for table '" + first.getKey()
                    + "' with invalid credentials, but the read succeeded");
        } catch (Exception ex) {
            LOGGER.info("[N-04] invalid credentials correctly rejected: {}", describeException(ex));
        }
    }
}
