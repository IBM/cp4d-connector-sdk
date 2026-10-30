/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.onedrive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.microsoft.graph.drives.DrivesRequestBuilder;
import com.microsoft.graph.drives.item.DriveItemRequestBuilder;
import com.microsoft.graph.drives.item.items.ItemsRequestBuilder;
import com.microsoft.graph.drives.item.items.item.DriveItemItemRequestBuilder;
import com.microsoft.graph.drives.item.items.item.permissions.PermissionsRequestBuilder;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.Identity;
import com.microsoft.graph.models.IdentitySet;
import com.microsoft.graph.models.Permission;
import com.microsoft.graph.models.PermissionCollectionResponse;
import com.microsoft.graph.models.SharePointIdentitySet;
import com.microsoft.graph.serviceclient.GraphServiceClient;

/**
 * Unit tests for the ACL classification logic in {@link OneDriveConnector}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>{@code identityWithEmail} static helper — email extraction from additionalData</li>
 *   <li>{@code buildAclResponse} static helper — ACL response structure</li>
 *   <li>{@code performGetAcl} — ACL permission classification into users/groups</li>
 * </ul>
 *
 * <p>All tests are self-contained and do NOT require a live OneDrive account.
 */
public class TestOneDriveAclClassification
{

    // -----------------------------------------------------------------------
    // Helper: create a minimal OneDriveConnector with mocked graph internals
    // -----------------------------------------------------------------------

    private static OneDriveConnector connectorWithMockedGraph(GraphServiceClient mockClient, String driveId)
            throws Exception
    {
        final ConnectionProperties connProps = new ConnectionProperties();
        connProps.put("tenant_id", "test");
        connProps.put("client_id", "test");
        connProps.put("username", "test@test.com");
        connProps.put("password", "test");

        final OneDriveConnector connector = new OneDriveConnector(connProps);

        final Field graphClientField = OneDriveConnector.class.getDeclaredField("graphClient");
        graphClientField.setAccessible(true);
        graphClientField.set(connector, mockClient);

        final Field driveIdField = OneDriveConnector.class.getDeclaredField("driveId");
        driveIdField.setAccessible(true);
        driveIdField.set(connector, driveId);

        return connector;
    }

    // -----------------------------------------------------------------------
    // identityWithEmail tests
    // -----------------------------------------------------------------------

    /**
     * When the identity has an email in additionalData, it is returned.
     */
    @Test
    public void testIdentityWithEmailReturnsEmailFromAdditionalData()
    {
        final Identity identity = new Identity();
        final Map<String, Object> extra = new HashMap<>();
        extra.put("email", "user@tenant.onmicrosoft.com");
        identity.setAdditionalData(extra);
        identity.setDisplayName("User Name");

        final String result = OneDriveConnector.identityWithEmail(identity);
        assertEquals("user@tenant.onmicrosoft.com", result);
    }

    /**
     * When additionalData has no email key, displayName is returned as fallback.
     */
    @Test
    public void testIdentityWithEmailFallsBackToDisplayName()
    {
        final Identity identity = new Identity();
        identity.setDisplayName("User Name");

        final String result = OneDriveConnector.identityWithEmail(identity);
        assertEquals("User Name", result);
    }

    /**
     * A null identity returns null.
     */
    @Test
    public void testIdentityWithEmailNullReturnsNull()
    {
        assertNull(OneDriveConnector.identityWithEmail(null));
    }

    // -----------------------------------------------------------------------
    // buildAclResponse tests
    // -----------------------------------------------------------------------

