/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.ibm.wdp.connect.sdk.connector.RowWriter;

/**
 * Fetches data from a REST API endpoint and streams each JSON object to a {@link RowWriter}.
 *
 * <p>This is the forge REST engine's JSON-to-Arrow streaming component. For each JSON object it calls
 * {@link RowWriter#startRow()}, {@link RowWriter#set(String, Object)} and {@link RowWriter#endRow()}.
 *
 * <h3>Memory behaviour</h3>
 * <p>The HTTP response body is consumed incrementally by a {@link PageParser}: no {@code JsonNode} tree of the
 * response, of the data array or of a record is ever built, for every pagination type and every {@code dataPath}.
 * Records are handed to the writer as soon as they are complete. Parser memory is bounded by the current record,
 * the single largest JSON-valued field of that record and the parser's token buffers, independent of the page size,
 * the number of pages and the total number of records. Pagination state is a few scalars plus (for loop detection)
 * one 32-byte digest per page, i.e. bounded by {@code maxPages}.
 *
 * <p>End-to-end memory also depends on the {@link RowWriter}. With the Flight {@code ArrowBatchWriter} it is bounded
 * by one Arrow batch (default 1000 rows, off-heap), and a full batch is sent synchronously on the calling thread.
 * A different writer that accumulates rows is outside the control of this class.
 *
 * <h3>Partial output</h3>
 * <p>Rows are written while the response is still being read, so a failure (malformed JSON late in a page, a failed
 * follow-up request, a pagination error, the page limit) leaves the rows written so far in the writer: neither
 * page-level nor request-level atomicity is provided. A record is, however, converted completely before it is
 * written, so a single record is never written partially because of a conversion or parsing problem.
 *
 * <h3>Cancellation</h3>
 * <p>A thread interrupt is honoured between and during requests ({@link InterruptedException}, interrupt flag
 * preserved). Cancellation of the Flight call by the client is <em>not</em> propagated into this class: the Flight
 * producer only checks {@code isCancelled()} after {@code stream()} returns, and a cancelled listener makes
 * {@code sendBatch} return silently, so the remaining pages are still downloaded and discarded until the stream
 * ends or the thread is interrupted.
 *
 * <h3>Security</h3>
 * <p>Server-supplied next-page URLs and redirects are validated (http/https only, no user information, no
 * https-to-http downgrade). Authentication headers are only ever sent to the origin of the first request; with
 * authentication configured a next-page URL on another origin is rejected, and redirects to another origin are
 * followed without credentials (at most {@value #MAX_REDIRECTS} hops). Query strings, cursors and tokens are never
 * logged or put into exception messages.
 *
 * <p>Supports all pagination strategies in {@link PaginationType}.
 */
public class JsonToArrowStream implements Closeable
{
    private static final Logger LOGGER = getLogger(JsonToArrowStream.class);

    public static final String STRICT_PARSING_PROPERTY = "com.ibm.connect.restconnector.strictParsing";

    /** Upper bound of pages per stream; reaching it while more data is announced is an error. */
    static final int MAX_PAGES = 10000;
    /** Maximum number of redirects followed for one request. */
    static final int MAX_REDIRECTS = 5;
    /** Maximum length of a cursor or next-page URL accepted from a server. */
    static final int MAX_CONTINUATION_LENGTH = 16 * 1024;

    private static final int HTTP_TIMEOUT_SECONDS = 60;
    private static final int HTTP_STATUS_2XX = 2;
    private static final String DEFAULT_ACCEPT = "application/json";

    /** Redirects are followed by {@link #fetch} so that credentials can be withheld from other origins. */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    static final HttpSender DEFAULT_SENDER = request -> HTTP_CLIENT.send(request, BodyHandlers.ofInputStream());

    private final String baseUrl;
    private final Map<String, String> authHeaders;
    private final String acceptHeader;
    private final PaginationConfig paginationConfig;
    private final HttpSender sender;
    private final int maxPages;
    private final PageParser pageParser;

    /** Sends one request; replaceable in tests. */
    @FunctionalInterface
    interface HttpSender
    {
        HttpResponse<InputStream> send(HttpRequest request) throws IOException, InterruptedException;
    }

    /** A response together with the URL that finally produced it (after redirects). */
    static final class Fetched
    {
        final HttpResponse<InputStream> response;
        final URI uri;

        Fetched(HttpResponse<InputStream> response, URI uri)
        {
            this.response = response;
            this.uri = uri;
        }
    }

