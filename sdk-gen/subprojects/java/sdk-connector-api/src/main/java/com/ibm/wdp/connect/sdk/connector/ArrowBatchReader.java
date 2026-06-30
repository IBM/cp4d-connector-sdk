/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;

/**
 * Concrete Flight-layer implementation of {@link RowReader}.
 *
 * <p>Connector authors never subclass this class. They read rows via the {@link RowReader}
 * interface by calling {@link #nextRow()} and {@link #get(String)}. All Apache Arrow
 * internals are hidden inside this class.
 *
 * <p>The Flight layer constructs this from incoming {@link VectorSchemaRoot} batches and passes
 * it to the connector's {@code consume(RowReader)} method.
 */
public final class ArrowBatchReader implements RowReader, AutoCloseable
{
    private final List<VectorSchemaRoot> batches;
    private int batchIndex;
    private VectorSchemaRoot current;
    private int rowIndex;
    private int rowCount;
    private Map<String, FieldVector> vectorCache;
    private boolean closed;

    /**
     * Creates an Arrow batch reader over the given batches.
     *
     * @param batches
     *            the list of {@link VectorSchemaRoot} batches to iterate; must not be null
     */
    public ArrowBatchReader(List<VectorSchemaRoot> batches)
    {
        this.batches = batches;
        this.batchIndex = -1;
        this.rowIndex = -1;
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
        batchIndex++;
        if (batchIndex < batches.size()) {
            current = batches.get(batchIndex);
            rowCount = current.getRowCount();
            rowIndex = -1;
            cacheVectors();
            return true;
        }
        return false;
    }

    private void cacheVectors()
    {
        vectorCache = new HashMap<>();
        for (final FieldVector v : current.getFieldVectors()) {
            vectorCache.put(v.getField().getName(), v);
        }
    }
}

// Made with Bob
