/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.box;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.arrow.flight.Ticket;

import com.ibm.connect.sdk.api.Record;
import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.file.FileConnector;
import com.ibm.connect.sdk.file.FileMsgs;
import com.ibm.connect.sdk.file.FileSourceInteraction;
import com.ibm.connect.sdk.file.FileUtils;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.connect.sdk.util.Utils;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

/**
 * An interaction with a Box asset as a source.
 *
 * <p>
 * Two read modes are supported:
 * <ul>
 * <li><strong>Structured</strong> ({@code file_format} is a recognised Spark
 * format such as {@code csv}, {@code json}, {@code parquet}, etc.) — the file
 * is streamed to a temporary local file and read by the Spark-based
 * {@link FileSourceInteraction} superclass, producing typed rows.</li>
 * <li><strong>Raw / unstructured</strong> ({@code file_format} is
 * {@code binary} or the file cannot be detected as a structured format) — the
 * raw bytes are streamed as a single Arrow record whose sole column is a
 * {@code varbinary} field named {@code content}.</li>
 * </ul>
 */
public class BoxSourceInteraction extends FileSourceInteraction
{
    private static final String CONTENT_FIELD_NAME = "content";
    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final BoxConnector connector;
    private final String fileName;
    private final boolean rawMode;

    private final ModelMapper modelMapper = new ModelMapper();
    private boolean rawRecordDelivered;
    private String tempFilename;

    /**
     * Creates a Box source interaction.
     *
     * @param connector
     *            the connector managing the connection to Box
     * @param asset
     *            the asset from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public BoxSourceInteraction(BoxConnector connector, CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        super(connector, asset, ticket);
        this.connector = connector;
        final Properties interactionProperties = getInteractionProperties();
        fileName = interactionProperties.getProperty("file_name");
        if (fileName == null) {
            throw new IllegalArgumentException(FileMsgs.MISSING_PROPERTY.format("file_name"));
        }

        // When file format is unknown or fields are missing (and the caller hasn't
        // explicitly requested binary mode), stream a prefix of the file to detect
        // MIME type, file format, CSV properties, and typed field definitions.
        // This mirrors the pattern used by S3 and localfs connectors.
        String fileFormat = interactionProperties.getProperty(PROP_FILE_FORMAT);
        boolean fileOpened = false;
        if (fileFormat == null || (asset.getFields() == null && !FileConnector.FILE_FORMAT_BINARY.equals(fileFormat))) {
            connector.addFileDetails(asset, fileName);
            fileOpened = true;
            fileFormat = getInteractionProperties().getProperty(PROP_FILE_FORMAT, FileConnector.FILE_FORMAT_BINARY);
        }
        rawMode = FileConnector.FILE_FORMAT_BINARY.equals(fileFormat);

        // In raw mode inject a single varbinary field so the schema is well-defined.
        if (rawMode && (asset.getFields() == null || asset.getFields().isEmpty())) {
            asset.addFieldsItem(new CustomFlightAssetField().name(CONTENT_FIELD_NAME).type("varbinary").nullable(false));
        }

        if (asset.getBatchSize() == null) {
            asset.setBatchSize(DEFAULT_BATCH_SIZE);
        }

        // On the getFlightInfo path (no ticket) fail fast when the file does not exist,
        // including in binary mode where no content has been read yet. Skipped when
        // addFileDetails() already opened (and thereby validated) the file.
        if (ticket == null && !fileOpened) {
            validateFile(fileName);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateFile(String name) throws Exception
    {
        connector.resolveFile(name);
    }

    /**
     * Streams the Box file to a local temp file so Spark can read it (structured
     * mode). In raw mode this method is never called.
     *
     * @return path to the local temp file
     */
    @Override
    protected String getFilename()
    {
        try {
            final String fileFormat = getInteractionProperties().getProperty(PROP_FILE_FORMAT, FileUtils.FILE_FORMAT_DELIMITED);
            final String baseName = fileName.contains("/") ? fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;
            try (InputStream fileStream = connector.openFile(fileName)) {
                tempFilename = FileUtils.createTempFile(fileStream, baseName, fileFormat);
            }
            return tempFilename;
        }
        catch (Exception e) {
            if (tempFilename != null) {
                FileUtils.deleteTempFile(tempFilename);
                tempFilename = null;
            }
            throw new UnsupportedOperationException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * In raw mode the entire Box file is returned as a single record containing a
     * {@code varbinary} value. In structured mode the superclass drives Spark.
     *
     * <p>
     * Before downloading, the file size is checked against the {@code byte_limit}
     * interaction property when specified.
     */
    @Override
    public Record getRecord()
    {
        if (!rawMode) {
            return super.getRecord();
        }
        if (rawRecordDelivered) {
            return null;
        }
        rawRecordDelivered = true;
        try {
            // Respect byte_limit before streaming.
            final String byteLimitStr = getInteractionProperties().getProperty("byte_limit");
            if (byteLimitStr != null) {
                final long byteLimit = Utils.parseByteLimit(byteLimitStr);
                final long fileSize = connector.getFileSize(fileName);
                if (fileSize > byteLimit) {
                    throw new IllegalArgumentException(
                            "File size " + fileSize + " exceeds byte_limit " + byteLimit);
                }
            }
            try (InputStream fileStream = connector.openFile(fileName)) {
                final byte[] bytes = readAllBytes(fileStream);
                final Record rec = new Record(1);
                rec.appendValue(bytes);
                return rec;
            }
        }
        catch (Exception e) {
            throw new UnsupportedOperationException(e.getMessage(), e);
        }
    }

    /**
     * Reads all bytes from {@code in} using a fixed-size buffer.
     */
    private static byte[] readAllBytes(InputStream in) throws java.io.IOException
    {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * {@inheritDoc}
     *
     * <p>In raw mode a single ticket is always returned (no partitioning).
     */
    @Override
    public List<Ticket> getTickets() throws Exception
    {
        final String requestId = UUID.randomUUID().toString();
        return Collections.singletonList(new Ticket(modelMapper.toBytes(new TicketInfo().requestId(requestId).partitionIndex(0))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CustomFlightAssetField> getFields()
    {
        return getAsset().getFields();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        super.close();
        if (tempFilename != null) {
            FileUtils.deleteTempFile(tempFilename);
            tempFilename = null;
        }
    }
}
