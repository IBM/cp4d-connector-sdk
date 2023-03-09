/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.util.List;

import org.apache.arrow.flight.Ticket;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;

/**
 * A connector that returns to the pool when closed.
 */
public class PooledConnector implements Connector<SourceInteraction<?>, TargetInteraction<?>>
{
    private final Connector<?, ?> delegate;
    private final GenericKeyedObjectPool<ConnectorPoolKey, PooledConnector> pool;
    private final ConnectorPoolKey key;

    /**
     * Constructs a pooled connector.
     *
     * @param delegate
     *            the connector to delegate to
     * @param pool
     *            the connector pool
     * @param key
     *            the connector pool key
     */
    public PooledConnector(Connector<?, ?> delegate, GenericKeyedObjectPool<ConnectorPoolKey, PooledConnector> pool, ConnectorPoolKey key)
    {
        this.delegate = delegate;
        this.pool = pool;
        this.key = key;
    }

    /**
     * Called when the connector is to be removed from the pool.
     *
     * @throws Exception
     */
    public void destroy() throws Exception
    {
        delegate.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        pool.returnObject(key, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect() throws Exception
    {
        delegate.connect();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception
    {
        return delegate.discoverAssets(criteria);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema(CustomFlightAssetDescriptor asset) throws Exception
    {
        return delegate.getSchema(asset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceInteraction<?> getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        return delegate.getSourceInteraction(asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TargetInteraction<?> getTargetInteraction(CustomFlightAssetDescriptor asset) throws Exception
    {
        return delegate.getTargetInteraction(asset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration properties) throws Exception
    {
        return delegate.performAction(action, properties);
    }

}
