/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

/**
 * Writer interface for row-based data production.
 *
 * <p>Connector authors call {@link #startRow()}, one or more {@link #set(String, Object)} calls,
 * then {@link #endRow()} for each row. The implementation accumulates rows into Arrow batches
 * and flushes automatically at the configured {@code batchSize}.
 *
 * <p>No Apache Arrow types are exposed through this interface — connector authors do not need
 * Arrow knowledge to implement a row-based connector.
 *
 * <p>Example usage:
 * <pre>
 *   public void stream(RowWriter writer) throws Exception {
 *       for (MyRow row : source.rows()) {
 *           writer.startRow();
 *           writer.set("id", row.getId());
 *           writer.set("name", row.getName());
 *           writer.endRow();
 *       }
 *   }
 * </pre>
 */
public interface RowWriter
{
    /**
     * Begins a new row. Must be called before any {@link #set(String, Object)} call for this row.
     */
    void startRow();

    /**
     * Sets the value for the named field in the current row.
     *
     * @param fieldName
     *            the field name as defined in the schema
     * @param value
     *            the value to write; may be null for nullable fields. Supported Java types:
     *            {@code String}, {@code Integer}, {@code Long}, {@code Double}, {@code Float},
     *            {@code Boolean}, {@code java.sql.Date}, {@code java.sql.Timestamp},
     *            {@code byte[]}, and numeric wrappers.
     */
    void set(String fieldName, Object value);

    /**
     * Ends the current row and adds it to the current batch.
     * When the batch reaches the configured batch size, it is automatically flushed.
     */
    void endRow();
}

// Made with Bob
