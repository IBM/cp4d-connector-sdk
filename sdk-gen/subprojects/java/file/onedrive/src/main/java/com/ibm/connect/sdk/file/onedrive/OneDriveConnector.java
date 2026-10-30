/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.onedrive;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.arrow.flight.Ticket;
import org.slf4j.Logger;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.UsernamePasswordCredentialBuilder;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import reactor.core.publisher.Mono;
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
import com.microsoft.graph.models.Drive;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemCollectionResponse;
import com.microsoft.graph.models.Identity;
import com.microsoft.graph.models.Permission;
import com.microsoft.graph.models.PermissionCollectionResponse;
import com.microsoft.graph.models.SharePointIdentitySet;
import com.microsoft.graph.serviceclient.GraphServiceClient;

/**
 * A connector for connecting to OneDrive via Microsoft Graph using
 * Username &amp; Password (ROPC) authentication.
 */
public class OneDriveConnector extends FileConnector
{
    private static final Logger LOGGER = getLogger(OneDriveConnector.class);

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

    private GraphServiceClient graphClient;
    private String driveId;

    /**
     * Creates a OneDrive connector.
     *
     * @param properties
     *            connection properties
     */
    public OneDriveConnector(ConnectionProperties properties)
    {
        super(properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect() throws Exception
    {
        if (graphClient == null) {
            final Properties connectionProperties = getConnectionProperties();
            final String tenantId = getRequiredProperty(connectionProperties, "tenant_id");
            final String clientId = getRequiredProperty(connectionProperties, "client_id");
            final String username = getRequiredProperty(connectionProperties, "username");
            final String password = getRequiredProperty(connectionProperties, "password");
            final String clientSecret = connectionProperties.getProperty("client_secret");

            final TokenCredential credential;
            if (clientSecret != null && !clientSecret.isEmpty()) {
                // Confidential client: use direct HTTP ROPC flow because
                // UsernamePasswordCredentialBuilder in azure-identity does not expose
                // clientSecret() — the MSAL4J confidential client must send the secret
                // in the token request body (AADSTS7000218 otherwise).
                final AccessToken oauthToken = acquireTokenRopc(tenantId, clientId, clientSecret, username, password);
                credential = new TokenCredential() {
                    @Override
                    public Mono<AccessToken> getToken(TokenRequestContext request)
                    {
                        return Mono.just(oauthToken);
                    }

                    @Override
                    public AccessToken getTokenSync(TokenRequestContext request)
                    {
                        return oauthToken;
                    }
                };
            } else {
                credential = new UsernamePasswordCredentialBuilder()
                        .tenantId(tenantId)
                        .clientId(clientId)
                        .username(username)
                        .password(password)
                        .build();
            }

            final GraphServiceClient client = new GraphServiceClient(
                    credential,
                    "https://graph.microsoft.com/.default");

            // Resolve the user's personal drive ID — must succeed before we commit
            // the client so a failure leaves graphClient == null.
            final Drive drive = client.me().drive().get();
            if (drive == null || drive.getId() == null) {
                throw new IllegalStateException("Could not retrieve OneDrive for user '" + username + "'");
            }
            final String resolvedDriveId = drive.getId();

            // All resolution succeeded — commit atomically.
            graphClient = client;
            driveId = resolvedDriveId;

            LOGGER.info("Connected to OneDrive for user '{}', drive '{}'", username, resolvedDriveId);
        }
    }

    /**
     * Acquires a delegated access token via the OAuth2 ROPC (Resource Owner Password
     * Credentials) grant, sending {@code client_secret} for confidential clients.
     * This is required when the app registration mandates a client secret in the token
     * request body (AADSTS7000218).
     */
    private static AccessToken acquireTokenRopc(String tenantId, String clientId, String clientSecret,
            String username, String password) throws Exception
    {
        final URL tokenUrl = new URL("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token");
        final HttpURLConnection conn = (HttpURLConnection) tokenUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        final StringBuilder body = new StringBuilder(512);
        body.append("grant_type=password")
            .append("&client_id=").append(URLEncoder.encode(clientId, StandardCharsets.UTF_8.name()))
            .append("&client_secret=").append(URLEncoder.encode(clientSecret, StandardCharsets.UTF_8.name()))
            .append("&username=").append(URLEncoder.encode(username, StandardCharsets.UTF_8.name()))
            .append("&password=").append(URLEncoder.encode(password, StandardCharsets.UTF_8.name()))
            .append("&scope=").append(URLEncoder.encode("https://graph.microsoft.com/.default offline_access", StandardCharsets.UTF_8.name()));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        final int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                final JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("access_token")) {
                    final String accessToken = json.get("access_token").getAsString();
                    final long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L;
                    return new AccessToken(accessToken, OffsetDateTime.now().plusSeconds(expiresIn));
                }
                throw new IllegalStateException("Token response did not contain access_token");
            }
        }