    /**
     * Creates a streaming instance with full configuration and default (safe) type parsing.
     *
     * @param url
     *            the base URL to fetch
     * @param dataPath
     *            optional JSON path to the data array (may be null)
     * @param fieldDefs
     *            the field definitions
     * @param authHeaders
     *            optional authentication headers (may be null)
     * @param paginationConfig
     *            optional pagination configuration (may be null for non-paginated APIs)
     * @param acceptHeader
     *            value for the HTTP {@code Accept} header (e.g. {@code "application/json"})
     */
    public JsonToArrowStream(String url, String dataPath, List<RestFieldDefinition> fieldDefs,
            Map<String, String> authHeaders, PaginationConfig paginationConfig, String acceptHeader)
    {
        this(url, dataPath, fieldDefs, authHeaders, paginationConfig, acceptHeader,
                Boolean.getBoolean(STRICT_PARSING_PROPERTY));
    }

    /**
     * Creates a streaming instance with full configuration and explicit strict mode.
     *
     * @param url
     *            the base URL to fetch
     * @param dataPath
     *            optional JSON path to the data array (may be null)
     * @param fieldDefs
     *            the field definitions
     * @param authHeaders
     *            optional authentication headers (may be null)
     * @param paginationConfig
     *            optional pagination configuration (may be null for non-paginated APIs)
     * @param acceptHeader
     *            value for the HTTP {@code Accept} header (e.g. {@code "application/json"})
     * @param strict
     *            if {@code true}, conversion errors throw exceptions instead of logging warnings
     * @throws IllegalArgumentException
     *             if the pagination configuration or a JSON path is invalid
     */
    public JsonToArrowStream(String url, String dataPath, List<RestFieldDefinition> fieldDefs,
            Map<String, String> authHeaders, PaginationConfig paginationConfig, String acceptHeader,
            boolean strict)
    {
        this(url, dataPath, fieldDefs, authHeaders, paginationConfig, acceptHeader, strict, DEFAULT_SENDER,
                MAX_PAGES);
    }

    JsonToArrowStream(String url, String dataPath, List<RestFieldDefinition> fieldDefs,
            Map<String, String> authHeaders, PaginationConfig paginationConfig, String acceptHeader,
            boolean strict, HttpSender sender, int maxPages)
    {
        validate(paginationConfig);
        this.baseUrl = url;
        this.authHeaders = authHeaders;
        this.acceptHeader = acceptHeader != null ? acceptHeader : DEFAULT_ACCEPT;
        this.paginationConfig = paginationConfig;
        this.sender = sender;
        this.maxPages = maxPages;
        String cursorPath = null;
        String nextUrlPath = null;
        if (paginationConfig != null) {
            final PaginationType type = paginationConfig.getTypeEnum();
            cursorPath = type == PaginationType.CURSOR ? paginationConfig.getNextCursorPath() : null;
            nextUrlPath = type == PaginationType.NEXT_URL ? paginationConfig.getNextUrlPath() : null;
        }
        this.pageParser = new PageParser(new FieldPlan(fieldDefs), dataPath, cursorPath, nextUrlPath, strict);
    }

