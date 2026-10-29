/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector.integ;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.ibm.connect.restconnector.AuthenticationType;

/**
 * Loads and validates a single connector integration test configuration from a {@code .properties} file.
 *
 * <p>One instance = one connector under test. Multi-connector runs are handled at
 * the {@code @Parameterized} layer in {@link ConnectorIntegTest}.
 */
public final class IntegTestConfig {

    private static final int DEFAULT_ROW_LIMIT = 50;

    /** Keys consumed by the framework — everything else is treated as an extra (path) property. */
    private static final Set<String> KNOWN_KEYS = new HashSet<>(Arrays.asList(
            "connector.json", "host", "port",
            "auth.type", "api.key", "username", "password", "bearer.token",
            "row.limit", "tables", "fail.on.empty.tables", "request.delay.ms"));

    private final String connectorJson;
    private final String host;
    private final Integer port;
    private final AuthenticationType authType;
    private final String apiKey;
    private final String username;
    private final String password;
    private final String bearerToken;
    private final int rowLimit;
    private final int requestDelayMs;
    private final List<String> tables;
    private final boolean failOnEmptyTables;
    private final Map<String, String> extraProperties;

    private IntegTestConfig(
            String connectorJson,
            String host,
            Integer port,
            AuthenticationType authType,
            String apiKey,
            String username,
            String password,
            String bearerToken,
            int rowLimit,
            int requestDelayMs,
            List<String> tables,
            boolean failOnEmptyTables,
            Map<String, String> extraProperties) {
        this.connectorJson = connectorJson;
        this.host = host;
        this.port = port;
        this.authType = authType;
        this.apiKey = apiKey;
        this.username = username;
        this.password = password;
        this.bearerToken = bearerToken;
        this.rowLimit = rowLimit;
        this.requestDelayMs = requestDelayMs;
        this.tables = tables;
        this.failOnEmptyTables = failOnEmptyTables;
        this.extraProperties = Collections.unmodifiableMap(extraProperties);
    }

    /**
     * Loads an {@link IntegTestConfig} from the given path.
     *
     * @param path path to the {@code .properties} file
     * @return the loaded config, or {@code null} if the file does not exist
     * @throws IllegalStateException if the file exists but contains invalid configuration
     * @throws IOException           if the file cannot be read
     */
    public static IntegTestConfig load(String path) throws IOException {
        final Path file = Paths.get(path.strip());
        if (!Files.exists(file)) {
            return null;
        }

        final Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }

        final String connectorJson = inferConnectorJson(props, file, path);

        final String hostVal = props.getProperty("host");
        final String portStr = props.getProperty("port");
        if ((hostVal != null) != (portStr != null)) {
            throw new IllegalStateException(
                    "Both 'host' and 'port' must be set together, or both omitted. "
                    + "Found host=" + hostVal + ", port=" + portStr + " in: " + path);
        }
        final Integer portVal = portStr == null ? null : parseInt("port", portStr, path);

        final AuthenticationType authType = parseAuthType(props.getProperty("auth.type"), path);

        final String apiKey      = props.getProperty("api.key");
        final String username    = props.getProperty("username");
        final String password    = props.getProperty("password");
        final String bearerToken = props.getProperty("bearer.token");
        validateCredentials(authType, apiKey, username, password, bearerToken, path);

        final String rowLimitStr = props.getProperty("row.limit");
        final int rowLimit = isBlank(rowLimitStr)
                ? DEFAULT_ROW_LIMIT
                : parseInt("row.limit", rowLimitStr, path);

        final String requestDelayStr = props.getProperty("request.delay.ms");
        final int requestDelayMs = isBlank(requestDelayStr)
                ? 0
                : parseInt("request.delay.ms", requestDelayStr, path);

        final String tablesStr = props.getProperty("tables");
        final List<String> tables = isBlank(tablesStr)
                ? null
                : Arrays.stream(tablesStr.split(","))
                        .map(String::strip)
                        .collect(Collectors.toUnmodifiableList());

        // When true, testTableReturnsRows fails (instead of warns) on empty tables.
        final String failOnEmptyTablesStr = props.getProperty("fail.on.empty.tables");
        final boolean failOnEmptyTables = failOnEmptyTablesStr != null && "true".equalsIgnoreCase(failOnEmptyTablesStr.strip());

