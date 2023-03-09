/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.generic;

import java.io.Reader;
import java.io.StringReader;
import java.sql.Driver;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.arrow.flight.Ticket;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.ibm.connect.sdk.jdbc.JdbcConnector;
import com.ibm.connect.sdk.jdbc.JdbcSourceInteraction;
import com.ibm.connect.sdk.jdbc.JdbcTargetInteraction;
import com.ibm.connect.sdk.util.Utils;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;

/**
 * A connector for connecting to a generic JDBC data source.
 */
public class GenericJdbcConnector extends JdbcConnector
{
    private static final Map<String, Class<? extends Driver>> DRIVER_CLASS_MAP = new HashMap<>();

    static {
        DRIVER_CLASS_MAP.put("db2", com.ibm.db2.jcc.DB2Driver.class);
        DRIVER_CLASS_MAP.put("derby", getDerbyDriverClass());
        DRIVER_CLASS_MAP.put("informix-sqli", com.informix.jdbc.IfxDriver.class);
        DRIVER_CLASS_MAP.put("mariadb", org.mariadb.jdbc.Driver.class);
        DRIVER_CLASS_MAP.put("mysql", com.mysql.cj.jdbc.Driver.class);
        DRIVER_CLASS_MAP.put("oracle", oracle.jdbc.OracleDriver.class);
        DRIVER_CLASS_MAP.put("postgresql", org.postgresql.Driver.class);
        DRIVER_CLASS_MAP.put("snowflake", net.snowflake.client.jdbc.SnowflakeDriver.class);
        DRIVER_CLASS_MAP.put("sqlserver", com.microsoft.sqlserver.jdbc.SQLServerDriver.class);
    }

    private static final HashBasedTable<String, String, String> LIMIT_CLAUSE_TABLE = HashBasedTable.create();

    static {
        LIMIT_CLAUSE_TABLE.put("db2", "suffix", "FETCH FIRST ${row_limit} ROWS ONLY");
        LIMIT_CLAUSE_TABLE.put("derby", "suffix", "FETCH FIRST ${row_limit} ROWS ONLY");
        LIMIT_CLAUSE_TABLE.put("informix-sqli", "suffix", "LIMIT ${row_limit}");
        LIMIT_CLAUSE_TABLE.put("mariadb", "suffix", "LIMIT ${row_limit}");
        LIMIT_CLAUSE_TABLE.put("mysql", "suffix", "LIMIT ${row_limit}");
        LIMIT_CLAUSE_TABLE.put("oracle", "suffix", "FETCH FIRST ${row_limit} ROWS ONLY");
        LIMIT_CLAUSE_TABLE.put("postgresql", "suffix", "LIMIT ${row_limit}");
        LIMIT_CLAUSE_TABLE.put("snowflake", "suffix", "LIMIT ${row_limit}");
        LIMIT_CLAUSE_TABLE.put("sqlserver", "prefix", "TOP ${row_limit}");
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Driver> getDerbyDriverClass()
    {

        try {
            return (Class<? extends Driver>) Class.forName("org.apache.derby.jdbc.ClientDriver");
        }
        catch (ClassNotFoundException e) {
            try {
                return (Class<? extends Driver>) Class.forName("org.apache.derby.client.ClientAutoloadedDriver");
            }
            catch (ClassNotFoundException e1) {
                return null;
            }
        }
    }

    /**
     * Creates a generic JDBC connector.
     *
     * @param properties
     *            connection properties
     */
    public GenericJdbcConnector(ConnectionProperties properties)
    {
        super(properties);

        // Validate the JDBC URL.
        final String jdbcUrl = connectionProperties.getProperty("jdbc_url");
        if (jdbcUrl == null) {
            throw new IllegalArgumentException("Missing jdbc_url");
        }
        final String driverName = getDriverName(jdbcUrl);
        if (!LIMIT_CLAUSE_TABLE.rowKeySet().contains(driverName)) {
            throw new IllegalArgumentException("Driver [" + driverName + "] is not one of " + LIMIT_CLAUSE_TABLE.rowKeySet());
        }
        final String rowLimitSupport = connectionProperties.getProperty("row_limit_support", "none");
        if ("prefix".equals(rowLimitSupport)) {
            if (connectionProperties.getProperty("row_limit_prefix") == null) {
                throw new IllegalArgumentException("Missing row_limit_prefix");
            }
        } else if ("suffix".equals(rowLimitSupport)) {
            if (connectionProperties.getProperty("row_limit_suffix") == null) {
                throw new IllegalArgumentException("Missing row_limit_suffix");
            }
        }
    }

    static String getDriverName(String jdbcUrl)
    {
        final Pattern pattern = Pattern.compile("jdbc:([^:]+):.*");
        final Matcher matcher = pattern.matcher(jdbcUrl);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid JDBC URL " + jdbcUrl);
        }
        return matcher.group(1);
    }

    static String getRowLimitPrefix(String driverName)
    {
        return LIMIT_CLAUSE_TABLE.get(driverName, "prefix");
    }

    static String getRowLimitSuffix(String driverName)
    {
        return LIMIT_CLAUSE_TABLE.get(driverName, "suffix");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Driver getDriver() throws Exception
    {
        final String jdbcUrl = connectionProperties.getProperty("jdbc_url");
        final String driverName = getDriverName(jdbcUrl);
        final Class<? extends Driver> driverClass = DRIVER_CLASS_MAP.get(driverName);
        return driverClass.newInstance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getConnectionURL()
    {
        return connectionProperties.getProperty("jdbc_url");
    }

    private Properties getJdbcProperties()
    {
        final Properties jdbcProperties = new Properties();
        String propertiesStr = connectionProperties.getProperty("jdbc_properties");
        if (propertiesStr != null) {
            final String truststoreFile = getTruststoreFile();
            if (truststoreFile != null) {
                final Map<String, String> tokens
                        = ImmutableMap.of("truststore_file", truststoreFile, "truststore_password", getTruststorePassword());
                propertiesStr = Utils.substituteTokens(propertiesStr, tokens);
            }
            propertiesStr = propertiesStr.replaceAll("\\\\", "\\\\\\\\");
            try (Reader reader = new StringReader(propertiesStr)) {
                jdbcProperties.load(reader);
            }
            catch (Exception e) {
                throw new IllegalArgumentException("Invalid jdbc_properties", e);
            }
        }
        return jdbcProperties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Properties getDriverConnectionProperties()
    {
        final Properties properties = super.getDriverConnectionProperties();
        properties.putAll(getJdbcProperties());
        return properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JdbcSourceInteraction getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        return new GenericJdbcSourceInteraction(this, asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JdbcTargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset) throws Exception
    {
        return new GenericJdbcTargetInteraction(this, asset);
    }
}
