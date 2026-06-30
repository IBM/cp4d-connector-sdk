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
 * Concrete Flight-layer implementation of {@link ColumnarReader}.
 *
 * <p>Connector authors never subclass this class. They read columns via the {@link ColumnarReader}
 * interface by calling {@link #nextBatch()} and {@link #getColumn(String)}. All Apache Arrow
 * internals are hidden inside this class.
 *
 * <p>The Flight layer constructs this from incoming {@link VectorSchemaRoot} batches and passes
 * it to the connector's {@code consume(ColumnarReader)} method.
 */
public final class ColumnarArrowBatchReader implements ColumnarReader, AutoCloseable
{
    private final List<VectorSchemaRoot> batches;
    private int batchIndex;
    private VectorSchemaRoot current;
    private Map<String, Object[]> columnCache;
    private boolean closed;

    /**
     * Creates a columnar Arrow batch reader over the given batches.
     *
     * @param batches
     *            the list of {@link VectorSchemaRoot} batches to iterate; must not be null
     */
    public ColumnarArrowBatchReader(List<VectorSchemaRoot> batches)
    {
        this.batches = batches;
        this.batchIndex = -1;
        this.closed = false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean nextBatch()
    {
        if (closed) {
            return false;
        }
        batchIndex++;
        if (batchIndex < batches.size()) {
            current = batches.get(batchIndex);
            columnCache = new HashMap<>();
            extractColumns();
            return true;
        }
        return false;
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
