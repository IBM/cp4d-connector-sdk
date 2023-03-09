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
import static org.junit.Assume.assumeNotNull;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.ActionType;
import org.apache.arrow.flight.Result;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * An abstract class for a cloud test suite.
 */
public abstract class CloudTestSuite extends FlightTestSuite
{
    protected abstract CloudClient getCloudClient();

    protected abstract String getConnectionId();

    /**
     * Verifies that test configuration has been specified before running tests.
     */
    @Before
    public void setUp()
    {
        assumeNotNull(getCloudClient());
    }

    /**
     * Test listActions.
     */
    @Test
    public void testListActions()
    {
        final List<String> actionTypes = StreamSupport.stream(getClient().listActions().spliterator(), false).map(ActionType::getType)
                .collect(Collectors.toList());
        assertTrue(actionTypes.contains("discovery"));
        assertTrue(actionTypes.contains("health_check"));
        assertTrue(actionTypes.contains("setup_phase"));
        assertTrue(actionTypes.contains("test"));
        assertTrue(actionTypes.contains("validate"));
        assertTrue(actionTypes.contains("wrapup_phase"));
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
        final String json = new String(result.getBody(), StandardCharsets.UTF_8);
        final JsonObject response = new Gson().fromJson(json, JsonObject.class);
        final JsonObject healthCheck = response.getAsJsonObject("health_check");
        final JsonObject versions = healthCheck.getAsJsonObject("versions");
        assertNotNull(versions.get("flight_service_version"));
        assertNotNull(versions.get("connect_library_version"));
        assertNotNull(versions.get("service_common_version"));
        assertNotNull(versions.get("arrow_version"));
        assertEquals("OK", healthCheck.get("status").getAsString());
    }

    /**
     * Test validate action with valid properties.
     *
     * @throws Exception
     */
    @Test
    public void testValidateAction() throws Exception
    {
        final JsonObject request = new JsonObject();
        request.addProperty("asset_id", getConnectionId());
        request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
        getClient().doAction(new Action("validate", request.toString().getBytes(StandardCharsets.UTF_8))).next();
    }

    /**
     * Test connection.
     *
     * @throws Exception
     */
    @Test
    public void testConnection() throws Exception
    {
        final JsonObject request = new JsonObject();
        request.addProperty("asset_id", getConnectionId());
        request.addProperty(getCloudClient().getContainerParameter(), getCloudClient().getContainerId());
        getClient().doAction(new Action("test", request.toString().getBytes(StandardCharsets.UTF_8))).next();
    }
}
