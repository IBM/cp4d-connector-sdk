package com.ibm.wdp.connect.sdk;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.ActionType;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightProducer;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.PutResult;
import org.apache.arrow.flight.Result;
import org.apache.arrow.flight.Ticket;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ibm.connect.sdk.util.ThreadLocale;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypeProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

public class MockProducer implements FlightProducer
{
    public static final String ACTION_LIST_DATASOURCE_TYPES = "list_datasource_types";
    public static final String DATASOURCE_NAME = "mock";
    private final ObjectMapper mapper = new ObjectMapper().configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private CustomFlightDatasourceTypes getDatasourceTypes()
    {
        final CustomFlightDatasourceTypes datasourceTypes = new CustomFlightDatasourceTypes();

        // Define the data source types supported by this flight producer.
        final CustomFlightDatasourceType datasourceType = new CustomFlightDatasourceType();
        datasourceTypes.addDatasourceTypesItem(datasourceType);
        datasourceType.setName(DATASOURCE_NAME);
        datasourceType.setLabel(MockLabels.DATASOURCE_TYPE_LABEL.format());
        datasourceType.setDescription(MockLabels.DATASOURCE_TYPE_DESCRIPTION.format());
        datasourceType.setAllowedAsSource(true);
        datasourceType.setAllowedAsTarget(true);
        datasourceType.setStatus(CustomFlightDatasourceType.StatusEnum.ACTIVE);
        datasourceType.setTags(Collections.emptyList());
        final CustomFlightDatasourceTypeProperties properties = new CustomFlightDatasourceTypeProperties();
        datasourceType.setProperties(properties);

        // Connection properties - NA

        // Source interaction properties
        properties.addSourceItem(buildCustomPropertyDef("file_name", MockLabels.SOURCE_FILE_NAME_LABEL.format(), MockLabels.SOURCE_FILE_NAME_DESCRIPTION.format()));

        // Target interaction properties
        properties.addTargetItem(buildCustomPropertyDef("file_name", MockLabels.TARGET_FILE_NAME_LABEL.format(), MockLabels.TARGET_FILE_NAME_DESCRIPTION.format()));
        
        return datasourceTypes;
    }

    private CustomDatasourceTypeProperty buildCustomPropertyDef(String name, String label, String description)
    {
        return new CustomDatasourceTypeProperty().name(name).label(label).description(description)
                .type(CustomDatasourceTypeProperty.TypeEnum.STRING);
    }

    @Override
    public void getStream(CallContext context, Ticket ticket, ServerStreamListener listener)
    {
        // no-op

    }

    @Override
    public void listFlights(CallContext context, Criteria criteria, StreamListener<FlightInfo> listener)
    {
        // no-op

    }

    @Override
    public FlightInfo getFlightInfo(CallContext context, FlightDescriptor descriptor)
    {
        ThreadLocale.setLocale(context);
        final Ticket ticket = new Ticket("whatever".getBytes(StandardCharsets.UTF_8));
        final Schema schema = createDefault();
        return new FlightInfo(schema, descriptor, Collections.singletonList(new FlightEndpoint(ticket)), -1, -1);
    }

    public static Schema createDefault()
    {
        final Field strField = new Field("col1", FieldType.nullable(new ArrowType.Utf8()), null);
        final Field intField = new Field("col2", FieldType.nullable(new ArrowType.Int(32, true)), null);
        return new Schema(Arrays.asList(strField, intField));
    }

    @Override
    public Runnable acceptPut(CallContext context, FlightStream flightStream, StreamListener<PutResult> ackStream)
    {
        // no-op
        return null;
    }

    @Override
    public void doAction(CallContext context, Action action, StreamListener<Result> listener)
    {
        try {
            ThreadLocale.setLocale(context);
            final CustomFlightActionResponse response = new CustomFlightActionResponse();
            if (ACTION_LIST_DATASOURCE_TYPES.equals(action.getType())) {
                response.setDatasourceTypes(getDatasourceTypes());
            } else {
                throw new UnsupportedOperationException(MockMsgs.UNSUPPORTED_ACTION.format(action.getType()));
            }
            final String responseJson = mapper.writeValueAsString(response);
            final Result result = new Result(responseJson.getBytes(StandardCharsets.UTF_8));
            listener.onNext(result);
            listener.onCompleted();
        }
        catch (Exception e) {
            listener.onError(CallStatus.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e).toRuntimeException());
        }
    }

    @Override
    public void listActions(CallContext context, StreamListener<ActionType> listener)
    {
        ThreadLocale.setLocale(context);
        listener.onNext(new ActionType(ACTION_LIST_DATASOURCE_TYPES, ACTION_LIST_DATASOURCE_TYPES));
        listener.onCompleted();

    }

}
