/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.wdp.connect.sdk.flight;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.arrow.flight.FlightProducer;
import org.apache.arrow.flight.FlightServer;
import org.apache.arrow.flight.Location;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.util.AutoCloseables;
import org.slf4j.Logger;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.ibm.connect.sdk.util.AuthUtils;
import com.ibm.connect.sdk.util.SSLUtils;
import com.ibm.connect.sdk.util.ServerTokenAuthHandler;

/**
 * A stand-alone Flight service that can be started from the Java command line.
 */
public class FlightService implements AutoCloseable
{
    private static final Logger LOGGER = getLogger(FlightService.class);

    // Configuration environment variables
    private static final String ENV_ENABLE_SSL = "ENABLE_SSL";
    private static final String ENV_FLIGHT_PORT = "FLIGHT_PORT";
    private static final String ENV_FLIGHT_CERTS_DIR = "FLIGHT_CERTS_DIR";

    // Configuration defaults
    private static final String ENABLE_SSL_DEFAULT = "true";
    private static final String FLIGHT_PORT_DEFAULT = "9443";
    private static final String FLIGHT_CERTS_DIR_DEFAULT = "etc/flight_certs";

    private static final String FLIGHT_HOST = "localhost";
    private static final String FLIGHT_IP_ADDRESS = "0.0.0.0"; // NOPMD
    private static final String FLIGHT_CERTS_DIR
            = getEnv(ENV_FLIGHT_CERTS_DIR, System.getProperty("com.ibm.wdp.connect.sdk.flight.certs_dir", FLIGHT_CERTS_DIR_DEFAULT));
    private static final String FLIGHT_CERT_FILENAME = "cert.pem";
    private static final String FLIGHT_KEY_FILENAME = "key.pem";

    private final ExecutorService executor;
    private final FlightServer server;
    private final AtomicBoolean running = new AtomicBoolean();

    static {
        System.setProperty("java.vendor", "International Business Machines Corporation");
    }

    /**
     * Starts a stand-alone Flight service.
     *
     * @param allocator
     * @throws Exception 
     */
    public FlightService(BufferAllocator allocator) throws Exception
    {
        final String envEnableSsl = getEnv(ENV_ENABLE_SSL, ENABLE_SSL_DEFAULT);
        final String envFlightPort = getEnv(ENV_FLIGHT_PORT, FLIGHT_PORT_DEFAULT);
        LOGGER.info(ENV_ENABLE_SSL + " = " + envEnableSsl);
        LOGGER.info(ENV_FLIGHT_PORT + " = " + envFlightPort);
        final boolean sslEnabled = Boolean.parseBoolean(envEnableSsl);
        final int flightPort = Integer.parseInt(envFlightPort);
        LOGGER.info("Creating executor");
        executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("wdp-connect-sdk-flight-executor-%d").build());
        LOGGER.info("Creating producer");
        final FlightProducer producer = new DelegatingFlightProducer();
        final Location location
                = sslEnabled ? Location.forGrpcTls(FLIGHT_IP_ADDRESS, flightPort) : Location.forGrpcInsecure(FLIGHT_IP_ADDRESS, flightPort);
        LOGGER.info("Starting Flight service at " + location.getUri());
        final FlightServer.Builder serverBuilder
                = FlightServer.builder(allocator, location, producer).executor(executor).authHandler(ServerTokenAuthHandler.getInstance());
        if (sslEnabled) {
            final Path certPath = Paths.get(FLIGHT_CERTS_DIR, FLIGHT_CERT_FILENAME).toAbsolutePath();
            final Path certKeyPath = Paths.get(FLIGHT_CERTS_DIR, FLIGHT_KEY_FILENAME).toAbsolutePath();
            final File certFile = certPath.toFile();
            final File certKeyFile = certKeyPath.toFile();
            if (certFile.exists() && certKeyFile.exists()) {
                LOGGER.info("Starting server using SSL with certificate from " + certPath);
                serverBuilder.useTls(certFile, certKeyFile);
            } else {
                if (!certFile.exists()) {
                    LOGGER.info("Certificate file not found: " + certPath);
                }
                if (!certKeyFile.exists()) {
                    LOGGER.info("Certificate key not found: " + certKeyPath);
                }
                LOGGER.info("Generating self-signed certificate");
                final KeyPair sslKeyPair = AuthUtils.generateKeyPair();
                final Certificate selfSignedCert = SSLUtils.generateSelfSignedCert(sslKeyPair, FLIGHT_HOST);
                final String sslCert = SSLUtils.convertCertToPEM(selfSignedCert);
                final String privateKeyPEM = SSLUtils.convertPrivateKeyToPEM(sslKeyPair.getPrivate());
                LOGGER.info("Starting server using SSL with self-signed certificate");
                LOGGER.info(sslCert);
                serverBuilder.useTls(new ByteArrayInputStream(sslCert.getBytes(StandardCharsets.UTF_8)),
                        new ByteArrayInputStream(privateKeyPEM.getBytes(StandardCharsets.UTF_8)));
            }
        }
        server = serverBuilder.build();
        LOGGER.info("Flight service ready to start");
    }

    private void startServer() throws IOException
    {
        LOGGER.info("Starting Flight server");
        server.start();
        running.set(true);
        LOGGER.info("Flight service ready for requests at " + server.getLocation().getUri());
    }

    /**
     * Block until the server shuts down.
     *
     * @throws InterruptedException 
     */
    private void awaitTermination() throws InterruptedException
    {
        server.awaitTermination();
    }

    /**
     * Request that the server shut down.
     */
    private void shutdown()
    {
        server.shutdown();
        executor.shutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        shutdown();
        if (running.get()) {
            LOGGER.info("Closing the Flight service");
            try {
                server.close();
            }
            catch (final InterruptedException e) {
                LOGGER.info("Close interrupted: {0}", e.getMessage());
                Thread.currentThread().interrupt();
            }
            finally {
                running.set(false);
            }
        }
    }

    /**
     * Runs a stand-alone Flight service.
     *
     * @param args
     */
    public static void main(String[] args)
    {
        LOGGER.info("Creating RootAllocator");
        try (RootAllocator allocator = new RootAllocator()) {
            runService(allocator);
        }
        catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private static void runService(RootAllocator allocator) throws Exception
    {
        LOGGER.info("Creating FlightService");
        try (FlightService service = new FlightService(allocator)) {
            LOGGER.info("Starting server");
            service.startServer();
            LOGGER.info("Adding shutdown hook");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    AutoCloseables.close(service, allocator);
                }
                catch (final Exception e) {
                    // NOTE: Logs do not output in shutdownHook
                    System.err.println(e.getMessage());
                }
            }));

            LOGGER.info("Waiting for termination");
            service.awaitTermination();
            LOGGER.info("Service terminated");
        }
    }

    private static String getEnv(String var, String defaultValue)
    {
        final String value = System.getenv(var);
        return value != null ? value : defaultValue;
    }

}
