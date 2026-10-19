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

    // ── LiveBatch ────────────────────────────────────────────────────────────

    /**
     * A live batch captured from a real Arrow stream: the schema as the server
     * actually emitted it, plus all collected row data.
     *
     * <p>Using a live batch (rather than {@code getFlightInfo().getSchema()}) means
     * every schema test exercises a real HTTP call and fails meaningfully when the
     * API is unreachable.
     */
    static final class LiveBatch {
        final Schema schema;
        final List<Map<String, Object>> rows;

        LiveBatch(Schema schema, List<Map<String, Object>> rows) {
            this.schema = schema;
            this.rows   = rows;
        }
    }

    // ── Read methods ─────────────────────────────────────────────────────────

    /**
     * Reads up to {@code rowLimit} rows from {@code tableName} and returns a
     * {@link LiveBatch} containing the Arrow schema as emitted by the server plus
     * all collected rows.
     *
     * <p>The schema is captured from the first {@link VectorSchemaRoot} before it is
     * closed, so it reflects the actual Arrow types the connector chose for this
     * stream. Falls back to the {@code FlightInfo} schema when the stream returns no
     * batches.
     */
    @SuppressWarnings({ "PMD.CloseResource", "deprecation" })
    static LiveBatch readLiveBatch(
            FlightClient client,
            String connectorName,
            String tableName,
            ConnectionProperties connectionProps,
            int rowLimit) throws Exception {

        final FlightInfo info = client.getInfo(descriptor(connectorName, tableName, connectionProps, rowLimit));
        Schema liveSchema = null;
        final List<Map<String, Object>> rows = new ArrayList<>();

        for (final FlightEndpoint ep : info.getEndpoints()) {
            try (FlightStream stream = client.getStream(ep.getTicket())) {
                while (stream.next()) {
                    // getRoot() returns the stream's shared root — do NOT close it here;
                    // the stream owns its buffers and closing mid-flight causes OOM on realloc.
                    final VectorSchemaRoot root = stream.getRoot();
                    if (liveSchema == null) {
                        liveSchema = root.getSchema();
                    }
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

        if (liveSchema == null) {
            // Stream returned no batches — fall back to the FlightInfo schema so schema
            // tests can still check column names and types against the JSON declaration.
            liveSchema = info.getSchema();
        }
        LOGGER.debug("[FlightTableReader] readLiveBatch({}) → {} rows", tableName, rows.size());
        return new LiveBatch(liveSchema, rows);
    }

    /**
     * Reads up to {@code rowLimit} rows from {@code tableName} and returns them as a
     * list of name→value maps.
     */
    static List<Map<String, Object>> readRows(
            FlightClient client,
            String connectorName,
            String tableName,
            ConnectionProperties connectionProps,
            int rowLimit) throws Exception {
        return readLiveBatch(client, connectorName, tableName, connectionProps, rowLimit).rows;
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
