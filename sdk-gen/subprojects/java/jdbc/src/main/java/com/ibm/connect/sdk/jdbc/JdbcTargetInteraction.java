/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc;

import static org.slf4j.LoggerFactory.getLogger;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DateMilliVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.LargeVarCharVector;
import org.apache.arrow.vector.TimeMicroVector;
import org.apache.arrow.vector.TimeMilliVector;
import org.apache.arrow.vector.TimeNanoVector;
import org.apache.arrow.vector.TimeSecVector;
import org.apache.arrow.vector.TimeStampMicroTZVector;
import org.apache.arrow.vector.TimeStampMilliTZVector;
import org.apache.arrow.vector.TimeStampNanoTZVector;
import org.apache.arrow.vector.TimeStampSecTZVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;
import org.slf4j.Logger;

import com.google.common.collect.HashBasedTable;
import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.api.TargetInteraction;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

/**
 * An interaction with a JDBC asset as a target.
 */
public abstract class JdbcTargetInteraction implements TargetInteraction<Connector<?, ?>>
{
    private static final Logger LOGGER = getLogger(JdbcTargetInteraction.class);

    private final JdbcConnector connector;
    private final CustomFlightAssetDescriptor asset;
    private final Properties interactionProperties;
    private final String schemaName;
    private final String tableName;
    private final String statementText;
    private final int[] paramIndexes;
    private final String staticStatementText;
    private final String writeMode;
    private final String tableAction;
    private PreparedStatement statement;

    /**
     * Creates a JDBC target interaction.
     *
     * @param connector
     *            the connector managing the connection to the data source
     * @param asset
     *            the asset to which to write
     * @throws Exception
     */
    public JdbcTargetInteraction(JdbcConnector connector, CustomFlightAssetDescriptor asset) throws Exception
    {
        this.connector = connector;
        this.asset = asset;
        interactionProperties = ModelMapper.toProperties(asset.getInteractionProperties());
        schemaName = interactionProperties.getProperty("schema_name");
        tableName = interactionProperties.getProperty("table_name");
        writeMode = interactionProperties.getProperty("write_mode", "insert");
        tableAction = interactionProperties.getProperty("table_action", "append");
        staticStatementText = interactionProperties.getProperty("static_statement");
        final String updateStmt = interactionProperties.getProperty("update_statement");
        if (tableName == null && ((updateStmt == null && staticStatementText == null) || !"append".equals(tableAction))) {
            throw new IllegalArgumentException("Missing table name");
        }
        if ("update_statement".equals(writeMode) && updateStmt == null) {
            throw new IllegalArgumentException("Missing update statement");
        }
        if ("static_statement".equals(writeMode) && staticStatementText == null) {
            throw new IllegalArgumentException("Missing static statement");
        }
        if (updateStmt != null) {
            statementText = updateStmt;
            paramIndexes = "insert".equals(writeMode) ? createInsertParamIndexes() : createUpdateParamIndexes();
        } else if ("insert".equals(writeMode)) {
            statementText = generateInsertStatementText();
            paramIndexes = createInsertParamIndexes();
        } else if ("update".equals(writeMode)) {
            statementText = generateUpdateStatementText();
            paramIndexes = createUpdateParamIndexes();
        } else {
            statementText = null;
            paramIndexes = null;
        }
        // For single partitions, do the prepare in setup because the table may not
        // exist yet.
        if (statementText != null && asset.getPartitionCount() != null && asset.getPartitionCount() > 1
                && asset.getPartitionIndex() != null) {
            statement = connector.getConnection().prepareStatement(statementText);
        }
    }

    private String generateInsertStatementText()
    {
        if (asset.getFields() == null || asset.getFields().size() == 0) {
            throw new IllegalArgumentException("Missing fields");
        }
        final StringBuilder stmt = new StringBuilder(50);
        stmt.append("INSERT INTO ");
        stmt.append(generateQualifiedTableName());
        stmt.append(' ');
        for (int i = 0; i < asset.getFields().size(); i++) {
            stmt.append(i == 0 ? '(' : ',');
            stmt.append(generateSQLIdentifier(asset.getFields().get(i).getName()));
        }
        stmt.append(") VALUES (?");
        for (int i = 1; i < asset.getFields().size(); i++) {
            stmt.append(",?");
        }
        stmt.append(')');
        return stmt.toString();
    }

    private String generateQualifiedTableName()
    {
        final StringBuilder qualifiedName = new StringBuilder(50);
        if (schemaName != null) {
            qualifiedName.append(generateSQLIdentifier(schemaName));
            qualifiedName.append('.');
        }
        qualifiedName.append(generateSQLIdentifier(tableName));
        return qualifiedName.toString();
    }