        // Log the Microsoft error body (e.g. AADSTS50126) for diagnosis; the thrown
        // message stays generic so it is not exposed to end users.
        try (InputStream in = conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()) {
            LOGGER.warn("OneDrive token exchange failed: HTTP {} - {}", responseCode,
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        catch (Exception e) {
            LOGGER.warn("OneDrive token exchange failed: HTTP {} (could not read response body: {})", responseCode,
                    e.getMessage());
        }
        throw new IllegalStateException("OneDrive authentication failed — check your credentials and try again.");
    }

    /**
     * Returns a required connection property, throwing if absent.
     */
    private static String getRequiredProperty(Properties properties, String name)
    {
        final String value = properties.getProperty(name);
        if (value == null) {
            throw new IllegalArgumentException(FileMsgs.MISSING_PROPERTY.format(name));
        }
        return value;
    }

    /**
     * Builds the Graph path reference for an item at {@code filePath} relative to
     * the drive root. The Graph SDK uses the special ID format
     * {@code "root:/{path}:"} to address items by path.
     *
     * @param filePath
     *            slash-delimited path (leading "/" stripped automatically)
     * @return Graph item ID reference string
     */
    private static String pathToItemId(String filePath)
    {
        if (filePath == null || filePath.isEmpty()) {
            return "root";
        }
        final String normalized = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        if (normalized.isEmpty()) {
            return "root";
        }
        return "root:/" + normalized + ":";
    }

    /**
     * Resolves a slash-delimited file path to the corresponding {@link DriveItem}.
     *
     * @param filePath
     *            slash-delimited path (e.g. {@code "folder/file.txt"})
     * @return the {@link DriveItem} for the resolved file
     * @throws IllegalArgumentException
     *             if the item is not found
     */
    DriveItem resolveFile(String filePath) throws Exception
    {
        try {
            return graphClient.drives().byDriveId(driveId)
                    .items().byDriveItemId(pathToItemId(filePath)).get();
        }
        catch (Exception e) {
            throw new IllegalArgumentException(OneDriveMsgs.FILE_NOT_FOUND.format(filePath), e);
        }
    }

    /**
     * Lists children at the given folder path (or root if null/empty/"/").
     */
    private List<DriveItem> listChildren(String folderPath) throws Exception
    {
        final String normalized = (folderPath == null || folderPath.isEmpty() || folderPath.equals("/")) ? "" :
                (folderPath.startsWith("/") ? folderPath.substring(1) : folderPath);
        final DriveItemCollectionResponse response;
        if (normalized.isEmpty()) {
            response = graphClient.drives().byDriveId(driveId)
                    .items().byDriveItemId("root").children().get();
        } else {
            response = graphClient.drives().byDriveId(driveId)
                    .items().byDriveItemId("root:/" + normalized + ":").children().get();
        }
        if (response == null || response.getValue() == null) {
            return new ArrayList<>();
        }
        return response.getValue();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses a background thread writing to a pipe so the caller can stream.
     */
    @Override
    protected InputStream openFile(String filePath) throws Exception
    {
        final DriveItem item = resolveFile(filePath);
        final String itemId = item.getId();
        final PipedOutputStream pipeOut = new PipedOutputStream();
        final PipedInputStream pipeIn = new PipedInputStream(pipeOut, 65536);

        final Thread downloadThread = new Thread(() -> {
            try {
                final InputStream content = graphClient.drives().byDriveId(driveId)
                        .items().byDriveItemId(itemId).content().get();
                if (content != null) {
                    final byte[] buf = new byte[65536];
                    int n;
                    while ((n = content.read(buf)) != -1) {
                        pipeOut.write(buf, 0, n);
                    }
                    content.close();
                }
            }
            catch (Exception e) {
                LOGGER.warn("OneDrive download thread failed for '{}': {}", filePath, e.getMessage(), e);
            }
            finally {
                try {
                    pipeOut.close();
                }
                catch (Exception ignored) {
                    // Nothing to do.
                }
            }
        }, "onedrive-download-" + filePath);
        downloadThread.setDaemon(true);
        downloadThread.start();

        return pipeIn;
    }

    /**
     * Returns the file size (in bytes) for a OneDrive file identified by path.
     */
    long getFileSize(String filePath) throws Exception
    {
        final DriveItem item = resolveFile(filePath);
        final Long size = item.getSize();
        return size != null ? size : 0L;
    }

    /**
     * Uploads an {@link InputStream} to OneDrive as the file identified by
     * {@code filePath}. Uses Graph's PUT-to-path content endpoint. If the file
     * already exists it is replaced; otherwise a new file is created.
     *
     * @param filePath
     *            slash-delimited destination path
     * @param content
     *            the data to upload
     * @throws Exception
     */
    void uploadFile(String filePath, InputStream content) throws Exception
    {
        final String itemRef = pathToItemId(filePath);
        graphClient.drives().byDriveId(driveId)
                .items().byDriveItemId(itemRef).content().put(content);
        LOGGER.info("Uploaded file '{}'", filePath);
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

        final List<DriveItem> children = listChildren(path);
        final List<CustomFlightAssetDescriptor> descriptors = new ArrayList<>();
        int totalSeen = 0;
        int added = 0;

        for (final DriveItem item : children) {
            totalSeen++;
            if (totalSeen <= offset) {
                continue;
            }
            if (added >= limit) {
                break;
            }
            final String itemName = item.getName();
            final String normalized = (path == null || path.equals("/")) ? "" : (path.startsWith("/") ? path : "/" + path);
            final String assetPath = normalized.isEmpty() ? "/" + itemName : normalized + "/" + itemName;

            if (item.getFolder() != null) {
                descriptors.add(new CustomFlightAssetDescriptor()
                        .id(item.getId())
                        .name(itemName)
                        .path(assetPath)
                        .assetType(folderAssetType()));
            } else {
                final DiscoveredAssetDetails details = new DiscoveredAssetDetails();
                if (item.getSize() != null) {
                    details.put("file_size", item.getSize());
                }
                final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
                interactionProperties.put("file_name", assetPath);
                descriptors.add(new CustomFlightAssetDescriptor()
                        .id(item.getId())
                        .name(itemName)
                        .path(assetPath)
                        .assetType(fileAssetType())
                        .details(details)
                        .interactionProperties(interactionProperties));
            }
            added++;
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
        return new OneDriveSourceInteraction(this, asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileTargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset) throws Exception
    {
        return new OneDriveTargetInteraction(this, asset);
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
        final DriveItem item = resolveFile(path);
        final ConnectionActionResponse response = new ConnectionActionResponse();
        if (item.getLastModifiedDateTime() != null) {
            response.put("last_modified", item.getLastModifiedDateTime().toInstant().toString());
        } else {
            response.put("last_modified", "");
        }
        response.put("size", item.getSize() != null ? item.getSize() : 0L);
        response.put("name", item.getName() != null ? item.getName() : "");
        response.put("path", path);
        return response;
    }

    /**
     * Executes the {@code get_acl} action for a given OneDrive path.
     * Lists permissions on the resolved item and classifies each entry.
     * All permissions — including those with role {@code owner} — are included.
     * <ol>
     *   <li>Identities in {@code grantedToV2.group} (Azure AD groups) — display name
     *       added to allowGroups.</li>
     *   <li>Identities in {@code grantedToV2.user} — email extracted from
     *       additionalData; falls back to display name. Added to allowUsers.</li>
     *   <li>Fallback — reads {@code grantedTo.user} and extracts email the same way.</li>
     * </ol>
     * Duplicate entries are deduplicated.
     */
    private ConnectionActionResponse performGetAcl(String path)
    {
        final List<String> allowUsers = new ArrayList<>();
        final List<String> allowGroups = new ArrayList<>();

        try {
            final DriveItem item = resolveFile(path);
            final PermissionCollectionResponse permsResponse = graphClient.drives().byDriveId(driveId)
                    .items().byDriveItemId(item.getId()).permissions().get();
            if (permsResponse != null && permsResponse.getValue() != null) {
                for (final Permission perm : permsResponse.getValue()) {
                    // Check grantedToV2 first (preferred by Graph API)
                    final SharePointIdentitySet v2 = perm.getGrantedToV2();
                    if (v2 != null) {
                        // Azure AD group
                        if (v2.getGroup() != null) {
                            final String displayName = v2.getGroup().getDisplayName();
                            if (displayName != null && !allowGroups.contains(displayName)) {
                                allowGroups.add(displayName);
                            }
                            continue;
                        }

                        // Azure AD / Microsoft account user
                        if (v2.getUser() != null) {
                            final String email = identityWithEmail(v2.getUser());
                            if (email != null && !allowUsers.contains(email)) {
                                allowUsers.add(email);
                            }
                            continue;
                        }
                    }

                    // Fallback — read grantedTo
                    if (perm.getGrantedTo() != null && perm.getGrantedTo().getUser() != null) {
                        final String email = identityWithEmail(perm.getGrantedTo().getUser());
                        if (email != null && !allowUsers.contains(email)) {
                            allowUsers.add(email);
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            LOGGER.warn("Could not retrieve permissions for path '{}': {}", path, e.getMessage(), e);
        }

        return buildAclResponse(path, allowUsers, allowGroups);
    }

    /**
     * Extracts the email address from an {@link Identity} by reading the
     * {@code "email"} key from {@code additionalData}. Falls back to
     * {@link Identity#getDisplayName()} when email is absent.
     *
     * @param identity
     *            the identity to read; may be {@code null}
     * @return email address, display name, or {@code null} when identity is null
     */
    static String identityWithEmail(Identity identity)
    {
        if (identity == null) {
            return null;
        }
        final Map<String, Object> extra = identity.getAdditionalData();
        if (extra != null && extra.get("email") instanceof String) {
            return (String) extra.get("email");
        }
        return identity.getDisplayName();
    }

    /**
     * Builds the standardised ACL response.
     */
    static ConnectionActionResponse buildAclResponse(String path, List<String> allowUsers, List<String> allowGroups)
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
        inheritance.put("enabled", Boolean.FALSE);
        inheritance.put("parent_precedence", "parent");
        response.put("inheritance", inheritance);

        response.put("precedence", "deny");
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
        graphClient = null;
        driveId = null;
    }
}
