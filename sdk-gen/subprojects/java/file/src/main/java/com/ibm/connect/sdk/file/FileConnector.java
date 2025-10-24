/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.spark.SparkConf;
import org.apache.spark.sql.DataFrameReader;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.BinaryType;
import org.apache.spark.sql.types.BooleanType;
import org.apache.spark.sql.types.ByteType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DateType;
import org.apache.spark.sql.types.DecimalType;
import org.apache.spark.sql.types.DoubleType;
import org.apache.spark.sql.types.FloatType;
import org.apache.spark.sql.types.IntegerType;
import org.apache.spark.sql.types.LongType;
import org.apache.spark.sql.types.ShortType;
import org.apache.spark.sql.types.StringType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.types.TimestampType;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableSet;
import com.ibm.connect.sdk.api.RowBasedConnector;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetDescriptor;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

import scala.collection.Iterator;

/**
 * An abstract file connector.
 */
public abstract class FileConnector extends RowBasedConnector<FileSourceInteraction, FileTargetInteraction>
{
    private static final Logger LOGGER = getLogger(FileConnector.class);

    private static final Map<String, String> PROPERTY_TO_SPARK_OPTION_MAP = new HashMap<>();
    static {
        PROPERTY_TO_SPARK_OPTION_MAP.put("comment_character_value", "comment");
        PROPERTY_TO_SPARK_OPTION_MAP.put("date_format", "dateFormat");
        PROPERTY_TO_SPARK_OPTION_MAP.put("encoding", "encoding");
        PROPERTY_TO_SPARK_OPTION_MAP.put("escape_character_value", "escape");
        PROPERTY_TO_SPARK_OPTION_MAP.put("field_delimiter_value", "delimiter");
        PROPERTY_TO_SPARK_OPTION_MAP.put("first_line_header", "header");
        PROPERTY_TO_SPARK_OPTION_MAP.put("infer_schema", "inferSchema");
        PROPERTY_TO_SPARK_OPTION_MAP.put("nan_value", "nanValue");
        PROPERTY_TO_SPARK_OPTION_MAP.put("negative_infinity_value", "negativeInf");
        PROPERTY_TO_SPARK_OPTION_MAP.put("null_value", "nullValue");
        PROPERTY_TO_SPARK_OPTION_MAP.put("positive_infinity_value", "positiveInf");
        PROPERTY_TO_SPARK_OPTION_MAP.put("quote_character_value", "quote");
        PROPERTY_TO_SPARK_OPTION_MAP.put("row_delimiter_value", "lineSep");
        PROPERTY_TO_SPARK_OPTION_MAP.put("row_tag", "rowTag");
        PROPERTY_TO_SPARK_OPTION_MAP.put("time_zone_format", "timestampFormat");
        PROPERTY_TO_SPARK_OPTION_MAP.put("timestamp_format", "timestampNTZFormat");
    }

    private static final Set<String> SIGNED_TYPES
            = ImmutableSet.of("tinyint", "smallint", "integer", "bigint", "decimal", "numeric", "real", "float", "double");

    private SparkSession spark;

    /**
     * Creates a file connector.
     *
     * @param properties
     *            connection properties
     */
    protected FileConnector(ConnectionProperties properties)
    {
        super(properties);
        // Tell spark to return dates as java.sql.Date and timestamps as
        // java.sql.Timestamp.
        final SparkConf conf = new SparkConf().set("spark.sql.datetime.java8API.enabled", "false");
        spark = SparkSession.builder().appName(FileConnector.class.getName()).config(conf).master("local[*]").getOrCreate();
    }

    protected void addAssetFields(CustomFlightAssetDescriptor asset, String filename)
    {
        final Dataset<Row> df = getDataframe(asset, filename);
        final StructType schema = df.schema();
        final Iterator<StructField> fieldIterator = schema.iterator();
        while (fieldIterator.hasNext()) {
            final StructField structField = fieldIterator.next();
            final CustomFlightAssetField assetField = new CustomFlightAssetField().name(structField.name());
            final DataType dataType = structField.dataType();
            final String type;
            if (dataType instanceof BooleanType) {
                type = "boolean";
            } else if (dataType instanceof ByteType) {
                type = "tinyint";
            } else if (dataType instanceof ShortType) {
                type = "smallint";
            } else if (dataType instanceof IntegerType) {
                type = "integer";
            } else if (dataType instanceof LongType) {
                type = "bigint";
            } else if (dataType instanceof FloatType) {
                type = "real";
            } else if (dataType instanceof DoubleType) {
                type = "double";
            } else if (dataType instanceof DecimalType) {
                type = "decimal";
            } else if (dataType instanceof TimestampType) {
                type = "timestamp";
            } else if (dataType instanceof DateType) {
                type = "date";
            } else if (dataType instanceof BinaryType) {
                type = "varbinary";
            } else if (dataType instanceof StringType) {
                type = "varchar";
            } else {
                type = "other";
            }
            assetField.setType(type);
            assetField.setNullable(structField.nullable());
            switch (type) {
            case "varchar":
            case "varbinary":
                assetField.setLength(dataType.defaultSize());
                break;
            case "decimal":
                assetField.setLength(((DecimalType) dataType).precision());
                assetField.setScale(((DecimalType) dataType).scale());
                break;
            default:
                break;
            }
            assetField.setSigned(SIGNED_TYPES.contains(type));
            asset.addFieldsItem(assetField);
        }
    }

    protected Dataset<Row> getDataframe(CustomFlightAssetDescriptor asset, String filename)
    {
        final Properties interactionProperties = ModelMapper.toProperties(asset.getInteractionProperties());
        final String fileFormat = interactionProperties.getProperty("file_format");
        final String sparkFileFormat = FileUtils.FILE_FORMAT_DELIMITED.equals(fileFormat) ? "csv" : fileFormat;
        final DataFrameReader reader = spark.read().format(sparkFileFormat);
        // Spark requires the row_tag property for XML, so apply a default if not
        // specified.
        if (FileUtils.FILE_FORMAT_XML.equals(fileFormat) && interactionProperties.getProperty("row_tag") == null) {
            LOGGER.info("Applying spark option rowTag=ROW");
            reader.option("rowTag", "ROW");
        }
        for (final String propName : interactionProperties.stringPropertyNames()) {
            final String sparkOption = PROPERTY_TO_SPARK_OPTION_MAP.get(propName);
            if (sparkOption != null) {
                final String value = interactionProperties.getProperty(propName);
                LOGGER.info("Applying spark option " + sparkOption + '=' + value);
                reader.option(sparkOption, value);
            }
        }
        return reader.load(filename);
    }

    @Override
    public void close() throws Exception
    {
        try {
            if (spark != null) {
                spark.close();
            }
        }
        finally {
            spark = null;
        }
    }
}