    /**
     * The ACL response must contain the required top-level keys with correct values.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBuildAclResponseStructure()
    {
        final ConnectionActionResponse response = OneDriveConnector.buildAclResponse(
                "test/file.docx",
                Arrays.asList("alice@example.com"),
                Arrays.asList("MyGroup"));

        assertNotNull(response);
        assertEquals("test/file.docx", response.get("path"));
        assertEquals("deny", response.get("precedence"));

        final Map<String, Object> allow = (Map<String, Object>) response.get("allow");
        assertNotNull(allow);
        assertEquals(Arrays.asList("alice@example.com"), allow.get("users"));
        assertEquals(Arrays.asList("MyGroup"), allow.get("groups"));

        final Map<String, Object> deny = (Map<String, Object>) response.get("deny");
        assertNotNull(deny);
        assertTrue(((List<?>) deny.get("users")).isEmpty());
        assertTrue(((List<?>) deny.get("groups")).isEmpty());

        final Map<String, Object> inheritance = (Map<String, Object>) response.get("inheritance");
        assertNotNull(inheritance);
        assertFalse("inheritance.enabled must be false", (Boolean) inheritance.get("enabled"));
        assertEquals("parent", inheritance.get("parent_precedence"));
    }

    // -----------------------------------------------------------------------
    // performGetAcl classification tests (via performAction + mocked Graph client)
    // -----------------------------------------------------------------------

    /**
     * A permission with grantedToV2.group set must have its displayName
     * added to allow.groups and not to allow.users.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testAzureAdGroupGoesToAllowGroups() throws Exception
    {
        final Identity group = new Identity();
        group.setDisplayName("Sales Team");

        final SharePointIdentitySet v2 = new SharePointIdentitySet();
        v2.setGroup(group);

        final Permission perm = new Permission();
        perm.setGrantedToV2(v2);

        final ConnectionActionResponse response = runAclWith("docs/report.docx", Arrays.asList(perm));

        final Map<String, Object> allow = (Map<String, Object>) response.get("allow");
        assertTrue("allow.users must be empty", ((List<?>) allow.get("users")).isEmpty());
        assertEquals("Sales Team must appear in allow.groups",
                Arrays.asList("Sales Team"), allow.get("groups"));
    }

    /**
     * A permission with grantedToV2.user set must have the email added to allow.users.
     * The email is extracted from additionalData.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testAzureAdUserGoesToAllowUsers() throws Exception
    {
        final Identity user = new Identity();
        final Map<String, Object> extra = new HashMap<>();
        extra.put("email", "AdeleV@36zvqr.onmicrosoft.com");
        user.setAdditionalData(extra);
        user.setDisplayName("Adele Vance");

        final SharePointIdentitySet v2 = new SharePointIdentitySet();
        v2.setUser(user);

        final Permission perm = new Permission();
        perm.setGrantedToV2(v2);

        final ConnectionActionResponse response = runAclWith("docs/report.docx", Arrays.asList(perm));

        final Map<String, Object> allow = (Map<String, Object>) response.get("allow");
        assertEquals("AdeleV@36zvqr.onmicrosoft.com must appear in allow.users",
                Arrays.asList("AdeleV@36zvqr.onmicrosoft.com"), allow.get("users"));
        assertTrue("allow.groups must be empty", ((List<?>) allow.get("groups")).isEmpty());
    }

    /**
     * A permission with only grantedTo.user (no grantedToV2) falls back to
     * reading grantedTo.user and adds its email to allow.users.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testFallbackGrantedToUserGoesToAllowUsers() throws Exception
    {
        final Identity user = new Identity();
        final Map<String, Object> extra = new HashMap<>();
        extra.put("email", "fallback@example.com");
        user.setAdditionalData(extra);

        final IdentitySet grantedTo = new IdentitySet();
        grantedTo.setUser(user);

        final Permission perm = new Permission();
        perm.setGrantedTo(grantedTo);

        final ConnectionActionResponse response = runAclWith("docs/file.docx", Arrays.asList(perm));

        final Map<String, Object> allow = (Map<String, Object>) response.get("allow");
        assertEquals("fallback@example.com must appear in allow.users",
                Arrays.asList("fallback@example.com"), allow.get("users"));
        assertTrue("allow.groups must be empty", ((List<?>) allow.get("groups")).isEmpty());
    }

    /**
     * A permission with role {@code owner} must be included in allow.users,
     * not silently dropped.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testOwnerPermissionIsIncludedInAllowUsers() throws Exception
    {
        final Identity user = new Identity();
        final Map<String, Object> extra = new HashMap<>();
        extra.put("email", "owner@tenant.onmicrosoft.com");
        user.setAdditionalData(extra);
        user.setDisplayName("Owner User");

        final SharePointIdentitySet v2 = new SharePointIdentitySet();
        v2.setUser(user);

        final Permission perm = new Permission();
        perm.setRoles(Arrays.asList("owner"));
        perm.setGrantedToV2(v2);

        final ConnectionActionResponse response = runAclWith("docs/owned.docx", Arrays.asList(perm));

        final Map<String, Object> allow = (Map<String, Object>) response.get("allow");
        assertEquals("owner@tenant.onmicrosoft.com must appear in allow.users",
                Arrays.asList("owner@tenant.onmicrosoft.com"), allow.get("users"));
        assertTrue("allow.groups must be empty", ((List<?>) allow.get("groups")).isEmpty());
    }

    /**
     * Duplicate user entries must be deduplicated.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testDuplicateUsersAreDeduped() throws Exception
    {
        // Two permissions for the same user
        final Identity user1 = new Identity();
        final Map<String, Object> extra1 = new HashMap<>();
        extra1.put("email", "AdeleV@36zvqr.onmicrosoft.com");
        user1.setAdditionalData(extra1);

        final SharePointIdentitySet v2a = new SharePointIdentitySet();
        v2a.setUser(user1);
        final Permission perm1 = new Permission();
        perm1.setGrantedToV2(v2a);

        final Identity user2 = new Identity();
        final Map<String, Object> extra2 = new HashMap<>();
        extra2.put("email", "AdeleV@36zvqr.onmicrosoft.com");
        user2.setAdditionalData(extra2);

        final SharePointIdentitySet v2b = new SharePointIdentitySet();
        v2b.setUser(user2);
        final Permission perm2 = new Permission();
        perm2.setGrantedToV2(v2b);

        final ConnectionActionResponse response = runAclWith("docs/shared.docx", Arrays.asList(perm1, perm2));

        final Map<String, Object> allow = (Map<String, Object>) response.get("allow");
        final List<String> users = (List<String>) allow.get("users");
        assertEquals("Duplicate user must appear only once", 1, users.size());
        assertEquals("AdeleV@36zvqr.onmicrosoft.com", users.get(0));
    }

    // -----------------------------------------------------------------------
    // Private helper: wire up mocked Graph client and run performAction
    // -----------------------------------------------------------------------

    /**
     * Creates a {@link OneDriveConnector} with a mocked Graph client that returns
     * the given permission list for {@code path}, then calls
     * {@code performAction("get_acl", ...)} and returns the response.
     */
    private static ConnectionActionResponse runAclWith(String path, List<Permission> permissions) throws Exception
    {
        // Build mock chain: drives().byDriveId(driveId).items().byDriveItemId(...).xxx
        final GraphServiceClient mockClient = mock(GraphServiceClient.class);
        final DrivesRequestBuilder mockDrives = mock(DrivesRequestBuilder.class);
        final DriveItemRequestBuilder mockDriveItem = mock(DriveItemRequestBuilder.class);
        final ItemsRequestBuilder mockItems = mock(ItemsRequestBuilder.class);
        final DriveItemItemRequestBuilder mockItemItem = mock(DriveItemItemRequestBuilder.class);
        final PermissionsRequestBuilder mockPerms = mock(PermissionsRequestBuilder.class);

        when(mockClient.drives()).thenReturn(mockDrives);
        when(mockDrives.byDriveId(any())).thenReturn(mockDriveItem);
        when(mockDriveItem.items()).thenReturn(mockItems);
        when(mockItems.byDriveItemId(any())).thenReturn(mockItemItem);

        // resolveFile(path) → DriveItem with a predictable id
        final DriveItem driveItem = new DriveItem();
        driveItem.setId("item-id-1");
        when(mockItemItem.get()).thenReturn(driveItem);

        // permissions().get() → PermissionCollectionResponse
        final PermissionCollectionResponse permsResponse = new PermissionCollectionResponse();
        permsResponse.setValue(permissions);
        when(mockItemItem.permissions()).thenReturn(mockPerms);
        when(mockPerms.get()).thenReturn(permsResponse);

        try (OneDriveConnector connector = connectorWithMockedGraph(mockClient, "drive-id-1")) {
            final ConnectionActionConfiguration inputProps = new ConnectionActionConfiguration();
            inputProps.put(OneDriveConnector.ACTION_PATH_PROP, path);

            return connector.performAction(OneDriveConnector.ACTION_GET_ACL, inputProps);
        }
    }
}
