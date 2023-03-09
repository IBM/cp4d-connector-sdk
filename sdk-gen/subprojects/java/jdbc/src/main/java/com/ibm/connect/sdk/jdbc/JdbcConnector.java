/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.arrow.adapter.jdbc.JdbcFieldInfo;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.slf4j.Logger;

import com.ibm.connect.sdk.api.Connector;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.connect.sdk.util.SSLUtils;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionConfiguration;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionActionResponse;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetFieldMetadata;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetsCriteria;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetDetails;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetExtendedMetadataProperty;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetInteractionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveredAssetType;

/**
 * An abstract JDBC connector
 */
public abstract class JdbcConnector implements Connector<JdbcSourceInteraction, JdbcTargetInteraction>
{
    private static final Logger LOGGER = getLogger(JdbcConnector.class);

    private static final Function<JdbcFieldInfo, ArrowType> TYPE_CONVERTER = jdbcField -> {
        switch (jdbcField.getJdbcType()) {
        case Types.CHAR:
        case Types.NCHAR:
        case Types.VARCHAR:
        case Types.NVARCHAR:
        case Types.LONGVARCHAR:
        case Types.LONGNVARCHAR:
        case Types.CLOB:
            return ArrowType.Utf8.INSTANCE;
        case Types.DECIMAL:
        case Types.NUMERIC:
            return new ArrowType.Decimal(jdbcField.getPrecision(), jdbcField.getScale(), 128);
        case Types.BIT:
        case Types.BOOLEAN:
            return ArrowType.Bool.INSTANCE;
        case Types.TINYINT:
            return new ArrowType.Int(8, true);
        case Types.SMALLINT:
            return new ArrowType.Int(16, true);
        case Types.INTEGER:
            return new ArrowType.Int(32, true);
        case Types.BIGINT:
            return new ArrowType.Int(64, true);
        case Types.REAL:
        case Types.FLOAT:
            return new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE);
        case Types.DOUBLE:
            return new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE);
        case Types.BINARY:
        case Types.VARBINARY:
        case Types.LONGVARBINARY:
        case Types.BLOB:
            return ArrowType.Binary.INSTANCE;
        case Types.DATE:
            return new ArrowType.Date(DateUnit.DAY);
        case Types.TIME:
            return new ArrowType.Time(TimeUnit.MILLISECOND, 32);
        case Types.TIME_WITH_TIMEZONE:
        case Types.TIMESTAMP:
        case Types.TIMESTAMP_WITH_TIMEZONE:
            return new ArrowType.Timestamp(TimeUnit.MILLISECOND, "UTC");
        default:
            return ArrowType.Utf8.INSTANCE;
        }
    };

    private static final String DRIVER_PROP_NAME_USERNAME = "user";
    private static final String DRIVER_PROP_NAME_PASS = "password";

    private static final String PROP_NAME_USERNAME = "username";
    private static final String PROP_NAME_PASS = "password";
    private static final String PROP_NAME_SSLCERT = "ssl_certificate";

    private static final Set<String> TABLE_TYPES_TABLE = Collections.singleton("TABLE");
    private static final Set<String> TABLE_TYPES_VIEW = Collections.singleton("VIEW");
    private static final Set<String> TABLE_TYPES_SYSTEM = Collections.singleton("SYSTEM TABLE");

    private final ModelMapper modelMapper = new ModelMapper();

    protected final Properties connectionProperties;

    private String truststoreFile;
    private Connection connection;
    private DatabaseMetaData dbMetadata;
    private String escapeString;
    private String identifierQuote;
    private boolean supportsSchemas;
    private String catalog;
    private boolean supportsScrollableCursors;

    /**
     * Creates a JDBC connector.
     *
     * @param properties
     *            connection properties
     */
    protected JdbcConnector(ConnectionProperties properties)
    {
        connectionProperties = ModelMapper.toProperties(properties);
    }

    /**
     * Returns the connection properties.
     *
     * @return the connection properties
     */
    public Properties getProperties()
    {
        return connectionProperties;
    }

    /**
     * Returns a JDBC to Arrow type converter.
     *
     * @return a JDBC to Arrow type converter
     */
    public Function<JdbcFieldInfo, ArrowType> getJdbcToArrowTypeConverter()
    {
        return TYPE_CONVERTER;
    }

    /**
     * Returns the JDBC driver.
     *
     * @return the JDBC driver
     * @throws Exception
     */
    protected abstract Driver getDriver() throws Exception;

    /**
     * Returns the JDBC connection URL based on the connection properties.
     *
     * @return the JDBC connection URL
     */
    protected abstract String getConnectionURL();

    /**
     * Returns the JDBC driver connection properties based on the connection
     * properties.
     *
     * @return the JDBC driver connection properties
     */
    protected Properties getDriverConnectionProperties()
    {
        final String username = getConnectionUsername();
        final String password = getConnectionPassword();

        final Properties properties = new Properties();
        if (username != null) {
            properties.setProperty(DRIVER_PROP_NAME_USERNAME, username);
        }
        if (password != null) {
            properties.setProperty(DRIVER_PROP_NAME_PASS, password);
        }
        return properties;
    }

    /**
     * Returns the user name to be used for the database connection.
     *
     * @return the user name to be used for the database connection.
     */
    protected String getConnectionUsername()
    {
        return connectionProperties.getProperty(PROP_NAME_USERNAME);
    }

    /**
     * Returns the password to be used for the database connection.
     *
     * @return the password to be used for the database connection.
     */
    protected String getConnectionPassword()
    {
        return connectionProperties.getProperty(PROP_NAME_PASS);
    }

    /**
     * Returns the password to be used for the trust store.
     *
     * @return the password to be used for the trust store.
     */
    protected String getTruststorePassword()
    {
        return getConnectionPassword();
    }

    private String createTruststoreFile(String truststorePassword) throws Exception
    {
        final String sslCert = connectionProperties.getProperty(PROP_NAME_SSLCERT);
        return (sslCert != null) ? SSLUtils.getTrustStoreFile(sslCert, truststorePassword) : null;
    }

    /**
     * Returns the path of the trust store file.
     *
     * @return the path of the trust store file
     */
    public String getTruststoreFile()
    {
        return truststoreFile;
    }

    private void cleanupTruststore()
    {
        if (truststoreFile != null) {
            try {
                Files.deleteIfExists(Paths.get(truststoreFile));
            }
            catch (final Exception e) {
                LOGGER.warn(e.getMessage(), e);
                new File(truststoreFile).deleteOnExit();
            }
            finally {
                truststoreFile = null;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect() throws Exception
    {
        // If the connection is not null, then we're reusing a pooled connection.
        if (connection == null) {
            final String url = getConnectionURL();
            try {
                truststoreFile = createTruststoreFile(getTruststorePassword());
                final Properties credentials = getDriverConnectionProperties();
                LOGGER.info("Connecting to " + url);
                final Driver driver = getDriver();
                connection = (driver != null) ? driver.connect(url, credentials) : DriverManager.getConnection(url, credentials);
                dbMetadata = connection.getMetaData();
                escapeString = dbMetadata.getSearchStringEscape();
                final String identifierQuoteString = dbMetadata.getIdentifierQuoteString();
                identifierQuote = identifierQuoteString != null ? identifierQuoteString : "";
                supportsSchemas = dbMetadata.supportsSchemasInTableDefinitions();
                catalog = connection.getCatalog();
                supportsScrollableCursors = dbMetadata.supportsResultSetType(ResultSet.TYPE_SCROLL_INSENSITIVE);
            }
            finally {
                cleanupTruststore();
            }
        }
    }

    /**
     * Returns the JDBC connection.
     *
     * @return the JDBC connection
     */
    public Connection getConnection()
    {
        return connection;
    }

    /**
     * Returns the identifier quote character.
     *
     * @return the identifier quote character
     */
    public String getIdentifierQuote()
    {
        return identifierQuote;
    }

    /**
     * Returns true if the data source supports schema qualifiers on table names.
     *
     * @return true if the data source supports schema qualifiers on table names
     */
    public boolean supportsSchemas()
    {
        return supportsSchemas;
    }

    /**
     * Returns the connection's current catalog name.
     *
     * @return the connection's current catalog name
     */
    public String getCatalog()
    {
        return catalog;
    }

    /**
     * Returns true if the data source supports scrollable cursors.
     *
     * @return true if the data source supports scrollable cursors
     */
    public boolean supportsScrollableCursors()
    {
        return supportsScrollableCursors;
    }

    /**
     * Checks whether the specified schema name refers to a system schema in the
     * database.
     *
     * @param schemaName
     *            name of the schema to check
     * @return true if the schema is recognized as system schema, false otherwise
     */
    protected boolean isSystemSchema(String schemaName)
    {
        // The default implementation is unaware of system and non-system schema
        // differentiation and therefore returns a value of false. Data source
        // specific subclasses of this class can override this logic based
        // on their knowledge of the built-in system schemas in the data source.
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CustomFlightAssetDescriptor> discoverAssets(CustomFlightAssetsCriteria criteria) throws Exception
    {
        final Properties filters = ModelMapper.toProperties(criteria.getFilters());
        final String schemaNamePattern = filters.getProperty("schema_name_pattern");
        final String path = normalizePath(criteria.getPath());
        final String[] pathElements = splitPath(path);
        final List<CustomFlightAssetDescriptor> assets;
        if (pathElements.length == 0) {
            assets = schemaNamePattern == null ? listSchemas(criteria) : listTables(criteria, null);
        } else if (pathElements.length == 1) {
            assets = listTables(criteria, pathElements[0]);
        } else if (pathElements.length == 2) {
            final String schemaName = pathElements[0];
            final String tableName = pathElements[1];
            final String includePrimaryKey = filters.getProperty("primary_key", "false");
            if (Boolean.valueOf(includePrimaryKey)) {
                assets = listPrimaryKeys(schemaName, tableName);
            } else {
                final DiscoveredAssetInteractionProperties interactionProperties = new DiscoveredAssetInteractionProperties();
                interactionProperties.put("schema_name", schemaName);
                interactionProperties.put("table_name", tableName);
                final CustomFlightAssetDescriptor asset = new CustomFlightAssetDescriptor().name(tableName).path(path)
                        .assetType(tableAssetType()).interactionProperties(interactionProperties);
                if (Boolean.TRUE.equals(criteria.isExtendedMetadata())) {
                    asset.setExtendedMetadata(listExtendedMetadata(schemaName, tableName));
                }
                assets = new ArrayList<>();
                assets.add(asset);
            }
        } else {
            throw new IllegalArgumentException("Invalid path");
        }
        return assets;
    }

    private String normalizePath(String path)
    {
        return path.startsWith("/") ? path : "/" + path;
    }

    private String[] splitPath(String path)
    {
        if (path.startsWith("/") && !"/".equals(path)) {
            path = path.substring(1);
        }
        return path.split("/");
    }

    /**
     * List the schemas.
     *
     * @param criteria
     *            asset criteria
     * @return a list of schema asset descriptors
     * @throws SQLException
     */
    private List<CustomFlightAssetDescriptor> listSchemas(CustomFlightAssetsCriteria criteria) throws SQLException
    {
        final List<CustomFlightAssetDescriptor> descriptors = new ArrayList<>();
        final Properties filters = ModelMapper.toProperties(criteria.getFilters());
        final String schemaPattern = filters.getProperty("name_pattern");
        final Boolean includeSystem = Boolean.valueOf(filters.getProperty("include_system", "true"));
        final int offset = criteria.getOffset() == null || criteria.getOffset() < 0 ? 0 : criteria.getOffset();
        final int limit = criteria.getLimit() == null || criteria.getLimit() < 0 ? Integer.MAX_VALUE : criteria.getLimit();
        try (ResultSet result = schemaPattern != null
                ? dbMetadata.getSchemas(supportsSchemas ? catalog : schemaPattern, supportsSchemas ? schemaPattern : null)
                : supportsSchemas ? dbMetadata.getSchemas() : dbMetadata.getCatalogs()) {
            int i = 0;
            while (result.next() && descriptors.size() < limit) {
                final String schemaName = supportsSchemas ? result.getString("TABLE_SCHEM")
                        : schemaPattern != null ? result.getString("TABLE_CATALOG") : result.getString("TABLE_CAT");
                if (includeSystem || !isSystemSchema(schemaName)) {
                    if (i < offset) {
                        i++;
                        continue;
                    }
                    final String path = "/" + schemaName;
                    descriptors.add(new CustomFlightAssetDescriptor().name(schemaName).path(path).assetType(schemaAssetType()));
                }
            }
        }
        return descriptors;
    }

    private DiscoveredAssetType schemaAssetType()
    {
        return new DiscoveredAssetType().type("schema").dataset(false).datasetContainer(true);
    }

    /**
     * List the tables.
     *
     * @param criteria
     *            asset criteria
     * @param schemaName
     *            the name of the schema or null if the criteria supplies a schema
     *            name pattern
     * @return a list of table asset descriptors
     * @throws SQLException
     */
    private List<CustomFlightAssetDescriptor> listTables(CustomFlightAssetsCriteria criteria, String schemaName) throws SQLException
    {
        final List<CustomFlightAssetDescriptor> descriptors = new ArrayList<>();
        final Properties filters = ModelMapper.toProperties(criteria.getFilters());
        final String tableNamePattern = filters.getProperty("name_pattern");
        final String schemaPattern = filters.getProperty("schema_name_pattern", escapeSQLWildcards(schemaName));
        final Boolean includeSystem = Boolean.valueOf(filters.getProperty("include_system", "true"));
        final Boolean includeTable = Boolean.valueOf(filters.getProperty("include_table", "true"));
        final Boolean includeView = Boolean.valueOf(filters.getProperty("include_view", "true"));
        final List<String> tableTypes = new ArrayList<>();
        if (includeTable) {
            tableTypes.addAll(TABLE_TYPES_TABLE);
        }
        if (includeView) {
            tableTypes.addAll(TABLE_TYPES_VIEW);
        }
        if (includeSystem) {
            tableTypes.addAll(TABLE_TYPES_SYSTEM);
        }
        final int offset = criteria.getOffset() == null || criteria.getOffset() < 0 ? 0 : criteria.getOffset();
        final int limit = criteria.getLimit() == null || criteria.getLimit() < 0 ? Integer.MAX_VALUE : criteria.getLimit();
        try (ResultSet result = dbMetadata.getTables(supportsSchemas ? catalog : schemaPattern, supportsSchemas ? schemaPattern : null,
                tableNamePattern, tableTypes.toArray(new String[0]))) {
            int i = 0;
            while (result.next() && descriptors.size() < limit) {
                final String tableSchema = supportsSchemas ? result.getString("TABLE_SCHEM") : result.getString("TABLE_CAT");
                // Not all JDBC drivers support escaping SQL wildcards including Derby, so if
                // we're searching by name and not pattern, double check that the schema
                // returned matches the name that we were looking for.
                if (schemaName != null && !schemaName.equals(tableSchema)) {
                    // The schema name contains a wildcard that matched the wrong schema.
                    continue;
                }
                if (i < offset) {
                    i++;
                    continue;
                }
                final String tableName = result.getString("TABLE_NAME");
                final String remarks = result.getString("REMARKS");
                final String path = "/" + tableSchema + "/" + tableName;
                descriptors
                        .add(new CustomFlightAssetDescriptor().name(tableName).path(path).assetType(tableAssetType()).description(remarks));
            }
        }
        return descriptors;
    }

    private String escapeSQLWildcards(String name)
    {
        if (name == null) {
            return null;
        }
        final String escapePattern = "\\".equals(escapeString) ? "\\\\" : escapeString;
        return name.replaceAll("_", escapePattern + "_").replaceAll("%", escapePattern + "%");
    }

    private DiscoveredAssetType tableAssetType()
    {
        return new DiscoveredAssetType().type("table").dataset(true).datasetContainer(false);
    }

    private List<CustomFlightAssetDescriptor> listPrimaryKeys(String schemaName, String tableName) throws SQLException
    {
        final List<CustomFlightAssetDescriptor> descriptors = new ArrayList<>();
        String pkName = null;
        final Map<Integer, String> keyColumns = new TreeMap<>();
        try (ResultSet result
                = dbMetadata.getPrimaryKeys(supportsSchemas ? catalog : schemaName, supportsSchemas ? schemaName : null, tableName)) {
            while (result.next()) {
                final String columnName = result.getString("COLUMN_NAME");
                final int keySeq = result.getShort("KEY_SEQ");
                pkName = result.getString("PK_NAME");
                keyColumns.put(keySeq, columnName);
            }
        }
        if (pkName == null && keyColumns.isEmpty()) {
            // There is no primary key.
            return descriptors;
        }
        if (pkName == null) {
            // There is a primary key but it is unnamed.
            pkName = "";
        }
        final StringBuilder pathBuilder = new StringBuilder(20);
        pathBuilder.append('/');
        pathBuilder.append(schemaName);
        pathBuilder.append('/');
        pathBuilder.append(tableName);
        pathBuilder.append('/');
        pathBuilder.append(pkName);
        final String path = pathBuilder.toString();
        final DiscoveredAssetDetails details = new DiscoveredAssetDetails();
        details.put("column_names", keyColumns.values());
        descriptors.add(new CustomFlightAssetDescriptor().name(pkName).path(path).assetType(primaryKeyAssetType()).details(details));
        return descriptors;
    }

    private DiscoveredAssetType primaryKeyAssetType()
    {
        return new DiscoveredAssetType().type("primary_key").dataset(false).datasetContainer(false);
    }

    private List<DiscoveredAssetExtendedMetadataProperty> listExtendedMetadata(String schemaName, String tableName) throws SQLException
    {
        final List<DiscoveredAssetExtendedMetadataProperty> metadata = new ArrayList<>();
        final String schemaPattern = escapeSQLWildcards(schemaName);
        final String tableNamePattern = escapeSQLWildcards(tableName);
        try (ResultSet result = dbMetadata.getTables(supportsSchemas ? catalog : schemaPattern, supportsSchemas ? schemaPattern : null,
                tableNamePattern, null)) {
            while (result.next()) {
                final String tableSchema = supportsSchemas ? result.getString("TABLE_SCHEM") : result.getString("TABLE_CAT");
                if (schemaName != null && !schemaName.equals(tableSchema)) {
                    // The schema name contains a wildcard that matched the wrong schema.
                    continue;
                }
                final String resultTableName = result.getString("TABLE_NAME");
                if (tableName != null && !tableName.equals(resultTableName)) {
                    // The table name contains a wildcard that matched the wrong table.
                    continue;
                }
                metadata.add(new DiscoveredAssetExtendedMetadataProperty().name("table_type").value(result.getString("TABLE_TYPE")));
            }
        }
        try (ResultSet result = dbMetadata.getColumns(supportsSchemas ? catalog : schemaPattern, supportsSchemas ? schemaPattern : null,
                tableNamePattern, null)) {
            int colCount;
            for (colCount = 0; result.next(); colCount++) {
                final String tableSchema = supportsSchemas ? result.getString("TABLE_SCHEM") : result.getString("TABLE_CAT");
                if (schemaName != null && !schemaName.equals(tableSchema)) {
                    // The schema name contains a wildcard that matched the wrong schema.
                    continue;
                }
                final String resultTableName = result.getString("TABLE_NAME");
                if (tableName != null && !tableName.equals(resultTableName)) {
                    // The table name contains a wildcard that matched the wrong table.
                    continue;
                }
            }
            metadata.add(new DiscoveredAssetExtendedMetadataProperty().name("num_columns").value(colCount));
        }
        final List<String> parentTables = new ArrayList<>();
        try (ResultSet result
                = dbMetadata.getImportedKeys(supportsSchemas ? catalog : schemaName, supportsSchemas ? schemaName : null, tableName)) {
            while (result.next()) {
                parentTables.add((supportsSchemas ? result.getString("PKTABLE_SCHEM") : result.getString("PKTABLE_CAT")) + "."
                        + result.getString("PKTABLE_NAME"));
            }
        }
        metadata.add(new DiscoveredAssetExtendedMetadataProperty().name("parent_tables").value(parentTables));
        metadata.add(new DiscoveredAssetExtendedMetadataProperty().name("num_parents").value(parentTables.size()));
        final List<String> childTables = new ArrayList<>();
        try (ResultSet result
                = dbMetadata.getExportedKeys(supportsSchemas ? catalog : schemaName, supportsSchemas ? schemaName : null, tableName)) {
            while (result.next()) {
                childTables.add((supportsSchemas ? result.getString("FKTABLE_SCHEM") : result.getString("FKTABLE_CAT")) + "."
                        + result.getString("FKTABLE_NAME"));
            }
        }
        metadata.add(new DiscoveredAssetExtendedMetadataProperty().name("child_tables").value(childTables));
        metadata.add(new DiscoveredAssetExtendedMetadataProperty().name("num_children").value(childTables.size()));
        return metadata;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema(CustomFlightAssetDescriptor asset) throws Exception
    {
        if (asset.getInteractionProperties() == null) {
            return new Schema(Collections.emptyList());
        }
        final Properties interactionProperties = ModelMapper.toProperties(asset.getInteractionProperties());
        final String schemaName = interactionProperties.getProperty("schema_name");
        final String tableName = interactionProperties.getProperty("table_name");
        final String schemaPattern = escapeSQLWildcards(schemaName);
        final String tableNamePattern = escapeSQLWildcards(tableName);
        final List<Field> fields = new ArrayList<>();
        try (ResultSet result = dbMetadata.getColumns(supportsSchemas ? catalog : schemaPattern, supportsSchemas ? schemaPattern : null,
                tableNamePattern, null)) {
            while (result.next()) {
                final String tableSchema = supportsSchemas ? result.getString("TABLE_SCHEM") : result.getString("TABLE_CAT");
                // Not all JDBC drivers support escaping SQL wildcards including Derby, so if
                // we're searching by name and not pattern, double check that the schema
                // returned matches the name that we were looking for.
                if (schemaName != null && !schemaName.equals(tableSchema)) {
                    // The schema name contains a wildcard that matched the wrong schema.
                    continue;
                }
                final String resultTableName = result.getString("TABLE_NAME");
                if (tableName != null && !tableName.equals(resultTableName)) {
                    // The table name contains a wildcard that matched the wrong table.
                    continue;
                }
                final String name = result.getString("COLUMN_NAME");
                final int jdbcType = result.getInt("DATA_TYPE");
                final String nativeType = result.getString("TYPE_NAME");
                final String type = AssetFieldType.getTypeName(jdbcType);
                final int length = result.getInt("COLUMN_SIZE");
                final int scale = result.getInt("DECIMAL_DIGITS");
                final boolean nullable = result.getInt("NULLABLE") != ResultSetMetaData.columnNoNulls;
                final boolean signed = AssetFieldType.isNumeric(jdbcType) && !nativeType.contains("UNSIGNED");
                final String remarks = result.getString("REMARKS");
                final String description = remarks != null && !remarks.isEmpty() ? remarks : null;
                final CustomFlightAssetField assetField = new CustomFlightAssetField().name(name).type(type).length(length).scale(scale)
                        .nullable(nullable).signed(signed).description(description);
                fields.add(getField(assetField, jdbcType));
            }
        }
        return new Schema(fields);
    }

    /**
     * Returns an Arrow field for the given asset field and JDBC type.
     *
     * @param assetField
     *            asset field
     * @param jdbcType
     *            JDBC type
     * @return an Arrow field for the given asset field and JDBC type
     * @throws Exception
     */
    public Field getField(CustomFlightAssetField assetField, int jdbcType) throws Exception
    {
        final CustomFlightAssetFieldMetadata fieldMetadata
                = modelMapper.fromBytes(modelMapper.toBytes(assetField), CustomFlightAssetFieldMetadata.class);
        final String fieldName = fieldMetadata.remove("name");
        // Remove null values from the metadata.
        final CustomFlightAssetFieldMetadata metadata = new CustomFlightAssetFieldMetadata();
        for (final Entry<String, String> entry : fieldMetadata.entrySet()) {
            if (entry.getValue() != null) {
                metadata.put(entry.getKey(), entry.getValue());
            }
        }
        final ArrowType jdbcArrowType = getArrowType(assetField, jdbcType);
        final ArrowType arrowType = jdbcArrowType != null ? jdbcArrowType : ArrowType.Utf8.INSTANCE;
        final FieldType fieldType = new FieldType(assetField.isNullable(), arrowType, null, metadata);
        return new Field(fieldName, fieldType, null);
    }

    private ArrowType getArrowType(CustomFlightAssetField assetField, int jdbcType)
    {
        final JdbcFieldInfo fieldInfo = jdbcType == Types.DECIMAL || jdbcType == Types.NUMERIC
                ? new JdbcFieldInfo(jdbcType, assetField.getLength(), assetField.getScale()) : new JdbcFieldInfo(jdbcType);
        return TYPE_CONVERTER.apply(fieldInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectionActionResponse performAction(String action, ConnectionActionConfiguration properties) throws Exception
    {
        if (!"get_record_count".equals(action)) {
            throw new UnsupportedOperationException("doAction " + action + " is not supported");
        }
        final Properties inputProperties = ModelMapper.toProperties(properties);
        final String schemaName = inputProperties.getProperty("schema_name");
        final String tableName = inputProperties.getProperty("table_name");
        final StringBuilder query = new StringBuilder(50);
        query.append("SELECT COUNT(*) FROM ");
        if (schemaName != null) {
            query.append(identifierQuote);
            query.append(schemaName);
            query.append(identifierQuote);
            query.append('.');
        }
        query.append(identifierQuote);
        query.append(tableName);
        query.append(identifierQuote);
        final String statementText = query.toString();
        LOGGER.info(statementText);
        long rowCount = 0;
        try (Statement queryStmt = connection.createStatement()) {
            try (ResultSet resultSet = queryStmt.executeQuery(statementText)) {
                if (resultSet.next()) {
                    rowCount = resultSet.getLong(1);
                }
            }
        }
        final ConnectionActionResponse response = new ConnectionActionResponse();
        response.put("record_count", rowCount);
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception
    {
        try {
            if (connection != null) {
                connection.close();
            }
        }
        finally {
            connection = null;
        }
    }

}
