/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static com.ibm.connect.restconnector.StreamTestSupport.cursor;
import static com.ibm.connect.restconnector.StreamTestSupport.fields;
import static com.ibm.connect.restconnector.StreamTestSupport.idArray;
import static com.ibm.connect.restconnector.StreamTestSupport.linkHeader;
import static com.ibm.connect.restconnector.StreamTestSupport.nextUrl;
import static com.ibm.connect.restconnector.StreamTestSupport.offset;
import static com.ibm.connect.restconnector.StreamTestSupport.page;
import static com.ibm.connect.restconnector.StreamTestSupport.stream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import com.ibm.connect.restconnector.StreamTestSupport.CollectingWriter;
import com.ibm.connect.restconnector.StreamTestSupport.Reply;
import com.ibm.connect.restconnector.StreamTestSupport.ScriptedSender;
import com.ibm.wdp.connect.sdk.connector.RowWriter;

/** Pagination, request building, resource cleanup, security and interruption behaviour of {@link JsonToArrowStream}. */
public class TestJsonToArrowStream
{
    private static final String BASE = "https://api.example.test/items";
    private static final List<RestFieldDefinition> ID_NAME = fields("id", "INTEGER", "name", "VARCHAR(10)");

    @After
    public void clearInterruptFlag()
    {
        Thread.interrupted();
    }

    private static CollectingWriter run(ScriptedSender sender, String url, String dataPath, PaginationConfig pagination)
            throws Exception
    {
        return run(sender, url, dataPath, pagination, null);
    }

    private static CollectingWriter run(ScriptedSender sender, String url, String dataPath, PaginationConfig pagination,
            Map<String, String> auth) throws Exception
    {
        final CollectingWriter writer = new CollectingWriter();
        try (JsonToArrowStream s = stream(sender, url, dataPath, ID_NAME, auth, pagination, false)) {
            s.streamTo(writer);
        }
        return writer;
    }

    private static IOException expectIOException(ScriptedSender sender, String url, String dataPath,
            PaginationConfig pagination, CollectingWriter writer, Map<String, String> auth) throws Exception
    {
        IOException failure = null;
        try (JsonToArrowStream s = stream(sender, url, dataPath, ID_NAME, auth, pagination, false)) {
            s.streamTo(writer);
        } catch (IOException e) {
            failure = e;
        }
        assertNotNull("expected an IOException", failure);
        return failure;
    }

    // ---- no pagination ----

