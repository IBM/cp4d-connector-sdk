/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import org.apache.arrow.vector.types.pojo.Schema;

/**
 * Minimal SDK connector interface.
 *
 * <p>Intentionally simpler than the library's {@code Connector&lt;P,I,O,D&gt;} — connector authors
 * should not need to understand {@code PartitionPlan}, {@code ExecutionPhase}, or other
 * library-specific lifecycle concepts.
 *
 * <p>The generic parameters let connector authors expose concrete interaction types without casts:
 * <pre>
 *   public class MyConnector implements SdkConnector&lt;MySourceInteraction, MyTargetInteraction,
 *                                                      MyDiscoveryInteraction&gt; { ... }
 * </pre>
 *
 * @param <S>
 *            the source interaction type (must extend {@link SdkSourceInteraction})
 * @param <T>
 *            the target interaction type (must extend {@link SdkTargetInteraction})
 * @param <D>
 *            the discovery interaction type (must extend {@link SdkDiscoveryInteraction})
 */
public interface SdkConnector<S extends SdkSourceInteraction,
                               T extends SdkTargetInteraction,
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
     * Returns the Arrow schema for the named asset.
     *
     * @param asset
     *            the asset descriptor identifying the table or object
     * @return the Arrow {@link Schema} describing the asset's fields
     * @throws Exception
     *             if the schema cannot be determined
     */
    Schema getSchema(AssetDescriptor asset) throws Exception;

    /**
     * Creates a source interaction for reading data from the named asset.
     *
     * @param asset
     *            the asset descriptor identifying the table or object to read
     * @param ticket
     *            the Arrow Flight ticket identifying this particular partition
     * @return a new source interaction; caller must close it when done
     * @throws Exception
     *             if the interaction cannot be created
     */
    S getSourceInteraction(AssetDescriptor asset, org.apache.arrow.flight.Ticket ticket) throws Exception;

    /**
     * Creates a target interaction for writing data to the named asset.
     *
     * @param asset
     *            the asset descriptor identifying the table or object to write
     * @return a new target interaction; caller must close it when done
     * @throws Exception
     *             if the interaction cannot be created
     */
    T getTargetInteraction(AssetDescriptor asset) throws Exception;

    /**
     * Creates a discovery interaction for browsing available assets.
     *
     * @param criteria
     *            the criteria scoping the discovery request
     * @return a new discovery interaction; caller must close it when done
     * @throws Exception
     *             if the interaction cannot be created
     */
    D getDiscoveryInteraction(DiscoveryCriteria criteria) throws Exception;
}

// Made with Bob
