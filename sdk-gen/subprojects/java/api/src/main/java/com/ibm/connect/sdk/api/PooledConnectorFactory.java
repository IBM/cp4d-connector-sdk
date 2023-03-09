/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.time.Duration;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;

/**
 * An abstract factory for creating connectors or borrowing one from a pool.
 */
public abstract class PooledConnectorFactory extends BaseKeyedPooledObjectFactory<ConnectorPoolKey, PooledConnector>
        implements ConnectorFactory
{
    private final GenericKeyedObjectPool<ConnectorPoolKey, PooledConnector> pool;

    protected PooledConnectorFactory()
    {
        super();

        final GenericKeyedObjectPoolConfig<PooledConnector> config = new GenericKeyedObjectPoolConfig<>();
        config.setMaxIdlePerKey(-1);
        config.setMaxTotalPerKey(-1);
        config.setNumTestsPerEvictionRun(Integer.MAX_VALUE);
        config.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        config.setMinEvictableIdleTime(Duration.ofMinutes(10));
        config.setSoftMinEvictableIdleTime(Duration.ofMinutes(10));
        config.setBlockWhenExhausted(true);
        config.setMaxWait(Duration.ofSeconds(5));

        pool = new GenericKeyedObjectPool<>(this, config);
    }

    /**
     * Creates a connector for the given data source type.
     *
     * @param datasourceTypeName
     *            the name of the data source type
     * @param properties
     *            connection properties
     * @return a connector for the given data source type
     * @throws Exception
     */
    abstract protected Connector<?, ?> createNewConnector(String datasourceTypeName, ConnectionProperties properties);

    /**
     * {@inheritDoc}
     */
    @Override
    public PooledConnector create(ConnectorPoolKey key) throws Exception
    {
        return new PooledConnector(createNewConnector(key.getDatasourceTypeName(), key.getProperties()), pool, key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PooledObject<PooledConnector> wrap(PooledConnector value)
    {
        return new DefaultPooledObject<>(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroyObject(ConnectorPoolKey key, PooledObject<PooledConnector> p) throws Exception
    {
        p.getObject().destroy();
    }

    /**
     * Creates a connector for the given data source type or borrows an existing one
     * from the pool.
     *
     * @param datasourceTypeName
     *            the name of the data source type
     * @param properties
     *            connection properties
     * @return a connector for the given data source type
     * @throws Exception
     */
    @Override
    public Connector<?, ?> createConnector(String datasourceTypeName, ConnectionProperties properties) throws Exception
    {
        return pool.borrowObject(new ConnectorPoolKey(datasourceTypeName, properties));
    }
}
