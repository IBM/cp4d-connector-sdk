/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import java.util.List;

import org.apache.arrow.flight.Ticket;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * Source interaction for connectors that produce data column-by-column rather than row-by-row.
 *
 * <p>Suitable for data sources where data is naturally organised in columnar format, such as
 * Parquet files or columnar databases. The Flight layer detects this interface via {@code instanceof}
 * and creates a {@link ColumnarArrowBatchWriter} instead of an {@link ArrowBatchWriter}.
 *
 * <p>Connector authors pick the right sub-interface and get exactly the methods they need.
 * There is no {@code UnsupportedOperationException} and no instanceof inside connectors.
 *
 * <p>Example:
 * <pre>
 *   public class MyColumnarSourceInteraction implements SdkColumnarSourceInteraction {
 *       {@literal @}Override
 *       public void stream(ColumnarWriter writer) throws Exception {
 *           for (ColumnBatch batch : source.batches()) {
 *               writer.writeColumn("id", batch.ids());
 *               writer.writeColumn("value", batch.values());
 *               writer.flushBatch();
 *           }
 *       }
 *       // ... getSchema(), getTickets(), close()
 *   }
 * </pre>
 */
public interface SdkColumnarSourceInteraction extends SdkSourceInteraction
{
    /**
     * {@inheritDoc}
     * <p>
     * Default implementation delegates to {@link #stream(ColumnarWriter)}.
     * The Flight layer calls this overload directly — connector authors implement
     * {@link #stream(ColumnarWriter)} instead.
     */
    @Override
    default void stream(RowWriter writer) throws Exception
    {
        throw new UnsupportedOperationException(
                "SdkColumnarSourceInteraction.stream(RowWriter) should not be called directly. "
                        + "The Flight layer calls stream(ColumnarWriter).");
    }

    /**
     * Streams data from the source into the provided {@link ColumnarWriter}.
     *
     * <p>The connector must write each column via {@link ColumnarWriter#writeColumn(String, Object[])}
     * and call {@link ColumnarWriter#flushBatch()} after all columns for a batch are written.
     *
     * @param writer
     *            the columnar writer to receive column data
     * @throws Exception
     *             if an error occurs during streaming
     */
    void stream(ColumnarWriter writer) throws Exception;
}

// Made with Bob
