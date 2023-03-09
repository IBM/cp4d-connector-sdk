/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.flight;

import static org.slf4j.LoggerFactory.getLogger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.ActionType;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightProducer;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.PutResult;
import org.apache.arrow.flight.Result;
import org.apache.arrow.flight.Ticket;
import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeAction;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionRequest;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * A Flight producer that delegates to a connector Flight producer.
 */
public class DelegatingFlightProducer implements FlightProducer
{
    private static final Logger LOGGER = getLogger(DelegatingFlightProducer.class);

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

    private final Cache<Ticket, FlightProducer> producerCache
            = CacheBuilder.newBuilder().maximumSize(100).expireAfterWrite(10, TimeUnit.MINUTES).build();
    private final ObjectMapper mapper = new ObjectMapper().configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final CustomFlightDatasourceTypes datasourceTypes = new CustomFlightDatasourceTypes();
    private final Map<String, FlightProducer> datasourceTypeIdToProducer = new HashMap<>();

    private class ResultListener<T> implements StreamListener<T>
    {
        private List<T> results = new LinkedList<>();
        private boolean done;

        @Override
        public void onNext(T val)
        {
            results.add(val);
        }

        @Override
        public void onError(Throwable t)
        {
            throw new RuntimeException(t);

        }

        @Override
        public synchronized void onCompleted()
        {
            done = true;
            this.notifyAll();
        }

        public synchronized List<T> getResults() throws InterruptedException
        {
            if (!done) {
                this.wait();
            }
            return results;
        }

    }

    /**
     * Constructs a delegating Flight producer.
     */
    public DelegatingFlightProducer()
    {
        for (final FlightProducer producer : ServiceLoader.load(FlightProducer.class)) {
            final ResultListener<Result> listener = new ResultListener<>();
            producer.doAction(null, new Action("list_datasource_types"), listener);
            try {
                for (final Result result : listener.getResults()) {
                    final CustomFlightActionResponse response
                            = mapper.readValue(new String(result.getBody(), StandardCharsets.UTF_8), CustomFlightActionResponse.class);
                    final CustomFlightDatasourceTypes resultDatasourceTypes = response.getDatasourceTypes();
                    for (final CustomFlightDatasourceType type : resultDatasourceTypes.getDatasourceTypes()) {
                        if (datasourceTypeIdToProducer.get(type.getName()) == null) {
                            datasourceTypes.addDatasourceTypesItem(type);
                            datasourceTypeIdToProducer.put(type.getName(), producer);
                        }
                    }
                }
            }
            catch (InterruptedException | JsonProcessingException e) {
                // shouldn't occur but just in case
                throw new RuntimeException(e);
            }
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void getStream(CallContext context, Ticket ticket, ServerStreamListener listener)
    {
        LOGGER.trace("getStream entry");
        try {
            final FlightProducer producer = producerCache.getIfPresent(ticket);
            producer.getStream(context, ticket, listener);
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            listener.error(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
        finally {
            LOGGER.trace("getStream exit");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void listFlights(CallContext context, Criteria criteria, StreamListener<FlightInfo> listener)
    {
        LOGGER.trace("listFlights entry");
        try {
            if (Criteria.ALL.equals(criteria) || criteria.getExpression().length == 0) {
                throw new IllegalArgumentException("Invalid criteria");
            }
            final FlightProducer producer = getProducerFromCriteria(criteria.getExpression());
            producer.listFlights(context, criteria, listener);
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            listener.onError(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
        finally {
            LOGGER.trace("listFlights exit");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlightInfo getFlightInfo(CallContext context, FlightDescriptor descriptor)
    {
        LOGGER.trace("getFlightInfo entry");
        try {
            final FlightProducer producer = getProducerFromCommand(descriptor.getCommand());
            final FlightInfo result = producer.getFlightInfo(context, descriptor);
            for (final Ticket ticket : result.getEndpoints().stream().map(endpoint -> endpoint.getTicket()).collect(Collectors.toList())) {
                producerCache.put(ticket, producer);
            }
            return result;
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException();
        }
        finally {
            LOGGER.trace("getFlightInfo exit");
        }
    }

    FlightProducer getProducerFromCommand(byte[] command) throws Exception
    {
        final CustomFlightAssetDescriptor asset
                = mapper.readValue(new String(command, StandardCharsets.UTF_8), CustomFlightAssetDescriptor.class);
        return datasourceTypeIdToProducer.get(asset.getDatasourceTypeName());
    }

    FlightProducer getProducerFromAction(byte[] command) throws Exception
    {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Missing action body");
        }
        final CustomFlightActionRequest action
                = mapper.readValue(new String(command, StandardCharsets.UTF_8), CustomFlightActionRequest.class);
        final String datasourceTypeName = action.getAsset() != null && action.getAsset().getDatasourceTypeName() != null
                ? action.getAsset().getDatasourceTypeName() : action.getDatasourceTypeName();
        if (datasourceTypeName == null) {
            throw new IllegalArgumentException("Missing datasource type name");
        }
        return datasourceTypeIdToProducer.get(datasourceTypeName);
    }

    FlightProducer getProducerFromCriteria(byte[] criteria) throws Exception
    {
        final CustomFlightAssetsCriteria assetsCriteria
                = mapper.readValue(new String(criteria, StandardCharsets.UTF_8), CustomFlightAssetsCriteria.class);
        return datasourceTypeIdToProducer.get(assetsCriteria.getDatasourceTypeName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Runnable acceptPut(CallContext context, FlightStream flightStream, StreamListener<PutResult> ackStream)
    {
        LOGGER.trace("acceptPut entry");
        try {
            return getProducerFromCommand(flightStream.getDescriptor().getCommand()).acceptPut(context, flightStream, ackStream);
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            ackStream.onError(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
            throw CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException();
        }
        finally {
            LOGGER.trace("acceptPut exit");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doAction(CallContext context, Action action, StreamListener<Result> listener)
    {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("doAction entry, action=" + action.getType());
        }
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
                Result result;

                result = new Result(mapper.writeValueAsString(response).getBytes(StandardCharsets.UTF_8));

                listener.onNext(result);
                listener.onCompleted();
            } else if (ACTION_LIST_DATASOURCE_TYPES.equals(action.getType())) {
                response.setDatasourceTypes(datasourceTypes);
                final Result result = new Result(mapper.writeValueAsString(response).getBytes(StandardCharsets.UTF_8));
                listener.onNext(result);
                listener.onCompleted();
            } else {
                final FlightProducer producer = getProducerFromAction(action.getBody());
                producer.doAction(context, action, listener);
            }
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            listener.onError(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
        finally {
            LOGGER.trace("doAction exit");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void listActions(CallContext context, StreamListener<ActionType> listener)
    {
        LOGGER.trace("listActions entry");
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
        finally {
            LOGGER.trace("listActions exit");
        }
    }

}
