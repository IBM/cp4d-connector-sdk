/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

/**
 * Target interaction for row-based connectors.
 *
 * <p>Connector authors implement this interface to consume rows from the Flight layer.
 * The implementation reads rows by calling {@link RowReader#nextRow()} and
 * {@link RowReader#get(String)}.
 *
 * <p>For columnar connectors, implement {@link SdkColumnarTargetInteraction} instead — it extends
 * this interface and provides a {@code consume(ColumnarReader)} overload.
 *
 * <p>Example:
 * <pre>
 *   public class MyTargetInteraction implements SdkTargetInteraction {
 *       {@literal @}Override
 *       public void setup() throws Exception {
 *           target.beginTransaction();
 *       }
 *       {@literal @}Override
 *       public void consume(RowReader reader) throws Exception {
 *           while (reader.nextRow()) {
 *               target.insert((String) reader.get("id"), reader.get("value"));
 *           }
 *       }
 *       {@literal @}Override
 *       public void wrapup() throws Exception {
 *           target.commit();
 *       }
 *   }
 * </pre>
 */
public interface SdkTargetInteraction extends AutoCloseable
{
    /**
     * Called before any data arrives. Use to create tables, begin transactions, etc.
     *
     * @throws Exception
     *             if setup fails
     */
    void setup() throws Exception;

    /**
     * Consumes incoming data rows from the provided {@link RowReader}.
     *
     * <p>The connector must call {@link RowReader#nextRow()} to advance through rows and
     * {@link RowReader#get(String)} to retrieve field values.
     *
     * @param reader
     *            the row reader providing incoming data
     * @throws Exception
     *             if an error occurs during consumption
     */
    void consume(RowReader reader) throws Exception;

    /**
     * Called after all data has been consumed. Use to commit transactions, finalize files, etc.
     *
     * @throws Exception
     *             if wrapup fails
     */
    void wrapup() throws Exception;
}

// Made with Bob
