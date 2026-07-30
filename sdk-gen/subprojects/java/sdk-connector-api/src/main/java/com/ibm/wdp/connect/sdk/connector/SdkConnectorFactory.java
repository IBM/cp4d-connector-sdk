/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import org.slf4j.Logger;

/**
 * Factory for creating {@link SdkConnector} instances.
 *
 * <p>Implementations are discovered at runtime via {@code java.util.ServiceLoader}. Register
 * a factory by placing its fully-qualified class name in
 * {@code META-INF/services/com.ibm.wdp.connect.sdk.connector.SdkConnectorFactory}.
 *
 * <p>Example:
 * <pre>
 *   public class MyConnectorFactory implements SdkConnectorFactory {
 *       {@literal @}Override
 *       public SdkDatasourceTypes getDatasourceTypes() {
 *           return new SdkDatasourceTypes(Collections.singletonList("my_connector"));
 *       }
 *       {@literal @}Override
 *       public SdkConnector&lt;?, ?, ?&gt; createConnector(String datasourceTypeName,
 *                                                       ConnectionProperties props) {
 *           return new MyConnector(props);
 *       }
 *   }
 * </pre>
 */
public interface SdkConnectorFactory
{
    /**
     * Returns the datasource type names handled by this factory.
     *
     * @return the supported datasource type names
     */
    SdkDatasourceTypes getDatasourceTypes();

    /**
     * Creates a new connector for the specified datasource type and connection properties.
     *
     * @param datasourceTypeName
     *            the datasource type name
     * @param properties
     *            the connection properties provided by the user
     * @return a new connector instance; caller is responsible for closing it
     * @throws Exception
     *             if the connector cannot be created
     */
    SdkConnector<?, ?, ?> createConnector(String datasourceTypeName, ConnectionProperties properties) throws Exception;

    /**
     * Passes the Logger object to this connector factory.
     *
     * @param logger
     *            Logger object.
     */
    void setLogger(Logger logger);

    /**
     * Retrieves the Logger object for this connector factory.
     *
     * @return Logger object.
     */
    Logger getLogger();
}