        return new IntegTestConfig(
                connectorJson, hostVal, portVal,
                authType, apiKey, username, password, bearerToken, rowLimit, requestDelayMs,
                tables, failOnEmptyTables, collectExtraProperties(props));
    }

    /**
     * Determines the connector JSON path.
     *
     * <p>Convention: when {@code connector.json} is absent, infer it from the properties filename.
     * A file named "myconnector.properties" looks for "myconnector.json" in the same
     * directory, then in the canonical DSL resources directory. This lets .properties files live
     * beside their DSL JSON (e.g. in connectors_forge_rest/src/main/resources/) with zero
     * boilerplate keys.
     */
    private static String inferConnectorJson(Properties props, Path propsFile, String originalPath) {
        final String raw = props.getProperty("connector.json");
        if (!isBlank(raw)) {
            final String explicit = resolveConnectorJson(raw);
            if (!Files.exists(Paths.get(explicit))) {
                throw new IllegalStateException(
                        "connector.json path does not exist: '" + explicit + "' (from: " + originalPath + ")");
            }
            return explicit;
        }

        final String propsName = propsFile.getFileName().toString();
        final String baseName = propsName.endsWith(".properties")
                ? propsName.substring(0, propsName.length() - ".properties".length())
                : propsName;
        final Path beside = propsFile.toAbsolutePath().resolveSibling(baseName + ".json");
        if (Files.exists(beside)) {
            return beside.toString();
        }
        final Path canonical = Paths.get(resolveConnectorJson(baseName + ".json")).toAbsolutePath();
        if (Files.exists(canonical)) {
            return canonical.toString();
        }
        throw new IllegalStateException(
                "connector.json is not set and could not be inferred: neither '"
                + beside + "' nor '" + canonical + "' exists (from: " + originalPath + ")");
    }

    private static AuthenticationType parseAuthType(String authTypeStr, String path) {
        if (isBlank(authTypeStr) || "none".equalsIgnoreCase(authTypeStr.strip())) {
            return AuthenticationType.NONE;
        }
        try {
            return AuthenticationType.fromValue(authTypeStr.strip().toLowerCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Unknown auth.type '" + authTypeStr + "' in: " + path
                    + ". Allowed values: " + AuthenticationType.validValues(), e);
        }
    }

    private static void validateCredentials(AuthenticationType authType, String apiKey,
            String username, String password, String bearerToken, String path) {
        switch (authType) {
            case API_KEY:
                requireNonBlank("api.key", apiKey, "api_key", path);
                break;
            case BASIC:
                requireNonBlank("username", username, "basic", path);
                requireNonBlank("password", password, "basic", path);
                break;
            case OAUTH2:
                requireNonBlank("bearer.token", bearerToken, "oauth2", path);
                break;
            default:
                break;
        }
    }

    private static void requireNonBlank(String key, String value, String authType, String path) {
        if (isBlank(value)) {
            throw new IllegalStateException(
                    "'" + key + "' is required when auth.type=" + authType + " in: " + path);
        }
    }

    private static int parseInt(String key, String value, String path) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "'" + key + "' is not a valid integer: '" + value + "' in: " + path, e);
        }
    }

    /**
     * Collects all keys not in {@link #KNOWN_KEYS} (path variables and anything else) from the
     * properties file. They are passed through as-is into ConnectionProperties so that
     * {@code $path_properties} variables declared in the DSL are automatically available.
     */
    private static Map<String, String> collectExtraProperties(Properties props) {
        final Map<String, String> extraProperties = new HashMap<>();
        for (final String key : props.stringPropertyNames()) {
            if (!KNOWN_KEYS.contains(key)) {
                final String val = props.getProperty(key);
                if (val != null) {
                    extraProperties.put(key, val);
                }
            }
        }
        return extraProperties;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Resolves a {@code connector.json} value to an absolute path.
     *
     * <p>If the value is a plain filename (no path separator), it is resolved relative to the
     * canonical connector-descriptor resources directory:
     * {@code sdk-gen/subprojects/connectors_forge_rest/src/main/resources/}.
     * Absolute and relative paths that already contain a separator are returned as-is.
     */
    private static String resolveConnectorJson(String value) {
        if (value.contains("/") || value.contains("\\")) {
            return value;
        }
        // Plain filename — resolve against the well-known resources directory, located three
        // levels above this subproject's root (impl/ → java/ → subprojects/java/ → subprojects/).
        final Path subprojectDir = Paths.get("").toAbsolutePath(); // JVM cwd = impl/
        final Path resourcesDir = subprojectDir
                .resolve("../../../connectors_forge_rest/src/main/resources")
                .normalize();
        return resourcesDir.resolve(value).toString();
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    /** Path to the connector JSON descriptor. */
    public String connectorJson()     { return connectorJson; }

    /** Hostname override, or {@code null} to fall back to the {@code $hostname} in the JSON. */
    public String host()              { return host; }

    /** Port override, or {@code null} to fall back to the {@code $hostname} in the JSON. */
    public Integer port()             { return port; }


    /** Authentication type. Never {@code null}; defaults to {@link AuthenticationType#NONE}. */
    public AuthenticationType authType() { return authType; }

    /** API key (valid when {@link #authType()} is {@code API_KEY}). */
    public String apiKey()            { return apiKey; }

    /** Username (valid when {@link #authType()} is {@code BASIC}). */
    public String username()          { return username; }

    /** Password (valid when {@link #authType()} is {@code BASIC}). */
    public String password()          { return password; }

    /** Bearer token (valid when {@link #authType()} is {@code OAUTH2}). */
    public String bearerToken()       { return bearerToken; }

    /** Maximum rows to sample per table. */
    public int rowLimit()             { return rowLimit; }

    /** Milliseconds to sleep before each HTTP request. Use to avoid rate-limiting on slow APIs. Defaults to 0. */
    public int requestDelayMs()       { return requestDelayMs; }

    /**
     * Table names to test, or {@code null} to test all tables defined in the connector JSON.
     */
    public List<String> tables()      { return tables; }

    /**
     * When {@code true}, {@code testTableReturnsRows} fails (not just warns) when a table
     * returns 0 rows. Useful when testing against a sidecar with seeded data.
     * Defaults to {@code false}.
     */
    public boolean failOnEmptyTables() { return failOnEmptyTables; }

    /**
     * Extra properties from the {@code .properties} file that are not consumed by the framework.
     * These typically correspond to {@code $path_properties} variables declared in the DSL JSON
     * and are passed through into {@code ConnectionProperties} by {@link ConnectorTestSession}.
     */
    public Map<String, String> extraProperties() { return extraProperties; }
}
