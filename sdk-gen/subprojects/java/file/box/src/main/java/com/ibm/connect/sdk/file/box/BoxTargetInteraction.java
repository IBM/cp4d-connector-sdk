/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.box;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Properties;

import com.ibm.connect.sdk.api.Record;
import com.ibm.connect.sdk.file.FileMsgs;
import com.ibm.connect.sdk.file.FileTargetInteraction;
import com.ibm.connect.sdk.file.FileUtils;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

/**
 * An interaction with a Box asset as a target.
 *
 * <p>
 * Two write modes are supported:
 * <ul>
 * <li><strong>Binary</strong> ({@code file_format} is {@code binary} or not
 * specified when the asset schema contains a single {@code varbinary} column
 * named {@code content}) — the raw bytes from the {@code content} field of the
 * first Arrow record are uploaded directly to Box.</li>
 * <li><strong>Structured</strong> ({@code file_format} is a recognised Spark
 * format such as {@code csv}, {@code json}, {@code parquet}, etc.) — the
 * tabular records are written to a local temporary file via the Spark-based
 * {@link FileTargetInteraction} superclass, and the resulting file is then
 * uploaded to Box.</li>
 * </ul>
 *
 * <p>
 * In both modes the target file in Box is identified by the required
 * {@code file_name} interaction property. If a file with that name already
 * exists in the resolved folder it is replaced; otherwise a new file is
 * created.
 */
public class BoxTargetInteraction extends FileTargetInteraction
{
    private static final String CONTENT_FIELD_NAME = "content";
    private static final String FILE_FORMAT_BINARY = "binary";

    private final BoxConnector connector;
    private final String fileName;
    private final boolean rawMode;

    private String tempFilename;

    /**
     * Creates a Box target interaction.
     *
     * @param connector
     *            the connector managing the connection to Box
     * @param asset
     *            the asset to which to write
     * @throws Exception
     */
    public BoxTargetInteraction(BoxConnector connector, CustomFlightAssetDescriptor asset) throws Exception
    {
        super(connector, asset);
        this.connector = connector;
        final Properties interactionProperties = getInteractionProperties();
        fileName = interactionProperties.getProperty("file_name");
        if (fileName == null) {
            throw new IllegalArgumentException(FileMsgs.MISSING_PROPERTY.format("file_name"));
        }

        final String fileFormat = interactionProperties.getProperty("file_format");
        rawMode = FILE_FORMAT_BINARY.equals(fileFormat)
                || (fileFormat == null && isSingleVarbinarySchema(asset));
    }

    /**
     * Returns {@code true} when the asset has a single field named
     * {@code content} of type {@code varbinary} — the binary-write schema.
     */
    private static boolean isSingleVarbinarySchema(CustomFlightAssetDescriptor asset)
    {
        if (asset.getFields() == null || asset.getFields().size() != 1) {
            return false;
        }
        final CustomFlightAssetField field = asset.getFields().get(0);
        return CONTENT_FIELD_NAME.equals(field.getName()) && "varbinary".equals(field.getType());
    }

    /**
     * Returns the local temporary file path used by Spark (structured mode only).
     * In raw mode this method is never called by the superclass.
     *
     * @return the absolute path to the local temp file
     */
    @Override
    protected String getFilename()
    {
        final Properties interactionProperties = getInteractionProperties();
        final String fileFormat = interactionProperties.getProperty("file_format", FileUtils.FILE_FORMAT_DELIMITED);
        final String baseName = fileName.contains("/") ? fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;
        try {
            tempFilename = java.nio.file.Files.createTempFile(baseName, FileUtils.getFilenameExtension(fileFormat)).toAbsolutePath().toString();
            return tempFilename;
        }
        catch (java.io.IOException e) {
            throw new UnsupportedOperationException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * In raw mode the {@code content} field of the first record is uploaded
     * directly to Box, bypassing Spark. In structured mode the superclass drives
     * Spark via {@link #getFilename()}.
     */
    @Override
    public void putAll(Iterator<Record> records)
    {
        if (!rawMode) {
            // Structured mode: delegate to Spark via the superclass.
            super.putAll(records);
            return;
        }
        // Binary mode: upload the raw bytes of the first record directly.
        if (!records.hasNext()) {
            return;
        }
        final Record record = records.next();
        final Object value = record.getValues().isEmpty() ? null : record.getValues().get(0);
        if (value == null) {
            throw new IllegalArgumentException("Binary write: 'content' field must not be null");
        }
        final byte[] bytes = (byte[]) value;
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            connector.uploadFile(fileName, in);
        }
        catch (Exception e) {
            throw new UnsupportedOperationException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightAssetDescriptor putSetup() throws Exception
    {
        return getAsset();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * In structured mode the Spark-written temp file is uploaded to Box here.
     */
    @Override
    public CustomFlightAssetDescriptor putWrapup() throws Exception
    {
        if (!rawMode && tempFilename != null) {
            try (InputStream in = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(tempFilename))) {
                connector.uploadFile(fileName, in);
            }
        }
        return getAsset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        if (tempFilename != null) {
            FileUtils.deleteTempFile(tempFilename);
            tempFilename = null;
        }
    }
}
