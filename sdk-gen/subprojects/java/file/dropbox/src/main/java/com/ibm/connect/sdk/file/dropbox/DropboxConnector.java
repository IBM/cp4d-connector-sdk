/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.dropbox;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.arrow.flight.Ticket;
import org.slf4j.Logger;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.CommitInfo;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.UploadSessionCursor;
import com.dropbox.core.v2.files.WriteMode;
import com.dropbox.core.v2.sharing.GroupMembershipInfo;
import com.dropbox.core.v2.sharing.SharedFileMembers;
import com.dropbox.core.v2.sharing.SharedFolderMembers;
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

    /**
     * Chunk size for upload sessions. Dropbox requires every chunk except the last
     * to be a multiple of 4 MiB.
     */
    private static final int UPLOAD_CHUNK_SIZE = 8 * 1024 * 1024;

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

            // Auto-retry transparently handles rate limiting (HTTP 429) and transient errors.
            final DbxRequestConfig config = DbxRequestConfig.newBuilder("cp4d-dropbox-connector/1.0")
                    .withAutoRetryEnabled().build();
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
            // Listing the root folder needs only the files.metadata.read scope that the
            // connector requires anyway (users/get_current_account needs account_info.read).
            newClient.files().listFolderBuilder("").withLimit(1L).start();

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
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(FileMsgs.INVALID_PATH.format(filePath));
        }
        // A single upload request is limited to 150 MB, so content that does not fit in
        // one chunk is sent through an upload session.
        final byte[] firstChunk = content.readNBytes(UPLOAD_CHUNK_SIZE);
        if (firstChunk.length < UPLOAD_CHUNK_SIZE) {
            client.files().uploadBuilder(normalized)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(new ByteArrayInputStream(firstChunk));
        } else {
            final String sessionId = client.files().uploadSessionStart()
                    .uploadAndFinish(new ByteArrayInputStream(firstChunk)).getSessionId();
            long offset = firstChunk.length;
            byte[] chunk = content.readNBytes(UPLOAD_CHUNK_SIZE);
            while (chunk.length == UPLOAD_CHUNK_SIZE) {
                client.files().uploadSessionAppendV2(new UploadSessionCursor(sessionId, offset))
                        .uploadAndFinish(new ByteArrayInputStream(chunk));
                offset += chunk.length;
                chunk = content.readNBytes(UPLOAD_CHUNK_SIZE);
            }
            final CommitInfo commitInfo = CommitInfo.newBuilder(normalized).withMode(WriteMode.OVERWRITE).build();
            client.files().uploadSessionFinish(new UploadSessionCursor(sessionId, offset), commitInfo)
                    .uploadAndFinish(new ByteArrayInputStream(chunk));
        }
        LOGGER.info("Uploaded file to Dropbox: " + normalized);
    }

    /**
     * Deletes the files and (recursively) folders directly under {@code folderPath}
     * whose names satisfy {@code nameFilter}. Used to clean up test data without
     * touching other content of a shared folder. Does nothing if the folder does
     * not exist.
     *
     * @param folderPath
     *            Dropbox path of the parent folder
     * @param nameFilter
     *            selects the child names to delete
     * @return the number of items deleted
     */
    int deleteChildren(String folderPath, Predicate<String> nameFilter) throws Exception
    {
        ListFolderResult result;
        try {
            result = client.files().listFolder(normalizePath(folderPath));
        }
        catch (ListFolderErrorException folderNotFound) {
            return 0;
        }
        int deleted = 0;
        while (true) {
            for (final Metadata entry : result.getEntries()) {
                if (nameFilter.test(entry.getName())) {
                    client.files().deleteV2(entry.getPathLower());
                    deleted++;
                }
            }
            if (!result.getHasMore()) {
                return deleted;
            }
            result = client.files().listFolderContinue(result.getCursor());
        }
    }

    /**
     * Ensures the path starts with a leading slash, as required by the Dropbox API,
     * and strips trailing slashes. An empty or null path is treated as the root and
     * returned as an empty string (for listFolder calls).
     */
    private static String normalizePath(String path)
    {
        if (path == null) {
            return "";
        }
        final String trimmed = path.replaceAll("/+$", "");
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
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

        ListFolderResult result;
        try {
            result = client.files().listFolder(folderPath);
        }
        catch (ListFolderErrorException folderError) {
            // The path may identify a single file: describe it, including its detected
            // format and schema, so the framework can complete the asset descriptor.
            final Metadata metadata;
            try {
                metadata = client.files().getMetadata(folderPath);
            }
            catch (DbxException metadataError) {
                throw folderError;
            }
            if (metadata instanceof FileMetadata) {
                return Collections.singletonList(describeFile(path, (FileMetadata) metadata));
            }
            throw folderError;
        }
        while (true) {
            for (final Metadata entry : result.getEntries()) {
                // Skip entries that are neither files nor folders (e.g. deleted entries).
                if (!(entry instanceof FolderMetadata) && !(entry instanceof FileMetadata)) {
                    continue;
                }
                totalSeen++;
                if (totalSeen <= offset) {
                    continue;
                }
                if (added >= limit) {
                    break;
                }
                final String itemName = entry.getName();
                // Build canonical asset path.
                final String assetPath = folderPath + "/" + itemName;

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

    /**
     * Builds a fully described file descriptor (size, MIME type, file format,
     * delimited-file properties and fields) for a single file path.
     */
    private CustomFlightAssetDescriptor describeFile(String path, FileMetadata metadata) throws Exception
    {
        final String assetPath = normalizePath(path);
        final DiscoveredAssetDetails details = new DiscoveredAssetDetails();
        details.put("file_size", metadata.getSize());
        final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
        interactionProperties.put("file_name", assetPath);
        final CustomFlightAssetDescriptor asset = new CustomFlightAssetDescriptor()
                .name(metadata.getName())
                .path(assetPath)
                .assetType(fileAssetType())
                .details(details)
                .interactionProperties(interactionProperties);
        addFileDetails(asset, assetPath);
        return asset;
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
        return response;
    }

    /**
     * Executes the {@code get_acl} action for the given Dropbox path.
     *
     * <p>
     * Failures are propagated: an empty ACL returned on error would be
     * indistinguishable from "nobody has access" for ACL consumers.
     */
    private ConnectionActionResponse performGetAcl(String path) throws Exception
    {
        final Set<String> allowUsers = new LinkedHashSet<>();
        final Set<String> allowGroups = new LinkedHashSet<>();

        final String normalized = normalizePath(path);
        final Metadata metadata = client.files().getMetadata(normalized);

        if (metadata instanceof FileMetadata) {
            // Include inherited members from parent shared folders so that folder-level
            // group memberships are reflected.
            final SharedFileMembers members = client.sharing()
                    .listFileMembersBuilder(normalized)
                    .withIncludeInherited(Boolean.TRUE)
                    .start();
            collectMembers(members.getUsers(), members.getGroups(), allowUsers, allowGroups);
            return buildAclResponse(path, allowUsers, allowGroups, false);
        }
        if (metadata instanceof FolderMetadata) {
            final FolderMetadata folderMetadata = (FolderMetadata) metadata;
            final String sharedFolderId = folderMetadata.getSharedFolderId();
            final boolean isUnshared = sharedFolderId == null || sharedFolderId.isEmpty();
            // Only shared folders have a member list; an unshared folder inherits its
            // permissions from its parent.
            if (!isUnshared) {
                final SharedFolderMembers members = client.sharing().listFolderMembers(sharedFolderId);
                collectMembers(members.getUsers(), members.getGroups(), allowUsers, allowGroups);
            }
            return buildAclResponse(path, allowUsers, allowGroups, isUnshared);
        }
        throw new IllegalArgumentException(DropboxMsgs.FILE_NOT_FOUND.format(path));
    }

    /**
     * Collects distinct user identities (email, falling back to display name) and
     * group names from the given membership lists.
     */
    private static void collectMembers(List<? extends UserMembershipInfo> userInfos, List<? extends GroupMembershipInfo> groupInfos,
            Set<String> users, Set<String> groups)
    {
        if (userInfos != null) {
            for (final UserMembershipInfo info : userInfos) {
                if (info.getUser() != null) {
                    final String email = info.getUser().getEmail();
                    final String identity = (email != null && !email.isEmpty()) ? email : info.getUser().getDisplayName();
                    if (identity != null) {
                        users.add(identity);
                    }
                }
            }
        }
        if (groupInfos != null) {
            for (final GroupMembershipInfo info : groupInfos) {
                if (info.getGroup() != null && info.getGroup().getGroupName() != null) {
                    groups.add(info.getGroup().getGroupName());
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
    static ConnectionActionResponse buildAclResponse(String path, Collection<String> allowUsers,
            Collection<String> allowGroups, boolean inheritanceEnabled)
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
