/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.s3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.arrow.flight.Ticket;

import com.ibm.connect.sdk.api.Record;
import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.file.FileMsgs;
import com.ibm.connect.sdk.file.FileSourceInteraction;
import com.ibm.connect.sdk.file.FileUtils;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

/**
 * An interaction with an Amazon S3 object as a source.
 *
 * <p>
 * Two read modes are supported:
 * <ul>
 * <li><strong>Structured</strong> ({@code file_format} is a recognised Spark
 * format such as {@code csv}, {@code json}, {@code parquet}, etc.) — the object
 * is downloaded to a temporary local file and read by the Spark-based
 * {@link FileSourceInteraction} superclass, producing typed rows.</li>
 * <li><strong>Raw / unstructured</strong> ({@code file_format} is
 * {@code binary} or the object cannot be detected as a structured format) — the
 * raw bytes are streamed as a single Arrow record whose sole column is a
 * {@code varbinary} field named {@code content}. This is the "read_raw"
 * capability for unstructured data.</li>
 * </ul>
 */
public class AWSS3SourceInteraction extends FileSourceInteraction
{
    private static final String RAW_CONTENT_FIELD = "content";
    private static final int DEFAULT_BATCH_SIZE = 1000;
    /** Read buffer size for raw-mode streaming (64 KiB). */
    private static final int READ_BUFFER_BYTES = 65536;

    private final AWSS3Connector connector;
    private final String objectKey;
    private final boolean rawMode;

    private String tempFilename;

    // State for raw-mode streaming (one record = entire object content).
    private final ModelMapper modelMapper = new ModelMapper();
    private boolean rawRecordDelivered;

    /**
     * Creates an Amazon S3 source interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public AWSS3SourceInteraction(AWSS3Connector connector, CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        super(connector, asset, ticket);
        this.connector = connector;

        final Properties interactionProperties = getInteractionProperties();
        final String fileName = interactionProperties.getProperty("file_name");
        if (fileName == null) {
            throw new IllegalArgumentException(FileMsgs.MISSING_PROPERTY.format("file_name"));
        }
        objectKey = connector.normalizeKey(fileName);

        // Resolve file format — may require inspecting the object.
        // Skip auto-detection when the caller has explicitly requested binary (raw) mode.
        String fileFormat = interactionProperties.getProperty("file_format");
        if (fileFormat == null || (asset.getFields() == null && !AWSS3DatasourceType.FILE_FORMAT_BINARY.equals(fileFormat))) {
            connector.validateObjectKey(objectKey);
            connector.addFileDetails(asset, objectKey);
            fileFormat = getInteractionProperties().getProperty("file_format");
        }
        rawMode = AWSS3DatasourceType.FILE_FORMAT_BINARY.equals(fileFormat);

        if (rawMode) {
            // Inject a single varbinary field so the schema is well-defined.
            if (asset.getFields() == null || asset.getFields().isEmpty()) {
                asset.addFieldsItem(new CustomFlightAssetField().name(RAW_CONTENT_FIELD).type("varbinary").nullable(false));
            }
        }

        if (asset.getBatchSize() == null) {
            asset.setBatchSize(DEFAULT_BATCH_SIZE);
        }
    }

    /**
     * Downloads the S3 object to a local temp file so Spark can read it (structured
     * mode). In raw mode this method is never called.
     *
     * @return path to the local temp file
     */
    @Override
    protected String getFilename()
    {
        try {
            final String fileFormat = getInteractionProperties().getProperty("file_format", FileUtils.FILE_FORMAT_DELIMITED);
            final String baseName = objectKey.contains("/") ? objectKey.substring(objectKey.lastIndexOf('/') + 1) : objectKey;
            try (InputStream objectStream = connector.openObject(objectKey)) {
                tempFilename = FileUtils.createTempFile(objectStream, baseName, fileFormat);
            }
            return tempFilename;
        }
        catch (Exception e) {
            // Clean up any partial temp file before propagating.
            if (tempFilename != null) {
                FileUtils.deleteTempFile(tempFilename);
                tempFilename = null;
            }
            throw new UnsupportedOperationException(e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Raw-mode record production — overrides the Spark-based path
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>
     * In raw mode the entire S3 object is returned as a single record containing a
     * {@code varbinary} value. In structured mode the superclass drives Spark.
     *
     * <p>
     * The object content is read in fixed-size chunks ({@value #READ_BUFFER_BYTES}
     * bytes) to avoid allocating the full object size upfront.
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
            // Respect byte_limit: reject objects that exceed it before buffering.
            final String byteLimitStr = getInteractionProperties().getProperty("byte_limit");
            if (byteLimitStr != null) {
                final long byteLimit = com.ibm.connect.sdk.util.Utils.parseByteLimit(byteLimitStr);
                final long objectSize = connector.headObject(objectKey).contentLength();
                if (objectSize > byteLimit) {
                    throw new IllegalArgumentException(
                            "Object size " + objectSize + " exceeds byte_limit " + byteLimit);
                }
            }
            try (InputStream objectStream = connector.openObject(objectKey)) {
                final byte[] bytes = readAllBytes(objectStream);
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
     * Reads all bytes from {@code in} using a fixed-size buffer so the JVM does not
     * need to know the stream length up-front.
     */
    private static byte[] readAllBytes(InputStream in) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buf = new byte[READ_BUFFER_BYTES];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * In raw mode a single ticket is always returned (no partitioning).
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
    public void close() throws Exception
    {
        super.close();
        if (tempFilename != null) {
            FileUtils.deleteTempFile(tempFilename);
            tempFilename = null;
        }
    }
}
