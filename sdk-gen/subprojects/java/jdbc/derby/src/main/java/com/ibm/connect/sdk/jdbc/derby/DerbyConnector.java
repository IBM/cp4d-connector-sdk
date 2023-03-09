/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.derby;

import java.sql.Driver;
import java.util.Set;

import org.apache.arrow.flight.Ticket;

import com.google.common.collect.ImmutableSet;
import com.ibm.connect.sdk.jdbc.JdbcConnector;
import com.ibm.connect.sdk.jdbc.JdbcSourceInteraction;
import com.ibm.connect.sdk.jdbc.JdbcTargetInteraction;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * A connector for connecting to Apache Derby.
 */
public class DerbyConnector extends JdbcConnector
{
    private static final Set<String> SYSTEM_SCHEMAS = ImmutableSet.of("NULLID", "SQLJ");

    /**
     * Creates an Apache Derby connector.
     *
     * @param properties
     *            connection properties
     */
    public DerbyConnector(ConnectionProperties properties)
    {
        super(properties);
        if (connectionProperties.getProperty("host") == null) {
            throw new IllegalArgumentException("Missing host");
        }
        if (connectionProperties.getProperty("port") == null) {
            throw new IllegalArgumentException("Missing port");
        }
        if (connectionProperties.getProperty("database") == null) {
            throw new IllegalArgumentException("Missing database");
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
    protected Driver getDriver() throws Exception
    {
    	try {
    		return (Driver) Class.forName("org.apache.derby.jdbc.ClientDriver").newInstance();
    	}catch (ClassNotFoundException e){
    		return (Driver) Class.forName("org.apache.derby.client.ClientAutoloadedDriver").newInstance();	
    	}
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getConnectionURL()
    {
        final StringBuilder urlBuilder = new StringBuilder(50);
        urlBuilder.append("jdbc:derby://");
        urlBuilder.append(connectionProperties.getProperty("host"));
        urlBuilder.append(':');
        urlBuilder.append(connectionProperties.getProperty("port"));
        urlBuilder.append('/');
        urlBuilder.append(connectionProperties.getProperty("database"));
        final String ssl = connectionProperties.getProperty("ssl", "false");
        if (Boolean.valueOf(ssl)) {
            urlBuilder.append(";ssl=basic");
        }
        final String createDatabase = connectionProperties.getProperty("create_database", "false");
        if (Boolean.valueOf(createDatabase)) {
            urlBuilder.append(";create=true");
        }
        return urlBuilder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isSystemSchema(String schemaName)
    {
        return schemaName.startsWith("SYS") || SYSTEM_SCHEMAS.contains(schemaName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JdbcSourceInteraction getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        return new DerbySourceInteraction(this, asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JdbcTargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset) throws Exception
    {
        return new DerbyTargetInteraction(this, asset);
    }
}
