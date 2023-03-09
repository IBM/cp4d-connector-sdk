/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.ActionType;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.Result;
import org.junit.Test;

import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionRequest;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;

/**
 * An abstract class for a connector test suite.
 */
public abstract class ConnectorTestSuite extends FlightTestSuite
{
    private static ModelMapper modelMapper = new ModelMapper();

    protected abstract String getDatasourceTypeName();

    protected abstract ConnectionProperties createConnectionProperties();

    /**
     * Test listFlights with invalid criteria.
     */
    @Test
    public void testListFlightsInvalidCriteria()
    {
        try {
            getClient().listFlights(Criteria.ALL).iterator().hasNext();
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("Invalid criteria"));
        }
    }

    /**
     * Test listActions.
     */
    @Test
    public void testListActions()
    {
        final List<String> actionTypes = StreamSupport.stream(getClient().listActions().spliterator(), false).map(ActionType::getType)
                .collect(Collectors.toList());
        assertTrue(actionTypes.contains("health_check"));
        assertTrue(actionTypes.contains("list_datasource_types"));
        assertTrue(actionTypes.contains("put_setup"));
        assertTrue(actionTypes.contains("put_wrapup"));
        assertTrue(actionTypes.contains("test"));
        assertTrue(actionTypes.contains("validate"));
    }

    /**
     * Test doAction with an unknown action.
     */
    @Test
    public void testUnsupportedAction()
    {
        try {
            final CustomFlightActionRequest request = new CustomFlightActionRequest();
            request.setDatasourceTypeName(getDatasourceTypeName());
            request.setConnectionProperties(createConnectionProperties());
            getClient().doAction(new Action("unknown", modelMapper.toBytes(request))).next();
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("not supported"));
        }
    }

    /**
     * Test doAction with a missing action body.
     */
    @Test
    public void testMissingActionBody()
    {
        try {
            getClient().doAction(new Action("test")).next();
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("Missing action body"));
        }
    }

    /**
     * Test health_check action.
     *
     * @throws Exception
     */
    @Test
    public void testHealthCheckAction() throws Exception
    {
        final Iterator<Result> resultIterator = getClient().doAction(new Action("health_check"));
        assertTrue(resultIterator.hasNext());
        final Result result = resultIterator.next();
        assertFalse(resultIterator.hasNext());
        assertNotNull(result.getBody());
        final CustomFlightActionResponse response = modelMapper.fromBytes(result.getBody(), CustomFlightActionResponse.class);
        final ConnectionActionResponse responseProperties = response.getResponseProperties();
        assertNotNull(responseProperties);
        assertNotNull(responseProperties.get("version"));
        assertEquals("OK", responseProperties.get("status"));
    }

    /**
     * Test list_datasource_types action.
     *
     * @throws Exception
     */
    @Test
    public void testListDatasourceTypesAction() throws Exception
    {
        final Iterator<Result> resultIterator = getClient().doAction(new Action("list_datasource_types"));
        assertTrue(resultIterator.hasNext());
        final Result result = resultIterator.next();
        assertFalse(resultIterator.hasNext());
        assertNotNull(result.getBody());
        final CustomFlightActionResponse response = modelMapper.fromBytes(result.getBody(), CustomFlightActionResponse.class);
        final CustomFlightDatasourceTypes customDatasourceTypes = response.getDatasourceTypes();
        assertNotNull(customDatasourceTypes);
        final List<String> customTypeNames = new ArrayList<>();
        for (final CustomFlightDatasourceType customType : customDatasourceTypes.getDatasourceTypes()) {
            customTypeNames.add(customType.getName());
        }
        assertTrue(customTypeNames.contains(getDatasourceTypeName()));
    }

    /**
     * Test validate action with valid properties.
     *
     * @throws Exception
     */
    @Test
    public void testValidateAction() throws Exception
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        getClient().doAction(new Action("validate", modelMapper.toBytes(request))).next();
    }

    /**
     * Test connection.
     *
     * @throws Exception
     */
    @Test
    public void testConnection() throws Exception
    {
        final CustomFlightActionRequest request = new CustomFlightActionRequest();
        request.setDatasourceTypeName(getDatasourceTypeName());
        request.setConnectionProperties(createConnectionProperties());
        getClient().doAction(new Action("test", modelMapper.toBytes(request))).next();
    }

}
