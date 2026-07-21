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
import java.util.List;
import java.util.Properties;

import org.apache.arrow.flight.Ticket;
import org.slf4j.Logger;

import com.ibm.connect.sdk.file.FileConnector;
import com.ibm.connect.sdk.file.FileMsgs;
import com.ibm.connect.sdk.file.FileSourceInteraction;
import com.ibm.connect.sdk.file.FileTargetInteraction;
import com.ibm.connect.sdk.file.FileUtils;
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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * A connector for connecting to Amazon S3 (unstructured / file data).
 */
public class AWSS3Connector extends FileConnector
{
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
        if (connectionProperties.getProperty("bucket") == null) {
            throw new IllegalArgumentException(FileMsgs.MISSING_PROPERTY.format("bucket"));
        }
        bucket = connectionProperties.getProperty("bucket");
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
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        s3Client = builder.build();

        // Validate the bucket is reachable (equivalent to a connection test).
        LOGGER.info("Validating access to S3 bucket: " + bucket);
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
        final ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .delimiter("/")
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
                if (totalSeen <= offset) {
                    continue;
                }
                if (added >= limit) {
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
                // the explicitly requested file.
                if (s3Object.key().equals(prefix) && !singleFileRequest) {
                    continue;
                }
                totalSeen++;
                if (totalSeen <= offset) {
                    continue;
                }
                if (added >= limit) {
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
    public FileTargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset) throws Exception
    {
        throw new UnsupportedOperationException(
                FileMsgs.DATASOURCE_TYPE_NOT_SUPPORTED.format(AWSS3DatasourceType.DATASOURCE_TYPE_NAME));
    }

    /**
     * {@inheritDoc}
     * Only the {@code get_acl} action is declared; full implementation is deferred to a future phase.
     */
    @Override
    public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration properties) throws Exception
    {
        if (AWSS3DatasourceType.ACTION_GET_ACL.equals(action)) {
            // TODO: Implement get_acl in a future phase.
            throw new UnsupportedOperationException(FileMsgs.UNSUPPORTED_ACTION.format(action));
        }
        throw new UnsupportedOperationException(FileMsgs.UNSUPPORTED_ACTION.format(action));
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
    void validateObjectKey(String key) throws Exception
    {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
        }
        catch (NoSuchKeyException e) {
            throw new IllegalArgumentException(AWSS3Msgs.OBJECT_DOES_NOT_EXIST.format(key), e);
        }
    }
}
