/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.s3;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.arrow.flight.Ticket;
import org.slf4j.Logger;

import com.ibm.connect.sdk.file.FileConnector;
import com.ibm.connect.sdk.file.FileMsgs;
import com.ibm.connect.sdk.file.FileSourceInteraction;
import com.ibm.connect.sdk.file.FileTargetInteraction;
import com.ibm.connect.sdk.file.FileUtils;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetDetails;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetType;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectAclRequest;
import software.amazon.awssdk.services.s3.model.GetObjectAclResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.Grant;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.Permission;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * A connector for connecting to Amazon S3 (unstructured / file data).
 */
public class AWSS3Connector extends FileConnector
{
    private static final String BUCKET_PROP = "bucket";

    /** ACL action name — matches {@code ACLProvider.ACTION_GETACL} in wdp-connect-library. */
    static final String ACTION_GET_ACL = "get_acl";

    /** File metadata action name — returns last_modified and size for an S3 object. */
    static final String ACTION_GET_FILE_METADATA = "get_file_metadata";

    /** Input parameter name for the ACL and file metadata actions — path within the bucket. */
    static final String ACTION_PATH_PROP = "path";

    // S3 permission values that grant read access (map to "allow").
    private static final Set<String> READ_PERMISSIONS = Set.of("FULL_CONTROL", "READ");

    private static final Logger LOGGER = getLogger(AWSS3Connector.class);

    private final String bucket;
    private S3Client s3Client;

