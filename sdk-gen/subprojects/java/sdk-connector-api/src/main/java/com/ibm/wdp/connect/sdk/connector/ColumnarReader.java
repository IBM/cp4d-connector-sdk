/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

/**
 * Reader interface for columnar data consumption.
 *
 * <p>Connector authors call {@link #nextBatch()} to advance to the next batch and
 * {@link #getColumn(String)} to read all values for a column in one call. Used in target
 * connectors where Arrow batches from the Flight stream are presented column by column.
 *
 * <p>No Apache Arrow types are exposed through this interface — connector authors do not need
 * Arrow knowledge to implement a columnar target connector.
 *
 * <p>Example usage:
 * <pre>
 *   public void consume(ColumnarReader reader) throws Exception {
 *       while (reader.nextBatch()) {
 *           Object[] ids = reader.getColumn("id");
 *           Object[] names = reader.getColumn("name");
 *           target.insertBatch(ids, names);
 *       }
 *   }
 * </pre>
 */
public interface ColumnarReader
{
    /**
     * Advances the cursor to the next batch.
     *
     * @return {@code true} if there is a batch available; {@code false} if all batches have been consumed
     */
    boolean nextBatch();

    /**
     * Returns all values for the named field in the current batch.
     *
     * <p>Must only be called after a successful {@link #nextBatch()} call.
     *
     * @param fieldName
     *            the field name as defined in the schema
     * @return array of values for this field in the current batch; elements may be {@code null}
     *         for nullable fields
     */
    Object[] getColumn(String fieldName);
}

// Made with Bob
