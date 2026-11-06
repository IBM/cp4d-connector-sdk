/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static com.ibm.connect.restconnector.StreamTestSupport.fields;
import static com.ibm.connect.restconnector.StreamTestSupport.idArray;
import static com.ibm.connect.restconnector.StreamTestSupport.linkHeader;
import static com.ibm.connect.restconnector.StreamTestSupport.offset;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.connect.restconnector.StreamTestSupport.CollectingWriter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * End-to-end tests against a real HTTP server bound to the loopback interface only, using the real
 * {@code java.net.http.HttpClient} of {@link JsonToArrowStream}.
 */
public class TestJsonToArrowStreamHttp
{
    private static final List<RestFieldDefinition> ID_NAME = fields("id", "INTEGER", "name", "VARCHAR(10)");

    private HttpServer server;
    private String base;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();

    @Before
    public void startServer() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();

        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void stopServer()
    {
        server.stop(0);
    }

    private void handle(String path, Handler handler)
    {
        server.createContext(path, exchange -> {
            requests.add(exchange.getRequestURI().toString());
            authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
    }

    @FunctionalInterface
    private interface Handler
    {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static void respond(HttpExchange exchange, int status, String body, String... headers) throws IOException
    {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < headers.length; i += 2) {
            exchange.getResponseHeaders().add(headers[i], headers[i + 1]);
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private CollectingWriter run(String path, String dataPath, PaginationConfig pagination, Map<String, String> auth)
            throws Exception
    {
        final CollectingWriter writer = new CollectingWriter();
        try (JsonToArrowStream s = new JsonToArrowStream(base + path, dataPath, ID_NAME, auth, pagination,
                "application/json", false)) {
            s.streamTo(writer);
        }
        return writer;
    }

    @Test
    public void offsetPaginationOverRealHttp() throws Exception
    {
        handle("/items", exchange -> {
            final String query = exchange.getRequestURI().getRawQuery();
            if (query.contains("offset=0")) {
                respond(exchange, 200, "{\"data\":" + idArray(0, 2) + "}");
            } else if (query.contains("offset=2")) {
                respond(exchange, 200, "{\"data\":" + idArray(2, 1) + "}");
            } else {
                respond(exchange, 400, "bad");
            }
        });
        final CollectingWriter w = run("/items?fixed=1", "data", offset(2), null);
        assertEquals(3, w.rows.size());
        assertEquals("/items?fixed=1&offset=0&limit=2", requests.get(0));
        assertEquals("/items?fixed=1&offset=2&limit=2", requests.get(1));
        assertEquals(2, requests.size());
    }

    @Test
    public void linkHeaderPaginationOverRealHttp() throws Exception
    {
        handle("/items", exchange -> {
            if (exchange.getRequestURI().getRawQuery() == null) {
                respond(exchange, 200, idArray(0, 2), "Link", "<" + base + "/items?page=2>; rel=\"next\"");
            } else {
                respond(exchange, 200, idArray(2, 2), "Link", "</items?page=1>; rel=\"prev\"");
            }
        });
        final CollectingWriter w = run("/items", null, linkHeader(), null);
        assertEquals(4, w.rows.size());
        assertEquals("/items", requests.get(0));
        assertEquals("/items?page=2", requests.get(1));
        assertEquals(2, requests.size());
    }

    @Test
    public void non2xxResponseIsAnIOException() throws Exception
    {
        handle("/fail", exchange -> respond(exchange, 503, "{\"error\":\"down\"}"));
        try {
            run("/fail?apikey=SECRET", null, null, null);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("503"));
            assertFalse(e.getMessage(), e.getMessage().contains("SECRET"));
        }
    }

    @Test
    public void connectionRefusedIsAnIOException() throws Exception
    {
        final int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        final CollectingWriter writer = new CollectingWriter();
        try (JsonToArrowStream s = new JsonToArrowStream("http://127.0.0.1:" + port + "/x?token=SECRET", null,
                ID_NAME, null, null, null, false)) {
            s.streamTo(writer);
            fail();
        } catch (IOException e) {
            assertFalse(String.valueOf(e.getMessage()), String.valueOf(e.getMessage()).contains("SECRET"));
        }
        assertTrue(writer.rows.isEmpty());
    }

    @Test
    public void sameOriginRedirectKeepsAuthorization() throws Exception
    {
        handle("/old", exchange -> respond(exchange, 302, "", "Location", "/new"));
        handle("/new", exchange -> respond(exchange, 200, idArray(0, 1)));
        final CollectingWriter w = run("/old", null, null, Collections.singletonMap("Authorization", "Bearer t"));
        assertEquals(1, w.rows.size());
        assertEquals("Bearer t", authorizations.get(0));
        assertEquals("Bearer t", authorizations.get(1));
    }

    @Test
    public void crossOriginRedirectDoesNotForwardAuthorization() throws Exception
    {
        // "localhost" is another origin than "127.0.0.1" for the same listening server
        final int port = server.getAddress().getPort();
        handle("/old", exchange -> respond(exchange, 302, "", "Location", "http://localhost:" + port + "/new"));
        handle("/new", exchange -> respond(exchange, 200, idArray(0, 1)));
        try {
            final CollectingWriter w = run("/old", null, null, Collections.singletonMap("Authorization", "Bearer t"));
            assertEquals(1, w.rows.size());
            assertEquals("Bearer t", authorizations.get(0));
            assertEquals("null", authorizations.get(1));
        } catch (IOException e) {
            // "localhost" may resolve to ::1 only, where the server is not listening; credentials were still withheld
            assertEquals(1, authorizations.size());
        }
    }

    @Test
    public void redirectLoopIsStopped() throws Exception
    {
        handle("/loop", exchange -> respond(exchange, 302, "", "Location", "/loop"));
        try {
            run("/loop", null, null, null);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Too many redirects"));
        }
        assertEquals(JsonToArrowStream.MAX_REDIRECTS + 1, requests.size());
    }

    @Test
    public void truncatedBodyIsReportedAndEarlierRowsRemain() throws Exception
    {
        handle("/cut", exchange -> {
            final byte[] full = idArray(0, 50).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, full.length + 100); // promises more than it sends
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(full, 0, full.length - 10);
            }
        });
        final CollectingWriter writer = new CollectingWriter();
        try (JsonToArrowStream s = new JsonToArrowStream(base + "/cut", null, ID_NAME, null, null, null, false)) {
            s.streamTo(writer);
            fail();
        } catch (IOException expected) {
            assertTrue(writer.rows.size() >= 40);
            assertTrue(writer.balanced());
        }
    }

    @Test
    public void lookupOverRealHttp() throws Exception
    {
        handle("/lookup", exchange -> respond(exchange, 200, "{\"items\":" + idArray(1, 3) + "}"));
        final List<String> values = JsonToArrowStream.fetchStringValues(base + "/lookup", "items", "name", null, null);
        assertEquals(java.util.Arrays.asList("n1", "n2", "n3"), values);
        assertEquals("null", authorizations.get(0)); // no credentials configured, none sent
    }
}
