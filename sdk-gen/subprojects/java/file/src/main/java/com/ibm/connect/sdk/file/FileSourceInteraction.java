/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025, 2026                  */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.arrow.flight.Ticket;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import com.ibm.connect.sdk.api.Record;
import com.ibm.connect.sdk.api.RowBasedSourceInteraction;
import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.connect.sdk.util.Utils;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

/**
 * An interaction with a file asset as a source.
 */
public abstract class FileSourceInteraction extends RowBasedSourceInteraction<FileConnector>
{
    /** Interaction property key for the file format (e.g. {@code "csv"}, {@code "json"}). */
    protected static final String PROP_FILE_FORMAT = "file_format";

    private final static int DEFAULT_BATCH_SIZE = 1000;

    private final ModelMapper modelMapper = new ModelMapper();
    private final TicketInfo ticketInfo;
    private final long rowLimit;
    private final long byteLimit;

    private Dataset<Row> dataframe;
    private Iterator<Row> rowIterator;
    private long rowCount;
    private long byteCount;

    /**
     * Creates a file source interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public FileSourceInteraction(FileConnector connector, CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        super();
        setConnector(connector);
        setAsset(asset);
        final Properties interactionProperties = getInteractionProperties();
        final String rowLimitStr = interactionProperties.getProperty("row_limit");
        final String byteLimitStr = interactionProperties.getProperty("byte_limit");
        rowLimit = (rowLimitStr != null) ? Long.parseLong(rowLimitStr) : -1;
        byteLimit = (byteLimitStr != null) ? Utils.parseByteLimit(byteLimitStr) : -1;
        if (rowLimit >= 0 || byteLimit >= 0) {
            asset.setPartitionCount(1);
        }
        ticketInfo = (ticket != null) ? modelMapper.fromBytes(ticket.getBytes(), TicketInfo.class) : null;
        if (asset.getBatchSize() == null) {
            asset.setBatchSize(DEFAULT_BATCH_SIZE);
        }
    }

    /**
     * Hook for validating that the file exists on the getFlightInfo path
     * ({@code ticket == null}), so that a missing file is reported at discovery time
     * rather than only when the data is streamed.
     *
     * <p>
     * This base class never calls it, because subclass fields (such as the
     * connector) are not yet initialised while this constructor runs. A subclass
     * that overrides it must call it itself at the end of its own constructor.
     *
     * <p>
     * The default implementation is a no-op. Subclasses that can perform a cheap
     * existence check (e.g. by resolving the file handle) should override this method
     * and throw {@link IllegalArgumentException} when the file is not found.
     *
     * @param fileName
     *            the value of the {@code file_name} interaction property
     * @throws Exception
     *             if the file cannot be found or accessed
     */
    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    protected void validateFile(String fileName) throws Exception
    {
        // no-op by default
    }

    /**
     * Returns the interaction properties.
     *
     * @return the interaction properties
     */
    public Properties getInteractionProperties()
    {
        return ModelMapper.toProperties(getAsset().getInteractionProperties());
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
     * @throws Exception 
     */
    @Override
    public Record getRecord()
    {
        if ((rowLimit >= 0 && rowCount >= rowLimit) || (byteLimit >= 0 && byteCount >= byteLimit)) {
            return null;
        }
        if (dataframe == null) {
            final String filename = getFilename();
            dataframe = getConnector().getDataframe(getAsset(), filename);
            rowIterator = dataframe.toLocalIterator();
        }
        if (rowIterator.hasNext()) {
            final Row row = rowIterator.next();
            final Record rec = new Record(row.size());
            for (int i = 0; i < row.size(); i++) {
                rec.appendValue((Serializable) row.get(i));
            }
            rowCount++;
            byteCount += rec.getSizeInBytes();
            return rec;
        }
        return null;
    }

    /**
     * Returns the name of the file asset that is accessible by Spark.
     *
     * @return the name of the file asset that is accessible by Spark
     */
    protected abstract String getFilename();

    /**
     * Returns the ticket info.
     *
     * @return the ticket info
     */
    public TicketInfo getTicketInfo()
    {
        return ticketInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Ticket> getTickets() throws Exception
    {
        final String requestId = UUID.randomUUID().toString();
        if (!isPartitioningSupported()) {
            return Collections.singletonList(new Ticket(modelMapper.toBytes(new TicketInfo().requestId(requestId).partitionIndex(0))));
        }
        final List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < getAsset().getPartitionCount(); i++) {
            tickets.add(new Ticket(modelMapper.toBytes(new TicketInfo().requestId(requestId).partitionIndex(i))));
        }
        return tickets;
    }

    /**
     * Returns true if the interaction supports partitioning.
     *
     * @return true if the interaction supports partitioning
     */
    protected boolean isPartitioningSupported()
    {
        return false;
    }

}
