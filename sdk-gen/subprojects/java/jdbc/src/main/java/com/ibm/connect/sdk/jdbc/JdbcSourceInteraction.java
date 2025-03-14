/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc;

import static org.slf4j.LoggerFactory.getLogger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.arrow.adapter.jdbc.ArrowVectorIterator;
import org.apache.arrow.adapter.jdbc.JdbcToArrow;
import org.apache.arrow.adapter.jdbc.JdbcToArrowConfig;
import org.apache.arrow.adapter.jdbc.JdbcToArrowConfigBuilder;
import org.apache.arrow.adapter.jdbc.JdbcToArrowUtils;
import org.apache.arrow.flight.Ticket;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DateMilliVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.FixedSizeBinaryVector;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.LargeVarCharVector;
import org.apache.arrow.vector.SmallIntVector;
import org.apache.arrow.vector.TimeMicroVector;
import org.apache.arrow.vector.TimeMilliVector;
import org.apache.arrow.vector.TimeNanoVector;
import org.apache.arrow.vector.TimeSecVector;
import org.apache.arrow.vector.TimeStampMicroTZVector;
import org.apache.arrow.vector.TimeStampMilliTZVector;
import org.apache.arrow.vector.TimeStampNanoTZVector;
import org.apache.arrow.vector.TimeStampSecTZVector;
import org.apache.arrow.vector.TinyIntVector;
import org.apache.arrow.vector.UInt1Vector;
import org.apache.arrow.vector.UInt2Vector;
import org.apache.arrow.vector.UInt4Vector;
import org.apache.arrow.vector.UInt8Vector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.SourceInteraction;
import com.ibm.connect.sdk.api.TicketInfo;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.connect.sdk.util.Utils;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

/**
 * An interaction with a JDBC asset as a source.
 */
public abstract class JdbcSourceInteraction implements SourceInteraction<Connector<?, ?>>
{
    private static final Logger LOGGER = getLogger(JdbcSourceInteraction.class);

    private final ModelMapper modelMapper = new ModelMapper();
    private final JdbcConnector connector;
    private final Properties interactionProperties;
    private final CustomFlightAssetDescriptor asset;
    private final long byteLimitValue;
    private final TicketInfo ticketInfo;
    private final String statementText;
    private final PreparedStatement statement;
    private PreparedStatement partitioningStatement;
    private ResultSet resultSet;
    private ArrowVectorIterator iterator;
    private long byteCount;
    private VectorSchemaRoot vectorSchemaRoot;
    private boolean fetchedNextRow;
    private boolean haveNextRow;

    /**
     * Creates a JDBC source interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset from which to read
     * @param ticket
     *            a Flight ticket to read a partition or null to get tickets
     * @throws Exception
     */
    public JdbcSourceInteraction(JdbcConnector connector, CustomFlightAssetDescriptor asset, Ticket ticket) throws Exception
    {
        this.connector = connector;
        this.asset = asset;
        interactionProperties = ModelMapper.toProperties(asset.getInteractionProperties());
        final String schemaName = interactionProperties.getProperty("schema_name");
        final String tableName = interactionProperties.getProperty("table_name");
        final String selectStatement = interactionProperties.getProperty("select_statement");
        if (tableName == null && selectStatement == null) {
            throw new IllegalArgumentException("Missing table name or SELECT statement");
        }
        final String byteLimit = interactionProperties.getProperty("byte_limit");
        if (byteLimit != null) {
            asset.setPartitionCount(1);
        }
        byteLimitValue = (byteLimit != null) ? Utils.parseByteLimit(byteLimit) : -1;
        ticketInfo = (ticket != null) ? modelMapper.fromBytes(ticket.getBytes(), TicketInfo.class) : null;
        if (selectStatement != null) {
            statementText = selectStatement;
            asset.setPartitionCount(1);
        } else {
            final StringBuilder query = new StringBuilder(50);
            query.append("SELECT ");
            final String rowLimitStr = interactionProperties.getProperty("row_limit");
            final long rowLimit = (rowLimitStr != null) ? Long.parseLong(rowLimitStr) : -1;
            if (rowLimit > 0) {
                asset.setPartitionCount(1);
                final String rowLimitPrefix = generateRowLimitPrefix(rowLimit);
                if (rowLimitPrefix != null) {
                    query.append(rowLimitPrefix);
                    query.append(' ');
                }
            }
            if (asset.getFields() == null) {
                query.append('*');
            } else {
                for (int i = 0; i < asset.getFields().size(); i++) {
                    if (i > 0) {
                        query.append(", ");
                    }
                    final CustomFlightAssetField field = asset.getFields().get(i);
                    query.append(connector.getIdentifierQuote());
                    query.append(field.getName());
                    query.append(connector.getIdentifierQuote());
                }
            }
            query.append(" FROM ");
            if (schemaName != null) {
                query.append(connector.getIdentifierQuote());
                query.append(schemaName);
                query.append(connector.getIdentifierQuote());
                query.append('.');
            }
            query.append(connector.getIdentifierQuote());
            query.append(tableName);
            query.append(connector.getIdentifierQuote());
            if (rowLimit > 0) {
                asset.setPartitionCount(1);
                final String rowLimitSuffix = generateRowLimitSuffix(rowLimit);
                if (rowLimitSuffix != null) {
                    query.append(' ');
                    query.append(rowLimitSuffix);
                }
            }
            statementText = query.toString();
        }
        statement = connector.getConnection().prepareStatement(statementText, getResultSetType(), ResultSet.CONCUR_READ_ONLY);
    }

