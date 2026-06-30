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
 * Concrete Flight-layer implementation of {@link RowWriter}.
 *
 * <p>Connector authors never subclass this class. They write rows by calling
 * {@link #startRow()}, {@link #set(String, Object)}, and {@link #endRow()} through
 * the {@link RowWriter} interface. All Apache Arrow memory management is hidden inside this class.
 *
 * <p>The Flight layer retrieves the accumulated batches via the package-private {@link #batches()}
 * method after the connector's {@code stream(RowWriter)} call completes.
 *
 * <p>Usage:
 * <pre>
 *   try (ArrowBatchWriter writer = new ArrowBatchWriter(schema, allocator, 1000)) {
 *       interaction.stream(writer);
 *       for (Iterator&lt;VectorSchemaRoot&gt; it = writer.batches(); it.hasNext(); ) {
 *           VectorSchemaRoot root = it.next();
 *           listener.putNext(root);
 *       }
 *   }
 * </pre>
 */
public final class ArrowBatchWriter implements RowWriter, AutoCloseable
{
    private final Schema schema;
    private final int batchSize;
    private final VectorSchemaRoot root;
    private final List<VectorSchemaRoot> completedBatches;

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
     */
    public ArrowBatchWriter(Schema schema, BufferAllocator allocator, int batchSize)
    {
        this.schema = schema;
        this.batchSize = batchSize > 0 ? batchSize : 1000;
        this.root = VectorSchemaRoot.create(schema, allocator);
        this.root.allocateNew();
        this.completedBatches = new ArrayList<>();
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
     * Returns an iterator over all completed batches plus any remaining partial batch.
     * <p>
     * For use by the Flight layer after the connector's {@code stream(RowWriter)} method returns.
     *
     * @return an iterator over {@link VectorSchemaRoot} batches; caller must not close the roots
     */
    public Iterator<VectorSchemaRoot> batches()
    {
        if (currentRow > 0) {
            flushCurrentBatch();
        }
        return completedBatches.iterator();
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
            for (final VectorSchemaRoot batch : completedBatches) {
                batch.close();
            }
            completedBatches.clear();
            root.close();
        }
    }

    // ---- private helpers ----

    private void flushCurrentBatch()
    {
        completedBatches.add(root.slice(0, currentRow));
        root.clear();
        root.allocateNew();
        currentRow = 0;
    }
}

// Made with Bob