    /**
     * Creates an Amazon S3 connector.
     *
     * @param properties
     *            connection properties
     */
    public AWSS3Connector(ConnectionProperties properties)
    {
        super(properties);
        final Properties connectionProperties = getConnectionProperties();
        if (connectionProperties.getProperty(BUCKET_PROP) == null) {
            throw new IllegalArgumentException(FileMsgs.MISSING_PROPERTY.format(BUCKET_PROP));
        }
        bucket = connectionProperties.getProperty(BUCKET_PROP);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect() throws Exception
    {
        // Re-use a pooled connection if the client is already initialised.
        if (s3Client != null) {
            return;
        }
        final Properties connectionProperties = getConnectionProperties();
        final S3ClientBuilder builder = S3Client.builder();

        // Region (optional — falls back to environment / instance metadata).
        final String region = connectionProperties.getProperty("region");
        if (region != null && !region.isEmpty()) {
            builder.region(Region.of(region));
        }

        // Custom endpoint for S3-compatible stores (MinIO, LocalStack, etc.).
        final String endpointUrl = connectionProperties.getProperty("endpoint_url");
        if (endpointUrl != null && !endpointUrl.isEmpty()) {
            builder.endpointOverride(URI.create(endpointUrl));
            // Path-style access is usually required for custom endpoints.
            builder.forcePathStyle(true);
        }

        // Credentials: static key pair or default provider chain.
        final String accessKeyId = connectionProperties.getProperty("access_key_id");
        final String secretAccessKey = connectionProperties.getProperty("secret_access_key");
        if (accessKeyId != null && !accessKeyId.isEmpty() && secretAccessKey != null && !secretAccessKey.isEmpty()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
        } else {
            // No explicit credentials — fall back to the default provider chain
            // (env vars, ~/.aws/credentials, EC2/ECS instance metadata, etc.).
            LOGGER.debug("No access_key_id/secret_access_key provided; using DefaultCredentialsProvider.");
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        s3Client = builder.build();

        // Validate the bucket is reachable (equivalent to a connection test).
        LOGGER.info("Validating access to S3 bucket: {}", bucket);
        s3Client.headBucket(b -> b.bucket(bucket));
    }

    /**
     * Returns the underlying S3 client for use by source interactions.
     *
     * @return the S3 client
     */
    S3Client getS3Client()
    {
        return s3Client;
    }

    /**
     * Returns the bucket name.
     *
     * @return the bucket name
     */
    String getBucket()
    {
        return bucket;
    }

    /**
     * Opens the S3 object identified by {@code key} and returns its content as a
     * stream. The caller is responsible for closing the stream.
     *
     * @param key
     *            the S3 object key
     * @return an InputStream over the object content
     */
    InputStream openObject(String key)
    {
        return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(normalizeKey(key)).build());
    }

    /**
     * Returns metadata for the S3 object identified by {@code key}.
     *
     * @param key
     *            the S3 object key
     * @return the HeadObjectResponse
     */
    HeadObjectResponse headObject(String key)
    {
        return s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(normalizeKey(key)).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception
    {
        final String prefix = normalizePrefix(criteria.getPath());
        return listObjects(criteria, prefix);
    }

    private List<CustomFlightAssetDescriptor> listObjects(CustomFlightAssetsCriteria criteria, String prefix)
            throws Exception
    {
        final List<CustomFlightAssetDescriptor> descriptors = new ArrayList<>();
        final int offset = criteria.getOffset() == null || criteria.getOffset() < 0 ? 0 : criteria.getOffset();
        final int limit = criteria.getLimit() == null || criteria.getLimit() < 0 ? Integer.MAX_VALUE : criteria.getLimit();

        // Use delimiter "/" to emulate directory-style listing.
        // Cap maxKeys to avoid fetching more items than needed for the requested page.
        final int maxKeys = (offset + limit > 0 && limit != Integer.MAX_VALUE)
                ? Math.min(offset + limit, 1000) : 1000;
        final ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .delimiter("/")
                .maxKeys(maxKeys)
                .build();

        int totalSeen = 0;
        int added = 0;
        String continuationToken = null;
        boolean truncated;

        do {
            final ListObjectsV2Request.Builder reqBuilder = request.toBuilder();
            if (continuationToken != null) {
                reqBuilder.continuationToken(continuationToken);
            }
            final ListObjectsV2Response response = s3Client.listObjectsV2(reqBuilder.build());

            // Common prefixes = sub-folders.
            for (final software.amazon.awssdk.services.s3.model.CommonPrefix cp : response.commonPrefixes()) {
                totalSeen++;
                final PageAction pageAction = getPageAction(totalSeen, offset, added, limit);
                if (pageAction == PageAction.SKIP) {
                    continue;
                }
                if (pageAction == PageAction.STOP) {
                    return descriptors;
                }
                final String folderPrefix = cp.prefix();
                final String folderName = folderName(folderPrefix);
                final String assetPath = "/" + folderPrefix;
                descriptors.add(new CustomFlightAssetDescriptor().name(folderName).path(assetPath).assetType(folderAssetType()));
                added++;
            }

            // Objects = files.
            // When the prefix resolves to exactly one object whose key matches the prefix
            // exactly (i.e. the caller specified a full file path), populate
            // interactionProperties so the framework can complete the asset descriptor.
            final boolean singleFileRequest = !prefix.isEmpty()
                    && response.commonPrefixes().isEmpty()
                    && response.contents().size() == 1
                    && response.contents().get(0).key().equals(prefix);
            for (final S3Object s3Object : response.contents()) {
                // Skip the prefix itself (a zero-byte "directory marker") unless it is
                // the explicitly requested file. These markers do not count toward the
                // offset/limit totals.
                if (s3Object.key().equals(prefix) && !singleFileRequest) {
                    continue;
                }
                totalSeen++;
                final PageAction pageAction = getPageAction(totalSeen, offset, added, limit);
                if (pageAction == PageAction.SKIP) {
                    continue;
                }
                if (pageAction == PageAction.STOP) {
                    return descriptors;
                }
                final CustomFlightAssetDescriptor asset = createFileDescriptor(s3Object, singleFileRequest);
                if (asset != null) {
                    descriptors.add(asset);
                    added++;
                }
            }

            truncated = response.isTruncated();
            continuationToken = response.nextContinuationToken();
        } while (truncated);

        return descriptors;
    }

    private PageAction getPageAction(int totalSeen, int offset, int added, int limit)
    {
        if (totalSeen <= offset) {
            return PageAction.SKIP;
        }
        if (added >= limit) {
            return PageAction.STOP;
        }
        return PageAction.ADD;
    }

    private enum PageAction {
        SKIP,
        STOP,
        ADD
    }

    private CustomFlightAssetDescriptor createFileDescriptor(S3Object s3Object, boolean describeInteraction)
            throws Exception
    {
        final String key = s3Object.key();
        final String fileName = objectName(key);
        final String assetPath = "/" + key;

        final CustomFlightAssetDescriptor asset = new CustomFlightAssetDescriptor()
                .name(fileName).path(assetPath).assetType(fileAssetType());

        // Add size detail directly available from the listing.
        final DiscoveredAssetDetails details = new DiscoveredAssetDetails();
        details.put("file_size", s3Object.size());
        asset.setDetails(details);

        if (describeInteraction) {
            final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
            interactionProperties.put("file_name", assetPath);
            asset.setInteractionProperties(interactionProperties);
            addFileDetails(asset, key);
        }
        return asset;
    }

    /**
     * Adds file format details by streaming a small prefix of the S3 object.
     *
     * @param asset
     *            the descriptor to enrich
     * @param key
     *            the S3 object key
     * @throws Exception
     */
    void addFileDetails(CustomFlightAssetDescriptor asset, String key) throws Exception
    {
        final String fileName = objectName(key);
        try (InputStream objectStream = FileUtils.ensureMarkSupported(openObject(key))) {
            // Detect mime type.
            final String mimeType = FileUtils.detectMimeType(objectStream, fileName);
            if (mimeType != null) {
                if (asset.getDetails() == null) {
                    asset.setDetails(new DiscoveredAssetDetails());
                }
                asset.getDetails().put("mime_type", mimeType);
            }

            // Detect file format.
            final String detectedFileFormat = FileUtils.detectFileFormat(mimeType, fileName, objectStream);
            final String fileFormat = detectedFileFormat != null ? detectedFileFormat : FileUtils.FILE_FORMAT_DELIMITED;
            if (asset.getInteractionProperties() == null) {
                asset.setInteractionProperties(new DiscoveredAssetInteractionProperties());
            }
            asset.getInteractionProperties().put("file_format", fileFormat);

            // Detect delimited file properties.
            if (FileUtils.FILE_FORMAT_CSV.equals(fileFormat) || FileUtils.FILE_FORMAT_DELIMITED.equals(fileFormat)) {
                FileUtils.detectDelimitedProperties(objectStream, asset.getInteractionProperties());
            }

            // Describe fields using Spark (only for structured formats).
            if (!AWSS3DatasourceType.FILE_FORMAT_BINARY.equals(fileFormat)) {
                final String tempFilename = FileUtils.createTempFile(objectStream, fileName, fileFormat);
                try {
                    addAssetFields(asset, tempFilename);
                }
                finally {
                    FileUtils.deleteTempFile(tempFilename);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileSourceInteraction getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        return new AWSS3SourceInteraction(this, asset, ticket);
    }

    /**
     * {@inheritDoc}
     * S3 connector is source-only; writing is not supported.
     */
    @Override
    public FileTargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset)
    {
        throw new UnsupportedOperationException(
                FileMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(AWSS3DatasourceType.DATASOURCE_TYPE_NAME));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Handles the {@code get_acl} action. Returns an ACL response whose JSON
     * structure matches the {@code ACLProvider} contract used by wdp-connect-library:
     * <pre>
     * {
     *   "path": "/path/to/object",
     *   "allow": { "users": [...], "groups": [] },
     *   "deny":  { "users": [],   "groups": [] },
     *   "inheritance": { "enabled": false, "parent_precedence": "parent" },
     *   "precedence": "deny"
     * }
     * </pre>
     *
     * <p>S3 object ACLs have no explicit deny grants; all grantees with
     * {@code FULL_CONTROL} or {@code READ} are placed in {@code allow.users}.
     * Group grantees (AllUsers, AuthenticatedUsers) are placed in
     * {@code allow.groups}.  Buckets with ACLs disabled return an empty
     * allow/deny structure with a descriptive error hint in the response.
     */
    @Override
    public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration properties)
    {
        if (ACTION_GET_FILE_METADATA.equals(action)) {
            final Properties inputProperties = ModelMapper.toProperties(properties);
            final String path = inputProperties.getProperty(ACTION_PATH_PROP);
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException(FileMsgs.MISSING_PROPERTY.format(ACTION_PATH_PROP));
            }
            final HeadObjectResponse head = headObject(path);
            final ConnectionActionResponse response = new ConnectionActionResponse();
            response.put("last_modified", head.lastModified().toString());
            response.put("size", head.contentLength());
            return response;
        }
        if (ACTION_GET_ACL.equals(action)) {
            final Properties inputProperties = ModelMapper.toProperties(properties);
            final String path = inputProperties.getProperty(ACTION_PATH_PROP);
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException(FileMsgs.MISSING_PROPERTY.format(ACTION_PATH_PROP));
            }
            final String normalizedKey = normalizeKey(path);

            final ConnectionActionResponse response = new ConnectionActionResponse();
            try {
                final GetObjectAclResponse aclResponse = s3Client.getObjectAcl(
                        GetObjectAclRequest.builder().bucket(bucket).key(normalizedKey).build());
                buildAclResponse(response, normalizedKey, aclResponse);
            }
            catch (S3Exception e) {
                if ("InvalidRequest".equals(e.awsErrorDetails().errorCode())
                        || "AclNotSupported".equals(e.awsErrorDetails().errorCode())) {
                    // Bucket has ACLs disabled (BucketOwnerEnforced ownership).
                    // Return an empty ACL structure so callers get a valid response
                    // shape rather than an exception.
                    LOGGER.warn(AWSS3Msgs.ACL_DISABLED.format(bucket));
                    buildEmptyAclResponse(response, normalizedKey);
                } else {
                    throw e;
                }
            }
            return response;
        }
        throw new UnsupportedOperationException(FileMsgs.UNSUPPORTED_ACTION.format(action));
    }

    /**
     * Populates {@code response} with the standardised ACL map derived from the
     * raw S3 GetObjectAcl response.
     *
     * <p>S3 ACLs have no explicit deny concept — omission of a grantee is the
     * only "deny".  All grantees holding {@code FULL_CONTROL} or {@code READ}
     * are treated as allowed.  Group grantees (AllUsers, AuthenticatedUsers) are
     * placed in {@code groups}; canonical-user grantees go into {@code users}.
     */
    private void buildAclResponse(ConnectionActionResponse response, String path, GetObjectAclResponse aclResponse)
    {
        final Set<String> allowUsers = new HashSet<>();
        final Set<String> allowGroups = new HashSet<>();

        for (final Grant grant : aclResponse.grants()) {
            final Permission perm = grant.permission();
            if (perm == null || !READ_PERMISSIONS.contains(perm.toString())) {
                continue;
            }
            final software.amazon.awssdk.services.s3.model.Grantee grantee = grant.grantee();
            if (grantee == null) {
                continue;
            }
            switch (grantee.typeAsString()) {
            case "CanonicalUser":
                final String name = grantee.displayName() != null ? grantee.displayName() : grantee.id();
                if (name != null) {
                    allowUsers.add(name);
                }
                break;
            case "Group":
                if (grantee.uri() != null) {
                    allowGroups.add(grantee.uri());
                }
                break;
            default:
                if (grantee.emailAddress() != null) {
                    allowUsers.add(grantee.emailAddress());
                }
                break;
            }
        }

        populateAclResponse(response, path, allowUsers, allowGroups);
    }

    /** Populates {@code response} with an empty (no grantees) ACL structure. */
    private void buildEmptyAclResponse(ConnectionActionResponse response, String path)
    {
        populateAclResponse(response, path, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Writes the standardised ACL map into the action response.
     *
     * <p>Output schema (matches {@code ACLProvider} contract):
     * <pre>
     * path          – full path of the object
     * allow.users   – set of identities with read access
     * allow.groups  – set of group URIs with read access
     * deny.users    – always empty (S3 ACLs have no explicit deny)
     * deny.groups   – always empty
     * inheritance   – { enabled: false, parent_precedence: "parent" }
     * precedence    – "deny"
     * </pre>
     */
    private static void populateAclResponse(ConnectionActionResponse response, String path,
            Set<String> allowUsers, Set<String> allowGroups)
    {
        response.put("path", path);

        final Map<String, Object> allow = new LinkedHashMap<>();
        allow.put("users", new ArrayList<>(allowUsers));
        allow.put("groups", new ArrayList<>(allowGroups));
        response.put("allow", allow);

        final Map<String, Object> deny = new LinkedHashMap<>();
        deny.put("users", Collections.emptyList());
        deny.put("groups", Collections.emptyList());
        response.put("deny", deny);

        final Map<String, Object> inheritance = new LinkedHashMap<>();
        inheritance.put("enabled", Boolean.FALSE);
        inheritance.put("parent_precedence", "parent");
        response.put("inheritance", inheritance);

        // S3 explicit-deny does not exist; deny always takes precedence when set.
        response.put("precedence", "deny");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit()
    {
        // No-op: S3 is not transactional.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        super.close();
        try {
            if (s3Client != null) {
                s3Client.close();
            }
        }
        finally {
            s3Client = null;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Strips a leading "/" from a path to produce a valid S3 prefix.
     */
    private String normalizePrefix(String path)
    {
        if (path == null || path.equals("/")) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /**
     * Strips a leading "/" from a key path for use in S3 API calls.
     */
    String normalizeKey(String key)
    {
        return key.startsWith("/") ? key.substring(1) : key;
    }

    /** Returns the trailing component of an S3 object key (the file name). */
    private String objectName(String key)
    {
        final int lastSlash = key.lastIndexOf('/');
        return lastSlash >= 0 ? key.substring(lastSlash + 1) : key;
    }

    /** Returns the trailing folder component of a common prefix. */
    private String folderName(String prefix)
    {
        // prefix ends with "/" — trim it, then take the last path element.
        final String trimmed = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        final int lastSlash = trimmed.lastIndexOf('/');
        return lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
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
     * Verifies that the given key exists as an S3 object (not a prefix).
     *
     * @param key S3 object key (no leading slash)
     * @throws IllegalArgumentException if the key does not exist or is a prefix
     */
    void validateObjectKey(String key)
    {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
        }
        catch (NoSuchKeyException e) {
            throw new IllegalArgumentException(AWSS3Msgs.OBJECT_DOES_NOT_EXIST.format(key), e);
        }
    }
}