    private int getResultSetType()
    {
        return connector.supportsScrollableCursors() ? ResultSet.TYPE_SCROLL_INSENSITIVE : ResultSet.TYPE_FORWARD_ONLY;
    }

    /**
     * Returns the connector managing the connection to the data source.
     *
     * @return the connector managing the connection to the data source
     */
    public JdbcConnector getConnector()
    {
        return connector;
    }

    /**
     * Generates the row limit prefix.
     *
     * @param rowLimit
     *            row limit
     * @return the generated row limit prefix
     */
    protected abstract String generateRowLimitPrefix(long rowLimit);

    /**
     * Generates the row limit suffix.
     *
     * @param rowLimit
     *            row limit
     * @return the generated row limit suffix
     */
    protected abstract String generateRowLimitSuffix(long rowLimit);

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() throws Exception
    {
        final List<Field> fields = new ArrayList<>();
        final ResultSetMetaData rsmd = statement.getMetaData();
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            final String name = rsmd.getColumnName(i);
            final int jdbcType = rsmd.getColumnType(i);
            final String nativeType = rsmd.getColumnTypeName(i);
            final String type = AssetFieldType.getTypeName(jdbcType);
            final int length = rsmd.getPrecision(i);
            final int scale = rsmd.getScale(i);
            final boolean nullable = rsmd.isNullable(i) != ResultSetMetaData.columnNoNulls;
            final boolean signed = AssetFieldType.isNumeric(jdbcType) && !nativeType.contains("UNSIGNED");
            final CustomFlightAssetField assetField
                    = new CustomFlightAssetField().name(name).type(type).length(length).scale(scale).nullable(nullable).signed(signed);
            fields.add(connector.getField(assetField, jdbcType));
        }
        return new Schema(fields);
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
        for (int i = 0; i < asset.getPartitionCount(); i++) {
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

    /**
     * Returns a partitioning predicate.
     *
     * @param partitionInfo
     *            information for which partition to read
     * @return a partitioning predicate
     */
    protected abstract String getPartitioningPredicate(TicketInfo partitionInfo);

    /**
     * {@inheritDoc}
     */
    @Override
    public void beginStream(BufferAllocator allocator) throws Exception
    {
        final String partitioningPredicate = getPartitioningPredicate(ticketInfo);
        if (partitioningPredicate != null) {
            final String partitioningQuery = statementText + " WHERE " + partitioningPredicate;
            LOGGER.info(partitioningQuery);
            partitioningStatement
                    = connector.getConnection().prepareStatement(partitioningQuery, getResultSetType(), ResultSet.CONCUR_READ_ONLY); // NOSONAR
        } else {
            LOGGER.info(statementText);
        }
        resultSet = partitioningStatement != null ? partitioningStatement.executeQuery() : statement.executeQuery();
        if (connector.supportsScrollableCursors()) {
            final JdbcToArrowConfig config = new JdbcToArrowConfigBuilder().setAllocator(allocator)
                    .setCalendar(JdbcToArrowUtils.getUtcCalendar()).setTargetBatchSize(getBatchSize())
                    .setJdbcToArrowTypeConverter(connector.getJdbcToArrowTypeConverter()).build();
            iterator = JdbcToArrow.sqlToArrowVectorIterator(resultSet, config);
        } else {
            vectorSchemaRoot = VectorSchemaRoot.create(getSchema(), allocator);
            fetchedNextRow = false;
            haveNextRow = false;
        }
    }

    private int getBatchSize()
    {
        return asset.getBatchSize() != null ? asset.getBatchSize() : JdbcToArrowConfig.DEFAULT_TARGET_BATCH_SIZE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNextBatch() throws Exception
    {
        if (byteLimitValue >= 0 && byteCount >= byteLimitValue) {
            return false;
        }
        if (iterator != null) {
            return iterator.hasNext();
        }
        return hasNextRow();
    }

    private boolean hasNextRow() throws Exception
    {
        if (!fetchedNextRow) {
            haveNextRow = resultSet.next();
            fetchedNextRow = true;
        }
        return haveNextRow;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("PMD.CloseResource")
    public VectorSchemaRoot nextBatch() throws Exception
    {
        final VectorSchemaRoot batch = iterator != null ? iterator.next() : nextResultSetBatch();
        if (byteLimitValue < 0) {
            return batch;
        }
        for (final FieldVector vector : batch.getFieldVectors()) {
            byteCount += vector.getBufferSize();
        }
        return batch;
    }

    @SuppressWarnings("PMD.CloseResource")
    private VectorSchemaRoot nextResultSetBatch() throws Exception
    {
        vectorSchemaRoot.clear();
        final int batchSize = getBatchSize();
        int rowIdx;
        for (rowIdx = 0; hasNextRow() && rowIdx < batchSize; rowIdx++) {
            for (int colIdx = 0; colIdx < vectorSchemaRoot.getFieldVectors().size(); colIdx++) {
                final FieldVector vector = vectorSchemaRoot.getFieldVectors().get(colIdx);
                final int columnIndex = colIdx + 1;
                if (vector instanceof VarCharVector) {
                    final String value = resultSet.getString(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((VarCharVector) vector).setSafe(rowIdx, value.getBytes(StandardCharsets.UTF_8));
                    }
                } else if (vector instanceof LargeVarCharVector) {
                    final String value = resultSet.getString(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((LargeVarCharVector) vector).setSafe(rowIdx, value.getBytes(StandardCharsets.UTF_8));
                    }
                } else if (vector instanceof BitVector) {
                    final boolean value = resultSet.getBoolean(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((BitVector) vector).setSafe(rowIdx, value ? 1 : 0);
                    }
                } else if (vector instanceof TinyIntVector) {
                    final byte value = resultSet.getByte(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((TinyIntVector) vector).setSafe(rowIdx, value);
                    }
                } else if (vector instanceof SmallIntVector) {
                    final int value = resultSet.getShort(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((SmallIntVector) vector).setSafe(rowIdx, value);
                    }
                } else if (vector instanceof IntVector) {
                    final int value = resultSet.getInt(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((IntVector) vector).setSafe(rowIdx, value);
                    }
                } else if (vector instanceof BigIntVector) {
                    final long value = resultSet.getLong(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((BigIntVector) vector).setSafe(rowIdx, value);
                    }
                } else if (vector instanceof UInt1Vector) {
                    final int value = resultSet.getShort(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((UInt1Vector) vector).setSafe(rowIdx, value);
                    }
                } else if (vector instanceof UInt2Vector) {
                    final int value = resultSet.getInt(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((UInt2Vector) vector).setSafe(rowIdx, value);
                    }
                } else if (vector instanceof UInt4Vector) {
                    final long value = resultSet.getLong(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((UInt4Vector) vector).setSafe(rowIdx, (int) value);
                    }
                } else if (vector instanceof UInt8Vector) {
                    final long value = resultSet.getLong(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((UInt8Vector) vector).setSafe(rowIdx, value);
                    }
                } else if (vector instanceof Float4Vector) {
                    final float value = resultSet.getFloat(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((Float4Vector) vector).setSafe(rowIdx, value);
                    }
                } else if (vector instanceof Float8Vector) {
                    final double value = resultSet.getDouble(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((Float8Vector) vector).setSafe(rowIdx, value);
                    }
                } else if (vector instanceof DecimalVector) {
                    final BigDecimal value = resultSet.getBigDecimal(columnIndex);
                    if (!resultSet.wasNull()) {
                        final int decimalScale = ((DecimalVector) vector).getScale();
                        if (decimalScale >= 0) {
                            ((DecimalVector) vector).setSafe(rowIdx, value.setScale(decimalScale, RoundingMode.FLOOR));
                        } else {
                            ((DecimalVector) vector).setSafe(rowIdx, value);
                        }
                    }
                } else if (vector instanceof FixedSizeBinaryVector) {
                    final byte[] value = resultSet.getBytes(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((FixedSizeBinaryVector) vector).setSafe(rowIdx, value);
                    }
                } else if (vector instanceof VarBinaryVector) {
                    final byte[] value = resultSet.getBytes(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((VarBinaryVector) vector).setSafe(rowIdx, value);
                    }
                } else if (vector instanceof TimeNanoVector) {
                    final Time value = resultSet.getTime(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((TimeNanoVector) vector).setSafe(rowIdx, TimeUnit.MILLISECONDS.toNanos(value.getTime()));
                    }
                } else if (vector instanceof TimeMicroVector) {
                    final Time value = resultSet.getTime(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((TimeMicroVector) vector).setSafe(rowIdx, TimeUnit.MILLISECONDS.toMicros(value.getTime()));
                    }
                } else if (vector instanceof TimeMilliVector) {
                    final Time value = resultSet.getTime(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((TimeMilliVector) vector).setSafe(rowIdx, (int) value.getTime());
                    }
                } else if (vector instanceof TimeSecVector) {
                    final Time value = resultSet.getTime(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((TimeSecVector) vector).setSafe(rowIdx, (int) TimeUnit.MILLISECONDS.toSeconds(value.getTime()));
                    }
                } else if (vector instanceof DateDayVector) {
                    final Date value = resultSet.getDate(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((DateDayVector) vector).setSafe(rowIdx, (int) TimeUnit.MILLISECONDS.toDays(value.getTime()));
                    }
                } else if (vector instanceof DateMilliVector) {
                    final Date value = resultSet.getDate(columnIndex);
                    if (!resultSet.wasNull()) {
                        ((DateMilliVector) vector).setSafe(rowIdx, value.getTime());
                    }
                } else if (vector instanceof TimeStampNanoTZVector) {
                    final TimeZone timeZone = getTimeZone((ArrowType.Timestamp) vector.getField().getType());
                    final Calendar calendar = Calendar.getInstance(timeZone);
                    final Timestamp value = resultSet.getTimestamp(columnIndex, calendar);
                    if (!resultSet.wasNull()) {
                        ((TimeStampNanoTZVector) vector).setSafe(rowIdx, TimeUnit.MILLISECONDS.toNanos(value.getTime()));
                    }
                } else if (vector instanceof TimeStampMicroTZVector) {
                    final TimeZone timeZone = getTimeZone((ArrowType.Timestamp) vector.getField().getType());
                    final Calendar calendar = Calendar.getInstance(timeZone);
                    final Timestamp value = resultSet.getTimestamp(columnIndex, calendar);
                    if (!resultSet.wasNull()) {
                        ((TimeStampMicroTZVector) vector).setSafe(rowIdx, TimeUnit.MILLISECONDS.toMicros(value.getTime()));
                    }
                } else if (vector instanceof TimeStampMilliTZVector) {
                    final TimeZone timeZone = getTimeZone((ArrowType.Timestamp) vector.getField().getType());
                    final Calendar calendar = Calendar.getInstance(timeZone);
                    final Timestamp value = resultSet.getTimestamp(columnIndex, calendar);
                    if (!resultSet.wasNull()) {
                        ((TimeStampMilliTZVector) vector).setSafe(rowIdx, value.getTime());
                    }
                } else if (vector instanceof TimeStampSecTZVector) {
                    final TimeZone timeZone = getTimeZone((ArrowType.Timestamp) vector.getField().getType());
                    final Calendar calendar = Calendar.getInstance(timeZone);
                    final Timestamp value = resultSet.getTimestamp(columnIndex, calendar);
                    if (!resultSet.wasNull()) {
                        ((TimeStampSecTZVector) vector).setSafe(rowIdx, TimeUnit.MILLISECONDS.toSeconds(value.getTime()));
                    }
                } else {
                    throw new UnsupportedOperationException(
                            "Unsupported data type for column " + vectorSchemaRoot.getSchema().getFields().get(colIdx).getName());
                }
            }
            fetchedNextRow = false;
        }
        vectorSchemaRoot.setRowCount(rowIdx);
        return vectorSchemaRoot;
    }

    private TimeZone getTimeZone(ArrowType.Timestamp arrowType)
    {
        final String timeZoneId = arrowType.getTimezone();
        if (timeZoneId == null) {
            return TimeZone.getDefault();
        }
        return TimeZone.getTimeZone(timeZoneId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        try {
            if (iterator != null) {
                iterator.close();
            }
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        try {
            if (vectorSchemaRoot != null) {
                vectorSchemaRoot.close();
            }
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        try {
            if (resultSet != null) {
                resultSet.close();
            }
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        try {
            if (partitioningStatement != null) {
                partitioningStatement.close();
            }
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        try {
            if (statement != null) {
                statement.close();
            }
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

}