    private String generateSQLIdentifier(String name)
    {
        final StringBuilder identifier = new StringBuilder(50);
        identifier.append(connector.getIdentifierQuote());
        identifier.append(name);
        identifier.append(connector.getIdentifierQuote());
        return identifier.toString();
    }

    private String generateUpdateStatementText() throws Exception
    {
        if (asset.getFields() == null || asset.getFields().size() == 0) {
            throw new IllegalArgumentException("Missing fields");
        }
        final List<String> fieldNames = asset.getFields().stream().map(CustomFlightAssetField::getName).collect(Collectors.toList());
        final List<String> pkFieldNames = getPrimaryKeyColumnNames();
        final Map<Boolean, List<String>> keyFieldNamesMap = fieldNames.stream().collect(Collectors.partitioningBy(pkFieldNames::contains));
        final List<String> keyFieldNames = keyFieldNamesMap.get(true);
        final List<String> nonKeyFieldNames = keyFieldNamesMap.get(false);
        final StringBuilder stmt = new StringBuilder(50);
        stmt.append("UPDATE ");
        stmt.append(generateQualifiedTableName());
        stmt.append(" SET ");
        stmt.append(
                nonKeyFieldNames.stream().map(fieldName -> generateSQLIdentifier(fieldName) + " = ?").collect(Collectors.joining(", ")));
        stmt.append(" WHERE ");
        stmt.append(
                keyFieldNames.stream().map(fieldName -> generateSQLIdentifier(fieldName) + " = ?").collect(Collectors.joining(" AND ")));
        return stmt.toString();
    }

    private List<String> getPrimaryKeyColumnNames() throws Exception
    {
        final String keyColumnNames = interactionProperties.getProperty("key_column_names");
        if (keyColumnNames != null) {
            final String[] keyColumnNamesArray = keyColumnNames.split(",");
            return Arrays.asList(keyColumnNamesArray);
        }
        if (schemaName == null) {
            throw new IllegalArgumentException("Missing schema name");
        }
        if (tableName == null) {
            throw new IllegalArgumentException("Missing table name");
        }
        final List<String> keyColumns = new ArrayList<>();
        try (ResultSet result
                = connector.getConnection().getMetaData().getPrimaryKeys(connector.supportsSchemas() ? connector.getCatalog() : schemaName,
                        connector.supportsSchemas() ? schemaName : null, tableName)) {
            while (result.next()) {
                keyColumns.add(result.getString("COLUMN_NAME"));
            }
        }
        return keyColumns;
    }

    private int[] createInsertParamIndexes()
    {
        if (asset.getFields() == null || asset.getFields().size() == 0) {
            throw new IllegalArgumentException("Missing fields");
        }
        final int fieldCount = asset.getFields().size();
        final int[] paramIndices = new int[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
            paramIndices[i] = i + 1;
        }
        return paramIndices;
    }

