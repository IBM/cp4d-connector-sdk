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
 * Concrete Flight-layer implementation of {@link RowWriter}.
 *
 * <p>Connector authors never subclass this class. They write rows by calling
 * {@link #startRow()}, {@link #set(String, Object)}, and {@link #endRow()} through
 * the {@link RowWriter} interface. All Apache Arrow memory management is hidden inside this class.
 *
 * <p>Each time a batch is full it is passed immediately to the {@code batchConsumer} supplied at
 * construction time. The Flight layer wires that consumer directly to {@code listener.putNext()},
 * so rows are sent to the client as they are produced rather than after the entire dataset is
 * buffered in memory.
 *
 * <p>Usage:
 * <pre>
 *   try (ArrowBatchWriter writer = new ArrowBatchWriter(schema, allocator, 1000, batch -> {
 *       // called once per full/final batch — send it immediately
 *       loader.load(unloader.getRecordBatch());
 *       listener.putNext();
 *   })) {
 *       interaction.stream(writer);
 *   }
 * </pre>
 */
public final class ArrowBatchWriter implements RowWriter, AutoCloseable
{
    private final Schema schema;
    private final int batchSize;
    private final VectorSchemaRoot root;
    private final Consumer<VectorSchemaRoot> batchConsumer;

    private int currentRow;
    private boolean closed;

    /**
     * Creates an Arrow batch writer.
     *
     * @param schema
     *            the Arrow schema describing the fields to write
     * @param allocator
     *            the buffer allocator to use for Arrow memory
     * @param batchSize
     *            the number of rows per batch; when a batch reaches this size it is flushed
     *            automatically on {@link #endRow()}
     * @param batchConsumer
     *            called once per completed batch (including the final partial batch on
     *            {@link #close()}); the supplied {@link VectorSchemaRoot} must be consumed
     *            before returning — it will be cleared and reused for the next batch
     */
    public ArrowBatchWriter(Schema schema, BufferAllocator allocator, int batchSize,
            Consumer<VectorSchemaRoot> batchConsumer)
    {
        this.schema = schema;
        this.batchSize = batchSize > 0 ? batchSize : 1000;
        this.root = VectorSchemaRoot.create(schema, allocator);
        this.root.allocateNew();
        this.batchConsumer = batchConsumer;
        this.currentRow = 0;
        this.closed = false;
    }

    /** {@inheritDoc} */
    @Override
    public void startRow()
    {
        // Row position is tracked by currentRow; no pre-row allocation needed
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("PMD.CloseResource")
    public void set(String fieldName, Object value)
    {
        final FieldVector vector = root.getVector(fieldName);
        if (vector == null) {
            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }
        if (value == null) {
            vector.setNull(currentRow);
        } else {
            ArrowValueExtractor.setValue(vector, currentRow, value);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void endRow()
    {
        currentRow++;
        root.setRowCount(currentRow);
        if (currentRow >= batchSize) {
            flushCurrentBatch();
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
            if (currentRow > 0) {
                flushCurrentBatch();
            }
            root.close();
        }
    }

    // ---- private helpers ----

    private void flushCurrentBatch()
    {
        root.setRowCount(currentRow);
        batchConsumer.accept(root);
        root.clear();
        root.allocateNew();
        currentRow = 0;
    }
}

// Made with Bob
