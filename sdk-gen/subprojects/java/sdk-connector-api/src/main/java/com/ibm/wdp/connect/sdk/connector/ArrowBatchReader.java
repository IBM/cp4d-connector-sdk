/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;

/**
 * Concrete Flight-layer implementation of {@link RowReader}.
 *
 * <p>Connector authors never subclass this class. They read rows via the {@link RowReader}
 * interface by calling {@link #nextRow()} and {@link #get(String)}. All Apache Arrow
 * internals are hidden inside this class.
 *
 * <p>Two construction modes are supported:
 * <ul>
 *   <li><b>Stream mode</b> ({@link #ArrowBatchReader(FlightStream)}): consumes batches lazily
 *       from a live {@link FlightStream}. This is the correct production path inside
 *       {@code acceptPut}: the Flight SDK reuses the same {@code VectorSchemaRoot} instance on
 *       every {@link FlightStream#next()} call, so each batch must be consumed before the stream
 *       is advanced.</li>
 *   <li><b>List mode</b> ({@link #ArrowBatchReader(List)}): iterates over a pre-collected list
 *       of independent {@code VectorSchemaRoot} objects. Used in unit tests.</li>
 * </ul>
 */
public final class ArrowBatchReader implements RowReader, AutoCloseable
{
    // Exactly one of these is non-null.
    private final FlightStream flightStream;
    private final List<VectorSchemaRoot> batches;

    private int batchIndex = -1; // used only in list mode
    private VectorSchemaRoot current;
    private int rowIndex;
    private int rowCount;
    private Map<String, FieldVector> vectorCache;
    private boolean closed;

    /**
     * Creates an Arrow batch reader that consumes batches lazily from a live {@link FlightStream}.
     *
     * <p>Use this constructor in {@code acceptPut}. The Flight SDK reuses the same root object
     * on every {@link FlightStream#next()} call, so the stream must be advanced only <em>after</em>
     * the current batch has been fully consumed — which this constructor guarantees.
     *
     * @param flightStream
     *            the incoming Flight stream; must not be null
     */
    public ArrowBatchReader(FlightStream flightStream)
    {
        this.flightStream = flightStream;
        this.batches = null;
        this.rowCount = 0;
        this.closed = false;
        advanceBatch();
    }

    /**
     * Creates an Arrow batch reader over a pre-collected list of independent batches.
     *
     * <p>Each root in the list must be an independent copy (not the reused root from a
     * {@link FlightStream}). Intended for unit tests.
     *
     * @param batches
     *            the list of {@link VectorSchemaRoot} batches to iterate; must not be null
     */
    public ArrowBatchReader(List<VectorSchemaRoot> batches)
    {
        this.flightStream = null;
        this.batches = batches;
        this.rowCount = 0;
        this.closed = false;
        advanceBatch();
    }

    /** {@inheritDoc} */
    @Override
    public boolean nextRow()
    {
        if (closed) {
            return false;
        }
        rowIndex++;
        if (rowIndex < rowCount) {
            return true;
        }
        // Try next batch
        if (advanceBatch()) {
            rowIndex = 0;
            return rowIndex < rowCount;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("PMD.CloseResource")
    public Object get(String fieldName)
    {
        final FieldVector vector = vectorCache.get(fieldName);
        if (vector == null) {
            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }
        if (vector.isNull(rowIndex)) {
            return null;
        }
        return ArrowValueExtractor.extract(vector, rowIndex);
    }

    /** {@inheritDoc} */
    @Override
    public void close()
    {
        closed = true;
    }

    // ---- private helpers ----

    private boolean advanceBatch()
    {
        if (flightStream != null) {
            try {
                if (!flightStream.next()) {
                    return false;
                }
            } catch (Exception e) {
                throw new RuntimeException("Error advancing FlightStream", e);
            }
            current = flightStream.getRoot();
        } else {
            batchIndex++;
            if (batchIndex >= batches.size()) {
                return false;
            }
            current = batches.get(batchIndex);
        }
        rowCount = current.getRowCount();
        rowIndex = -1;
        cacheVectors();
        return true;
    }

    @SuppressWarnings("PMD.CloseResource")
    private void cacheVectors()
    {
        vectorCache = new HashMap<>();
        for (final FieldVector v : current.getFieldVectors()) {
            vectorCache.put(v.getField().getName(), v);
        }
    }
}

// Made with Bob
