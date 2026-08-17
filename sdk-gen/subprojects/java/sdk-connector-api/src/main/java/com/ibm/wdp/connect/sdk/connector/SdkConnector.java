/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import org.apache.arrow.flight.Ticket;
import org.apache.arrow.vector.types.pojo.Schema;

import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;

/**
 * Minimal SDK connector interface.
 *
 * <p>Intentionally simpler than the library's {@code Connector&lt;P,I,O,D&gt;} — connector authors
 * should not need to understand {@code PartitionPlan}, {@code ExecutionPhase}, or other
 * library-specific lifecycle concepts.
 *
 * <p>The generic parameters let connector authors expose concrete interaction types without casts:
 * <pre>
 *   public class MyConnector implements SdkConnector&lt;MyInputInteraction, MyOutputInteraction,
 *                                                      MyDiscoveryInteraction&gt; { ... }
 * </pre>
 *
 * @param <I>
 *            the input interaction type (must extend {@link SdkInputInteraction})
 * @param <O>
 *            the output interaction type (must extend {@link SdkOutputInteraction})
 * @param <D>
 *            the discovery interaction type (must extend {@link SdkDiscoveryInteraction})
 */
public interface SdkConnector<I extends SdkInputInteraction,
                               O extends SdkOutputInteraction,
                               D extends SdkDiscoveryInteraction>
        extends AutoCloseable
{
    /**
     * Establishes the underlying connection to the data source.
     *
     * @throws Exception
     *             if the connection cannot be established
     */
    void connect() throws Exception;

    /**
     * Returns the Arrow schema for the described asset.
     *
     * @param asset
     *            the asset descriptor identifying the table or object
     * @return the Arrow {@link Schema} describing the asset's fields
     * @throws Exception
     *             if the schema cannot be determined
     */
    Schema getSchema(CustomFlightAssetDescriptor asset) throws Exception;

    /**
     * Creates an input interaction for reading data from the described asset.
     *
     * @param asset
     *            the asset descriptor identifying the table or object to read
     * @param ticket
     *            the Arrow Flight ticket identifying this particular partition
     * @return a new input interaction; caller must close it when done
     * @throws Exception
     *             if the interaction cannot be created
     */
    I getInputInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception;

    /**
     * Creates an output interaction for writing data to the described asset.
     *
     * @param asset
     *            the asset descriptor identifying the table or object to write
     * @return a new output interaction; caller must close it when done
     * @throws Exception
     *             if the interaction cannot be created
     */
    O getOutputInteraction(CustomFlightAssetDescriptor asset) throws Exception;

    /**
     * Creates a discovery interaction for browsing available assets.
     *
     * @param criteria
     *            the criteria scoping the discovery request
     * @return a new discovery interaction; caller must close it when done
     * @throws Exception
     *             if the interaction cannot be created
     */
    D getDiscoveryInteraction(CustomFlightAssetsCriteria criteria) throws Exception;
}