    private int[] createUpdateParamIndexes() throws Exception
    {
        if (asset.getFields() == null || asset.getFields().size() == 0) {
            throw new IllegalArgumentException("Missing fields");
        }
        final List<String> fieldNames = asset.getFields().stream().map(CustomFlightAssetField::getName).collect(Collectors.toList());
        final List<String> pkFieldNames = getPrimaryKeyColumnNames();
        final Map<Boolean, List<String>> keyFieldNamesMap = fieldNames.stream().collect(Collectors.partitioningBy(pkFieldNames::contains));
        final List<String> keyFieldNames = keyFieldNamesMap.get(true);
        final List<String> nonKeyFieldNames = keyFieldNamesMap.get(false);
        final int fieldCount = fieldNames.size();
        final int[] paramIndices = new int[fieldCount];
        int nextParamIndex = 1;
        for (final String fieldName : nonKeyFieldNames) {
            final int i = fieldNames.indexOf(fieldName);
            paramIndices[i] = nextParamIndex;
            nextParamIndex++;
        }
        for (final String fieldName : keyFieldNames) {
            final int i = fieldNames.indexOf(fieldName);
            paramIndices[i] = nextParamIndex;
            nextParamIndex++;
        }
        return paramIndices;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomFlightAssetDescriptor putSetup() throws Exception
    {
        if (tableName != null) {
            // Check whether the table exists.
            final Schema schema = connector.getSchema(asset);
            if (schema.getFields().isEmpty()) {
                createTable();
            } else if ("replace".equals(tableAction)) {
                dropTable();
                createTable();
            } else if ("truncate".equals(tableAction)) {
                truncateTable();
            }
        }
        if (staticStatementText != null) {
            LOGGER.info(staticStatementText);
            try (Statement staticStmt = connector.getConnection().createStatement()) {
                staticStmt.execute(staticStatementText);
            }
        }
        if (statementText != null) {
            statement = connector.getConnection().prepareStatement(statementText);
        }
        return asset;
    }

    private void dropTable() throws Exception
    {
        final StringBuilder stmt = new StringBuilder(200);
        stmt.append("DROP TABLE ");
        stmt.append(generateQualifiedTableName());
        final String dropStatementText = stmt.toString();
        LOGGER.info(dropStatementText);
        try (Statement dropStmt = connector.getConnection().createStatement()) {
            dropStmt.execute(dropStatementText);
        }
    }

    private void truncateTable() throws Exception
    {
        final StringBuilder stmt = new StringBuilder(200);
        stmt.append("TRUNCATE TABLE ");
        stmt.append(generateQualifiedTableName());
        final String truncateStatementText = stmt.toString();
        LOGGER.info(truncateStatementText);
        try (Statement truncateStmt = connector.getConnection().createStatement()) {
            truncateStmt.execute(truncateStatementText);
        }
    }

    private void createTable() throws Exception
    {
        final String createStatement = interactionProperties.getProperty("create_statement");
        final String createStatementText;
        if (createStatement != null) {
            createStatementText = createStatement;
        } else {
            // Get the native data type names supported by the data source.
            final HashBasedTable<String, Integer, String> typeTable = HashBasedTable.create();
            try (ResultSet rs = connector.getConnection().getMetaData().getTypeInfo()) {
                while (rs.next()) {
                    final String nativeType = rs.getString("TYPE_NAME");
                    final int fieldType = rs.getInt("DATA_TYPE");
                    final String typeName = AssetFieldType.getTypeName(fieldType);
                    final String createParams = rs.getString("CREATE_PARAMS");
                    typeTable.put(typeName, 0, nativeType);
                    if (createParams != null) {
                        typeTable.put(typeName, 1, createParams);
                    }
                }
            }

            // Generate the CREATE TABLE statement;
            final StringBuilder stmt = new StringBuilder(200);
            stmt.append("CREATE TABLE ");
            stmt.append(generateQualifiedTableName());
            for (int i = 0; i < asset.getFields().size(); i++) {
                final CustomFlightAssetField field = asset.getFields().get(i);
                stmt.append((i == 0) ? " (" : ", ");
                stmt.append(generateSQLIdentifier(field.getName()));
                stmt.append(' ');
                final String nativeType = typeTable.get(field.getType(), 0);
                final String createParams = typeTable.get(field.getType(), 1);
                if (createParams == null) {
                    stmt.append(nativeType);
                } else if ("length".equals(createParams) || "precision".equals(createParams)) {
                    final int parenIndex = nativeType.indexOf('(');
                    if (parenIndex < 0) {
                        stmt.append(nativeType);
                        if (field.getLength() != null) {
                            stmt.append('(');
                            stmt.append(field.getLength());
                            stmt.append(')');
                        }
                    } else {
                        if (field.getLength() == null) {
                            throw new IllegalArgumentException("Missing length for field " + field.getName());
                        }
                        stmt.append(nativeType.substring(0, parenIndex + 1));
                        stmt.append(field.getLength());
                        stmt.append(nativeType.substring(parenIndex + 1));
                    }
                } else if ("precision,scale".equals(createParams)) {
                    if (field.getLength() == null) {
                        throw new IllegalArgumentException("Missing length for field " + field.getName());
                    }
                    stmt.append(nativeType);
                    stmt.append('(');
                    stmt.append(field.getLength());
                    if (field.getScale() != null && field.getScale() != 0) {
                        stmt.append(',');
                        stmt.append(field.getScale());
                    }
                    stmt.append(')');

                } else {
                    stmt.append(nativeType);
                }
                if (!field.isNullable()) {
                    stmt.append(" NOT NULL");
                }
            }
            stmt.append(')');
            createStatementText = stmt.toString();
        }
        LOGGER.info(createStatementText);
        try (Statement createStmt = connector.getConnection().createStatement()) {
            createStmt.execute(createStatementText);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putStream(FlightStream flightStream) throws Exception
    {
        // If the write_mode is static_statement, there is no write.
        if (statementText == null) {
            return;
        }
        LOGGER.info(statementText);
        try (VectorSchemaRoot root = flightStream.getRoot()) {
            while (flightStream.next()) {
                if (root.getRowCount() > 0) {
                    for (int rowIdx = 0; rowIdx < root.getRowCount(); rowIdx++) {
                        for (int colIdx = 0; colIdx < root.getFieldVectors().size(); colIdx++) {
                            @SuppressWarnings("PMD.CloseResource")
                            final FieldVector vector = root.getFieldVectors().get(colIdx);
                            final int parameterIndex = paramIndexes[colIdx];
                            if (vector.isNull(rowIdx)) {
                                statement.setNull(parameterIndex, AssetFieldType.getFieldType(asset.getFields().get(colIdx).getType()));
                            } else if (vector instanceof VarCharVector) {
                                final Text text = ((VarCharVector) vector).getObject(rowIdx);
                                statement.setString(parameterIndex, text.toString());
                            } else if (vector instanceof LargeVarCharVector) {
                                final Text text = ((LargeVarCharVector) vector).getObject(rowIdx);
                                statement.setString(parameterIndex, text.toString());
                            } else if (vector instanceof TimeNanoVector) {
                                final Long nanos = ((TimeNanoVector) vector).getObject(rowIdx);
                                statement.setTime(parameterIndex, new Time(TimeUnit.NANOSECONDS.toMillis(nanos)));
                            } else if (vector instanceof TimeMicroVector) {
                                final Long micros = ((TimeMicroVector) vector).getObject(rowIdx);
                                statement.setTime(parameterIndex, new Time(TimeUnit.MICROSECONDS.toMillis(micros)));
                            } else if (vector instanceof TimeMilliVector) {
                                final LocalDateTime time = ((TimeMilliVector) vector).getObject(rowIdx);
                                statement.setTime(parameterIndex, Time.valueOf(time.toLocalTime()));
                            } else if (vector instanceof TimeSecVector) {
                                final Integer seconds = ((TimeSecVector) vector).getObject(rowIdx);
                                statement.setTime(parameterIndex, new Time(TimeUnit.SECONDS.toMillis(seconds)));
                            } else if (vector instanceof DateDayVector) {
                                final Integer days = ((DateDayVector) vector).getObject(rowIdx);
                                statement.setDate(parameterIndex, new Date(TimeUnit.DAYS.toMillis(days)));
                            } else if (vector instanceof DateMilliVector) {
                                final LocalDateTime date = ((DateMilliVector) vector).getObject(rowIdx);
                                statement.setDate(parameterIndex, Date.valueOf(date.toLocalDate()));
                            } else if (vector instanceof TimeStampNanoTZVector) {
                                final Long nanos = ((TimeStampNanoTZVector) vector).getObject(rowIdx);
                                final TimeZone timeZone = getTimeZone((ArrowType.Timestamp) vector.getField().getType());
                                final Calendar calendar = Calendar.getInstance(timeZone);
                                statement.setTimestamp(parameterIndex, new Timestamp(TimeUnit.NANOSECONDS.toMillis(nanos)), calendar);
                            } else if (vector instanceof TimeStampMicroTZVector) {
                                final Long micros = ((TimeStampMicroTZVector) vector).getObject(rowIdx);
                                final TimeZone timeZone = getTimeZone((ArrowType.Timestamp) vector.getField().getType());
                                final Calendar calendar = Calendar.getInstance(timeZone);
                                statement.setTimestamp(parameterIndex, new Timestamp(TimeUnit.MICROSECONDS.toMillis(micros)), calendar);
                            } else if (vector instanceof TimeStampMilliTZVector) {
                                final Long millis = ((TimeStampMilliTZVector) vector).getObject(rowIdx);
                                final TimeZone timeZone = getTimeZone((ArrowType.Timestamp) vector.getField().getType());
                                final Calendar calendar = Calendar.getInstance(timeZone);
                                statement.setTimestamp(parameterIndex, new Timestamp(millis), calendar);
                            } else if (vector instanceof TimeStampSecTZVector) {
                                final Long seconds = ((TimeStampSecTZVector) vector).getObject(rowIdx);
                                final TimeZone timeZone = getTimeZone((ArrowType.Timestamp) vector.getField().getType());
                                final Calendar calendar = Calendar.getInstance(timeZone);
                                statement.setTimestamp(parameterIndex, new Timestamp(TimeUnit.SECONDS.toMillis(seconds)), calendar);
                            } else {
                                statement.setObject(parameterIndex, vector.getObject(rowIdx));
                            }
                        }
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                root.clear();
            }
        }
        connector.getConnection().commit();
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
    public CustomFlightAssetDescriptor putWrapup() throws Exception
    {
        return asset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
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
