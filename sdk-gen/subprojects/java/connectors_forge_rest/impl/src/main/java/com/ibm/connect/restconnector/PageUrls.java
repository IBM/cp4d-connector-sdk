/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * URL helpers for paginated requests: query-parameter building without duplicates, resolution and
 * validation of server-supplied next-page URLs, and redaction for logging.
 */
final class PageUrls
{
    private PageUrls()
    {
    }

    /**
     * Parses an absolute http(s) URL; the exception message never contains the URL. The cause is deliberately not
     * chained: {@link URISyntaxException#getMessage()} quotes the input, which may contain secrets.
     */
    @SuppressWarnings("PMD.PreserveStackTrace")
    static URI parseHttp(String url) throws IOException
    {
        if (url == null) {
            throw new IOException("The request URL is missing");
        }
        final URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new IOException("The request URL is not a valid URI");
        }
        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme) || uri.getHost() == null) {
            throw new IOException("The request URL must be an absolute http or https URL");
        }
        return uri;
    }

    /**
     * Returns {@code baseUrl} with the given pagination parameters set.
     *
     * <p>Existing query pairs are kept byte-for-byte, except pairs whose (decoded) name is one of the
     * names being set: those are replaced, so a parameter never appears twice. Names and values of the
     * new pairs are percent-encoded. Any fragment is dropped.
     *
     * @param baseUrl
     *            the URL, possibly already containing a query string
     * @param namesAndValues
     *            alternating parameter names and values; a {@code null} name skips the pair
     */
    static String withParams(String baseUrl, String... namesAndValues)
    {
        String url = baseUrl;
        final int hash = url.indexOf('#');
        if (hash >= 0) {
            url = url.substring(0, hash);
        }
        final int q = url.indexOf('?');
        final String path = q >= 0 ? url.substring(0, q) : url;
        final String query = q >= 0 ? url.substring(q + 1) : "";

        final Set<String> replaced = new HashSet<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            if (namesAndValues[i] != null) {
                replaced.add(namesAndValues[i]);
            }
        }

        final StringBuilder sb = new StringBuilder(url.length() + 64).append(path);
        char separator = '?';
        if (!query.isEmpty()) {
            for (final String pair : query.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                final int eq = pair.indexOf('=');
                final String rawName = eq >= 0 ? pair.substring(0, eq) : pair;
                if (!replaced.contains(decode(rawName))) {
                    sb.append(separator).append(pair);
                    separator = '&';
                }
            }
        }
        for (int i = 0; i < namesAndValues.length; i += 2) {
            if (namesAndValues[i] != null) {
                sb.append(separator).append(encode(namesAndValues[i])).append('=').append(encode(namesAndValues[i + 1]));
                separator = '&';
            }
        }
        return sb.toString();
    }

    /**
     * Resolves a next-page reference against the URL it was returned for and checks that it is safe to follow.
     *
     * <p>Rules: only {@code http}/{@code https}; no user information; a host is required; no
     * {@code https} to {@code http} downgrade; and when credentials are sent, the target must have the same
     * origin (scheme, host, port) as the first request, so credentials are never forwarded to another host.
     *
     * @param current
     *            the URL of the page that returned the reference
     * @param origin
     *            the URL of the first request of the stream
     * @param reference
     *            the absolute or relative reference supplied by the server
     * @param credentialsAttached
     *            whether authentication headers are sent with each request
     * @return the absolute, validated URI
     * @throws IOException
     *             if the reference is malformed or not acceptable; the message never contains the URL
     */
    @SuppressWarnings("PMD.PreserveStackTrace") // the cause quotes the (possibly secret) URL, so it is not chained
    static URI resolveNext(URI current, URI origin, String reference, boolean credentialsAttached) throws IOException
    {
        final URI target;
        try {
            final String ref = reference.trim();
            if (ref.startsWith("?")) {
                // RFC 3986: a query-only reference keeps the path (java.net.URI.resolve would drop its last segment)
                final String base = current.toString();
                final int cut = base.indexOf('?') >= 0 ? base.indexOf('?') : base.length();
                final int hash = base.indexOf('#');
                target = new URI(base.substring(0, hash >= 0 ? Math.min(cut, hash) : cut) + ref);
            } else {
                target = current.resolve(ref);
            }
        } catch (IllegalArgumentException | URISyntaxException e) {
            throw new IOException("The URL returned by the server is malformed");
        }
        validate(target, current, origin, credentialsAttached);
        return target;
    }

    /** Validates a redirect or next-page target; see {@link #resolveNext}. */
    static void validate(URI target, URI current, URI origin, boolean credentialsAttached) throws IOException
    {
        final String scheme = target.getScheme() == null ? "" : target.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IOException("The URL returned by the server uses an unsupported scheme; only http and https are allowed");
        }
        if (target.getRawUserInfo() != null) {
            throw new IOException("The URL returned by the server contains user information, which is not allowed");
        }
        if (target.getHost() == null) {
            throw new IOException("The URL returned by the server has no host");
        }
        if ("https".equalsIgnoreCase(current.getScheme()) && "http".equals(scheme)) {
            throw new IOException("The URL returned by the server would downgrade the connection from https to http");
        }
        if (credentialsAttached && !sameOrigin(origin, target)) {
            throw new IOException("The URL returned by the server points to a different origin than the request; "
                    + "credentials are not forwarded to another origin");
        }
    }

    /** Returns {@code true} if both URIs have the same scheme, host and effective port. */
    static boolean sameOrigin(URI a, URI b)
    {
        return a.getScheme() != null && b.getScheme() != null
                && a.getScheme().equalsIgnoreCase(b.getScheme())
                && a.getHost() != null && b.getHost() != null
                && a.getHost().equalsIgnoreCase(b.getHost())
                && effectivePort(a) == effectivePort(b);
    }

    /** Returns {@code scheme://host[:port]/path} without user information, query or fragment. */
    static String redact(URI uri)
    {
        final StringBuilder sb = new StringBuilder();
        if (uri.getScheme() != null) {
            sb.append(uri.getScheme()).append("://");
        }
        if (uri.getHost() != null) {
            sb.append(uri.getHost());
        }
        if (uri.getPort() >= 0) {
            sb.append(':').append(uri.getPort());
        }
        if (uri.getRawPath() != null) {
            sb.append(uri.getRawPath());
        }
        return sb.toString();
    }

    /** Redacts a URL given as text; text that is not a valid URI yields a fixed placeholder. */
    static String redact(String url)
    {
        try {
            return redact(URI.create(url));
        } catch (IllegalArgumentException e) {
            return "<invalid url>";
        }
    }

    private static int effectivePort(URI uri)
    {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decode(String value)
    {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
