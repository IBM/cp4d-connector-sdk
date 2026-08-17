/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

/**
 * Writer interface for columnar data production.
 *
 * <p>Connector authors call {@link #writeColumn(String, Object[])} for each column in a batch,
 * then {@link #flushBatch()} to commit the batch. This is suitable for columnar data sources
 * such as Parquet files or columnar databases where data is organized by column.
 *
 * <p>No Apache Arrow types are exposed through this interface — connector authors do not need
 * Arrow knowledge to implement a columnar connector.
 *
 * <p>Example usage:
 * <pre>
 *   public void stream(ColumnarWriter writer) throws Exception {
 *       for (ColumnBatch batch : source.batches()) {
 *           writer.writeColumn("id", batch.getColumn("id"));
 *           writer.writeColumn("name", batch.getColumn("name"));
 *           writer.flushBatch();
 *       }
 *   }
 * </pre>
 */
public interface ColumnarWriter
{
    /**
     * Writes all values for a single column in the current batch.
     *
     * <p>All columns written before the next {@link #flushBatch()} must have the same array length.
     *
     * @param fieldName
     *            the field name as defined in the schema
     * @param values
     *            the column values for this batch; may contain null elements for nullable fields.
     *            Supported element types match those described in {@link RowWriter#set}.
     */
    void writeColumn(String fieldName, Object[] values);

    /**
     * Flushes the current batch. All columns must have been written before calling this method.
     * Resets the writer state to accept the next batch.
     */
    void flushBatch();
}

// Made with Bob
