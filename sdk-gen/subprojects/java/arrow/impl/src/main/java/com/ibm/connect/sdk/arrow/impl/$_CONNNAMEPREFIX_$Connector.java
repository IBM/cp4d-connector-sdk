/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

/* *************************************************** */
package com.ibm.connect.sdk.arrow.impl;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.List;
import java.util.Properties;

import org.apache.arrow.flight.Ticket;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;

/**
 * An Arrow-based connector for connecting to a data source.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class $_CONNNAMEPREFIX_$Connector implements Connector<$_CONNNAMEPREFIX_$SourceInteraction, $_CONNNAMEPREFIX_$TargetInteraction>
{
    private static final Logger LOGGER = getLogger($_CONNNAMEPREFIX_$Connector.class);

    private final Properties connectionProperties;

   /**
     * Creates an Arrow-based connector.
     *
     * @param properties
     *            connection properties
     */
    public $_CONNNAMEPREFIX_$Connector(ConnectionProperties properties)
    {
        connectionProperties = ModelMapper.toProperties(properties);
        if (connectionProperties.getProperty("host") == null) {
            throw new IllegalArgumentException("Missing host");
        }
        if (connectionProperties.getProperty("port") == null) {
            throw new IllegalArgumentException("Missing port");
        }
        if (connectionProperties.getProperty("username") == null) {
            throw new IllegalArgumentException("Missing username");
        }
        if (connectionProperties.getProperty("password") == null) {
            throw new IllegalArgumentException("Missing password");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect() throws Exception
    {
        // TODO Connect to the data source
        LOGGER.info("Connecting");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception
    {
        // TODO Implement asset discovery
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema(CustomFlightAssetDescriptor asset) throws Exception
    {
        // TODO Implement asset schema discovery
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public $_CONNNAMEPREFIX_$SourceInteraction getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        return new $_CONNNAMEPREFIX_$SourceInteraction(this, asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public $_CONNNAMEPREFIX_$TargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset) throws Exception
    {
        return new $_CONNNAMEPREFIX_$TargetInteraction(this, asset);
    }

    @Override
    public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration properties) throws Exception
    {
        if (!"get_record_count".equals(action)) {
            throw new UnsupportedOperationException("doAction " + action + " is not supported");
        }
        // TODO Implement any custom actions
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        // TODO Close any open resources
    }

}
