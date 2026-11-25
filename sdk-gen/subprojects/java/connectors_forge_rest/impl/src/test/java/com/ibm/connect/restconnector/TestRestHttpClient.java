/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link RedirectPolicy} and {@link RestHttpClient}.
 *
 * <p>A loopback {@link HttpServer} plays the remote API. Redirects to blocked addresses are refused
 * before any connection is attempted, so nothing leaves the machine.
 */
public class TestRestHttpClient
{
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private HttpServer server;
    private String origin;
    private final AtomicInteger okHits = new AtomicInteger();
    private final AtomicInteger redirectHits = new AtomicInteger();
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();

    @Before
    public void startServer() throws Exception
    {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        origin = "http://127.0.0.1:" + server.getAddress().getPort();

        server.createContext("/ok", exchange -> {
            okHits.incrementAndGet();
            lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            final byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/to-ok", exchange -> redirect(exchange, 302, "/ok"));
        server.createContext("/to-ok-307", exchange -> redirect(exchange, 307, "/ok"));
        server.createContext("/loop", exchange -> redirect(exchange, 302, "/loop"));
        server.createContext("/to-metadata", exchange -> redirect(exchange, 302, "http://169.254.169.254/latest/meta-data/"));
        server.createContext("/to-ftp", exchange -> redirect(exchange, 302, "ftp://example.com/x"));
        server.createContext("/no-location", exchange -> {
            redirectHits.incrementAndGet();
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
    }

    private void redirect(com.sun.net.httpserver.HttpExchange exchange, int status, String location) throws IOException
    {
        redirectHits.incrementAndGet();
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    @After
    public void stopServer()
    {
        server.stop(0);
    }

    private RestHttpClient client(RedirectPolicy policy, String... blocked) throws IOException
    {
        return new RestHttpClient(TIMEOUT, policy, BlocklistTestSupport.blocklistWith(folder, blocked));
    }

    private HttpRequest get(String path)
    {
        return HttpRequest.newBuilder(URI.create(origin + path))
                .header("Authorization", "Bearer secret")
                .timeout(TIMEOUT)
                .GET()
                .build();
    }

    // ---- RedirectPolicy ----

    @Test
    public void testRedirectPolicyParsing()
    {
        assertEquals(RedirectPolicy.ALWAYS, RedirectPolicy.parse("ALWAYS", RedirectPolicy.NEVER));
        assertEquals(RedirectPolicy.NEVER, RedirectPolicy.parse("never", RedirectPolicy.ALWAYS));
        assertEquals(RedirectPolicy.NORMAL, RedirectPolicy.parse("  Normal ", RedirectPolicy.NEVER));
    }

    @Test
    public void testRedirectPolicyFallsBackWhenUnsetOrInvalid()
    {
        assertEquals(RedirectPolicy.NEVER, RedirectPolicy.parse(null, RedirectPolicy.NEVER));
        assertEquals(RedirectPolicy.NORMAL, RedirectPolicy.parse("", RedirectPolicy.NORMAL));
        assertEquals(RedirectPolicy.NORMAL, RedirectPolicy.parse("   ", RedirectPolicy.NORMAL));
        assertEquals(RedirectPolicy.NEVER, RedirectPolicy.parse("sometimes", RedirectPolicy.NEVER));
    }

    // ---- redirect handling ----

    @Test
    public void testNoRedirectIsPassedThrough() throws Exception
    {
        final HttpResponse<String> response = client(RedirectPolicy.NORMAL).send(get("/ok"), BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("{}", response.body());
    }

    @Test
    public void testNeverReturnsTheRedirectResponse() throws Exception
    {
        final HttpResponse<String> response = client(RedirectPolicy.NEVER).send(get("/to-ok"), BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
        assertEquals("/ok", response.headers().firstValue("Location").orElse(null));
        assertEquals(0, okHits.get());
    }

    @Test
    public void testNormalFollowsRedirectsAndKeepsHeaders() throws Exception
    {
        for (final String path : new String[] { "/to-ok", "/to-ok-307" }) {
            lastAuthHeader.set(null);
            final HttpResponse<String> response = client(RedirectPolicy.NORMAL).send(get(path), BodyHandlers.ofString());
            assertEquals(path, 200, response.statusCode());
            assertEquals("Bearer secret", lastAuthHeader.get());
        }
    }

    @Test
    public void testAlwaysFollowsRedirects() throws Exception
    {
        final HttpResponse<String> response = client(RedirectPolicy.ALWAYS).send(get("/to-ok"), BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(1, okHits.get());
    }

    @Test
    public void testRedirectLoopIsBounded() throws Exception
    {
        for (final RedirectPolicy policy : new RedirectPolicy[] { RedirectPolicy.NORMAL, RedirectPolicy.ALWAYS }) {
            redirectHits.set(0);
            try {
                client(policy).send(get("/loop"), BodyHandlers.ofString());
                fail("A redirect loop must be cut off for " + policy);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("redirects"));
            }
            assertTrue("hops: " + redirectHits.get(), redirectHits.get() <= 10);
        }
    }

    @Test
    public void testRedirectWithoutLocationOrToOtherSchemeIsReturned() throws Exception
    {
        assertEquals(302, client(RedirectPolicy.ALWAYS).send(get("/no-location"), BodyHandlers.ofString()).statusCode());
        assertEquals(302, client(RedirectPolicy.ALWAYS).send(get("/to-ftp"), BodyHandlers.ofString()).statusCode());
    }

    // ---- blocklist ----

    @Test
    public void testBlockedInitialTargetIsNotContacted() throws Exception
    {
        try {
            client(RedirectPolicy.NORMAL, "127.0.0.0/8").send(get("/ok"), BodyHandlers.ofString());
            fail("The loopback address is on the blocklist");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("blocked"));
        }
        assertEquals(0, okHits.get());
    }

    @Test
    public void testRedirectToBlockedAddressIsNotFollowed() throws Exception
    {
        for (final RedirectPolicy policy : new RedirectPolicy[] { RedirectPolicy.NORMAL, RedirectPolicy.ALWAYS }) {
            redirectHits.set(0);
            try {
                client(policy, "169.254.0.0/16").send(get("/to-metadata"), BodyHandlers.ofString());
                fail("The redirect target is on the blocklist (" + policy + ")");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("blocked"));
            }
            assertEquals("only the first hop may be issued", 1, redirectHits.get());
        }
    }

    @Test
    public void testNeverDoesNotCheckAnUnfollowedRedirectTarget() throws Exception
    {
        final HttpResponse<String> response =
                client(RedirectPolicy.NEVER, "169.254.0.0/16").send(get("/to-metadata"), BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
    }

    @Test
    public void testUnrelatedBlocklistEntriesDoNotInterfere() throws Exception
    {
        final HttpResponse<String> response =
                client(RedirectPolicy.NORMAL, "10.0.0.0/8", "169.254.0.0/16", "fd00::/8").send(get("/to-ok"), BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
}
