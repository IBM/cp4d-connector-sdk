/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import javax.net.ssl.SSLSession;

import com.ibm.connect.restconnector.JsonToArrowStream.HttpSender;
import com.ibm.wdp.connect.sdk.connector.RowWriter;

/** Shared fixtures for the streaming tests: scripted HTTP responses with close tracking and a collecting writer. */
final class StreamTestSupport
{
    private StreamTestSupport()
    {
    }

    /** An input stream that records whether it was closed. */
    @SuppressWarnings("PMD.AvoidUsingVolatile") // read from the asserting thread after the stream thread closed it
    static final class TrackingStream extends FilterInputStream
    {
        private volatile boolean closed;

        TrackingStream(InputStream in)
        {
            super(in);
        }

        @Override
        public void close() throws IOException
        {
            closed = true;
            super.close();
        }

        boolean isClosed()
        {
            return closed;
        }
    }

    /** A canned response. */
    static final class Reply
    {
        final int status;
        final Map<String, List<String>> headers = new LinkedHashMap<>();
        final InputStream body;

        Reply(int status, InputStream body)
        {
            this.status = status;
            this.body = body;
        }

        @SuppressWarnings("PMD.ShortMethodName")
        static Reply ok(String json)
        {
            return new Reply(200, new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        }

        static Reply status(int status, String body)
        {
            return new Reply(status, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        }

        @SuppressWarnings("PMD.ShortMethodName")
        static Reply of(int status, InputStream body)
        {
            return new Reply(status, body);
        }

        Reply header(String name, String value)
        {
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            return this;
        }
    }

    /** Sends scripted replies and keeps every request and every handed-out body for later assertions. */
    static final class ScriptedSender implements HttpSender
    {
        final List<HttpRequest> requests = new ArrayList<>();
        final List<TrackingStream> bodies = new ArrayList<>();
        /** Set to false for large runs, so that the history does not itself grow with the page count. */
        @SuppressWarnings("PMD.AvoidUsingVolatile")
        volatile boolean keepHistory = true;
        private final Function<HttpRequest, Reply> script;
        private final AtomicInteger calls = new AtomicInteger();

        ScriptedSender(Function<HttpRequest, Reply> script)
        {
            this.script = script;
        }

        /** Replies in order; the last reply is not repeated, a further request fails the test. */
        static ScriptedSender sequence(Reply... replies)
        {
            final List<Reply> list = Arrays.asList(replies);
            final AtomicInteger next = new AtomicInteger();
            return new ScriptedSender(request -> {
                final int i = next.getAndIncrement();
                if (i >= list.size()) {
                    throw new AssertionError("Unexpected request #" + (i + 1) + ": " + request.uri());
                }
                return list.get(i);
            });
        }

        @Override
        public HttpResponse<InputStream> send(HttpRequest request) throws IOException, InterruptedException
        {
            calls.incrementAndGet();
            final Reply reply = script.apply(request);
            @SuppressWarnings("PMD.CloseResource") // handed to the code under test, which closes it
            final TrackingStream body = new TrackingStream(reply.body);
            if (keepHistory) {
                requests.add(request);
                bodies.add(body);
            }
            return new FakeResponse(request, reply, body);
        }

        int calls()
        {
            return calls.get();
        }

        boolean allBodiesClosed()
        {
            return bodies.stream().allMatch(TrackingStream::isClosed);
        }

        String uri(int i)
        {
            return requests.get(i).uri().toString();
        }
    }

    private static final class FakeResponse implements HttpResponse<InputStream>
    {
        private final HttpRequest request;
        private final Reply reply;
        private final InputStream body;

        FakeResponse(HttpRequest request, Reply reply, InputStream body)
        {
            this.request = request;
            this.reply = reply;
            this.body = body;
        }

        @Override
        public int statusCode()
        {
            return reply.status;
        }

        @Override
        public HttpRequest request()
        {
            return request;
        }

        @Override
        public Optional<HttpResponse<InputStream>> previousResponse()
        {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers()
        {
            return HttpHeaders.of(reply.headers, (a, b) -> true);
        }

        @Override
        public InputStream body()
        {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession()
        {
            return Optional.empty();
        }

        @Override
        public URI uri()
        {
            return request.uri();
        }

        @Override
        public HttpClient.Version version()
        {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    /** Collects rows as ordered maps. */
    static final class CollectingWriter implements RowWriter
    {
        final List<Map<String, Object>> rows = new ArrayList<>();
        private Map<String, Object> current;

        @Override
        public void startRow()
        {
            if (current != null) {
                throw new AssertionError("startRow() while a row is open");
            }
            current = new LinkedHashMap<>();
        }

        @Override
        public void set(String fieldName, Object value)
        {
            if (current == null) {
                throw new AssertionError("set() outside of a row");
            }
            if (value == null) {
                throw new AssertionError("null values must be omitted, not set: " + fieldName);
            }
            current.put(fieldName, value);
        }

        @Override
        public void endRow()
        {
            if (current == null) {
                throw new AssertionError("endRow() without startRow()");
            }
            rows.add(current);
            current = null;
        }

        /** True if no row is currently open (every startRow was matched by an endRow). */
        boolean balanced()
        {
            return current == null;
        }
    }

    static RestFieldDefinition field(String name, String type)
    {
        return new RestFieldDefinition(name, type, false, false);
    }

    static List<RestFieldDefinition> fields(String... nameTypePairs)
    {
        final List<RestFieldDefinition> list = new ArrayList<>();
        for (int i = 0; i < nameTypePairs.length; i += 2) {
            list.add(field(nameTypePairs[i], nameTypePairs[i + 1]));
        }
        return list;
    }

    static PaginationConfig offset(int pageSize)
    {
        return new PaginationConfig(PaginationType.OFFSET, "offset", null, "limit", pageSize, 0, 1, null, null, null);
    }

    static PaginationConfig page(int pageSize)
    {
        return new PaginationConfig(PaginationType.PAGE, null, "page", "per_page", pageSize, 0, 1, null, null, null);
    }

    static PaginationConfig cursor(String cursorPath)
    {
        return new PaginationConfig(PaginationType.CURSOR, null, null, "limit", 100, 0, 1, "cursor", cursorPath, null);
    }

    static PaginationConfig nextUrl(String path)
    {
        return new PaginationConfig(PaginationType.NEXT_URL, null, null, null, 100, 0, 1, null, null, path);
    }

    static PaginationConfig linkHeader()
    {
        return new PaginationConfig(PaginationType.LINK_HEADER, null, null, null, 100, 0, 1, null, null, null);
    }

    /** Builds a JSON array of {@code n} small objects {@code {"id":i,"name":"n<i>"}} starting at {@code from}. */
    @SuppressWarnings("PMD.InsufficientStringBufferDeclaration") // capacity is computed from n
    static String idArray(int from, int n)
    {
        final StringBuilder sb = new StringBuilder(2 + n * 40).append('[');
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":").append(from + i).append(",\"name\":\"n").append(from + i).append("\"}");
        }
        return sb.append(']').toString();
    }

    static JsonToArrowStream stream(HttpSender sender, String url, String dataPath, List<RestFieldDefinition> fields,
            Map<String, String> auth, PaginationConfig pagination, boolean strict)
    {
        return new JsonToArrowStream(url, dataPath, fields, auth, pagination, "application/json", strict, sender,
                JsonToArrowStream.MAX_PAGES);
    }
}
