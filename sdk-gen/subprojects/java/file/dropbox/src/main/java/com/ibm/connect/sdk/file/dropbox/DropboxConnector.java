/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.dropbox;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.arrow.flight.Ticket;
import org.slf4j.Logger;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.sharing.GroupMembershipInfo;
import com.dropbox.core.v2.sharing.SharedFileMembers;
import com.dropbox.core.v2.sharing.UserMembershipInfo;
import com.google.common.collect.ImmutableList;
import com.ibm.connect.sdk.file.FileConnector;
import com.ibm.connect.sdk.file.FileMsgs;
import com.ibm.connect.sdk.file.FileSourceInteraction;
import com.ibm.connect.sdk.file.FileTargetInteraction;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetDetails;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetType;

/**
 * A connector for connecting to Dropbox.
 */
public class DropboxConnector extends FileConnector
{
    private static final Logger LOGGER = getLogger(DropboxConnector.class);

    /**
     * ACL action name.
     */
    static final String ACTION_GET_ACL = "get_acl";

    /**
     * File metadata action name.
     */
    static final String ACTION_GET_FILE_METADATA = "get_file_metadata";

    /**
     * Input path property name for the ACL and file metadata actions.
     */
    static final String ACTION_PATH_PROP = "path";

    private DbxClientV2 client;

