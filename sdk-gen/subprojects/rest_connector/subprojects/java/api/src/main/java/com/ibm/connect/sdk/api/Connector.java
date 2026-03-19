/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.util.List;

import org.apache.arrow.flight.Ticket;
import org.apache.arrow.vector.types.pojo.Schema;

import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;

/**
 * An interface that connectors should implement.
 *
 * @param <S> a source interaction class
 * @param <T> a target interaction class
 */
public interface Connector<S extends SourceInteraction<?>, T extends TargetInteraction<?>> extends ActionProvider, AutoCloseable
{
    /**
     * Connects to the data source.
     *
     * @throws Exception
     */
    void connect() throws Exception;

    /**
     * Discover assets matching given criteria.
     *
     * @param criteria
     *            assets criteria
     * @return assets matching given criteria
     * @throws Exception
     */
    List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception;

    /**
     * Return the schema for the given asset.
     *
     * @param asset
     *            the asset for which to return the schema
     * @return the schema for the given asset
     * @throws Exception
     */
    Schema getSchema(CustomFlightAssetDescriptor asset) throws Exception;

    /**
     * Creates a source interaction to read data from an asset.
     *
     * @param asset
     *            the asset from which to read data
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @return a source interaction to read data from an asset
     * @throws Exception
     */
    S getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception;

    /**
     * Creates a target interaction to write data to an asset.
     *
     * @param asset
     *            the asset to which to write data
     * @return a target interaction to write data to an asset
     * @throws Exception
     */
    T getTargetInteraction(CustomFlightAssetDescriptor asset) throws Exception;
}
