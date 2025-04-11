/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

/* *************************************************** */
package com.ibm.connect.sdk.jdbc.impl;

import java.sql.Driver;

import org.apache.arrow.flight.Ticket;

import com.ibm.connect.sdk.jdbc.JdbcConnector;
import com.ibm.connect.sdk.jdbc.JdbcSourceInteraction;
import com.ibm.connect.sdk.jdbc.JdbcTargetInteraction;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * A connector for connecting to a data source using JDBC.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class $_CONNNAMEPREFIX_$Connector extends JdbcConnector
{
    // TODO: Set the JDBC driver class name.
    private static final String DRIVER_CLASS_NAME = "";

    /**
     * Creates a JDBC-based connector.
     *
     * @param properties
     *            connection properties
     */
    public $_CONNNAMEPREFIX_$Connector(ConnectionProperties properties)
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
        return (Driver) Class.forName(DRIVER_CLASS_NAME).getDeclaredConstructor().newInstance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getConnectionURL()
    {
        final StringBuilder urlBuilder = new StringBuilder(50);
        // TODO: Set the JDBC connection URL.
        urlBuilder.append("jdbc:drivername://");
        urlBuilder.append(connectionProperties.getProperty("host"));
        urlBuilder.append(':');
        urlBuilder.append(connectionProperties.getProperty("port"));
        urlBuilder.append('/');
        urlBuilder.append(connectionProperties.getProperty("database"));
        return urlBuilder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JdbcSourceInteraction getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        return new $_CONNNAMEPREFIX_$SourceInteraction(this, asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JdbcTargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset) throws Exception
    {
        return new $_CONNNAMEPREFIX_$TargetInteraction(this, asset);
    }

}
