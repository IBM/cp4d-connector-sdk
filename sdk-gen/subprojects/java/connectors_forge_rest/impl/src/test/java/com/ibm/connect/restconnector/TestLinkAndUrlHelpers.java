/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;

import org.junit.Test;

/** {@link LinkHeaderParser} and {@link PageUrls}. */
public class TestLinkAndUrlHelpers
{
    private static final URI CURRENT = URI.create("https://api.example.test/v1/items?page=1");
    private static final URI ORIGIN = URI.create("https://api.example.test/v1/items");

    private static String next(String... headers)
    {
        return LinkHeaderParser.findNext(Arrays.asList(headers));
    }

    // ---- Link header ----

    @Test
    public void simpleNextLink()
    {
        assertEquals("https://x.test/p2", next("<https://x.test/p2>; rel=\"next\""));
        assertEquals("https://x.test/p2", next("<https://x.test/p2>; rel=next"));
        assertEquals("https://x.test/p2", next("<https://x.test/p2>;rel=NEXT"));
        assertEquals("https://x.test/p2", next("<https://x.test/p2> ; REL = \"next\""));
    }

    @Test
    public void nextAmongSeveralLinks()
    {
        assertEquals("/n", next("</p>; rel=\"prev\", </n>; rel=\"next\", </l>; rel=\"last\""));
        assertEquals("/n", next("</f>; rel=first", "</n>; rel=next"));
    }

    @Test
    public void commasInsideUriAndQuotedValues()
    {
        assertEquals("/n?x=a,b,c", next("</p?x=1,2>; rel=\"prev\", </n?x=a,b,c>; rel=\"next\""));
        assertEquals("/n", next("</p>; title=\"a, <b>; rel=next\"; rel=\"prev\", </n>; rel=\"next\""));
    }

    @Test
    public void relationLists()
    {
        assertEquals("/n", next("</n>; rel=\"prev next\""));
        assertEquals("/n", next("</n>; rel=\"NEXT\""));
        assertNull(next("</n>; rel=\"nextpage\""));
        assertNull(next("</n>; rel=\"previous\""));
    }

    @Test
    public void extraParametersAreIgnored()
    {
        assertEquals("/n", next("</n>; title=\"Next page\"; rel=\"next\"; type=\"application/json\""));
        assertEquals("/n", next("</n>; rel=\"next\"; title*=UTF-8''x"));
    }

    @Test
    public void noNextAndMalformedHeaders()
    {
        assertNull(next());
        assertNull(next(""));
        assertNull(next("</p>; rel=\"prev\""));
        assertNull(next("garbage"));
        assertNull(next("<unterminated; rel=next"));
        assertNull(next("</n>"));
        assertNull(next("<>; rel=next"));
        assertNull(next("</n>; rel"));
    }

    @Test
    public void malformedEntryDoesNotHideALaterValidOne()
    {
        assertEquals("/n", next("junk, </n>; rel=\"next\""));
    }

    // ---- withParams ----

    @Test
    public void withParamsAddsAndReplaces()
    {
        assertEquals("https://x.test/a?o=1&l=2", PageUrls.withParams("https://x.test/a", "o", "1", "l", "2"));
        assertEquals("https://x.test/a?q=1&o=1", PageUrls.withParams("https://x.test/a?q=1", "o", "1"));
        assertEquals("https://x.test/a?q=1&o=2", PageUrls.withParams("https://x.test/a?q=1&o=9", "o", "2"));
        assertEquals("https://x.test/a?o=2", PageUrls.withParams("https://x.test/a?o=9&o=8", "o", "2"));
        assertEquals("https://x.test/a?o=2", PageUrls.withParams("https://x.test/a?%6F=9", "o", "2"));
    }

    @Test
    public void withParamsKeepsOtherPairsVerbatim()
    {
        assertEquals("https://x.test/a?f=a%26b&flag&z=1&o=1",
                PageUrls.withParams("https://x.test/a?f=a%26b&flag&z=1", "o", "1"));
        assertEquals("https://x.test/a?o=1", PageUrls.withParams("https://x.test/a?", "o", "1"));
        assertEquals("https://x.test/a?o=1", PageUrls.withParams("https://x.test/a?&&", "o", "1"));
    }

    @Test
    public void withParamsEncodesAndSkipsNullNames()
    {
        assertEquals("https://x.test/a?o%20%26=%C3%A4%2B%3D", PageUrls.withParams("https://x.test/a", "o &", "\u00e4+="));
        assertEquals("https://x.test/a?l=5", PageUrls.withParams("https://x.test/a", null, null, "l", "5"));
        assertEquals("https://x.test/a", PageUrls.withParams("https://x.test/a"));
        assertEquals("https://x.test/a?o=1", PageUrls.withParams("https://x.test/a#frag", "o", "1"));
    }

    // ---- resolveNext / validate ----

