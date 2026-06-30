/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.util.Collections;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;
import com.ibm.wdp.connect.sdk.connector.SdkConnectorFactory;

/**
 * Convenience base class for Flight producers that use {@link SdkConnectorFactory}.
 *
 * <p>Subclasses implement only {@link #getSdkConnectorFactory()} — the old
 * {@link ConnectorFactory} path is satisfied by a no-op factory whose datasource types
 * are derived from the SDK factory. The SDK connector path is active whenever
 * {@link #getSdkConnectorFactory()} returns non-null.
 *
 * <p>Usage:
 * <pre>
 *   public class MyFlightProducer extends AbstractSdkConnectorFlightProducer {
 *       {@literal @}Override
 *       protected SdkConnectorFactory getSdkConnectorFactory() {
 *           return MyConnectorFactory.getInstance();
 *       }
 *   }
 * </pre>
 */
public abstract class AbstractSdkConnectorFlightProducer extends ConnectorFlightProducer
{
    /**
     * {@inheritDoc}
     *
     * <p>Returns a no-op {@link ConnectorFactory} whose datasource types are derived from
     * the SDK connector factory. The old connector path is never invoked when
     * {@link #getSdkConnectorFactory()} returns non-null.
     */
    @Override
    protected ConnectorFactory getConnectorFactory()
    {
        return new NoOpConnectorFactory(getSdkConnectorFactory());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected abstract SdkConnectorFactory getSdkConnectorFactory();

    // ---- inner class ----

    /**
     * A no-op {@link ConnectorFactory} that exposes the datasource types from the SDK factory
     * but throws {@link UnsupportedOperationException} on {@link #createConnector}.
     *
     * <p>This satisfies the {@link ConnectorFlightProducer} constructor requirement.
     * It is never actually called because the SDK connector path short-circuits all operations
     * when {@code sdkConnectorFactory != null}.
     */
    private static final class NoOpConnectorFactory extends PooledConnectorFactory
    {
        private final SdkConnectorFactory sdkFactory;

        NoOpConnectorFactory(SdkConnectorFactory sdkFactory)
        {
            super();
            this.sdkFactory = sdkFactory;
        }

        @Override
        public CustomFlightDatasourceTypes getDatasourceTypes()
        {
            final CustomFlightDatasourceTypes types = new CustomFlightDatasourceTypes();
            // Return empty list — datasource type listing for SDK producers goes through
            // doAction(ACTION_LIST_DATASOURCE_TYPES) which uses connectorFactory.getDatasourceTypes().
            // SDK producers that want to expose types should override doAction or provide
            // their own CustomFlightDatasourceTypes mapping.
            types.setDatasourceTypes(Collections.emptyList());
            return types;
        }

        @Override
        protected Connector<?, ?> createNewConnector(String datasourceTypeName, ConnectionProperties properties)
        {
            throw new UnsupportedOperationException(
                    "NoOpConnectorFactory.createNewConnector should never be called when sdkConnectorFactory is set. "
                    + "datasourceTypeName=" + datasourceTypeName);
        }
    }
}
