/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector.integ;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.ibm.connect.restconnector.PathPropertyDef;
import com.ibm.connect.restconnector.RestApiMapping;
import com.ibm.connect.restconnector.RestTableDefinition;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;

/**
 * Holds per-connector state for one {@link ConnectorIntegTest} parameter group.
 *
 * <p>One instance is created per connector (per {@code .properties} file) during
 * {@code @Before} setup. It is immutable after construction.
 */
final class ConnectorTestSession {

    private final IntegTestConfig config;
    private final RestApiMapping mapping;
    private final ConnectionProperties connectionProps;

    ConnectorTestSession(IntegTestConfig config, RestApiMapping mapping) throws Exception {
        this.config = config;
        this.mapping = mapping;
        this.connectionProps = buildConnectionProperties(config, mapping);
        validateTablesFilter(config, mapping);
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    IntegTestConfig config()                    { return config; }
    RestApiMapping mapping()                    { return mapping; }
    ConnectionProperties connectionProps()      { return connectionProps; }

    /**
     * Returns the table entries to test, honouring the optional {@code tables}
     * filter from the {@code .properties} file.
     */
    Set<Entry<String, RestTableDefinition>> tablesToTest() {
        final Map<String, RestTableDefinition> all = mapping.getTables();
        final List<String> filter = config.tables();
        if (filter == null || filter.isEmpty()) {
            return all.entrySet();
        }
        final Set<Entry<String, RestTableDefinition>> subset = new LinkedHashSet<>();
        for (final Entry<String, RestTableDefinition> e : all.entrySet()) {
            if (filter.contains(e.getKey())) {
                subset.add(e);
            }
        }
        return subset;
    }

    private static void validateTablesFilter(IntegTestConfig config, RestApiMapping mapping) {
        final List<String> filter = config.tables();
        if (filter == null || filter.isEmpty()) {
            return;
        }
        final Set<String> known = mapping.getTables().keySet();
        final List<String> unknown = filter.stream()
                .filter(name -> !known.contains(name))
                .collect(Collectors.toList());
        if (!unknown.isEmpty()) {
            throw new IllegalStateException(
                    "The 'tables' filter contains name(s) not found in the DSL: " + unknown + ". Fix the 'tables' property in your .properties file.");
        }
    }

    // ── Private builder ──────────────────────────────────────────────────────

    /**
     * Builds {@link ConnectionProperties} from the loaded config, falling back to
     * the {@code $hostname} URL in the connector JSON when {@code host} and
     * {@code port} are absent from the {@code .properties} file.
     */
    private static ConnectionProperties buildConnectionProperties(
            IntegTestConfig cfg, RestApiMapping map) throws Exception {

        final ConnectionProperties props = new ConnectionProperties();

        String host = cfg.host();
        Integer port = cfg.port();
        if (host == null || port == null) {
            final java.net.URL url = new java.net.URL(map.getBaseUrl());
            if (host == null) {
                host = url.getHost();
            }
            if (port == null) {
                port = url.getPort();
                if (port == -1) {
                    port = "https".equalsIgnoreCase(url.getProtocol()) ? 443 : 80;
                }
            }
        }
        props.put("host", host);
        props.put("port", port);

        switch (cfg.authType()) {
            case API_KEY: props.put("api_key",      cfg.apiKey());      break;
            case BASIC:   props.put("username",     cfg.username());
                          props.put("password",     cfg.password());    break;
            case OAUTH2:  props.put("bearer_token", cfg.bearerToken()); break;
            default:      break;
        }

        // ── path properties ──────────────────────────────────────────────────
        // Cross-reference the DSL's $path_properties against the extra properties
        // read from the .properties file. Fail fast with a clear message if any
        // required path variable is missing.
        for (final PathPropertyDef pd : map.getPathProperties()) {
            final String value = cfg.extraProperties().get(pd.getName());
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "Missing required path property '" + pd.getName() + "' ("
                        + pd.getLabel() + ") — add '" + pd.getName() + "=<value>' "
                        + "to your .properties file");
            }
            props.put(pd.getName(), value);
        }

        // Put any remaining extras (not known framework keys, not path properties) through as-is
        // so connectors with non-standard connection properties still work.
        final Set<String> pathPropNames = map.getPathProperties().stream()
                .map(PathPropertyDef::getName)
                .collect(Collectors.toSet());
        for (final Entry<String, String> e : cfg.extraProperties().entrySet()) {
            if (!pathPropNames.contains(e.getKey())) {
                props.put(e.getKey(), e.getValue());
            }
        }

        return props;
    }
}
