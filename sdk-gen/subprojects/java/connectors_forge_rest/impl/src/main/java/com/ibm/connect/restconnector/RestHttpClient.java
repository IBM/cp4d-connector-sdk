/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;

/**
 * The HTTP client of the REST connector.
 *
 * <p>Redirects are not left to the JDK client, which would not let the connector inspect the
 * targets. The underlying client never follows them; this class does, according to the
 * {@link RedirectPolicy}, and checks the {@link EgressBlocklist} before the initial request and
 * before every redirect hop.
 *
 * <p>The connector only issues {@code GET} requests, so redirected requests are always
 * {@code GET} and carry the headers of the original request.
 */
final class RestHttpClient
{
    private static final Logger LOGGER = getLogger(RestHttpClient.class);

    private static final int MAX_REDIRECTS = 5;
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;

    /** The only request headers (lower case) that are forwarded to a different origin. */
    private static final Set<String> CROSS_ORIGIN_HEADERS = Set.of("accept", "user-agent");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(60);

    private static final RestHttpClient INSTANCE = new RestHttpClient(CONNECT_TIMEOUT,
            RedirectPolicy.fromEnvironment(RedirectPolicy.NORMAL), EgressBlocklist.getInstance());

    private final HttpClient client;
    private final RedirectPolicy policy;
    private final EgressBlocklist blocklist;

    /**
     * Returns the process-wide client used by the whole connector. The redirect policy comes from
     * the environment ({@code CF_REDIRECT_POLICY}, default {@link RedirectPolicy#NORMAL}) and the
     * blocklist from the mounted blocklist file.
     *
     * @return the shared client
     */
    static RestHttpClient getInstance()
    {
        return INSTANCE;
    }

    RestHttpClient(Duration connectTimeout, RedirectPolicy policy, EgressBlocklist blocklist)
    {
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.policy = policy;
        this.blocklist = blocklist;
    }

    /**
     * Sends a request and follows redirects according to the policy.
     *
     * @param request
     *            the request
     * @param handler
     *            the response body handler
     * @param <T>
     *            the response body type
     * @return the final response
     * @throws IOException
     *             on I/O errors, if the target is blocked, or if there are too many redirects
     * @throws InterruptedException
     *             if interrupted while waiting
     */
    <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler) throws IOException, InterruptedException
    {
        HttpRequest current = request;
        for (int hop = 0;; hop++) {
            blocklist.check(current.uri());
            final HttpResponse<T> response = client.send(current, handler);
            final URI target = redirectTarget(current.uri(), response);
            if (target == null) {
                return response;
            }
            if (hop >= MAX_REDIRECTS) {
                closeQuietly(response);
                throw new IOException("Too many redirects.");
            }
            closeQuietly(response);
            current = redirected(current, target);
        }
    }

    /** Returns the URI to follow, or {@code null} if the response must be returned as it is. */
    private URI redirectTarget(URI from, HttpResponse<?> response)
    {
        if (policy == RedirectPolicy.NEVER || !isRedirect(response.statusCode())) {
            return null;
        }
        final Optional<String> location = response.headers().firstValue("Location");
        if (!location.isPresent()) {
            return null;
        }
        final URI target;
        try {
            target = from.resolve(location.get());
        } catch (IllegalArgumentException e) {
            return null;
        }
        final String scheme = target.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        if (policy == RedirectPolicy.NORMAL && "https".equalsIgnoreCase(from.getScheme()) && "http".equalsIgnoreCase(scheme)) {
            return null;
        }
        return target;
    }

    private static boolean isRedirect(int status)
    {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    /**
     * Builds the request that follows a redirect. Credentials must never reach another origin, and
     * the connector cannot tell which headers carry them (the DSL lets the tenant name any header),
     * so a cross-origin hop keeps only the headers of {@link #CROSS_ORIGIN_HEADERS}.
     */
    private static HttpRequest redirected(HttpRequest original, URI target)
    {
        final boolean sameOrigin = isSameOrigin(original.uri(), target);
        final HttpRequest.Builder builder = HttpRequest.newBuilder(target).GET();
        original.timeout().ifPresent(builder::timeout);
        original.headers().map().forEach((name, values) -> {
            if (sameOrigin || CROSS_ORIGIN_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                values.forEach(value -> builder.header(name, value));
            }
        });
        if (!sameOrigin) {
            LOGGER.debug("Redirected to another origin: request headers other than {} were not forwarded", CROSS_ORIGIN_HEADERS);
        }
        return builder.build();
    }

    /** Same origin means the same scheme, host and port (default ports are filled in). */
    private static boolean isSameOrigin(URI a, URI b)
    {
        return equalsIgnoreCase(a.getScheme(), b.getScheme())
                && equalsIgnoreCase(a.getHost(), b.getHost())
                && effectivePort(a) == effectivePort(b);
    }

    private static boolean equalsIgnoreCase(String a, String b)
    {
        return a != null && a.equalsIgnoreCase(b);
    }

    private static int effectivePort(URI uri)
    {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? HTTPS_PORT : HTTP_PORT;
    }

    private static void closeQuietly(HttpResponse<?> response)
    {
        if (response.body() instanceof InputStream) {
            try {
                ((InputStream) response.body()).close();
            } catch (IOException e) {
                // The redirect response body is discarded, so this is not fatal. The URI is not logged: it may carry credentials.
                LOGGER.debug("Failed to close the body of a redirect response (status {})", response.statusCode(), e);
            }
        }
    }
}
