/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.api;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.ActionType;
import org.apache.arrow.flight.BackpressureStrategy;
import org.apache.arrow.flight.BackpressureStrategy.CallbackBackpressureStrategy;
import org.apache.arrow.flight.BackpressureStrategy.WaitResult;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightProducer;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.Location;
import org.apache.arrow.flight.PutResult;
import org.apache.arrow.flight.Result;
import org.apache.arrow.flight.Ticket;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.VectorLoader;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.VectorUnloader;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;

import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.connect.sdk.util.Utils;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeAction;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionRequest;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * An abstract Flight producer for connectors.
 */
public abstract class ConnectorFlightProducer implements FlightProducer
{
    private static final Logger LOGGER = getLogger(ConnectorFlightProducer.class);

    private static final String UNKNOWN_VERSION = "unknown";

    /**
     * Action type to check the health of the service and return its version.
     */
    private static final String ACTION_HEALTH_CHECK = "health_check";
    private static final String ACTION_HEALTH_CHECK_DESCRIPTION = "Check the health of the service and return its version";

    /**
     * Action type to list data source types supported by this Flight producer.
     */
    private static final String ACTION_LIST_DATASOURCE_TYPES = "list_datasource_types";
    private static final String ACTION_LIST_DATASOURCE_TYPES_DESCRIPTION = "List data source types supported by this Flight producer";

    /**
     * Action type to perform any setup required before a partitioned write such as
     * creating the target table.
     */
    private static final String ACTION_PUT_SETUP = "put_setup";
    private static final String ACTION_PUT_SETUP_DESCRIPTION = "Perform any setup required before a partitioned write";

    /**
     * Action type to perform any wrap-up required after a partitioned write such as
     * updating table statistics.
     */
    private static final String ACTION_PUT_WRAPUP = "put_wrapup";
    private static final String ACTION_PUT_WRAPUP_DESCRIPTION = "Perform any wrap-up required after a partitioned write";

    /**
     * Action type to test a connection to a custom data source type.
     */
    private static final String ACTION_TEST = "test";
    private static final String ACTION_TEST_DESCRIPTION = "Test a connection to a custom data source type";

    /**
     * Action type to validate connection properties for a custom data source type.
     */
    private static final String ACTION_VALIDATE = "validate";
    private static final String ACTION_VALIDATE_DESCRIPTION = "Validate the connection properties for a custom data source type";

    /**
     * The data source types supported by this Flight producer.
     */
    private final CustomFlightDatasourceTypes datasourceTypes;

    /**
     * A factory for creating connectors.
     */
    private final ConnectorFactory connectorFactory;

    private final ModelMapper modelMapper;
    private final FlightDescriptorCache descriptorCache;
    private final BufferAllocator rootAllocator;

    /**
     * Constructs a flight producer for connectors.
     */
    public ConnectorFlightProducer()
    {
        connectorFactory = getConnectorFactory();
        datasourceTypes = connectorFactory.getDatasourceTypes();
        modelMapper = new ModelMapper();
        descriptorCache = new FlightDescriptorCache();
        rootAllocator = new RootAllocator();
    }

    /**
     * Returns a factory for creating connectors.
     *
     * @return a factory for creating connectors
     */
    abstract protected ConnectorFactory getConnectorFactory();

