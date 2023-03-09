/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test.jdbc.derby;

import static org.slf4j.LoggerFactory.getLogger;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.derby.drda.NetworkServerControl;
import org.slf4j.Logger;

import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;

/**
 * Utility methods for Apache Derby.
 */
public class DerbyUtils
{
    private static final Logger LOGGER = getLogger(DerbyUtils.class);

    private static final int MAX_STARTUP_RETRIES = 60;

    /**
     * Starts a network Derby server.
     *
     * @param address
     *            The IP address of the server host
     * @param port
     *            The server port number
     * @param username
     *            The user name for actions requiring authorization
     * @param password
     *            The password for actions requiring authorization
     * @return a started network Derby server
     * @throws Exception
     */
    public static NetworkServerControl startServer(InetAddress address, int port, String username, String password) throws Exception
    {
        final NetworkServerControl server = new NetworkServerControl(address, port, username, password);
        server.start(null);

        // Wait for the server to start up
        for (int i = 0; i < MAX_STARTUP_RETRIES; i++) {
            try {
                server.ping();
                break;
            }
            catch (final Exception e) {
                LOGGER.warn(e.getMessage(), e);
            }
            try {
                Thread.sleep(1000);
            }
            catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }
        LOGGER.info("Derby server started at " + address + ':' + port);
        return server;
    }

    /**
     * Creates a JDBC connection to Apache Derby.
     *
     * @param properties
     *            connection properties
     * @return a JDBC connection
     * @throws SQLException
     */
    public static Connection createConnection(ConnectionProperties properties) throws SQLException
    {
        final Properties connectionProperties = ModelMapper.toProperties(properties);
        final String url = getConnectionURL(properties);
        final Properties credentials = new Properties();
        credentials.setProperty("user", connectionProperties.getProperty("username"));
        credentials.setProperty("password", connectionProperties.getProperty("password"));
        return DriverManager.getConnection(url, credentials);
    }

    /**
     * Creates a JDBC connection URL to Apache Derby.
     *
     * @param properties
     *            connection properties
     * @return a JDBC connection URL
     */
    public static String getConnectionURL(ConnectionProperties properties)
    {
        final Properties connectionProperties = ModelMapper.toProperties(properties);
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
        final String createDatabase = connectionProperties.getProperty("create_database");
        if (createDatabase != null && Boolean.valueOf(createDatabase)) {
            urlBuilder.append(";create=true");
        }
        return urlBuilder.toString();
    }
}
