/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector.integ;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * Reads Arrow Flight data from an in-process {@link FlightClient} for integration tests.
 *
 * <p>All methods are static; callers supply the client, connector name, connection
 * properties, and table name on each call so this class holds no state.
 */
final class FlightTableReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlightTableReader.class);

    private FlightTableReader() {
    }

    // ── Read methods ─────────────────────────────────────────────────────────

    /**
     * Streams exactly one row from {@code tableName} and returns the Arrow schema
     * as actually emitted by the server.
     *
     * <p>Using a live stream (rather than {@code getFlightInfo().getSchema()}) means
     * every schema test exercises a real HTTP call and fails meaningfully when the
     * API is unreachable. Falls back to the {@code FlightInfo} schema when the stream
     * returns no batches.
     */
    @SuppressWarnings({ "PMD.CloseResource", "deprecation" })
    static Schema readLiveSchema(
            FlightClient client,
            String connectorName,
            String tableName,
            ConnectionProperties connectionProps) throws Exception {

        final FlightInfo info = client.getInfo(descriptor(connectorName, tableName, connectionProps, 1));
        for (final FlightEndpoint ep : info.getEndpoints()) {
            try (FlightStream stream = client.getStream(ep.getTicket())) {
                if (stream.next()) {
                    final Schema schema = stream.getRoot().getSchema();
                    LOGGER.debug("[FlightTableReader] readLiveSchema({}) → {} fields", tableName, schema.getFields().size());
                    return schema;
                }
            }
        }
        // Stream returned no batches — fall back to the FlightInfo schema.
        LOGGER.debug("[FlightTableReader] readLiveSchema({}) → no batches, using FlightInfo schema", tableName);
        return info.getSchema();
    }

    /**
     * Reads up to {@code rowLimit} rows from {@code tableName} and returns them as a
     * list of name→value maps keyed by Arrow field name.
     *
     * <p>Values are produced by {@link FieldVector#getObject(int)}; null slots are
     * represented as {@code null} map entries.
     */
    @SuppressWarnings({ "PMD.CloseResource", "deprecation" })
    static List<Map<String, Object>> readRows(
            FlightClient client,
            String connectorName,
            String tableName,
            ConnectionProperties connectionProps,
            int rowLimit) throws Exception {

        final FlightInfo info = client.getInfo(descriptor(connectorName, tableName, connectionProps, rowLimit));
        final List<Map<String, Object>> rows = new ArrayList<>();

        for (final FlightEndpoint ep : info.getEndpoints()) {
            try (FlightStream stream = client.getStream(ep.getTicket())) {
                while (stream.next()) {
                    // getRoot() returns the stream's shared root — do NOT close it here;
                    // the stream owns its buffers and closing mid-flight causes OOM on realloc.
                    final VectorSchemaRoot root = stream.getRoot();
                    for (int r = 0; r < root.getRowCount() && rows.size() < rowLimit; r++) {
                        final Map<String, Object> row = new LinkedHashMap<>();
                        for (final FieldVector vec : root.getFieldVectors()) {
                            row.put(vec.getName(), vec.isNull(r) ? null : vec.getObject(r));
                        }
                        rows.add(row);
                    }
                    if (rows.size() >= rowLimit) {
                        break;
                    }
                }
            }
            if (rows.size() >= rowLimit) {
                break;
            }
        }

        LOGGER.debug("[FlightTableReader] readRows({}) → {} rows", tableName, rows.size());
        return rows;
    }

    // ── Descriptor builder ───────────────────────────────────────────────────

    /** Builds the Flight command descriptor for a table read. */
    static FlightDescriptor descriptor(
            String connectorName,
            String tableName,
            ConnectionProperties connectionProps,
            int batchSize) throws Exception {

        final CustomFlightAssetDescriptor asset = new CustomFlightAssetDescriptor();
        asset.setDatasourceTypeName(connectorName);
        asset.setPath("/" + tableName);
        asset.setConnectionProperties(connectionProps);
        asset.setBatchSize(batchSize);
        return FlightDescriptor.command(new ModelMapper().toBytes(asset));
    }
}
