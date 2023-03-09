package com.ibm.wdp.connect.sdk;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightInfo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.connect.sdk.util.Utils;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
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
        testFlight = TestDelegatingFlight.createLocal(Utils.getFreePort(), false, new MockProducer());
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

    @AfterClass
    public static void tearDownOnce() throws IOException, InterruptedException
    {
        testFlight.close();
        client.close();
    }
}