    private static URI resolve(String ref, boolean credentials) throws IOException
    {
        return PageUrls.resolveNext(CURRENT, ORIGIN, ref, credentials);
    }

    @Test
    public void resolvesReferencesLikeRfc3986() throws Exception
    {
        assertEquals("https://api.example.test/v1/items?page=2", resolve("?page=2", true).toString());
        assertEquals("https://api.example.test/v1/other", resolve("other", true).toString());
        assertEquals("https://api.example.test/root", resolve("/root", true).toString());
        assertEquals("https://api.example.test/v1/x?a=1", resolve("x?a=1", true).toString());
        assertEquals("https://api.example.test/v1/items?p=3", resolve("  ?p=3  ", true).toString());
        assertEquals("https://api.example.test/p?q", resolve("//api.example.test/p?q", true).toString());
        assertEquals("https://other.test/p", resolve("https://other.test/p", false).toString());
    }

    @Test
    public void queryOnlyReferenceKeepsPathOfUrlWithoutQuery() throws Exception
    {
        final URI current = URI.create("https://api.example.test/v1/items");
        assertEquals("https://api.example.test/v1/items?p=2",
                PageUrls.resolveNext(current, current, "?p=2", true).toString());
    }

    private static void assertRejected(String ref, boolean credentials, String fragment)
    {
        try {
            resolve(ref, credentials);
            fail("expected rejection of " + ref);
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(fragment));
            assertFalse("the URL must not appear in the message", e.getMessage().contains("evil"));
            assertFalse(e.getMessage().contains("secret"));
        }
    }

    @Test
    public void rejectsUnsafeReferences() throws Exception
    {
        assertRejected("http://api.example.test/evil", false, "downgrade");
        assertRejected("ftp://evil.test/x", false, "scheme");
        assertRejected("file:///evil", false, "scheme");
        assertRejected("javascript:evil()", false, "scheme");
        assertRejected("https://user:secret@api.example.test/x", false, "user information");
        assertRejected("https://:secret@api.example.test/x", false, "user information");
        assertRejected("https://evil.test/x", true, "different origin");
        assertRejected("https://api.example.test:8443/evil", true, "different origin");
        assertRejected("http://evil.test/x", true, "downgrade");
        assertRejected("https://evil .test/x", false, "malformed");
    }

    @Test
    public void downgradeIsRejectedEvenForSameHost() throws Exception
    {
        assertRejected("http://api.example.test/x", true, "downgrade");
    }

    @Test
    public void originComparisonUsesDefaultPortsAndIgnoresCase() throws Exception
    {
        assertTrue(PageUrls.sameOrigin(URI.create("https://A.test/x"), URI.create("https://a.test:443/y")));
        assertTrue(PageUrls.sameOrigin(URI.create("http://a.test"), URI.create("http://a.test:80/")));
        assertFalse(PageUrls.sameOrigin(URI.create("http://a.test"), URI.create("https://a.test")));
        assertFalse(PageUrls.sameOrigin(URI.create("https://a.test"), URI.create("https://b.test")));
        assertFalse(PageUrls.sameOrigin(URI.create("https://a.test"), URI.create("https://a.test:8443")));
        assertFalse(PageUrls.sameOrigin(URI.create("/relative"), URI.create("https://a.test")));
    }

    @Test
    public void upgradeToHttpsIsAcceptedWithoutCredentials() throws Exception
    {
        final URI http = URI.create("http://api.example.test/items");
        assertEquals("https://api.example.test/items",
                PageUrls.resolveNext(http, http, "https://api.example.test/items", false).toString());
    }

    // ---- parseHttp / redact ----

    @Test
    public void parseHttpAcceptsOnlyAbsoluteHttpUrls() throws Exception
    {
        assertEquals("https://x.test/a", PageUrls.parseHttp(" https://x.test/a ").toString());
        for (final String bad : new String[] { "", "x", "/a", "ftp://x.test", "file:///a", "https://", "http:/a",
                "https://x.test/a b", "https://x.test/a\n" }) {
            try {
                PageUrls.parseHttp("https://x.test/a\n".equals(bad) ? "https://x.test/a b" : bad);
                fail("expected rejection of '" + bad + "'");
            } catch (IOException e) {
                assertFalse(e.getMessage().contains("x.test"));
            }
        }
        try {
            PageUrls.parseHttp(null);
            fail();
        } catch (IOException expected) {
            // fine
        }
    }

    @Test
    public void redactRemovesQueryUserInfoAndFragment()
    {
        assertEquals("https://x.test:8443/a/b",
                PageUrls.redact(URI.create("https://user:pw@x.test:8443/a/b?token=SECRET&c=d#frag")));
        assertEquals("https://x.test", PageUrls.redact(URI.create("https://x.test?k=v")));
        assertEquals("https://x.test/a", PageUrls.redact("https://x.test/a?k=v"));
        assertEquals("<invalid url>", PageUrls.redact("not a uri %%"));
    }
}
