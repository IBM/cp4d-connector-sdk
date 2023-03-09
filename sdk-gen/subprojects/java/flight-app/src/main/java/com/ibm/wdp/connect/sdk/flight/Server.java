/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.flight;

import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.arrow.flight.LocationSchemes;
import org.apache.arrow.util.AutoCloseables;

import com.ibm.connect.sdk.util.ServerTokenAuthHandler;

/**
 * Servlet listener that manages a gRPC flight server.
 */
public class Server implements AutoCloseable, ServletContextListener
{
    private static final Logger LOG = Logger.getLogger(Server.class.getCanonicalName());

    private Service service;
    private DelegateServer delegate;

    /**
     * {@inheritDoc}
     */
    @Override
    public void contextInitialized(ServletContextEvent sce)
    {
        LOG.entering(this.getClass().toString(), "contextInitialized");
        final String flightUrl = System.getenv("FLIGHT_URL");
        if (flightUrl != null) {
            service = new Service();
            try {
                final URI uri = new URI(flightUrl);
                delegate = new DelegateServer(Service.getRootAllocator(), uri.getPort(),
                        LocationSchemes.GRPC_TLS.equals(uri.getScheme()) ? SSLContext.getDefault() : null,
                        ServerTokenAuthHandler.getInstance(), Service.getProducer(), null);
                delegate.start();
            }
            catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
        LOG.exiting(this.getClass().toString(), "contextInitialized");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void contextDestroyed(ServletContextEvent sce)
    {
        LOG.entering(this.getClass().toString(), "contextDestroyed");
        close();
        LOG.exiting(this.getClass().toString(), "contextDestroyed");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        LOG.entering(this.getClass().toString(), "close");
        try {
            AutoCloseables.close(delegate, service);
        }
        catch (final RuntimeException e) {
            LOG.log(Level.FINE, e.getMessage());
            throw e;
        }
        catch (final Exception e) {
            LOG.log(Level.FINE, e.getMessage());
            throw new RuntimeException(e);
        }
        finally {
            delegate = null;
            service = null;
        }
        LOG.exiting(this.getClass().toString(), "close");
    }
}
