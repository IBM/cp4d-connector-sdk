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
 * Concrete Flight-layer implementation of {@link ColumnarReader}.
 *
 * <p>Connector authors never subclass this class. They read columns via the {@link ColumnarReader}
 * interface by calling {@link #nextBatch()} and {@link #getColumn(String)}. All Apache Arrow
 * internals are hidden inside this class.
 *
 * <p>Two construction modes are supported:
 * <ul>
 *   <li><b>Stream mode</b> ({@link #ColumnarArrowBatchReader(FlightStream)}): consumes batches
 *       lazily from a live {@link FlightStream}. This is the correct production path inside
 *       {@code acceptPut}: the Flight SDK reuses the same {@code VectorSchemaRoot} instance on
 *       every {@link FlightStream#next()} call, so each batch must be consumed before the stream
 *       is advanced.</li>
 *   <li><b>List mode</b> ({@link #ColumnarArrowBatchReader(List)}): iterates over a
 *       pre-collected list of independent {@code VectorSchemaRoot} objects. Used in unit tests.</li>
 * </ul>
 */
public final class ColumnarArrowBatchReader implements ColumnarReader, AutoCloseable
{
    // Exactly one of these is non-null.
    private final FlightStream flightStream;
    private final List<VectorSchemaRoot> batches;

    private int batchIndex = -1; // used only in list mode
    private VectorSchemaRoot current;
    private Map<String, Object[]> columnCache;
    private boolean closed;

    /**
     * Creates a columnar Arrow batch reader that consumes batches lazily from a live
     * {@link FlightStream}.
     *
     * <p>Use this constructor in {@code acceptPut}. The Flight SDK reuses the same root object
     * on every {@link FlightStream#next()} call, so the stream must be advanced only <em>after</em>
     * the current batch has been fully consumed — which this constructor guarantees.
     *
     * @param flightStream
     *            the incoming Flight stream; must not be null
     */
    public ColumnarArrowBatchReader(FlightStream flightStream)
    {
        this.flightStream = flightStream;
        this.batches = null;
        this.closed = false;
    }

    /**
     * Creates a columnar Arrow batch reader over a pre-collected list of independent batches.
     *
     * <p>Each root in the list must be an independent copy (not the reused root from a
     * {@link FlightStream}). Intended for unit tests.
     *
     * @param batches
     *            the list of {@link VectorSchemaRoot} batches to iterate; must not be null
     */
    public ColumnarArrowBatchReader(List<VectorSchemaRoot> batches)
    {
        this.flightStream = null;
        this.batches = batches;
        this.closed = false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean nextBatch()
    {
        if (closed) {
            return false;
        }
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
        columnCache = new HashMap<>();
        extractColumns();
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public Object[] getColumn(String fieldName)
    {
        final Object[] column = columnCache.get(fieldName);
        if (column == null) {
            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }
        return column;
    }

    /** {@inheritDoc} */
    @Override
    public void close()
    {
        closed = true;
    }

    // ---- private helpers ----

    @SuppressWarnings("PMD.CloseResource")
    private void extractColumns()
    {
        final int rowCount = current.getRowCount();
        for (final FieldVector vector : current.getFieldVectors()) {
            final Object[] values = new Object[rowCount];
            for (int i = 0; i < rowCount; i++) {
                values[i] = vector.isNull(i) ? null : ArrowValueExtractor.extract(vector, i);
            }
            columnCache.put(vector.getField().getName(), values);
        }
    }
}

// Made with Bob
