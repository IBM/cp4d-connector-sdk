/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.derby;

import static org.slf4j.LoggerFactory.getLogger;

import java.sql.CallableStatement;
import java.util.Properties;

import org.slf4j.Logger;

import com.ibm.connect.sdk.jdbc.JdbcTargetInteraction;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * An interaction with an Apache Derby asset as a target.
 */
public class DerbyTargetInteraction extends JdbcTargetInteraction
{
    private static final Logger LOGGER = getLogger(DerbyTargetInteraction.class);

    private final DerbyConnector connector;
    private final CustomFlightAssetDescriptor asset;
    private final Properties interactionProperties;
    private final String schemaName;
    private final String tableName;

    /**
     * Creates an Apache Derby target interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset to which to write
     * @throws Exception
     */
    public DerbyTargetInteraction(DerbyConnector connector, CustomFlightAssetDescriptor asset) throws Exception
    {
        super(connector, asset);
        this.connector = connector;
        this.asset = asset;
        interactionProperties = ModelMapper.toProperties(asset.getInteractionProperties());
        schemaName = interactionProperties.getProperty("schema_name");
        tableName = interactionProperties.getProperty("table_name");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightAssetDescriptor putWrapup() throws Exception
    {
        final String updateStatistics = interactionProperties.getProperty("update_statistics", "false");
        if (Boolean.valueOf(updateStatistics)) {
            if (schemaName == null) {
                throw new IllegalArgumentException("Missing schema name");
            }
            if (tableName == null) {
                throw new IllegalArgumentException("Missing table name");
            }
            final StringBuilder stmt = new StringBuilder(200);
            stmt.append("CALL SYSCS_UTIL.SYSCS_UPDATE_STATISTICS('");
            stmt.append(schemaName);
            stmt.append("','");
            stmt.append(tableName);
            stmt.append("', null)");
            final String stmtText = stmt.toString();
            LOGGER.info(stmtText);
            try (CallableStatement callStmt = connector.getConnection().prepareCall(stmtText)) {
                callStmt.execute();
            }
        }
        return asset;
    }
}
