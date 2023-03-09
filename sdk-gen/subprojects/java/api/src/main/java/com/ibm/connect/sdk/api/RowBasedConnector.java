/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.util.Collections;
import java.util.Properties;

import org.apache.arrow.vector.types.pojo.Schema;

import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An abstract row-based connector.
 *
 * @param <S>
 *            a row-based source interaction class
 * @param <T>
 *            a row-based target interaction class
 */
public abstract class RowBasedConnector<S extends RowBasedSourceInteraction<?>, T extends RowBasedTargetInteraction<?>>
        implements Connector<S, T>
{
    private final Properties connectionProperties;

    /**
     * Creates a row-based connector.
     *
     * @param properties
     *            connection properties
     */
    public RowBasedConnector(ConnectionProperties properties)
    {
        this.connectionProperties = ModelMapper.toProperties(properties);
    }

    /**
     * Returns the connection properties.
     *
     * @return the connection properties
     */
    public Properties getConnectionProperties()
    {
        return connectionProperties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema(CustomFlightAssetDescriptor asset) throws Exception
    {
        final Schema schema;
        if (asset.getFields() == null) {
            schema = new Schema(Collections.emptyList());
        } else {
            schema = ArrowConversions.toArrow(asset.getFields());
        }
        return schema;
    }

    /**
     * An optional method to commit the rows. This method can be called multiple
     * times in the course of data transfer. The connector may choose not to
     * implement this method or to implement it as a no-op, such as when the data
     * source does not support transactions.
     */
    public abstract void commit();
}
