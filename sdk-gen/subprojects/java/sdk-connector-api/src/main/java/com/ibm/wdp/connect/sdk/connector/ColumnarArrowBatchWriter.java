/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import java.util.function.Consumer;

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
 * <p>Each time {@link #flushBatch()} is called the completed batch is passed immediately to the
 * {@code batchConsumer} supplied at construction time. The Flight layer wires that consumer
 * directly to {@code listener.putNext()}, so batches are sent to the client as they are produced
 * rather than after the entire dataset is buffered in memory.
 */
public final class ColumnarArrowBatchWriter implements ColumnarWriter, AutoCloseable
{
    private final Schema schema;
    private final VectorSchemaRoot root;
    private final Consumer<VectorSchemaRoot> batchConsumer;
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
     * @param batchConsumer
     *            called once per {@link #flushBatch()} invocation; the supplied
     *            {@link VectorSchemaRoot} must be consumed before returning — it will be cleared
     *            and reused for the next batch
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public ColumnarArrowBatchWriter(Schema schema, BufferAllocator allocator, int batchSize,
            Consumer<VectorSchemaRoot> batchConsumer)
    {
        this.schema = schema;
        this.root = VectorSchemaRoot.create(schema, allocator);
        this.root.allocateNew();
        this.batchConsumer = batchConsumer;
        this.currentBatchRows = 0;
        this.closed = false;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("PMD.CloseResource")
    public void writeColumn(String fieldName, Object[] values)
    {
        final FieldVector vector = root.getVector(fieldName);
        if (vector == null) {
            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }
        // Track row count from the first column written in this batch;
        // subsequent columns must have the same length.
        if (currentBatchRows == 0 && values.length > 0) {
            currentBatchRows = values.length;
        } else if (values.length != currentBatchRows) {
            throw new IllegalArgumentException(
                    "Column '" + fieldName + "' has " + values.length
                    + " values but expected " + currentBatchRows + " (from earlier columns in this batch)");
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                vector.setNull(i);
            } else {
                ArrowValueExtractor.setValue(vector, i, values[i]);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void flushBatch()
    {
        if (currentBatchRows > 0) {
            root.setRowCount(currentBatchRows);
            batchConsumer.accept(root);
            root.clear();
            root.allocateNew();
            currentBatchRows = 0;
        }
    }

    /**
     * Returns the Arrow schema.
     * <p>
     * For use by the Flight layer.
     *
     * @return the schema
     */
    public Schema getSchema()
    {
        return schema;
    }

    /** {@inheritDoc} */
    @Override
    public void close()
    {
        if (!closed) {
            closed = true;
            root.close();
        }
    }
}

// Made with Bob