    @Test
    public void singleRequestWithoutPagination() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(idArray(1, 3)));
        final CollectingWriter w = run(sender, BASE, null, null);
        assertEquals(3, w.rows.size());
        assertEquals(1, sender.calls());
        assertEquals(BASE, sender.uri(0));
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void acceptAndAuthHeadersAreSent() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("[]"));
        run(sender, BASE, null, null, Collections.singletonMap("Authorization", "Bearer t"));
        assertEquals("application/json", sender.requests.get(0).headers().firstValue("Accept").orElse(null));
        assertEquals("Bearer t", sender.requests.get(0).headers().firstValue("Authorization").orElse(null));
    }

    @Test
    public void customAcceptHeader() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("[]"));
        final CollectingWriter w = new CollectingWriter();
        new JsonToArrowStream(BASE, null, ID_NAME, null, null, "application/vnd.api+json", false, sender,
                JsonToArrowStream.MAX_PAGES).streamTo(w);
        assertEquals("application/vnd.api+json", sender.requests.get(0).headers().firstValue("Accept").orElse(null));
    }

    @Test
    public void invalidBaseUrlIsAnIOException() throws Exception
    {
        for (final String url : new String[] { "not a url", "ftp://host/x", "file:///etc/passwd", "/relative" }) {
            final ScriptedSender sender = ScriptedSender.sequence();
            expectIOException(sender, url, null, null, new CollectingWriter(), null);
            assertEquals(0, sender.calls());
        }
    }

    // ---- offset / page ----

    @Test
    public void offsetPaginationStopsOnShortPage() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(idArray(0, 2)), Reply.ok(idArray(2, 2)),
                Reply.ok(idArray(4, 1)));
        final CollectingWriter w = run(sender, BASE, null, offset(2));
        assertEquals(5, w.rows.size());
        assertEquals(BASE + "?offset=0&limit=2", sender.uri(0));
        assertEquals(BASE + "?offset=2&limit=2", sender.uri(1));
        assertEquals(BASE + "?offset=4&limit=2", sender.uri(2));
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void offsetPaginationExactPageBoundaryNeedsOneEmptyPage() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(idArray(0, 2)), Reply.ok(idArray(2, 2)),
                Reply.ok("[]"));
        assertEquals(4, run(sender, BASE, null, offset(2)).rows.size());
        assertEquals(3, sender.calls());
    }

    @Test
    public void offsetPaginationEmptyFirstPage() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("[]"));
        assertEquals(0, run(sender, BASE, null, offset(2)).rows.size());
        assertEquals(1, sender.calls());
    }

    @Test
    public void offsetPaginationHonoursInitialOffset() throws Exception
    {
        final PaginationConfig config = new PaginationConfig(PaginationType.OFFSET, "start", null, "count", 10, 30, 1,
                null, null, null);
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(idArray(0, 1)));
        run(sender, BASE, null, config);
        assertEquals(BASE + "?start=30&count=10", sender.uri(0));
    }

    @Test
    public void offsetPaginationWithoutLimitParameter() throws Exception
    {
        final PaginationConfig config = new PaginationConfig(PaginationType.OFFSET, "offset", null, null, 2, 0, 1,
                null, null, null);
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(idArray(0, 2)), Reply.ok("[]"));
        run(sender, BASE, null, config);
        assertEquals(BASE + "?offset=0", sender.uri(0));
        assertEquals(BASE + "?offset=2", sender.uri(1));
    }

    @Test
    public void pagePaginationStartsAtInitialPage() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(idArray(0, 2)), Reply.ok(idArray(2, 1)));
        final CollectingWriter w = run(sender, BASE, null, page(2));
        assertEquals(3, w.rows.size());
        assertEquals(BASE + "?page=1&per_page=2", sender.uri(0));
        assertEquals(BASE + "?page=2&per_page=2", sender.uri(1));
    }

    @Test
    public void paginationParametersReplaceExistingOnesInsteadOfDuplicating() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(idArray(0, 2)), Reply.ok("[]"));
        run(sender, BASE + "?filter=a%26b&offset=99&limit=7&x=1", null, offset(2));
        assertEquals(BASE + "?filter=a%26b&x=1&offset=0&limit=2", sender.uri(0));
        assertEquals(BASE + "?filter=a%26b&x=1&offset=2&limit=2", sender.uri(1));
    }

    @Test
    public void existingQueryIsKeptAndSeparatorIsCorrect() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("[]"));
        run(sender, BASE + "?a=1", null, offset(2));
        assertEquals(BASE + "?a=1&offset=0&limit=2", sender.uri(0));
    }

    @Test
    public void parameterNamesAndValuesAreEncoded() throws Exception
    {
        final PaginationConfig config = new PaginationConfig(PaginationType.OFFSET, "page offset&x", null, "li mit",
                5, 0, 1, null, null, null);
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("[]"));
        run(sender, BASE, null, config);
        assertEquals(BASE + "?page%20offset%26x=0&li%20mit=5", sender.uri(0));
    }

    @Test
    public void fragmentIsDroppedFromPaginatedRequests() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("[]"));
        run(sender, BASE + "#frag", null, offset(2));
        assertEquals(BASE + "?offset=0&limit=2", sender.uri(0));
    }

    @Test
    public void maximumPagesReachedWithMoreDataIsAnError() throws Exception
    {
        final ScriptedSender sender = new ScriptedSender(r -> Reply.ok(idArray(0, 2)));
        final CollectingWriter w = new CollectingWriter();
        try (JsonToArrowStream s = new JsonToArrowStream(BASE, null, ID_NAME, null, offset(2), null, false, sender, 3)) {
            s.streamTo(w);
            fail("silent truncation must not happen");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("maximum of 3 pages"));
        }
        assertEquals(3, sender.calls());
        assertEquals(6, w.rows.size()); // rows delivered before the error remain written (no atomicity)
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void maximumPagesReachedWithoutMoreDataIsFine() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(idArray(0, 2)), Reply.ok(idArray(2, 1)));
        final CollectingWriter w = new CollectingWriter();
        new JsonToArrowStream(BASE, null, ID_NAME, null, offset(2), null, false, sender, 2).streamTo(w);
        assertEquals(3, w.rows.size());
    }

    @Test
    public void maximumPagesWithCursorWithoutNextCursorIsFine() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("{\"data\":[{\"id\":1}],\"next\":\"c\"}"),
                Reply.ok("{\"data\":[{\"id\":2}]}"));
        final CollectingWriter w = new CollectingWriter();
        new JsonToArrowStream(BASE, "data", ID_NAME, null, cursor("next"), null, false, sender, 2).streamTo(w);
        assertEquals(2, w.rows.size());
    }

    // ---- cursor ----

    @Test
    public void cursorPaginationWithMetadataAfterAndBeforeData() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok("{\"data\":[{\"id\":1},{\"id\":2}],\"meta\":{\"next\":\"c 1&\"}}"),
                Reply.ok("{\"meta\":{\"next\":\"c2\"},\"data\":[{\"id\":3}]}"),
                Reply.ok("{\"data\":[{\"id\":4}],\"meta\":{\"next\":null}}"));
        final CollectingWriter w = run(sender, BASE, "data", cursor("meta.next"));
        assertEquals(4, w.rows.size());
        assertEquals(BASE + "?limit=100", sender.uri(0));
        assertEquals(BASE + "?cursor=c%201%26&limit=100", sender.uri(1));
        assertEquals(BASE + "?cursor=c2&limit=100", sender.uri(2));
        assertEquals(3, sender.calls());
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void cursorPaginationStopsOnEmptyOrMissingCursor() throws Exception
    {
        for (final String body : new String[] { "{\"data\":[{\"id\":1}],\"next\":\"\"}", "{\"data\":[{\"id\":1}]}",
                "{\"data\":[{\"id\":1}],\"next\":null}" }) {
            final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(body));
            assertEquals(1, run(sender, BASE, "data", cursor("next")).rows.size());
        }
    }

    @Test
    public void cursorPaginationDoesNotRequestAfterFullPageWithoutCursor() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("{\"data\":" + idArray(0, 100) + "}"));
        assertEquals(100, run(sender, BASE, "data", cursor("next")).rows.size());
        assertEquals(1, sender.calls());
    }

    @Test
    public void repeatedCursorIsALoop() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("{\"data\":[{\"id\":1}],\"next\":\"c\"}"),
                Reply.ok("{\"data\":[{\"id\":2}],\"next\":\"c\"}"));
        final CollectingWriter w = new CollectingWriter();
        final IOException e = expectIOException(sender, BASE, "data", cursor("next"), w, null);
        assertTrue(e.getMessage(), e.getMessage().contains("same cursor twice"));
        assertEquals(2, sender.calls());
        assertFalse(e.getMessage().contains("\"c\""));
    }

    @Test
    public void oversizedCursorIsRejected() throws Exception
    {
        final char[] big = new char[JsonToArrowStream.MAX_CONTINUATION_LENGTH + 1];
        Arrays.fill(big, 'x');
        final ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok("{\"data\":[{\"id\":1}],\"next\":\"" + new String(big) + "\"}"));
        final IOException e = expectIOException(sender, BASE, "data", cursor("next"), new CollectingWriter(), null);
        assertTrue(e.getMessage(), e.getMessage().contains("longer than"));
    }

    @Test
    public void cursorIsNeverPutIntoMessages() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok("{\"data\":[{\"id\":1}],\"next\":\"SECRET-CURSOR\"}"), Reply.status(500, "boom"));
        final IOException e = expectIOException(sender, BASE, "data", cursor("next"), new CollectingWriter(), null);
        assertFalse(e.getMessage(), e.getMessage().contains("SECRET-CURSOR"));
    }

    // ---- next_url ----

    @Test
    public void nextUrlPaginationWithAbsoluteAndRelativeUrls() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok("{\"results\":[{\"id\":1}],\"paging\":{\"next\":\"https://api.example.test/items?page=2\"}}"),
                Reply.ok("{\"paging\":{\"next\":\"/items?page=3\"},\"results\":[{\"id\":2}]}"),
                Reply.ok("{\"results\":[{\"id\":3}],\"paging\":{\"next\":\"?page=4\"}}"),
                Reply.ok("{\"results\":[{\"id\":4}],\"paging\":{\"next\":null}}"));
        final CollectingWriter w = run(sender, BASE, "results", nextUrl("paging.next"));
        assertEquals(4, w.rows.size());
        assertEquals(BASE, sender.uri(0));
        assertEquals(BASE + "?page=2", sender.uri(1));
        assertEquals(BASE + "?page=3", sender.uri(2));
        assertEquals(BASE + "?page=4", sender.uri(3));
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void nextUrlRelativeToTheCurrentDirectory() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok("{\"results\":[{\"id\":1}],\"next\":\"page2.json\"}"), Reply.ok("{\"results\":[]}"));
        run(sender, "https://api.example.test/v1/list/page1.json", "results", nextUrl("next"));
        assertEquals("https://api.example.test/v1/list/page2.json", sender.uri(1));
    }

    @Test
    public void nextUrlIsUsedExactlyAsGiven() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok("{\"results\":[{\"id\":1}],\"next\":\"https://api.example.test/items?after=a%2Bb&limit=5\"}"),
                Reply.ok("{\"results\":[]}"));
        run(sender, BASE + "?limit=100", "results", nextUrl("next"));
        assertEquals("https://api.example.test/items?after=a%2Bb&limit=5", sender.uri(1));
    }

    @Test
    public void nextUrlLoopsAreDetected() throws Exception
    {
        ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok("{\"results\":[{\"id\":1}],\"next\":\"/items?p=2\"}"),
                Reply.ok("{\"results\":[{\"id\":2}],\"next\":\"/items?p=2\"}"));
        IOException e = expectIOException(sender, BASE, "results", nextUrl("next"), new CollectingWriter(), null);
        assertTrue(e.getMessage(), e.getMessage().contains("same next-page URL twice"));

        // pointing back at the first page
        sender = ScriptedSender.sequence(Reply.ok("{\"results\":[{\"id\":1}],\"next\":\"" + BASE + "\"}"));
        e = expectIOException(sender, BASE, "results", nextUrl("next"), new CollectingWriter(), null);
        assertTrue(e.getMessage().contains("same next-page URL twice"));
    }

    @Test
    public void nextUrlOnAnotherOriginIsRejectedWhenCredentialsAreConfigured() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok("{\"results\":[{\"id\":1}],\"next\":\"https://evil.example.test/steal\"}"));
        final CollectingWriter w = new CollectingWriter();
        final IOException e = expectIOException(sender, BASE, "results", nextUrl("next"), w,
                Collections.singletonMap("Authorization", "Bearer t"));
        assertTrue(e.getMessage(), e.getMessage().contains("different origin"));
        assertEquals(1, sender.calls()); // the foreign URL was never requested
        assertFalse(e.getMessage().contains("evil"));
    }

    @Test
    public void nextUrlOnAnotherPortOrSchemeCountsAsAnotherOrigin() throws Exception
    {
        for (final String next : new String[] { "https://api.example.test:8443/x", "https://API.example.test:444/x" }) {
            final ScriptedSender sender = ScriptedSender.sequence(
                    Reply.ok("{\"results\":[{\"id\":1}],\"next\":\"" + next + "\"}"));
            expectIOException(sender, BASE, "results", nextUrl("next"), new CollectingWriter(),
                    Collections.singletonMap("Authorization", "x"));
        }
    }

    @Test
    public void nextUrlOnSameOriginKeepsCredentials() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok("{\"results\":[{\"id\":1}],\"next\":\"HTTPS://API.example.test:443/items?p=2\"}"),
                Reply.ok("{\"results\":[]}"));
        run(sender, BASE, "results", nextUrl("next"), Collections.singletonMap("Authorization", "Bearer t"));
        assertEquals("Bearer t", sender.requests.get(1).headers().firstValue("Authorization").orElse(null));
    }

    @Test
    public void nextUrlOnAnotherOriginIsAllowedWithoutCredentialsAndNoneAreSent() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok("{\"results\":[{\"id\":1}],\"next\":\"https://other.example.test/items?p=2\"}"),
                Reply.ok("{\"results\":[]}"));
        run(sender, BASE, "results", nextUrl("next"));
        assertEquals("https://other.example.test/items?p=2", sender.uri(1));
        assertFalse(sender.requests.get(1).headers().firstValue("Authorization").isPresent());
    }

    @Test
    public void unsafeNextUrlsAreRejected() throws Exception
    {
        final String[] bad = { "http://api.example.test/items?p=2", // https -> http downgrade
                "https://user:pw@api.example.test/items", // user information
                "file:///etc/passwd", "ftp://api.example.test/x", "javascript:alert(1)", "mailto:a@b.test",
                "https:///nohost", "https://api.example.test/a b" };
        for (final String next : bad) {
            final ScriptedSender sender = ScriptedSender.sequence(
                    Reply.ok("{\"results\":[{\"id\":1}],\"next\":\"" + next + "\"}"));
            final IOException e = expectIOException(sender, BASE, "results", nextUrl("next"), new CollectingWriter(),
                    null);
            assertEquals(next, 1, sender.calls());
            assertFalse(next, e.getMessage().contains("pw"));
            assertTrue(sender.allBodiesClosed());
        }
    }

    @Test
    public void httpToHttpsUpgradeIsAllowed() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok("{\"results\":[{\"id\":1}],\"next\":\"https://api.example.test/items?p=2\"}"),
                Reply.ok("{\"results\":[]}"));
        run(sender, "http://api.example.test/items", "results", nextUrl("next"));
        assertEquals("https://api.example.test/items?p=2", sender.uri(1));
    }

    // ---- Link header ----

    @Test
    public void linkHeaderPagination() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok(idArray(0, 2)).header("Link", "<https://api.example.test/items?page=2>; rel=\"next\""),
                Reply.ok(idArray(2, 2)).header("Link", "</items?page=3>; rel=\"prev\"")
                        .header("Link", "<a,b>; rel=\"last\", </items?page=3&x=a,b>; rel=\"next\""),
                Reply.ok(idArray(4, 1)).header("Link", "</items?page=2>; rel=\"prev\""));
        final CollectingWriter w = run(sender, BASE, null, linkHeader());
        assertEquals(5, w.rows.size());
        assertEquals(BASE, sender.uri(0));
        assertEquals(BASE + "?page=2", sender.uri(1));
        assertEquals(BASE + "?page=3&x=a,b", sender.uri(2));
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void linkHeaderWithoutNextEndsPagination() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(idArray(0, 100)));
        assertEquals(100, run(sender, BASE, null, linkHeader()).rows.size());
        assertEquals(1, sender.calls());
    }

    @Test
    public void linkHeaderToAnotherOriginWithCredentialsIsRejected() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok(idArray(0, 1)).header("Link", "<https://evil.example.test/x>; rel=next"));
        expectIOException(sender, BASE, null, linkHeader(), new CollectingWriter(),
                Collections.singletonMap("Authorization", "x"));
        assertEquals(1, sender.calls());
    }

    @Test
    public void linkHeaderLoopIsDetected() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(
                Reply.ok(idArray(0, 1)).header("Link", "</items?p=2>; rel=next"),
                Reply.ok(idArray(1, 1)).header("Link", "</items?p=2>; rel=next"));
        final IOException e = expectIOException(sender, BASE, null, linkHeader(), new CollectingWriter(), null);
        assertTrue(e.getMessage().contains("twice"));
    }

    // ---- configuration validation ----

    private static void assertInvalid(PaginationConfig config)
    {
        try {
            new JsonToArrowStream(BASE, null, ID_NAME, null, config, null, false);
            fail("expected IllegalArgumentException for " + config);
        } catch (IllegalArgumentException expected) {
            assertNull(expected.getCause());
        }
    }

    @Test
    public void invalidPaginationConfigurationIsRejectedUpFront() throws Exception
    {
        assertInvalid(new PaginationConfig(PaginationType.OFFSET, "offset", null, "limit", 0, 0, 1, null, null, null));
        assertInvalid(new PaginationConfig(PaginationType.OFFSET, "offset", null, "limit", -5, 0, 1, null, null, null));
        assertInvalid(new PaginationConfig(PaginationType.OFFSET, null, null, "limit", 10, 0, 1, null, null, null));
        assertInvalid(new PaginationConfig(PaginationType.OFFSET, " ", null, "limit", 10, 0, 1, null, null, null));
        assertInvalid(new PaginationConfig(PaginationType.OFFSET, "offset", null, "limit", 10, -1, 1, null, null, null));
        assertInvalid(new PaginationConfig(PaginationType.PAGE, null, null, "size", 10, 0, 1, null, null, null));
        assertInvalid(new PaginationConfig(PaginationType.PAGE, null, "page", "size", 0, 0, 1, null, null, null));
        assertInvalid(new PaginationConfig(PaginationType.PAGE, null, "page", "size", 10, 0, -1, null, null, null));
        assertInvalid(new PaginationConfig(PaginationType.CURSOR, null, null, null, 10, 0, 1, null, "next", null));
        assertInvalid(new PaginationConfig(PaginationType.CURSOR, null, null, null, 10, 0, 1, "cursor", null, null));
        assertInvalid(new PaginationConfig(PaginationType.CURSOR, null, null, "limit", 0, 0, 1, "cursor", "next", null));
        assertInvalid(new PaginationConfig(PaginationType.NEXT_URL, null, null, null, 10, 0, 1, null, null, null));
        assertInvalid(new PaginationConfig(PaginationType.NEXT_URL, null, null, "limit", 0, 0, 1, null, null, "next"));
        assertInvalid(new PaginationConfig(null, "offset", null, "limit", 10, 0, 1, null, null, null));
    }

    @Test
    public void invalidPathsAreRejectedUpFront() throws Exception
    {
        try {
            new JsonToArrowStream(BASE, "a..b", ID_NAME, null, null, null, false);
            fail();
        } catch (IllegalArgumentException expected) {
            // fine
        }
        try {
            new JsonToArrowStream(BASE, "data", ID_NAME, null, cursor("data.next"), null, false);
            fail();
        } catch (IllegalArgumentException expected) {
            // the cursor path must not lie inside the data node
        }
    }

    @Test
    public void noRequestIsMadeForAnInvalidConfiguration() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence();
        try {
            new JsonToArrowStream(BASE, null, ID_NAME, null,
                    new PaginationConfig(PaginationType.OFFSET, "offset", null, "limit", 0, 0, 1, null, null, null),
                    null, false, sender, 10);
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals(0, sender.calls());
        }
    }

    // ---- HTTP failures and cleanup ----

    @Test
    public void nonSuccessStatusClosesTheBodyAndHidesTheQuery() throws Exception
    {
        for (final int status : new int[] { 400, 401, 404, 429, 500, 503, 199, 300, 304 }) {
            final ScriptedSender sender = ScriptedSender.sequence(Reply.status(status, "{\"error\":\"nope\"}"));
            final CollectingWriter w = new CollectingWriter();
            final IOException e = expectIOException(sender, BASE + "?token=SECRET", null, null, w, null);
            assertTrue(e.getMessage(), e.getMessage().contains(String.valueOf(status)));
            assertFalse(e.getMessage(), e.getMessage().contains("SECRET"));
            assertTrue("status " + status, sender.allBodiesClosed());
            assertTrue(w.rows.isEmpty());
        }
    }

    @Test
    public void failureOnLaterPageKeepsEarlierRowsAndClosesEverything() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(idArray(0, 2)), Reply.status(502, "bad gateway"));
        final CollectingWriter w = new CollectingWriter();
        expectIOException(sender, BASE, null, offset(2), w, null);
        assertEquals(2, w.rows.size());
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void malformedJsonClosesTheBody() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("[{\"id\":1},{\"id\":"));
        final CollectingWriter w = new CollectingWriter();
        final IOException e = expectIOException(sender, BASE + "?key=SECRET", null, null, w, null);
        assertTrue(e.getMessage(), e.getMessage().contains("Invalid JSON"));
        assertFalse(e.getMessage().contains("SECRET"));
        assertEquals(1, w.rows.size());
        assertTrue(sender.allBodiesClosed());
        assertTrue(w.balanced());
    }

    @Test
    public void invalidJsonMessageDoesNotQuoteTheResponse() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("[{\"id\":1,\"name\":\"TOPSECRETDATA\" oops}]"));
        final IOException e = expectIOException(sender, BASE, null, null, new CollectingWriter(), null);
        assertFalse(e.getMessage(), e.getMessage().contains("TOPSECRETDATA"));
        assertFalse(e.getMessage(), e.getMessage().contains("oops"));
    }

    @Test
    public void missingDataPathClosesTheBody() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("{\"other\":[]}"));
        final IOException e = expectIOException(sender, BASE, "data.items", null, new CollectingWriter(), null);
        assertTrue(e.getMessage().contains("'data.items'"));
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void writerExceptionClosesTheBodyAndPropagates() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(idArray(0, 5)));
        final RowWriter failing = new RowWriter()
        {
            private int rows;

            @Override
            public void startRow()
            {
                // nothing
            }

            @Override
            public void set(String fieldName, Object value)
            {
                // nothing
            }

            @Override
            public void endRow()
            {
                rows++;
                if (rows == 3) {
                    throw new IllegalStateException("writer failed");
                }
            }
        };
        try (JsonToArrowStream s = stream(sender, BASE, null, ID_NAME, null, null, false)) {
            s.streamTo(failing);
            fail();
        } catch (IllegalStateException e) {
            assertEquals("writer failed", e.getMessage());
        }
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void strictConversionFailureClosesTheBody() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("[{\"id\":\"x\"}]"));
        try (JsonToArrowStream s = stream(sender, BASE, null, ID_NAME, null, null, true)) {
            s.streamTo(new CollectingWriter());
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("strict"));
        }
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void connectionFailureIsPropagated() throws Exception
    {
        final JsonToArrowStream.HttpSender failing = request -> {
            throw new ConnectException("refused");
        };
        try (JsonToArrowStream s = new JsonToArrowStream(BASE, null, ID_NAME, null, null, null, false, failing, 10)) {
            s.streamTo(new CollectingWriter());
            fail();
        } catch (ConnectException expected) {
            assertEquals("refused", expected.getMessage());
        }
    }

    // ---- interruption ----

    @Test
    public void interruptedSenderPropagatesInterruptedException() throws Exception
    {
        final JsonToArrowStream.HttpSender interrupted = request -> {
            throw new InterruptedException("stop");
        };
        try (JsonToArrowStream s = new JsonToArrowStream(BASE, null, ID_NAME, null, null, null, false, interrupted,
                10)) {
            s.streamTo(new CollectingWriter());
            fail();
        } catch (InterruptedException expected) {
            assertEquals("stop", expected.getMessage());
        }
    }

    @Test
    public void alreadyInterruptedThreadMakesNoRequest() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence();
        Thread.currentThread().interrupt();
        try (JsonToArrowStream s = stream(sender, BASE, null, ID_NAME, null, null, false)) {
            s.streamTo(new CollectingWriter());
            fail();
        } catch (InterruptedException expected) {
            assertTrue("the interrupt flag must be preserved", Thread.currentThread().isInterrupted());
        }
        assertEquals(0, sender.calls());
    }

    @Test
    public void interruptionBetweenPagesStopsPagination() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(idArray(0, 2)));
        final CollectingWriter inner = new CollectingWriter();
        final RowWriter interrupting = new RowWriter()
        {
            @Override
            public void startRow()
            {
                inner.startRow();
            }

            @Override
            public void set(String fieldName, Object value)
            {
                inner.set(fieldName, value);
            }

            @Override
            public void endRow()
            {
                inner.endRow();
                Thread.currentThread().interrupt();
            }
        };
        try (JsonToArrowStream s = stream(sender, BASE, null, ID_NAME, null, offset(2), false)) {
            s.streamTo(interrupting);
            fail();
        } catch (InterruptedException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        }
        assertEquals(1, sender.calls());
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void interruptionIsDetectedWhileStreamingRecords() throws Exception
    {
        final int total = 5000;
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(idArray(0, total)));
        final int[] rows = { 0 };
        final RowWriter interrupting = new RowWriter()
        {
            @Override
            public void startRow()
            {
                // nothing
            }

            @Override
            public void set(String fieldName, Object value)
            {
                // nothing
            }

            @Override
            public void endRow()
            {
                rows[0]++;
                if (rows[0] == 10) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        try (JsonToArrowStream s = stream(sender, BASE, null, ID_NAME, null, null, false)) {
            s.streamTo(interrupting);
            fail();
        } catch (InterruptedException expected) {
            assertTrue(rows[0] < total);
        }
        assertTrue(sender.allBodiesClosed());
    }

    /** A body whose read fails with {@link InterruptedIOException}. */
    private static InputStream interruptedBody(boolean setFlag)
    {
        return new InputStream()
        {
            @Override
            public int read() throws IOException
            {
                if (setFlag) {
                    Thread.currentThread().interrupt();
                }
                throw new InterruptedIOException("timed out");
            }
        };
    }

    @Test
    public void readTimeoutIsAnIOExceptionNotAnInterruption() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.of(200, interruptedBody(false)));
        final IOException e = expectIOException(sender, BASE, null, null, new CollectingWriter(), null);
        assertTrue(e.getMessage(), e.getMessage().contains("timed out"));
        assertFalse(Thread.currentThread().isInterrupted());
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void readInterruptedByThreadInterruptBecomesInterruptedException() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.of(200, interruptedBody(true)));
        try (JsonToArrowStream s = stream(sender, BASE, null, ID_NAME, null, null, false)) {
            s.streamTo(new CollectingWriter());
            fail();
        } catch (InterruptedException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        }
        assertTrue(sender.allBodiesClosed());
    }

    // ---- redirects ----

    private static Reply redirect(String location)
    {
        return Reply.status(302, "").header("Location", location);
    }

    @Test
    public void sameOriginRedirectKeepsCredentials() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(redirect("/moved/items"), Reply.ok(idArray(0, 1)));
        final CollectingWriter w = run(sender, BASE, null, null, Collections.singletonMap("Authorization", "Bearer t"));
        assertEquals(1, w.rows.size());
        assertEquals("https://api.example.test/moved/items", sender.uri(1));
        assertEquals("Bearer t", sender.requests.get(1).headers().firstValue("Authorization").orElse(null));
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void crossOriginRedirectWithholdsCredentials() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(redirect("https://cdn.example.test/data"),
                Reply.ok(idArray(0, 1)));
        final CollectingWriter w = run(sender, BASE, null, null, Collections.singletonMap("Authorization", "Bearer t"));
        assertEquals(1, w.rows.size());
        assertEquals("Bearer t", sender.requests.get(0).headers().firstValue("Authorization").orElse(null));
        assertFalse(sender.requests.get(1).headers().firstValue("Authorization").isPresent());
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void redirectToOtherOriginAndBackDoesNotLeakCredentialsInBetween() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(redirect("https://cdn.example.test/a"),
                redirect("https://api.example.test/b"), Reply.ok("[]"));
        run(sender, BASE, null, null, Collections.singletonMap("Authorization", "Bearer t"));
        assertFalse(sender.requests.get(1).headers().firstValue("Authorization").isPresent());
        assertTrue(sender.requests.get(2).headers().firstValue("Authorization").isPresent());
    }

    @Test
    public void redirectDowngradeAndUserInfoAreRejected() throws Exception
    {
        for (final String location : new String[] { "http://api.example.test/x", "https://u:p@api.example.test/x",
                "ftp://api.example.test/x" }) {
            final ScriptedSender sender = ScriptedSender.sequence(redirect(location));
            expectIOException(sender, BASE, null, null, new CollectingWriter(), null);
            assertEquals(1, sender.calls());
            assertTrue(sender.allBodiesClosed());
        }
    }

    @Test
    public void redirectLoopsAreLimited() throws Exception
    {
        final ScriptedSender sender = new ScriptedSender(r -> redirect("/again"));
        final IOException e = expectIOException(sender, BASE, null, null, new CollectingWriter(), null);
        assertTrue(e.getMessage(), e.getMessage().contains("Too many redirects"));
        assertEquals(JsonToArrowStream.MAX_REDIRECTS + 1, sender.calls());
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void redirectWithoutLocationIsTreatedAsFinalResponse() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.status(302, ""));
        final IOException e = expectIOException(sender, BASE, null, null, new CollectingWriter(), null);
        assertTrue(e.getMessage().contains("302"));
    }

    @Test
    public void linkHeaderIsResolvedAgainstTheRedirectedUrl() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(redirect("/v2/items"),
                Reply.ok(idArray(0, 1)).header("Link", "<?page=2>; rel=next"), Reply.ok("[]"));
        run(sender, BASE, null, linkHeader());
        assertEquals("https://api.example.test/v2/items?page=2", sender.uri(2));
    }

    // ---- fetchStringValues ----

    private static List<String> lookup(ScriptedSender sender, String dataPath, String field) throws Exception
    {
        return JsonToArrowStream.fetchStringValues(sender, BASE, dataPath, field, null, null);
    }

    @Test
    public void lookupCollectsScalarValues() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(
                "[{\"id\":1},{\"id\":\"b\"},{\"id\":null},{},{\"id\":\"\"},{\"id\":{\"x\":1}},{\"id\":[1]},{\"id\":true},"
                        + "{\"id\":2.50}]"));
        assertEquals(Arrays.asList("1", "b", "true", "2.5"), lookup(sender, null, "id"));
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void lookupWithNestedDataPathAndNestedField() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok(
                "{\"junk\":[{\"user\":{\"id\":\"x\"}}],\"response\":{\"items\":[{\"user\":{\"id\":\"u1\"}},"
                        + "{\"user\":{\"id\":\"u2\"}},{\"user\":5}]}}"));
        assertEquals(Arrays.asList("u1", "u2"), lookup(sender, "response.items", "user.id"));
    }

    @Test
    public void lookupEmptyResultAndRootObject() throws Exception
    {
        assertEquals(Collections.emptyList(), lookup(ScriptedSender.sequence(Reply.ok("[]")), null, "id"));
        assertEquals(Collections.singletonList("7"), lookup(ScriptedSender.sequence(Reply.ok("{\"id\":7}")), null, "id"));
    }

    @Test
    public void lookupErrorsCloseTheBody() throws Exception
    {
        ScriptedSender sender = ScriptedSender.sequence(Reply.status(500, "x"));
        try {
            lookup(sender, null, "id");
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Lookup request failed with HTTP 500"));
        }
        assertTrue(sender.allBodiesClosed());

        sender = ScriptedSender.sequence(Reply.ok("{\"a\":1}"));
        try {
            lookup(sender, "items", "id");
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("not found"));
        }
        assertTrue(sender.allBodiesClosed());

        sender = ScriptedSender.sequence(Reply.ok("[{\"id\":1},{"));
        try {
            lookup(sender, null, "id");
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Invalid JSON"));
        }
        assertTrue(sender.allBodiesClosed());
    }

    @Test
    public void lookupSendsAuthHeaders() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("[]"));
        JsonToArrowStream.fetchStringValues(sender, BASE, null, "id", Collections.singletonMap("X-Key", "k"), "text/json");
        assertEquals("k", sender.requests.get(0).headers().firstValue("X-Key").orElse(null));
        assertEquals("text/json", sender.requests.get(0).headers().firstValue("Accept").orElse(null));
    }

    // ---- misc ----

    @Test
    public void streamCanBeRunTwiceWithoutStateLeaking() throws Exception
    {
        final ScriptedSender sender = ScriptedSender.sequence(Reply.ok("{\"data\":[{\"id\":1}],\"next\":\"c\"}"),
                Reply.ok("{\"data\":[{\"id\":2}]}"), Reply.ok("{\"data\":[{\"id\":3}],\"next\":\"c\"}"),
                Reply.ok("{\"data\":[{\"id\":4}]}"));
        final CollectingWriter first = new CollectingWriter();
        final CollectingWriter second = new CollectingWriter();
        try (JsonToArrowStream s = stream(sender, BASE, "data", ID_NAME, null, cursor("next"), false)) {
            s.streamTo(first);
            s.streamTo(second); // "c" was seen in the first run only
        }
        assertEquals(2, first.rows.size());
        assertEquals(2, second.rows.size());
        assertEquals(BASE + "?limit=100", sender.uri(2));
    }

    @Test
    public void utf8BodyIsDecoded() throws Exception
    {
        final byte[] body = "[{\"id\":1,\"name\":\"\u00e4\u00f6\u00fc\u20ac\"}]".getBytes(StandardCharsets.UTF_8);
        final ScriptedSender sender = ScriptedSender.sequence(Reply.of(200, new ByteArrayInputStream(body)));
        assertEquals("\u00e4\u00f6\u00fc\u20ac", run(sender, BASE, null, null).rows.get(0).get("name"));
    }
}
