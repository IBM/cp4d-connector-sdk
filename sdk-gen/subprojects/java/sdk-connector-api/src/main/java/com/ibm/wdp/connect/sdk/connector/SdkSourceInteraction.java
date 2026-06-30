/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import java.util.List;

import org.apache.arrow.flight.Ticket;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * Source interaction for row-based connectors (most connectors).
 *
 * <p>Connector authors implement this interface to stream rows into the Flight layer.
 * The implementation calls {@link RowWriter#startRow()}, {@link RowWriter#set(String, Object)},
 * and {@link RowWriter#endRow()} for each row.
 *
 * <p>For columnar connectors (Parquet, columnar databases), implement
 * {@link SdkColumnarSourceInteraction} instead — it extends this interface and provides a
 * {@code stream(ColumnarWriter)} overload. The Flight layer detects the correct interface via
 * {@code instanceof} and creates the appropriate writer.
 *
 * <p>Example:
 * <pre>
 *   public class MySourceInteraction implements SdkSourceInteraction {
 *       {@literal @}Override
 *       public Schema getSchema() {
 *           return mySchema;
 *       }
 *       {@literal @}Override
 *       public List&lt;Ticket&gt; getTickets() {
 *           return Collections.singletonList(new Ticket(new byte[0]));
 *       }
 *       {@literal @}Override
 *       public void stream(RowWriter writer) throws Exception {
 *           for (MyRow row : source.rows()) {
 *               writer.startRow();
 *               writer.set("id", row.getId());
 *               writer.set("name", row.getName());
 *               writer.endRow();
 *           }
 *       }
 *   }
 * </pre>
 */
public interface SdkSourceInteraction extends AutoCloseable
{
    /**
     * Returns the Arrow schema describing the data this interaction will produce.
     *
     * @return the Arrow {@link Schema}
     * @throws Exception
     *             if the schema cannot be determined
     */
    Schema getSchema() throws Exception;

    /**
     * Returns the list of Arrow Flight tickets representing the partitions this interaction covers.
     *
     * <p>For single-partition connectors, return a list with a single ticket. The Flight layer
     * will call {@link SdkConnector#getSourceInteraction(AssetDescriptor, Ticket)} once per ticket.
     *
     * @return a non-empty list of tickets
     * @throws Exception
     *             if the ticket list cannot be determined
     */
    List<Ticket> getTickets() throws Exception;

    /**
     * Streams data rows into the provided {@link RowWriter}.
     *
     * <p>The connector must call {@link RowWriter#startRow()}, {@link RowWriter#set(String, Object)},
     * and {@link RowWriter#endRow()} for each row. The writer batches rows internally and flushes
     * at the configured batch size.
     *
     * @param writer
     *            the row writer to receive row data
     * @throws Exception
     *             if an error occurs during streaming
     */
    void stream(RowWriter writer) throws Exception;
}

// Made with Bob