    /**
     * {@inheritDoc}
     */
    @Override
    public void getStream(CallContext context, Ticket ticket, ServerStreamListener listener)
    {
        final BackpressureStrategy bpStrategy = new CallbackBackpressureStrategy();
        bpStrategy.register(listener);
        try {
            final FlightDescriptor descriptor = descriptorCache.get(ticket);
            if (descriptor == null) {
                throw new IllegalArgumentException("No flight descriptor available for the given ticket");
            }
            final CustomFlightAssetDescriptor asset = modelMapper.fromBytes(descriptor.getCommand(), CustomFlightAssetDescriptor.class);
            try (Connector<?, ?> connector
                    = connectorFactory.createConnector(asset.getDatasourceTypeName(), asset.getConnectionProperties())) {
                connector.connect();
                try (SourceInteraction<?> interaction = connector.getSourceInteraction(asset, ticket)) {
                    final Schema schema = interaction.getSchema();
                    try (VectorSchemaRoot vectorSchemaRoot = VectorSchemaRoot.create(schema, rootAllocator)) {
                        final VectorLoader loader = new VectorLoader(vectorSchemaRoot);
                        listener.start(vectorSchemaRoot);
                        interaction.beginStream(rootAllocator);
                        while (interaction.hasNextBatch()) {
                            try (VectorSchemaRoot batch = interaction.nextBatch()) {
                                if (batch.getRowCount() == 0) {
                                    break;
                                }
                                final VectorUnloader unloader = new VectorUnloader(batch);
                                loader.load(unloader.getRecordBatch());
                            }
                            WaitResult wr;
                            while ((wr = bpStrategy.waitForListener(5000)) == WaitResult.TIMEOUT) {
                                LOGGER.info("Waiting for ready from client");
                            }
                            if (wr == WaitResult.CANCELLED) {
                                break;
                            }
                            listener.putNext();
                            vectorSchemaRoot.clear();
                        }
                        if (listener.isCancelled()) {
                            LOGGER.info("Stream has been cancelled");
                            listener.error(CallStatus.CANCELLED.withDescription("Stream cancelled.").toRuntimeException());
                        } else {
                            listener.completed();
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            listener.error(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void listFlights(CallContext context, Criteria criteria, StreamListener<FlightInfo> listener)
    {
        try {
            if (Criteria.ALL.equals(criteria) || criteria.getExpression().length == 0) {
                throw new IllegalArgumentException("Invalid criteria");
            }
            final CustomFlightAssetsCriteria assetsCriteria
                    = modelMapper.fromBytes(criteria.getExpression(), CustomFlightAssetsCriteria.class);
            try (Connector<?, ?> connector
                    = connectorFactory.createConnector(assetsCriteria.getDatasourceTypeName(), assetsCriteria.getConnectionProperties())) {
                connector.connect();
                final List<CustomFlightAssetDescriptor> assets = connector.discoverAssets(assetsCriteria);
                for (final CustomFlightAssetDescriptor asset : assets) {
                    completeAsset(asset);
                    final FlightDescriptor descriptor = FlightDescriptor.command(modelMapper.toBytes(asset));
                    final Schema schema = connector.getSchema(asset);
                    final FlightInfo flightInfo = createFlightInfo(descriptor, schema, Collections.emptyList());
                    listener.onNext(flightInfo);
                }
            }
            listener.onCompleted();
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            listener.onError(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
    }

    private void completeAsset(CustomFlightAssetDescriptor asset)
    {
        // An asset must have an id or a name. An id takes precedence for the path.
        final String id = asset.getId() != null ? asset.getId() : asset.getName();
        if (id == null) {
            throw new IllegalArgumentException("Missing asset id");
        }
        if (asset.getId() == null) {
            asset.setId(id);
        }
        if (asset.getName() == null) {
            asset.setName(id);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlightInfo getFlightInfo(CallContext context, FlightDescriptor descriptor)
    {
        try {
            final CustomFlightAssetDescriptor asset = modelMapper.fromBytes(descriptor.getCommand(), CustomFlightAssetDescriptor.class);
            try (Connector<?, ?> connector
                    = connectorFactory.createConnector(asset.getDatasourceTypeName(), asset.getConnectionProperties())) {
                connector.connect();
                try (SourceInteraction<?> interaction = connector.getSourceInteraction(asset, null)) {
                    final Schema schema = interaction.getSchema();
                    asset.setFields(Utils.getAssetFields(schema));
                    final List<Ticket> tickets = interaction.getTickets();
                    return createFlightInfo(FlightDescriptor.command(modelMapper.toBytes(asset)), schema, tickets);
                }
            }
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException();
        }
    }

    private FlightInfo createFlightInfo(FlightDescriptor descriptor, Schema schema, List<Ticket> tickets) throws Exception
    {
        if (!tickets.isEmpty()) {
            descriptorCache.put(tickets.get(0), descriptor);
        }
        final List<FlightEndpoint> endpoints = new ArrayList<>(tickets.size());
        for (final Ticket ticket : tickets) {
            endpoints.add(new FlightEndpoint(ticket, new Location[0]));
        }
        return new FlightInfo(schema, descriptor, endpoints, -1, -1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Runnable acceptPut(CallContext context, FlightStream flightStream, StreamListener<PutResult> ackStream)
    {
        return () -> {
            try {
                final CustomFlightAssetDescriptor asset
                        = modelMapper.fromBytes(flightStream.getDescriptor().getCommand(), CustomFlightAssetDescriptor.class);
                if (asset.getPartitionCount() != null && asset.getPartitionCount() > 1 && asset.getPartitionIndex() == null) {
                    throw new IllegalArgumentException("Missing partition index");
                }
                asset.setFields(Utils.getAssetFields(flightStream.getSchema()));
                try (Connector<?, ?> connector
                        = connectorFactory.createConnector(asset.getDatasourceTypeName(), asset.getConnectionProperties())) {
                    connector.connect();
                    try (TargetInteraction<?> interaction = connector.getTargetInteraction(asset)) {
                        if (asset.getPartitionCount() == null || asset.getPartitionCount() == 1) {
                            interaction.putSetup();
                        }
                        interaction.putStream(flightStream);
                        if (asset.getPartitionCount() == null || asset.getPartitionCount() == 1) {
                            interaction.putWrapup();
                        }
                    }
                }
                ackStream.onCompleted();
            }
            catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
                ackStream.onError(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doAction(CallContext context, Action action, StreamListener<Result> listener)
    {
        try {
            final CustomFlightActionResponse response = new CustomFlightActionResponse();
            if (ACTION_HEALTH_CHECK.equals(action.getType())) {
                final ConnectionActionResponse responseProperties = new ConnectionActionResponse();
                response.setResponseProperties(responseProperties);
                String version;
                try {
                    version = this.getClass().getPackage().getImplementationVersion();
                    if (version == null) {
                        version = UNKNOWN_VERSION;
                    }
                }
                catch (final Exception e) {
                    version = UNKNOWN_VERSION;
                }
                responseProperties.put("version", version);
                responseProperties.put("status", "OK");
            } else if (ACTION_LIST_DATASOURCE_TYPES.equals(action.getType())) {
                response.setDatasourceTypes(datasourceTypes);
            } else if (ACTION_PUT_SETUP.equals(action.getType())) {
                if (action.getBody() == null || action.getBody().length == 0) {
                    throw new IllegalArgumentException("Missing action body");
                }
                final CustomFlightActionRequest request = modelMapper.fromBytes(action.getBody(), CustomFlightActionRequest.class);
                final CustomFlightAssetDescriptor asset = request.getAsset();
                if (asset == null) {
                    throw new IllegalArgumentException("Missing asset");
                }
                try (Connector<?, ?> connector
                        = connectorFactory.createConnector(asset.getDatasourceTypeName(), asset.getConnectionProperties())) {
                    connector.connect();
                    try (TargetInteraction<?> interaction = connector.getTargetInteraction(asset)) {
                        response.setAsset(interaction.putSetup());
                    }
                }
            } else if (ACTION_PUT_WRAPUP.equals(action.getType())) {
                if (action.getBody() == null || action.getBody().length == 0) {
                    throw new IllegalArgumentException("Missing action body");
                }
                final CustomFlightActionRequest request = modelMapper.fromBytes(action.getBody(), CustomFlightActionRequest.class);
                final CustomFlightAssetDescriptor asset = request.getAsset();
                if (asset == null) {
                    throw new IllegalArgumentException("Missing asset");
                }
                try (Connector<?, ?> connector
                        = connectorFactory.createConnector(asset.getDatasourceTypeName(), asset.getConnectionProperties())) {
                    connector.connect();
                    try (TargetInteraction<?> interaction = connector.getTargetInteraction(asset)) {
                        response.setAsset(interaction.putWrapup());
                    }
                }
            } else if (ACTION_TEST.equals(action.getType())) {
                if (action.getBody() == null || action.getBody().length == 0) {
                    throw new IllegalArgumentException("Missing action body");
                }
                final CustomFlightActionRequest request = modelMapper.fromBytes(action.getBody(), CustomFlightActionRequest.class);
                try (Connector<?, ?> connector
                        = connectorFactory.createConnector(request.getDatasourceTypeName(), request.getConnectionProperties())) {
                    connector.connect();
                }
            } else if (ACTION_VALIDATE.equals(action.getType())) {
                if (action.getBody() == null || action.getBody().length == 0) {
                    throw new IllegalArgumentException("Missing action body");
                }
                final CustomFlightActionRequest request = modelMapper.fromBytes(action.getBody(), CustomFlightActionRequest.class);
                try (Connector<?, ?> connector
                        = connectorFactory.createConnector(request.getDatasourceTypeName(), request.getConnectionProperties())) {
                    // No action required.
                }
            } else {
                if (action.getBody() == null || action.getBody().length == 0) {
                    throw new IllegalArgumentException("Missing action body");
                }
                final CustomFlightActionRequest request = modelMapper.fromBytes(action.getBody(), CustomFlightActionRequest.class);
                try (Connector<?, ?> connector
                        = connectorFactory.createConnector(request.getDatasourceTypeName(), request.getConnectionProperties())) {
                    connector.connect();
                    response.setResponseProperties(connector.performAction(action.getType(), request.getRequestProperties()));
                }
            }
            final Result result = new Result(modelMapper.toBytes(response));
            listener.onNext(result);
            listener.onCompleted();
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            listener.onError(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void listActions(CallContext context, StreamListener<ActionType> listener)
    {
        try {
            final Map<String, String> actions = new TreeMap<>();
            actions.put(ACTION_HEALTH_CHECK, ACTION_HEALTH_CHECK_DESCRIPTION);
            actions.put(ACTION_LIST_DATASOURCE_TYPES, ACTION_LIST_DATASOURCE_TYPES_DESCRIPTION);
            actions.put(ACTION_PUT_SETUP, ACTION_PUT_SETUP_DESCRIPTION);
            actions.put(ACTION_PUT_WRAPUP, ACTION_PUT_WRAPUP_DESCRIPTION);
            actions.put(ACTION_TEST, ACTION_TEST_DESCRIPTION);
            actions.put(ACTION_VALIDATE, ACTION_VALIDATE_DESCRIPTION);
            for (final CustomFlightDatasourceType datasourceType : datasourceTypes.getDatasourceTypes()) {
                if (datasourceType.getActions() != null) {
                    for (final CustomDatasourceTypeAction action : datasourceType.getActions()) {
                        actions.put(action.getName(), action.getDescription());
                    }
                }
            }
            for (final Map.Entry<String, String> action : actions.entrySet()) {
                listener.onNext(new ActionType(action.getKey(), action.getValue()));
            }
            listener.onCompleted();
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            listener.onError(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
    }

}
