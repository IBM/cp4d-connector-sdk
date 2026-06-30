/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

/**
 * Target interaction for connectors that consume data column-by-column rather than row-by-row.
 *
 * <p>Suitable for data targets where data is naturally organised in columnar format, such as
 * Parquet files or columnar databases. The Flight layer detects this interface via {@code instanceof}
 * and creates a {@link ColumnarArrowBatchReader} instead of an {@link ArrowBatchReader}.
 *
 * <p>Connector authors pick the right sub-interface and get exactly the methods they need.
 * There is no {@code UnsupportedOperationException} and no instanceof inside connectors.
 *
 * <p>Example:
 * <pre>
 *   public class MyColumnarTargetInteraction implements SdkColumnarTargetInteraction {
 *       {@literal @}Override
 *       public void consume(ColumnarReader reader) throws Exception {
 *           while (reader.nextBatch()) {
 *               Object[] ids = reader.getColumn("id");
 *               Object[] values = reader.getColumn("value");
 *               target.insertBatch(ids, values);
 *           }
 *       }
 *       // ... setup(), wrapup(), close()
 *   }
 * </pre>
 */
public interface SdkColumnarTargetInteraction extends SdkTargetInteraction
{
    /**
     * {@inheritDoc}
     * <p>
     * Default implementation throws — the Flight layer calls {@link #consume(ColumnarReader)} instead.
     */
    @Override
    default void consume(RowReader reader) throws Exception
    {
        throw new UnsupportedOperationException(
                "SdkColumnarTargetInteraction.consume(RowReader) should not be called directly. "
                        + "The Flight layer calls consume(ColumnarReader).");
    }

    /**
     * Consumes incoming data from the provided {@link ColumnarReader}.
     *
     * <p>The connector must call {@link ColumnarReader#nextBatch()} to advance to each batch
     * and {@link ColumnarReader#getColumn(String)} to retrieve column values.
     *
     * @param reader
     *            the columnar reader providing column data
     * @throws Exception
     *             if an error occurs during consumption
     */
    void consume(ColumnarReader reader) throws Exception;
}

// Made with Bob