    /**
     * Creates a Dropbox connector.
     *
     * @param properties
     *            connection properties
     */
    public DropboxConnector(ConnectionProperties properties)
    {
        super(properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect() throws Exception
    {
        if (client == null) {
            final Properties connectionProperties = getConnectionProperties();
            final String accessToken = connectionProperties.getProperty("access_token");
            final String refreshToken = connectionProperties.getProperty("refresh_token");
            final String appKey = connectionProperties.getProperty("app_key");
            final String appSecret = connectionProperties.getProperty("app_secret");

            final DbxRequestConfig config = DbxRequestConfig.newBuilder("cp4d-dropbox-connector/1.0").build();
            final DbxClientV2 newClient;

            if (refreshToken != null && !refreshToken.isEmpty()
                    && appKey != null && !appKey.isEmpty()
                    && appSecret != null && !appSecret.isEmpty()) {
                // OAuth2 refresh token flow.
                // DbxCredential requires a non-null accessToken string; pass empty
                // string here so the SDK immediately refreshes using the refresh token.
                final DbxCredential credential = new DbxCredential("", -1L, refreshToken, appKey, appSecret);
                newClient = new DbxClientV2(config, credential);
                LOGGER.info("Connected to Dropbox using refresh token flow");
            } else if (accessToken != null && !accessToken.isEmpty()) {
                // Direct access token flow.
                newClient = new DbxClientV2(config, accessToken);
                LOGGER.info("Connected to Dropbox using access token");
            } else {
                throw new IllegalArgumentException(DropboxMsgs.MISSING_AUTH_CREDENTIALS.format());
            }

            // Force token validation by making a lightweight API call.
            // Dropbox SDK uses lazy authentication and only validates the token on the
            // first real API call — this ensures bad credentials surface immediately
            // during ACTION_TEST (connect()) rather than silently succeeding.
            newClient.users().getCurrentAccount();

            // Only assign the field once validated, so a failed connect() does not leave
            // a non-null client that would make the next connect() skip validation.
            client = newClient;
        }
    }

    /**
     * Returns the underlying Dropbox client (package-private for subclasses).
     *
     * @return the Dropbox client
     */
    DbxClientV2 getClient()
    {
        return client;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected InputStream openFile(String filePath) throws Exception
    {
        final String normalized = normalizePath(filePath);
        return client.files().download(normalized).getInputStream();
    }

    /**
     * Returns the file size (in bytes) for the Dropbox file at the given path.
     *
     * @param filePath
     *            the Dropbox path of the file
     * @return size in bytes
     * @throws Exception
     */
    long getFileSize(String filePath) throws Exception
    {
        final String normalized = normalizePath(filePath);
        final Metadata metadata = client.files().getMetadata(normalized);
        if (metadata instanceof FileMetadata) {
            return ((FileMetadata) metadata).getSize();
        }
        throw new IllegalArgumentException(DropboxMsgs.FILE_NOT_FOUND.format(filePath));
    }

    /**
     * Uploads an {@link InputStream} to Dropbox at the given path, overwriting any
     * existing file.
     *
     * @param filePath
     *            the destination Dropbox path
     * @param content
     *            the data to upload; the stream is not closed by this method
     * @throws Exception
     */
    void uploadFile(String filePath, InputStream content) throws Exception
    {
        final String normalized = normalizePath(filePath);
        client.files().uploadBuilder(normalized)
                .withMode(com.dropbox.core.v2.files.WriteMode.OVERWRITE)
                .uploadAndFinish(content);
        LOGGER.info("Uploaded file to Dropbox: " + normalized);
    }

    /**
     * Ensures the path starts with a leading slash, as required by the Dropbox API.
     * An empty or null path is treated as the root and returned as an empty string
     * (for listFolder calls).
     */
    private static String normalizePath(String path)
    {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns folders and files at the path specified in {@code criteria.getPath()}.
     */
    @Override
    public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception
    {
        final String path = criteria.getPath();
        final int offset = criteria.getOffset() == null || criteria.getOffset() < 0 ? 0 : criteria.getOffset();
        final int limit = criteria.getLimit() == null || criteria.getLimit() < 0 ? Integer.MAX_VALUE : criteria.getLimit();

        final String folderPath = normalizePath(path);
        final List<CustomFlightAssetDescriptor> descriptors = new ArrayList<>();
        int totalSeen = 0;
        int added = 0;

        ListFolderResult result = client.files().listFolder(folderPath);
        while (true) {
            for (final Metadata entry : result.getEntries()) {
                totalSeen++;
                if (totalSeen <= offset) {
                    continue;
                }
                if (added >= limit) {
                    break;
                }
                final String itemName = entry.getName();
                // Build canonical asset path.
                final String basePath = (path == null || path.equals("/")) ? "" : (path.startsWith("/") ? path : "/" + path);
                final String assetPath = basePath.isEmpty() ? "/" + itemName : basePath + "/" + itemName;

                if (entry instanceof FolderMetadata) {
                    descriptors.add(new CustomFlightAssetDescriptor()
                            .name(itemName)
                            .path(assetPath)
                            .assetType(folderAssetType()));
                } else if (entry instanceof FileMetadata) {
                    final long size = ((FileMetadata) entry).getSize();
                    final DiscoveredAssetDetails details = new DiscoveredAssetDetails();
                    details.put("file_size", size);
                    final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
                    interactionProperties.put("file_name", assetPath);
                    descriptors.add(new CustomFlightAssetDescriptor()
                            .name(itemName)
                            .path(assetPath)
                            .assetType(fileAssetType())
                            .details(details)
                            .interactionProperties(interactionProperties));
                }
                added++;
            }
            if (!result.getHasMore() || added >= limit) {
                break;
            }
            result = client.files().listFolderContinue(result.getCursor());
        }
        return descriptors;
    }

    private DiscoveredAssetType folderAssetType()
    {
        return new DiscoveredAssetType().type("folder").dataset(false).datasetContainer(true);
    }

    private DiscoveredAssetType fileAssetType()
    {
        return new DiscoveredAssetType().type("file").dataset(true).datasetContainer(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileSourceInteraction getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        return new DropboxSourceInteraction(this, asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileTargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset) throws Exception
    {
        return new DropboxTargetInteraction(this, asset);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Handles the {@code get_acl} and {@code get_file_metadata} actions.
     */
    @Override
    public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration properties) throws Exception
    {
        if (!ACTION_GET_FILE_METADATA.equals(action) && !ACTION_GET_ACL.equals(action)) {
            throw new UnsupportedOperationException(FileMsgs.UNSUPPORTED_ACTION.format(action));
        }

        final Properties inputProperties = com.ibm.connect.sdk.util.ModelMapper.toProperties(properties);
        final String path = inputProperties.getProperty(ACTION_PATH_PROP);
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException(FileMsgs.MISSING_PROPERTY.format(ACTION_PATH_PROP));
        }

        if (ACTION_GET_FILE_METADATA.equals(action)) {
            return performGetFileMetadata(path);
        }
        return performGetAcl(path);
    }

    /**
     * Executes the {@code get_file_metadata} action.
     */
    private ConnectionActionResponse performGetFileMetadata(String path) throws Exception
    {
        final String normalized = normalizePath(path);
        final Metadata metadata = client.files().getMetadata(normalized);
        if (!(metadata instanceof FileMetadata)) {
            throw new IllegalArgumentException(DropboxMsgs.FILE_NOT_FOUND.format(path));
        }
        final FileMetadata fileMetadata = (FileMetadata) metadata;
        final ConnectionActionResponse response = new ConnectionActionResponse();
        response.put("last_modified", fileMetadata.getServerModified().toInstant().toString());
        response.put("size", fileMetadata.getSize());
        response.put("rev", fileMetadata.getRev());
        response.put("id", fileMetadata.getId());
        response.put("path", fileMetadata.getPathDisplay());
        return response;
    }

    /**
     * Executes the {@code get_acl} action for the given Dropbox path.
     */
    private ConnectionActionResponse performGetAcl(String path)
    {
        final List<String> allowUsers = new ArrayList<>();
        final List<String> allowGroups = new ArrayList<>();

        try {
            final String normalized = normalizePath(path);
            // Try to determine if path is a file by getting its metadata.
            Metadata metadata = null;
            try {
                metadata = client.files().getMetadata(normalized);
            }
            catch (DbxException e) {
                LOGGER.warn("Could not get metadata for path '{}': {}", path, e.getMessage());
            }

            if (metadata instanceof FileMetadata) {
                // Retrieve sharing members for the file, including inherited members from
                // parent shared folders so that folder-level group memberships are reflected.
                try {
                    final SharedFileMembers members = client.sharing()
                            .listFileMembersBuilder(normalized)
                            .withIncludeInherited(Boolean.TRUE)
                            .start();
                    collectFileMembers(members, allowUsers, allowGroups);
                }
                catch (Exception e) {
                    LOGGER.warn("Could not list file members for '{}': {}", path, e.getMessage());
                }
            } else if (metadata instanceof FolderMetadata) {
                final FolderMetadata folderMetadata = (FolderMetadata) metadata;
                // Retrieve sharing members for the folder.
                try {
                    final String folderId = folderMetadata.getId();
                    final com.dropbox.core.v2.sharing.SharedFolderMembers members =
                            client.sharing().listFolderMembers(folderId);
                    collectFolderMembers(members, allowUsers, allowGroups);
                }
                catch (Exception e) {
                    LOGGER.warn("Could not list folder members for '{}': {}", path, e.getMessage());
                }
                // Unshared subfolders inherit permissions from their parent.
                final boolean isUnshared = folderMetadata.getSharedFolderId() == null
                        || folderMetadata.getSharedFolderId().isEmpty();
                return buildAclResponse(path, allowUsers, allowGroups, isUnshared);
            }
        }
        catch (Exception e) {
            LOGGER.warn("Could not retrieve ACL for path '{}': {}", path, e.getMessage());
        }

        return buildAclResponse(path, allowUsers, allowGroups, false);
    }

    /**
     * Collects users and groups from a {@link SharedFileMembers} result.
     */
    private static void collectFileMembers(SharedFileMembers members, List<String> users, List<String> groups)
    {
        if (members.getUsers() != null) {
            for (final UserMembershipInfo info : members.getUsers()) {
                if (info.getUser() != null) {
                    final String email = info.getUser().getEmail();
                    final String identity = (email != null && !email.isEmpty()) ? email : info.getUser().getDisplayName();
                    if (identity != null && !users.contains(identity)) {
                        users.add(identity);
                    }
                }
            }
        }
        if (members.getGroups() != null) {
            for (final GroupMembershipInfo info : members.getGroups()) {
                if (info.getGroup() != null) {
                    final String groupName = info.getGroup().getGroupName();
                    if (groupName != null && !groups.contains(groupName)) {
                        groups.add(groupName);
                    }
                }
            }
        }
    }

    /**
     * Collects users and groups from a {@link com.dropbox.core.v2.sharing.SharedFolderMembers} result.
     */
    private static void collectFolderMembers(com.dropbox.core.v2.sharing.SharedFolderMembers members,
            List<String> users, List<String> groups)
    {
        if (members.getUsers() != null) {
            for (final UserMembershipInfo info : members.getUsers()) {
                if (info.getUser() != null) {
                    final String email = info.getUser().getEmail();
                    final String identity = (email != null && !email.isEmpty()) ? email : info.getUser().getDisplayName();
                    if (identity != null && !users.contains(identity)) {
                        users.add(identity);
                    }
                }
            }
        }
        if (members.getGroups() != null) {
            for (final GroupMembershipInfo info : members.getGroups()) {
                if (info.getGroup() != null) {
                    final String groupName = info.getGroup().getGroupName();
                    if (groupName != null && !groups.contains(groupName)) {
                        groups.add(groupName);
                    }
                }
            }
        }
    }

    /**
     * Builds the standardised ACL response (matches the ACLProvider contract).
     *
     * @param inheritanceEnabled
     *            {@code true} when the resource inherits permissions from its parent
     *            (e.g. an unshared subfolder)
     */
    static ConnectionActionResponse buildAclResponse(String path, List<String> allowUsers, List<String> allowGroups,
            boolean inheritanceEnabled)
    {
        final ConnectionActionResponse response = new ConnectionActionResponse();
        response.put("path", path);

        final Map<String, Object> allow = new LinkedHashMap<>();
        allow.put("users", ImmutableList.copyOf(allowUsers));
        allow.put("groups", ImmutableList.copyOf(allowGroups));
        response.put("allow", allow);

        final Map<String, Object> deny = new LinkedHashMap<>();
        deny.put("users", ImmutableList.of());
        deny.put("groups", ImmutableList.of());
        response.put("deny", deny);

        final Map<String, Object> inheritance = new LinkedHashMap<>();
        inheritance.put("enabled", inheritanceEnabled);
        inheritance.put("parent_precedence", "parent");
        response.put("inheritance", inheritance);

        response.put("precedence", "allow");
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit()
    {
        // Do nothing.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        super.close();
        client = null;
    }
}
