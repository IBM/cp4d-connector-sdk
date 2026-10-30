/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.dropbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;

/**
 * Unit tests for the ACL classification logic in {@link DropboxConnector}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>{@code buildAclResponse} static helper — ACL response structure and content</li>
 *   <li>ACL deduplication and empty list handling</li>
 * </ul>
 *
 * <p>All tests are self-contained and do NOT require a live Dropbox account.
 */
public class TestDropboxAclClassification
{

    // -----------------------------------------------------------------------
    // Helper: call the package-visible buildAclResponse via reflection
    // -----------------------------------------------------------------------

    /** Calls the package-private static {@code buildAclResponse(String, List, List, boolean)} via reflection. */
    @SuppressWarnings("unchecked")
    private static ConnectionActionResponse invokeBuildAclResponse(String path, List<String> users,
            List<String> groups) throws Exception
    {
        return invokeBuildAclResponse(path, users, groups, false);
    }

    /** Calls the package-private static {@code buildAclResponse(String, List, List, boolean)} via reflection. */
    @SuppressWarnings("unchecked")
    private static ConnectionActionResponse invokeBuildAclResponse(String path, List<String> users,
            List<String> groups, boolean inheritanceEnabled) throws Exception
    {
        final Method m = DropboxConnector.class.getDeclaredMethod("buildAclResponse", String.class, List.class,
                List.class, boolean.class);
        m.setAccessible(true);
        return (ConnectionActionResponse) m.invoke(null, path, users, groups, inheritanceEnabled);
    }

    // -----------------------------------------------------------------------
    // buildAclResponse structure tests
    // -----------------------------------------------------------------------

    /**
     * The ACL response must contain all required top-level keys with the correct
     * values and structure.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBuildAclResponseStructure() throws Exception
    {
        final ConnectionActionResponse response = invokeBuildAclResponse(
                "test/file.txt",
                Arrays.asList("user@example.com"),
                Arrays.asList("connectivity-test"));

        assertNotNull(response);
        assertEquals("test/file.txt", response.get("path"));
        assertEquals("allow", response.get("precedence"));

        final Map<String, Object> allow = (Map<String, Object>) response.get("allow");
        assertNotNull("allow must not be null", allow);
        assertEquals(Arrays.asList("user@example.com"), allow.get("users"));
        assertEquals(Arrays.asList("connectivity-test"), allow.get("groups"));

        final Map<String, Object> deny = (Map<String, Object>) response.get("deny");
        assertNotNull("deny must not be null", deny);
        assertTrue("deny.users must be empty", ((List<?>) deny.get("users")).isEmpty());
        assertTrue("deny.groups must be empty", ((List<?>) deny.get("groups")).isEmpty());

        final Map<String, Object> inheritance = (Map<String, Object>) response.get("inheritance");
        assertNotNull("inheritance must not be null", inheritance);
        assertFalse("inheritance.enabled must be false when inheritanceEnabled=false",
                (Boolean) inheritance.get("enabled"));
        assertEquals("parent", inheritance.get("parent_precedence"));
    }

    /**
     * When {@code inheritanceEnabled=true} the inheritance block must reflect that.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBuildAclResponseInheritanceEnabled() throws Exception
    {
        final ConnectionActionResponse response = invokeBuildAclResponse(
                "/subfolder",
                Collections.emptyList(),
                Collections.emptyList(),
                true);

        final Map<String, Object> inheritance = (Map<String, Object>) response.get("inheritance");
        assertNotNull("inheritance must not be null", inheritance);
        assertTrue("inheritance.enabled must be true when inheritanceEnabled=true",
                (Boolean) inheritance.get("enabled"));
    }

    /**
     * Empty user and group lists produce empty lists in the response (not null).
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBuildAclResponseEmptyLists() throws Exception
    {
        final ConnectionActionResponse response = invokeBuildAclResponse(
                "some/path.txt",
                Collections.emptyList(),
                Collections.emptyList());

        final Map<String, Object> allow = (Map<String, Object>) response.get("allow");
        assertNotNull("allow must not be null", allow);
        assertTrue("allow.users must be empty", ((List<?>) allow.get("users")).isEmpty());
        assertTrue("allow.groups must be empty", ((List<?>) allow.get("groups")).isEmpty());
    }

    /**
     * The ACL response must contain expected user from the allow list.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBuildAclResponseUserEmail() throws Exception
    {
        final ConnectionActionResponse response = invokeBuildAclResponse(
                "/ACL_TEST/ACL_TEST_File_1.txt",
                Arrays.asList("conndev@us.ibm.com"),
                Arrays.asList("connectivity-test"));

        final Map<String, Object> allow = (Map<String, Object>) response.get("allow");
        assertNotNull(allow);
        final List<String> users = (List<String>) allow.get("users");
        assertFalse("allow.users must not be empty", users.isEmpty());
        assertTrue("conndev@us.ibm.com must be in allow.users", users.contains("conndev@us.ibm.com"));
        final List<String> groups = (List<String>) allow.get("groups");
        assertFalse("allow.groups must not be empty", groups.isEmpty());
        assertTrue("connectivity-test must be in allow.groups", groups.contains("connectivity-test"));
    }

    /**
     * Multiple users are all included; no deduplication removes distinct entries.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBuildAclResponseMultipleUsers() throws Exception
    {
        final ConnectionActionResponse response = invokeBuildAclResponse(
                "folder/file.txt",
                Arrays.asList("alice@example.com", "bob@example.com"),
                Arrays.asList("team-alpha"));

        final Map<String, Object> allow = (Map<String, Object>) response.get("allow");
        assertNotNull(allow);
        final List<String> users = (List<String>) allow.get("users");
        assertEquals("Both users must appear", 2, users.size());
        assertTrue(users.contains("alice@example.com"));
        assertTrue(users.contains("bob@example.com"));
        assertEquals(Arrays.asList("team-alpha"), allow.get("groups"));
    }
}
