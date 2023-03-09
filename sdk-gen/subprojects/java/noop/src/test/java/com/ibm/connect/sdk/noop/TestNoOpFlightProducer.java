/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.noop;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.AsyncPutListener;
import org.apache.arrow.flight.Criteria;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.Ticket;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.connect.sdk.test.TestConfig;
import com.ibm.connect.sdk.test.TestFlight;

/**
 * Tests a no-op Flight producer that has no implementation.
 */
public class TestNoOpFlightProducer
{

    private static TestFlight testFlight;
    private static FlightClient client;

    /**
     * Setup before tests.
     *
     * @throws Exception
     */
    @BeforeClass
    public static void setUpOnce() throws Exception
    {
        testFlight = TestFlight.createLocal(TestConfig.getPort("noop.flight.port"), false, new NoOpFlightProducer());
        client = testFlight.getClient();
    }

    /**
     * Cleanup after tests.
     *
     * @throws IOException
     */
    @AfterClass
    public static void tearDownOnce() throws IOException
    {
        testFlight.close();
    }

    /**
     * Test getStream.
     */
    @Test
    public void testGetStream()
    {
        try (FlightStream stream = client.getStream(new Ticket(new byte[0]))) {
            stream.next();
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("not implemented"));
        }
    }

    /**
     * Test listFlights.
     */
    @Test
    public void testListFlights()
    {
        try {
            client.listFlights(Criteria.ALL).iterator().hasNext();
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("not implemented"));
        }
    }

    /**
     * Test getFlightInfo.
     */
    @Test
    public void testGetFlightInfo()
    {
        try {
            client.getInfo(FlightDescriptor.command(new byte[0]));
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("not implemented"));
        }
    }

    /**
     * Test acceptPut.
     */
    @Test
    public void testAcceptPut()
    {
        try {
            client.startPut(FlightDescriptor.command(new byte[0]), new AsyncPutListener()).getResult();
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("not implemented"));
        }
    }

    /**
     * Test doAction.
     */
    @Test
    public void testDoAction()
    {
        try {
            client.doAction(new Action("test")).next();
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("not implemented"));
        }
    }

    /**
     * Test listActions.
     */
    @Test
    public void testListActions()
    {
        try {
            client.listActions().iterator().hasNext();
            fail("Exception expected");
        }
        catch (Exception e) {
            assertTrue(e.getMessage().contains("not implemented"));
        }
    }
}
