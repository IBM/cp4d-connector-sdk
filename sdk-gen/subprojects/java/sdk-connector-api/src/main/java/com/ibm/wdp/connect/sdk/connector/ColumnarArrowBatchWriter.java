/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * Concrete Flight-layer implementation of {@link ColumnarWriter}.
 *
 * <p>Connector authors never subclass this class. They write columns by calling
 * {@link #writeColumn(String, Object[])} for each column in a batch, then {@link #flushBatch()}.
 * All Apache Arrow memory management is hidden inside this class.
 *
 * <p>The Flight layer retrieves the accumulated batches via the package-private {@link #batches()}
 * method after the connector's {@code stream(ColumnarWriter)} call completes.
 */
public final class ColumnarArrowBatchWriter implements ColumnarWriter, AutoCloseable
{
    private final Schema schema;
    private final VectorSchemaRoot root;
    private final List<VectorSchemaRoot> completedBatches;
    private int currentBatchRows;
    private boolean closed;

    /**
     * Creates a columnar Arrow batch writer.
     *
     * @param schema
     *            the Arrow schema describing the fields to write
     * @param allocator
     *            the buffer allocator to use for Arrow memory
     * @param batchSize
     *            hint for initial allocation; actual batch size is driven by {@link #writeColumn} array lengths
     */
    public ColumnarArrowBatchWriter(Schema schema, BufferAllocator allocator, int batchSize)
    {
        this.schema = schema;
        this.root = VectorSchemaRoot.create(schema, allocator);
        this.root.allocateNew();
        this.completedBatches = new ArrayList<>();
        this.currentBatchRows = 0;
        this.closed = false;
    }

    /** {@inheritDoc} */
    @Override
    public void writeColumn(String fieldName, Object[] values)
    {
        final FieldVector vector = root.getVector(fieldName);
        if (vector == null) {
            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                vector.setNull(i);
            } else {
                ArrowValueExtractor.setValue(vector, i, values[i]);
            }
        }
        // Track row count from the first column written in this batch
        if (currentBatchRows == 0 && values.length > 0) {
            currentBatchRows = values.length;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void flushBatch()
    {
        if (currentBatchRows > 0) {
            root.setRowCount(currentBatchRows);
            completedBatches.add(root.slice(0, currentBatchRows));
            root.clear();
            root.allocateNew();
            currentBatchRows = 0;
        }
    }

    /**
     * Returns an iterator over all completed batches.
     * <p>
     * <b>Package-private</b> — for use by the Flight layer only.
     *
     * @return an iterator over {@link VectorSchemaRoot} batches
     */
    Iterator<VectorSchemaRoot> batches()
    {
        if (currentBatchRows > 0) {
            flushBatch();
        }
        return completedBatches.iterator();
    }

    /**
     * Returns the Arrow schema.
     * <p>
     * <b>Package-private</b> — for use by the Flight layer only.
     *
     * @return the schema
     */
    Schema getSchema()
    {
        return schema;
    }

    /** {@inheritDoc} */
    @Override
    public void close()
    {
        if (!closed) {
            closed = true;
            for (final VectorSchemaRoot batch : completedBatches) {
                batch.close();
            }
            completedBatches.clear();
            root.close();
        }
    }
}

// Made with Bob
