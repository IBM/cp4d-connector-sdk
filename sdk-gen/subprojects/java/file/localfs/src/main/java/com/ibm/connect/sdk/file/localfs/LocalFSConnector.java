/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.localfs;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

/**
 * A connector for connecting to the local file system.
 */
public class LocalFSConnector extends FileConnector
{
    private static final Logger LOGGER = getLogger(LocalFSConnector.class);

    private static final String ENVVAR_ROOT_PATH = "LOCALFS_ROOT_PATH";

    private Path fsRootPath;
    private boolean fsRootPathCreated;
    private Path connectionRootPath;

    /**
     * Creates a local file system connector.
     *
     * @param properties
     *            connection properties
     */
    public LocalFSConnector(ConnectionProperties properties)
    {
        super(properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect() throws Exception
    {
        // If the root path is not null, then we're reusing a pooled connection.
        if (fsRootPath == null) {
            // The environment needs to define what part of the file system we are allowed
            // to access. If not defined, create a temporary directory as the root path.
            final String fsRootPathStr = System.getenv(ENVVAR_ROOT_PATH);
            if (fsRootPathStr == null) {
                fsRootPath = Files.createTempDirectory("LocalFSConnector");
                fsRootPathCreated = true;
                LOGGER.info("Created temp directory " + fsRootPath);
            } else {
                fsRootPath = Paths.get(fsRootPathStr).normalize().toAbsolutePath();
                if (!fsRootPath.toFile().exists()) {
                    throw new IllegalArgumentException(LocalFSMsgs.DOES_NOT_EXIST.format(fsRootPathStr));
                }
                if (!fsRootPath.toFile().isDirectory()) {
                    throw new IllegalArgumentException(LocalFSMsgs.NOT_A_DIRECTORY.format(fsRootPathStr));
                }
            }
            final String connectionRootPathStr = getConnectionProperties().getProperty("root_path", "");
            connectionRootPath = resolvePath(fsRootPath, connectionRootPathStr);
            if (connectionRootPath.toFile().exists() && !connectionRootPath.toFile().isDirectory()) {
                throw new IllegalArgumentException(LocalFSMsgs.NOT_A_DIRECTORY.format(connectionRootPathStr));
            }
        }
    }

    private Path resolvePath(Path root, String pathStr)
    {
        final Path path = root.resolve(normalizePath(pathStr)).toAbsolutePath();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException(FileMsgs.INVALID_PATH.format(pathStr));
        }
        return path;
    }

    protected Path resolvePath(String pathStr)
    {
        return resolvePath(connectionRootPath, pathStr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception
    {
        return listFiles(criteria, resolvePath(criteria.getPath()));
    }

    private String normalizePath(String path)
    {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private List<CustomFlightAssetDescriptor> listFiles(CustomFlightAssetsCriteria criteria, Path filePath)
            throws Exception
    {
        final List<CustomFlightAssetDescriptor> descriptors = new ArrayList<>();
        if (filePath.toFile().isDirectory()) {
            final Iterator<Path> pathIterator = Files.newDirectoryStream(filePath).iterator();
            final int offset = criteria.getOffset() == null || criteria.getOffset() < 0 ? 0 : criteria.getOffset();
            final int limit = criteria.getLimit() == null || criteria.getLimit() < 0 ? Integer.MAX_VALUE : criteria.getLimit();
            final int endIndex = offset + limit;
            for (int i = 0; i < offset && pathIterator.hasNext(); i++) {
                pathIterator.next();
            }
            for (int i = offset; i < endIndex && pathIterator.hasNext(); i++) {
                final Path path = pathIterator.next();
                final CustomFlightAssetDescriptor asset = createAssetDescriptor(path, false);
                if (asset != null) {
                    descriptors.add(asset);
                }
            }
        } else {
            final CustomFlightAssetDescriptor asset = createAssetDescriptor(filePath, true);
            if (asset != null) {
                descriptors.add(asset);
            }
        }
        return descriptors;
    }

    private CustomFlightAssetDescriptor createAssetDescriptor(Path path, boolean describeInteraction)
            throws Exception
    {
        final String fileName = path.getFileName().toString();
        final int rootNameCount = connectionRootPath.getNameCount();
        final int pathNameCount = path.getNameCount();
        final String filePath;
        if (rootNameCount == pathNameCount) {
            filePath = "/";
        } else {
            String subpath = path.subpath(rootNameCount, pathNameCount).toString();
            if (File.separatorChar == '\\') {
                subpath = subpath.replaceAll("\\\\", "/");
            }
            if (!subpath.startsWith("/")) {
                subpath = "/" + subpath;
            }
            filePath = subpath;
        }
        if (path.toFile().isDirectory()) {
            return new CustomFlightAssetDescriptor().name(fileName).path(filePath).assetType(folderAssetType());
        }
        if (path.toFile().isFile()) {
            final CustomFlightAssetDescriptor asset
                    = new CustomFlightAssetDescriptor().name(fileName).path(filePath).assetType(fileAssetType());

            // Add details like file_size that can be acquired directly from the file information.
            addObjectDetails(asset, path);

            if (describeInteraction) {
                final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
                interactionProperties.put("file_name", filePath);
                asset.setInteractionProperties(interactionProperties);

                // Add details that require examining the file content.
                addFileDetails(asset, path);
            }
            return asset;
        }
        return null;
    }

    private DiscoveredAssetType folderAssetType()
    {
        return new DiscoveredAssetType().type("folder").dataset(false).datasetContainer(true);
    }

    private DiscoveredAssetType fileAssetType()
    {
        return new DiscoveredAssetType().type("file").dataset(true).datasetContainer(false);
    }

    protected void addObjectDetails(CustomFlightAssetDescriptor asset, Path path)
    {
        final long fileSize = path.toFile().length();
        final DiscoveredAssetDetails details = new DiscoveredAssetDetails();
        details.put("file_size", fileSize);
        asset.setDetails(details);
    }

    protected void addFileDetails(CustomFlightAssetDescriptor asset, Path path) throws Exception
    {
        // Examine the file contents to detect mime_type and file_format.
        final String fileName = path.getFileName().toString();
        try (InputStream fileStream = FileUtils.ensureMarkSupported(Files.newInputStream(path))) {
            // Detect mime type.
            final String mimeType = FileUtils.detectMimeType(fileStream, fileName);
            if (mimeType != null) {
                asset.getDetails().put("mime_type", mimeType);
            }

            // Detect file format.
            final String detectedFileFormat = FileUtils.detectFileFormat(mimeType, fileName, fileStream);
            final String fileFormat = detectedFileFormat != null ? detectedFileFormat : FileUtils.FILE_FORMAT_DELIMITED;
            asset.getInteractionProperties().put("file_format", fileFormat);

            // Detect delimited file properties.
            if (FileUtils.FILE_FORMAT_CSV.equals(fileFormat) || FileUtils.FILE_FORMAT_DELIMITED.equals(fileFormat)) {
                FileUtils.detectDelimitedProperties(fileStream, asset.getInteractionProperties());
            }
        }

        // Describe fields.
        addAssetFields(asset, path.toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileSourceInteraction getSourceInteraction(CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        return new LocalFSSourceInteraction(this, asset, ticket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileTargetInteraction getTargetInteraction(CustomFlightAssetDescriptor asset) throws Exception
    {
        return new LocalFSTargetInteraction(this, asset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration properties) throws Exception
    {
        throw new UnsupportedOperationException(FileMsgs.UNSUPPORTED_ACTION.format(action));
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
        try {
            if (fsRootPathCreated) {
                LOGGER.info("Deleting temp directory " + fsRootPath);
                FileUtils.deleteTempDirectory(fsRootPath.toString());
            }
        }
        finally {
            fsRootPath = null;
            fsRootPathCreated = false;
        }
    }
}
