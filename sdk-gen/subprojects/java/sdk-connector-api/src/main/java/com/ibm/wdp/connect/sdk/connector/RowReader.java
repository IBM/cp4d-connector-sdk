/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

/**
 * Reader interface for row-based data consumption.
 *
 * <p>Connector authors call {@link #nextRow()} to advance to the next row and
 * {@link #get(String)} to read field values. Used in target connectors where Arrow
 * batches from the Flight stream are presented row by row.
 *
 * <p>No Apache Arrow types are exposed through this interface — connector authors do not need
 * Arrow knowledge to implement a row-based target connector.
 *
 * <p>Example usage:
 * <pre>
 *   public void consume(RowReader reader) throws Exception {
 *       while (reader.nextRow()) {
 *           String id = (String) reader.get("id");
 *           Integer count = (Integer) reader.get("count");
 *           target.insert(id, count);
 *       }
 *   }
 * </pre>
 */
public interface RowReader
{
    /**
     * Advances the cursor to the next row.
     *
     * @return {@code true} if there is a row available; {@code false} if all rows have been consumed
     */
    boolean nextRow();

    /**
     * Returns the value of the named field in the current row.
     *
     * <p>Must only be called after a successful {@link #nextRow()} call.
     *
     * @param fieldName
     *            the field name as defined in the schema
     * @return the field value, or {@code null} if the value is SQL null
     */
    Object get(String fieldName);
}

// Made with Bob