    /**
     * Fetches all data from the configured URL and streams it into the provided {@link RowWriter}.
     *
     * <p>For each JSON object in the response, calls {@link RowWriter#startRow()},
     * {@link RowWriter#set(String, Object)} for each non-null field and {@link RowWriter#endRow()}. Every HTTP
     * response body is closed on all paths (success, non-2xx status, parse failure, writer failure).
     *
     * @param writer
     *            the row writer to receive the data
     * @throws IOException
     *             if an HTTP, JSON or pagination error occurs, including reaching the page limit while the API
     *             announces further pages
     * @throws InterruptedException
     *             if the thread is interrupted; the thread's interrupt flag is left set when the interruption
     *             was detected through it
     */
    public void streamTo(RowWriter writer) throws IOException, InterruptedException
    {
        final URI origin = PageUrls.parseHttp(baseUrl);
        final boolean credentials = authHeaders != null && !authHeaders.isEmpty();
        final PaginationType type = paginationConfig == null ? null : paginationConfig.getTypeEnum();
        final Set<String> seen = new HashSet<>();
        seen.add(fingerprint(origin.toString()));

        long offset = paginationConfig != null ? paginationConfig.getInitialOffset() : 0;
        long page = paginationConfig != null ? paginationConfig.getInitialPage() : 1;
        String cursor = null;
        URI nextUri = null;
        int pagesFetched = 0;

        pageParser.resetFailureCounts();
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Interrupted before requesting the next page");
                }
                final URI requestUri = buildPageUri(origin, offset, page, cursor, nextUri);
                pagesFetched++;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Fetching page {} from {}", pagesFetched, PageUrls.redact(requestUri));
                }

                final Fetched fetched = fetch(sender, requestUri, origin, authHeaders, acceptHeader);
                String linkReference = null;
                try (InputStream body = fetched.response.body()) {
                    checkStatus(fetched, false);
                    if (type == PaginationType.LINK_HEADER) {
                        linkReference = LinkHeaderParser.findNext(fetched.response.headers().allValues("Link"));
                    }
                    try (JsonParser jsonParser = PageParser.open(body)) {
                        pageParser.parse(jsonParser, writer);
                    }
                } catch (JsonProcessingException e) {
                    throw invalidJson(fetched.uri, e);
                } catch (InterruptedIOException e) {
                    throw ioOrInterrupted(e);
                }

                // Decide whether, and where, to continue
                final int records = pageParser.recordCount();
                boolean more = false;
                cursor = null;
                nextUri = null;
                if (type == PaginationType.OFFSET && paginationConfig != null) {
                    offset += paginationConfig.getPageSize();
                    more = records >= paginationConfig.getPageSize();
                } else if (type == PaginationType.PAGE && paginationConfig != null) {
                    page++;
                    more = records >= paginationConfig.getPageSize();
                } else if (type == PaginationType.CURSOR) {
                    final String found = pageParser.nextCursor();
                    if (found != null && !found.isEmpty()) {
                        requireUsable(found, seen, "cursor");
                        cursor = found;
                        more = true;
                    }
                } else if (type == PaginationType.LINK_HEADER || type == PaginationType.NEXT_URL) {
                    final String reference = type == PaginationType.LINK_HEADER ? linkReference
                            : pageParser.nextPageUrl();
                    if (reference != null && !reference.isEmpty()) {
                        nextUri = PageUrls.resolveNext(fetched.uri, origin, reference.trim(), credentials);
                        requireUsable(nextUri.toString(), seen, "next-page URL");
                        more = true;
                    }
                }
                if (!more) {
                    return;
                }
                if (pagesFetched >= maxPages) {
                    throw new IOException("Stopped after the maximum of " + maxPages
                            + " pages although the API announces more data; the result would be incomplete");
                }
            }
        } finally {
            pageParser.logFailureSummary();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void close()
    {
        // No persistent resources to close in this stateless implementation
    }

    // ---- package-visible static helpers ----

    /**
     * Fetches all values of a single field from a lookup endpoint.
     *
     * <p>Used by {@link RestInputInteraction} when a table declares a {@code $path_key}: the engine calls this
     * method against the lookup {@code source_path} to collect the possible key values.
     *
     * <p>The response is parsed incrementally (no JSON tree), but the method returns a {@code List<String>}, so the
     * extracted values themselves are necessarily retained: memory is proportional to the number and size of the
     * returned values, not to the size of the response. Pagination is not supported for lookup endpoints.
     *
     * <p>Behaviour notes: an object whose field is missing, {@code null}, empty or a JSON object/array is skipped;
     * a {@code dataPath} that is missing or holds a scalar is an {@link IOException}.
     *
     * @param url
     *            the fully-assembled lookup URL (base URL + source_path)
     * @param dataPath
     *            optional dot-notation path to the array inside the response; {@code null} for top-level arrays
     * @param fieldName
     *            the JSON field name (dot-notation for nested objects) whose value to collect from each object
     * @param authHeaders
     *            optional HTTP authentication headers; may be {@code null}
     * @param acceptHeader
     *            value for the HTTP {@code Accept} header
     * @return a list of string values (may be empty; never {@code null})
     * @throws IOException
     *             if the HTTP request fails or the response cannot be parsed
     * @throws InterruptedException
     *             if the thread is interrupted during the HTTP request
     */
    static List<String> fetchStringValues(String url, String dataPath, String fieldName,
            Map<String, String> authHeaders, String acceptHeader)
            throws IOException, InterruptedException
    {
        return fetchStringValues(DEFAULT_SENDER, url, dataPath, fieldName, authHeaders, acceptHeader);
    }

    static List<String> fetchStringValues(HttpSender sender, String url, String dataPath, String fieldName,
            Map<String, String> authHeaders, String acceptHeader)
            throws IOException, InterruptedException
    {
        final URI origin = PageUrls.parseHttp(url);
        final PageParser parser = new PageParser(FieldPlan.forLookup(fieldName), dataPath, null, null, false);
        final LookupCollector collector = new LookupCollector();
        final Fetched fetched = fetch(sender, origin, origin, authHeaders,
                acceptHeader != null ? acceptHeader : DEFAULT_ACCEPT);
        consume(fetched, parser, collector, true);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Fetched {} value(s) of field '{}' from {}", collector.values.size(), fieldName,
                    PageUrls.redact(fetched.uri));
        }
        return collector.values;
    }

    /** Collects the single text value written per row; empty values are dropped. */
    private static final class LookupCollector implements RowWriter
    {
        private final List<String> values = new ArrayList<>();

        @Override
        public void startRow()
        {
            // nothing to prepare
        }

        @Override
        public void set(String fieldName, Object value)
        {
            if (value instanceof String && !((String) value).isEmpty()) {
                values.add((String) value);
            }
        }

        @Override
        public void endRow()
        {
            // nothing to finish
        }
    }

    // ---- request execution ----

    /**
     * Sends a GET request and follows redirects manually (at most {@value #MAX_REDIRECTS}). Authentication
     * headers are attached only while the request goes to the origin of the first request, so credentials are never
     * forwarded to another host. The returned response body is open and must be closed by the caller.
     */
    static Fetched fetch(HttpSender sender, URI start, URI origin, Map<String, String> authHeaders,
            String accept) throws IOException, InterruptedException
    {
        URI current = start;
        int redirects = 0;
        while (true) {
            final HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(current)
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .header("Accept", accept)
                    .header("User-Agent", "CP4D-REST-Connector/1.0")
                    .GET();
            if (authHeaders != null && PageUrls.sameOrigin(origin, current)) {
                authHeaders.forEach(builder::header);
            }
            final HttpResponse<InputStream> response = sender.send(builder.build());
            final String location = isRedirect(response.statusCode())
                    ? response.headers().firstValue("Location").orElse(null)
                    : null;
            if (location == null) {
                return new Fetched(response, current);
            }
            closeQuietly(response);
            if (redirects >= MAX_REDIRECTS) {
                throw new IOException("Too many redirects (limit " + MAX_REDIRECTS + ") from "
                        + PageUrls.redact(start));
            }
            redirects++;
            current = PageUrls.resolveNext(current, origin, location, false);
        }
    }

    private static void checkStatus(Fetched fetched, boolean lookup) throws IOException
    {
        final int status = fetched.response.statusCode();
        if (status / 100 != HTTP_STATUS_2XX) {
            throw new IOException((lookup ? "Lookup request failed with HTTP " : "HTTP request failed with status ")
                    + status + " for " + PageUrls.redact(fetched.uri));
        }
    }

    /**
     * Checks the status, then parses the body into {@code writer}. The body is closed on every path: success,
     * non-2xx status, malformed JSON, conversion failure and writer failure.
     */
    private static void consume(Fetched fetched, PageParser parser, RowWriter writer, boolean lookup)
            throws IOException, InterruptedException
    {
        try (InputStream body = fetched.response.body()) {
            checkStatus(fetched, lookup);
            try (JsonParser jsonParser = PageParser.open(body)) {
                parser.parse(jsonParser, writer);
            }
        } catch (JsonProcessingException e) {
            throw invalidJson(fetched.uri, e);
        } catch (InterruptedIOException e) {
            throw ioOrInterrupted(e);
        }
    }

    private static boolean isRedirect(int status)
    {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static void closeQuietly(HttpResponse<InputStream> response)
    {
        try (InputStream ignored = response.body()) {
            // closing releases the connection; the redirect body is not needed
        } catch (IOException e) {
            LOGGER.debug("Ignoring failure while closing a redirect response body");
        }
    }

    /**
     * Distinguishes a real thread interruption from a timeout or other interrupted I/O. The interrupt flag is not
     * cleared here, so callers up the stack still see it.
     */
    private static IOException ioOrInterrupted(InterruptedIOException e) throws InterruptedException
    {
        if (Thread.currentThread().isInterrupted()) {
            final InterruptedException interrupted = new InterruptedException("Interrupted while reading the response");
            interrupted.initCause(e);
            throw interrupted;
        }
        return new IOException("Reading the response timed out or was interrupted: " + e.getClass().getSimpleName(), e);
    }

    /** Never includes response content: Jackson's message may quote the offending token. */
    private static IOException invalidJson(URI uri, JsonProcessingException e)
    {
        final StringBuilder message = new StringBuilder("Invalid JSON in the response from ")
                .append(PageUrls.redact(uri));
        if (e.getLocation() != null) {
            message.append(" at line ").append(e.getLocation().getLineNr())
                    .append(", column ").append(e.getLocation().getColumnNr());
        }
        if (e instanceof StreamConstraintsException) {
            message.append(": ").append(e.getOriginalMessage());
        }
        return new IOException(message.toString());
    }

    // ---- pagination ----

    private static void validate(PaginationConfig config)
    {
        if (config == null) {
            return;
        }
        final PaginationType type = config.getTypeEnum();
        if (type == null) {
            throw new IllegalArgumentException("Pagination type is missing. Valid types: "
                    + PaginationType.validValues());
        }
        final boolean limitSent = limitParam(config) != null;
        switch (type) {
        case OFFSET:
            require(config.getOffsetParam(), "offset_param", type);
            requirePositivePageSize(config, type);
            if (config.getInitialOffset() < 0) {
                throw new IllegalArgumentException("Pagination type 'offset' requires initial_offset >= 0");
            }
            break;
        case PAGE:
            require(config.getPageParam(), "page_param", type);
            requirePositivePageSize(config, type);
            if (config.getInitialPage() < 0) {
                throw new IllegalArgumentException("Pagination type 'page' requires initial_page >= 0");
            }
            break;
        case CURSOR:
            require(config.getCursorParam(), "cursor_param", type);
            require(config.getNextCursorPath(), "next_cursor_path", type);
            break;
        case NEXT_URL:
            require(config.getNextUrlPath(), "next_url_path", type);
            break;
        case LINK_HEADER:
        default:
            break;
        }
        if (limitSent) {
            requirePositivePageSize(config, type);
        }
    }

    private static void require(String value, String name, PaginationType type)
    {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Pagination type '" + type.getValue() + "' requires '" + name + "'");
        }
    }

    private static void requirePositivePageSize(PaginationConfig config, PaginationType type)
    {
        if (config.getPageSize() <= 0) {
            throw new IllegalArgumentException("Pagination type '" + type.getValue()
                    + "' requires page_size > 0 but it is " + config.getPageSize());
        }
    }

    private static String limitParam(PaginationConfig config)
    {
        final String limit = config.getLimitParam();
        return isBlank(limit) ? null : limit;
    }

    private static boolean isBlank(String value)
    {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private URI buildPageUri(URI origin, long offset, long page, String cursor, URI nextUri) throws IOException
    {
        if (paginationConfig == null) {
            return origin;
        }
        if (nextUri != null) {
            return nextUri; // validated server-supplied URL, used exactly as given
        }
        final String limit = limitParam(paginationConfig);
        final String size = Integer.toString(paginationConfig.getPageSize());
        final String url = origin.toString();
        switch (paginationConfig.getTypeEnum()) {
        case OFFSET:
            return PageUrls.parseHttp(PageUrls.withParams(url, paginationConfig.getOffsetParam(),
                    Long.toString(offset), limit, size));
        case PAGE:
            return PageUrls.parseHttp(PageUrls.withParams(url, paginationConfig.getPageParam(),
                    Long.toString(page), limit, size));
        case CURSOR:
            return PageUrls.parseHttp(PageUrls.withParams(url,
                    cursor != null ? paginationConfig.getCursorParam() : null, cursor, limit, size));
        case LINK_HEADER:
        case NEXT_URL:
        default:
            return limit == null ? origin : PageUrls.parseHttp(PageUrls.withParams(url, limit, size));
        }
    }

    /**
     * Rejects oversized continuation values and values seen before in this stream (pagination loop). Only a
     * digest of each value is remembered, not the value itself.
     */
    private static void requireUsable(String value, Set<String> seen, String what) throws IOException
    {
        if (value.length() > MAX_CONTINUATION_LENGTH) {
            throw new IOException("The " + what + " returned by the API is longer than "
                    + MAX_CONTINUATION_LENGTH + " characters");
        }
        if (!seen.add(fingerprint(value))) {
            throw new IOException("The API returned the same " + what
                    + " twice; stopping to avoid an endless pagination loop");
        }
    }

    private static String fingerprint(String value)
    {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(digest.length * 2);
            for (final byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
