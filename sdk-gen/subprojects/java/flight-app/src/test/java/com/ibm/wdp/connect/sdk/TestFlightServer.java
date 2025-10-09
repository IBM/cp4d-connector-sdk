package com.ibm.wdp.connect.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Locale;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.Result;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.connect.sdk.util.Utils;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypes;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;
import com.ibm.wdp.connect.sdk.flight.TestDelegatingFlight;

public class TestFlightServer
{
    private static TestDelegatingFlight testFlight;
    private static FlightClient client;
    private static ModelMapper modelMapper = new ModelMapper();

    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        testFlight = TestDelegatingFlight.createLocal(Utils.getFreePort(), false, new MockProducer(), null);
        client = testFlight.getClient();
    }

    @Test
    public void testServerConnection() throws Exception
    {
        final CustomFlightAssetDescriptor descriptor = new CustomFlightAssetDescriptor();
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        final ConnectionProperties connectionProperties = new ConnectionProperties();
        descriptor.setDatasourceTypeName("mock");
        descriptor.setConnectionProperties(connectionProperties);
        descriptor.setInteractionProperties(interactionProperties);
        interactionProperties.put("schema_name", "SCHEMA1");
        interactionProperties.put("table_name", "T1");
        connectionProperties.put("url", "http://whatever.ibm.com");
        final FlightInfo info = client.getInfo(FlightDescriptor.command(modelMapper.toBytes(descriptor)));
        assertNotNull(info);
    }

    /**
     * Test DelegateServer error message with a requested locale.
     *
     * @throws Exception
     */
    @Test
    public void testInvalidActionGerman() throws Exception
    {
        try (TestDelegatingFlight localeTestFlight
                = TestDelegatingFlight.createLocal(Utils.getFreePort(), false, new MockProducer(), Locale.GERMAN)) {
            try (FlightClient localeClient = localeTestFlight.getClient()) {
                try {
                    localeClient.doAction(new Action("invalid")).next();
                    fail("Exception expected");
                }
                catch (Exception e) {
                    assertTrue(e.getMessage().contains("not supported in German"));
                }
            }
        }
    }

    /**
     * Test DelegateServer connector label with a requested locale.
     *
     * @throws Exception
     */
    @Test
    public void testListDatasourceTypesActionFrench() throws Exception
    {
        try (TestDelegatingFlight localeTestFlight
                = TestDelegatingFlight.createLocal(Utils.getFreePort(), false, new MockProducer(), Locale.FRENCH)) {
            try (FlightClient localeClient = localeTestFlight.getClient()) {
                final Result result = localeClient.doAction(new Action("list_datasource_types")).next();
                final CustomFlightActionResponse response = modelMapper.fromBytes(result.getBody(), CustomFlightActionResponse.class);
                final CustomFlightDatasourceTypes customDatasourceTypes = response.getDatasourceTypes();
                assertEquals(1, customDatasourceTypes.getDatasourceTypes().size());
                final CustomFlightDatasourceType customType = customDatasourceTypes.getDatasourceTypes().get(0);
                assertEquals("mock label in French", customType.getLabel());
            }
        }
    }

    @AfterClass
    public static void tearDownOnce() throws IOException, InterruptedException
    {
        testFlight.close();
        client.close();
    }
}
