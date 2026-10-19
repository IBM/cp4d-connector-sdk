/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector.integ;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.connect.restconnector.AuthConfig.HeaderDef;
import com.ibm.connect.restconnector.AuthenticationType;
import com.ibm.connect.restconnector.PathPropertyDef;
import com.ibm.connect.restconnector.RestApiMapping;
import com.ibm.connect.restconnector.RestApiMappingLoader;

/**
 * Generates a ready-to-fill {@code .properties} file for a Connector Forge integration test
 * beside the given DSL JSON, tailored to the connector's auth type and path properties.
 */
final class PropertiesFileGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertiesFileGenerator.class);

    private PropertiesFileGenerator() { }

    /**
     * Generates a {@code .properties} file beside the given DSL JSON file.
     *
     * <p>The output path is derived by replacing the {@code .json} extension of
     * {@code jsonPath} with {@code .properties}. If that file already exists it is
     * left untouched and {@code null} is returned.
     *
     * @param jsonPath path to the connector DSL JSON file
     * @return the absolute path of the generated file, or {@code null} if the file
     *         already exists, the JSON cannot be loaded, or writing fails
     */
    static String generate(String jsonPath) {
        final File jsonFile = new File(jsonPath.strip());
        if (!jsonFile.exists()) {
            LOGGER.warn("DSL JSON not found: {}", jsonFile.getAbsolutePath());
            return null;
        }

        final String jsonName = jsonFile.getName();
        final String baseName = jsonName.endsWith(".json")
                ? jsonName.substring(0, jsonName.length() - ".json".length())
                : jsonName;

        final File dest = new File(jsonFile.getParentFile(), baseName + ".properties");

        // Never overwrite an existing file — the user may have filled in secrets.
        if (dest.exists()) {
            LOGGER.debug("Skipping generation — .properties file already exists: {}", dest.getAbsolutePath());
            return null;
        }

        final RestApiMapping mapping;
        try {
            mapping = RestApiMappingLoader.load(jsonFile.getAbsolutePath());
        } catch (Exception ex) {
            LOGGER.warn("Could not load DSL JSON '{}': {}", jsonFile.getAbsolutePath(), ex.getMessage());
            return null;
        }

        try {
            writeTemplate(dest.toPath(), jsonName, mapping);
            return dest.getAbsolutePath();
        } catch (IOException ex) {
            LOGGER.warn("Could not write .properties file at '{}': {}", dest, ex.getMessage());
            return null;
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private static void writeTemplate(Path dest, String jsonFilename, RestApiMapping mapping)
            throws IOException {
        final StringBuilder sb = new StringBuilder(1024);
        appendHeader(sb, dest.toAbsolutePath(), mapping.getConnectorName());
        appendConnectorJson(sb, jsonFilename);
        appendApiCoordinates(sb, mapping.getBaseUrl());
        appendAuthSection(sb, mapping);
        appendPathProperties(sb, mapping.getPathProperties());
        appendTuning(sb, mapping);

        Files.createDirectories(dest.getParent());
        Files.writeString(dest, sb, StandardCharsets.UTF_8);
    }

    private static void appendHeader(StringBuilder sb, Path absDest, String connectorName) {
        sb.append("# Connector Forge Integration Test — ").append(connectorName).append('\n');
        sb.append("# Auto-generated — fill in the values below and re-run integTestForge.\n");
        sb.append("# Keep this file out of version control if it contains secrets.\n");
        sb.append("#\n");
        sb.append("# Run:\n");
        sb.append("#   ./gradlew :wdp-connect-sdk-gen-java-connectors_forge_rest-impl:integTestForge \\\n");
        sb.append("#       -Pinteg.configs=").append(absDest).append('\n');
        sb.append('\n');
    }

    private static void appendConnectorJson(StringBuilder sb, String jsonFilename) {
        sb.append("# ── connector.json ──────────────────────────────────────────────────────────\n");
        sb.append("connector.json=").append(jsonFilename).append('\n');
        sb.append('\n');
    }

    private static void appendApiCoordinates(StringBuilder sb, String baseUrl) {
        sb.append("# ── API coordinates (optional — defaults come from $hostname in the JSON) ───\n");
        sb.append("# host=").append(hostOf(baseUrl)).append('\n');
        sb.append("# port=443\n");
        sb.append('\n');
    }

    private static void appendPathProperties(StringBuilder sb, List<PathPropertyDef> pathProps) {
        if (pathProps.isEmpty()) {
            return;
        }
        sb.append("# ── Path properties ($path_properties in the DSL) ───────────────────────────\n");
        for (final PathPropertyDef p : pathProps) {
            sb.append("# ").append(hintFor(p)).append('\n');
            sb.append(p.getName()).append("=<").append(p.getLabel()).append(">\n");
        }
        sb.append('\n');
    }

    private static void appendTuning(StringBuilder sb, RestApiMapping mapping) {
        sb.append("# ── Tuning ──────────────────────────────────────────────────────────────────\n");
        sb.append("# row.limit=50\n");
        final String tableHint = mapping.getTables().keySet().stream()
                .limit(3)
                .collect(Collectors.joining(","));
        if (!tableHint.isEmpty()) {
            sb.append("# tables=").append(tableHint).append('\n');
        }
        sb.append("# strict=false\n");
    }

    private static String hostOf(String baseUrl) {
        return baseUrl.replaceFirst("^https?://", "").replaceFirst("/.*", "");
    }

    private static String hintFor(PathPropertyDef p) {
        return p.getDescription() != null && !p.getDescription().isBlank()
                ? p.getLabel() + ": " + p.getDescription()
                : p.getLabel();
    }

    private static void appendAuthSection(StringBuilder sb, RestApiMapping mapping) {
        sb.append("# ── Authentication ──────────────────────────────────────────────────────────\n");
        appendAuthEntries(sb, mapping.getAuthConfig().getType(), mapping);
        sb.append('\n');
    }

    private static void appendAuthEntries(StringBuilder sb, AuthenticationType authType,
            RestApiMapping mapping) {
        switch (authType) {
            case NONE:
                sb.append("auth.type=none\n");
                break;
            case API_KEY:
                sb.append("auth.type=api_key\n");
                sb.append("api.key=<api key>\n");
                break;
            case BASIC:
                sb.append("auth.type=basic\n");
                sb.append("username=<username>\n");
                sb.append("password=<password>\n");
                break;
            case OAUTH2:
                sb.append("auth.type=oauth2\n");
                sb.append("bearer.token=<OAuth 2.0 access token>\n");
                break;
            default:
                sb.append("auth.type=").append(authType.getValue()).append('\n');
                for (final HeaderDef h : mapping.getAuthConfig().getHeaders()) {
                    sb.append(h.getName()).append("=<").append(h.getLabel()).append(">\n");
                }
                break;
        }
    }
}
