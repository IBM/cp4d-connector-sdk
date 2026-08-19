/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Unit tests for {@link RestInputInteraction#resolveTemplate(String, Map)}.
 *
 * <p>The method is package-private to allow direct testing without a full
 * connector stack.
 */
public class TestResolveTemplate
{
    // -------------------------------------------------------------------------
    // Happy path — single $var substitution
    // -------------------------------------------------------------------------

    /**
     * A simple Bearer-token template with one placeholder.
     */
    @Test
    public void testSingleVarSubstitution()
    {
        final Map<String, Object> props = props("bearer_token", "mytoken123");
        assertEquals("Bearer mytoken123",
                RestInputInteraction.resolveTemplate("Bearer $bearer_token", props));
    }

    /**
     * Template with no placeholders is returned unchanged.
     */
    @Test
    public void testNoPlaceholders()
    {
        assertEquals("application/json",
                RestInputInteraction.resolveTemplate("application/json", Collections.emptyMap()));
    }

    /**
     * Multiple distinct placeholders are all substituted.
     */
    @Test
    public void testMultipleVarSubstitutions()
    {
        final Map<String, Object> props = props("host", "example.com", "port", "8080");
        assertEquals("https://example.com:8080",
                RestInputInteraction.resolveTemplate("https://$host:$port", props));
    }

    /**
     * Placeholder at the very start of the template.
     */
    @Test
    public void testVarAtStart()
    {
        final Map<String, Object> props = props("api_key", "abc");
        assertEquals("abc", RestInputInteraction.resolveTemplate("$api_key", props));
    }

    /**
     * Placeholder whose value contains regex-special characters is handled safely.
     */
    @Test
    public void testVarValueWithSpecialCharacters()
    {
        final Map<String, Object> props = props("token", "abc$123\\def");
        assertEquals("Bearer abc$123\\def",
                RestInputInteraction.resolveTemplate("Bearer $token", props));
    }

    // -------------------------------------------------------------------------
    // base64(...) encoding
    // -------------------------------------------------------------------------

    /**
     * Basic-auth round-trip: base64($username:$password) produces a valid
     * Authorization header value.
     */
    @Test
    public void testBase64BasicAuthRoundTrip()
    {
        final Map<String, Object> props = props("username", "alice", "password", "s3cr3t");
        final String result = RestInputInteraction.resolveTemplate(
                "Basic base64($username:$password)", props);

        // Extract the encoded part and decode it to verify correctness
        assertEquals("Basic ", result.substring(0, 6));
        final String encoded = result.substring(6);
        final String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertEquals("alice:s3cr3t", decoded);
    }

    /**
     * A standalone base64(...) with a literal value (no $var) is encoded correctly.
     */
    @Test
    public void testBase64LiteralValue()
    {
        final String result = RestInputInteraction.resolveTemplate(
                "base64(hello:world)", Collections.emptyMap());
        final String expected = Base64.getEncoder()
                .encodeToString("hello:world".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, result);
    }

    /**
     * base64 applied after $var substitution — the placeholder is resolved first,
     * then the combined string is encoded.
     */
    @Test
    public void testBase64AppliedAfterVarSubstitution()
    {
        final Map<String, Object> props = props("user", "bob", "pass", "p@ss");
        final String result = RestInputInteraction.resolveTemplate(
                "Basic base64($user:$pass)", props);
        final String encoded = result.substring(6);
        final String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertEquals("bob:p@ss", decoded);
    }

    // -------------------------------------------------------------------------
    // Missing placeholder → null
    // -------------------------------------------------------------------------

    /**
     * A single placeholder that is absent in the props map returns null.
     */
    @Test
    public void testMissingPlaceholderReturnsNull()
    {
        assertNull(RestInputInteraction.resolveTemplate("Bearer $missing_token",
                Collections.emptyMap()));
    }

    /**
     * When one of multiple placeholders is missing, the whole result is null.
     */
    @Test
    public void testPartiallyMissingPlaceholderReturnsNull()
    {
        final Map<String, Object> props = props("username", "alice");
        // password is absent
        assertNull(RestInputInteraction.resolveTemplate(
                "Basic base64($username:$password)", props));
    }

    /**
     * If the placeholder is present but mapped to null, the result is null.
     */
    @Test
    public void testNullValueReturnsNull()
    {
        final Map<String, Object> props = new HashMap<>();
        props.put("token", null);
        assertNull(RestInputInteraction.resolveTemplate("Bearer $token", props));
    }

    // -------------------------------------------------------------------------
    // Custom-type end-to-end: multiple headers resolved correctly
    // -------------------------------------------------------------------------

    /**
     * Custom auth type with two distinct headers — both are resolved from props
     * and placed into the result map by {@code buildAuthHeaders} indirectly via
     * resolveTemplate calls in sequence.
     *
     * <p>This test exercises the full multi-header scenario by calling
     * {@code resolveTemplate} twice (mirroring what {@code buildAuthHeaders} does
     * for a custom auth config with two entries) and verifying both results.
     */
    @Test
    public void testCustomAuthMultipleHeaders()
    {
        final Map<String, Object> props = props(
                "x_api_key", "key-abc-123",
                "x_tenant",  "tenant-456");

        final String header1 = RestInputInteraction.resolveTemplate("$x_api_key", props);
        final String header2 = RestInputInteraction.resolveTemplate("$x_tenant", props);

        assertEquals("key-abc-123", header1);
        assertEquals("tenant-456",  header2);
    }

    /**
     * Custom auth with a static prefix around the placeholder.
     */
    @Test
    public void testCustomAuthWithPrefix()
    {
        final Map<String, Object> props = props("api_key", "my-key");
        assertEquals("ApiKey my-key",
                RestInputInteraction.resolveTemplate("ApiKey $api_key", props));
    }

    /**
     * Underscore-and-digit variable names are matched by the pattern.
     */
    @Test
    public void testVarNameWithUnderscoreAndDigits()
    {
        final Map<String, Object> props = props("token_v2", "tok");
        assertEquals("Bearer tok",
                RestInputInteraction.resolveTemplate("Bearer $token_v2", props));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Convenience builder for a {@code Map<String, Object>} from alternating key/value pairs. */
    private static Map<String, Object> props(Object... keyValues)
    {
        final Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }
}
