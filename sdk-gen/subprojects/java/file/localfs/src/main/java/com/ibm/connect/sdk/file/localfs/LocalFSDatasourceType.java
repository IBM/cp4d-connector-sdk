/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.localfs;

import java.util.Collections;

import com.ibm.connect.sdk.file.FileLabels;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty.TypeEnum;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypeProperties;
import com.ibm.wdp.connect.common.sdk.api.models.DatasourceTypeDiscovery;
import com.ibm.wdp.connect.common.sdk.api.models.DatasourceTypePropertyValues;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveryAssetType;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveryPathProperty;
import com.ibm.wdp.connect.common.sdk.api.models.DiscoveryPathSegment;

/**
 * The definition of a custom local file system data source type.
 */
public class LocalFSDatasourceType extends CustomFlightDatasourceType
{
    /**
     * An instance of the custom local file system data source type.
     */
    public static final LocalFSDatasourceType INSTANCE = new LocalFSDatasourceType();

    /**
     * The unique identifier name of the data source type.
     */
    public static final String DATASOURCE_TYPE_NAME = "custom_localfs";

    /**
     * Defines a custom data source type for the local file system.
     */
    public LocalFSDatasourceType()
    {
        super();

        // Set the data source type attributes.
        setName(DATASOURCE_TYPE_NAME);
        setLabel(LocalFSLabels.DATASOURCE_TYPE_LABEL.format());
        setDescription(LocalFSLabels.DATASOURCE_TYPE_DESCRIPTION.format());
        setAllowedAsSource(true);
        setAllowedAsTarget(true);
        setStatus(CustomFlightDatasourceType.StatusEnum.ACTIVE);
        setTags(Collections.emptyList());
        final CustomFlightDatasourceTypeProperties properties = new CustomFlightDatasourceTypeProperties();
        setProperties(properties);

        // Define the connection properties.
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("root_path").label(LocalFSLabels.CONNECTION_ROOT_PATH_LABEL.format())
                        .description(LocalFSLabels.CONNECTION_ROOT_PATH_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));

        // Define the source interaction properties.
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("file_name").label(FileLabels.SOURCE_FILE_NAME_LABEL.format())
                .description(FileLabels.SOURCE_FILE_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(true));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("file_format").label(FileLabels.SOURCE_FILE_FORMAT_LABEL.format())
                .description(FileLabels.SOURCE_FILE_FORMAT_DESCRIPTION.format()).type(TypeEnum.ENUM).required(false)
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("avro").label(FileLabels.SOURCE_FILE_FORMAT_VALUE_AVRO_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("csv").label(FileLabels.SOURCE_FILE_FORMAT_VALUE_CSV_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("delimited")
                        .label(FileLabels.SOURCE_FILE_FORMAT_VALUE_DELIMITED_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("json").label(FileLabels.SOURCE_FILE_FORMAT_VALUE_JSON_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("orc").label(FileLabels.SOURCE_FILE_FORMAT_VALUE_ORC_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("parquet")
                        .label(FileLabels.SOURCE_FILE_FORMAT_VALUE_PARQUET_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("xml").label(FileLabels.SOURCE_FILE_FORMAT_VALUE_XML_LABEL.format())));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("row_limit").label(FileLabels.SOURCE_ROW_LIMIT_LABEL.format())
                .description(FileLabels.SOURCE_ROW_LIMIT_DESCRIPTION.format()).type(TypeEnum.INTEGER).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("byte_limit").label(FileLabels.SOURCE_BYTE_LIMIT_LABEL.format())
                .description(FileLabels.SOURCE_BYTE_LIMIT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        // CSV and delimited options
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("comment_character_value")
                .label(FileLabels.SOURCE_COMMENT_CHARACTER_VALUE_LABEL.format())
                .description(FileLabels.SOURCE_COMMENT_CHARACTER_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("date_format").label(FileLabels.SOURCE_DATE_FORMAT_LABEL.format())
                .description(FileLabels.SOURCE_DATE_FORMAT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("encoding").label(FileLabels.SOURCE_ENCODING_LABEL.format())
                .description(FileLabels.SOURCE_ENCODING_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("escape_character_value")
                .label(FileLabels.SOURCE_ESCAPE_CHARACTER_VALUE_LABEL.format())
                .description(FileLabels.SOURCE_ESCAPE_CHARACTER_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("field_delimiter_value")
                .label(FileLabels.SOURCE_FIELD_DELIMITER_VALUE_LABEL.format())
                .description(FileLabels.SOURCE_FIELD_DELIMITER_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("first_line_header").label(FileLabels.SOURCE_FIRST_LINE_HEADER_LABEL.format())
                        .description(FileLabels.SOURCE_FIRST_LINE_HEADER_DESCRIPTION.format()).type(TypeEnum.BOOLEAN).required(false));
        properties
                .addSourceItem(new CustomDatasourceTypeProperty().name("infer_schema").label(FileLabels.SOURCE_INFER_SCHEMA_LABEL.format())
                        .description(FileLabels.SOURCE_INFER_SCHEMA_DESCRIPTION.format()).type(TypeEnum.BOOLEAN).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("nan_value").label(FileLabels.SOURCE_NAN_VALUE_LABEL.format())
                .description(FileLabels.SOURCE_NAN_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("negative_infinity_value")
                .label(FileLabels.SOURCE_NEGATIVE_INFINITY_VALUE_LABEL.format())
                .description(FileLabels.SOURCE_NEGATIVE_INFINITY_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("null_value").label(FileLabels.SOURCE_NULL_VALUE_LABEL.format())
                .description(FileLabels.SOURCE_NULL_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("positive_infinity_value")
                .label(FileLabels.SOURCE_POSITIVE_INFINITY_VALUE_LABEL.format())
                .description(FileLabels.SOURCE_POSITIVE_INFINITY_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("quote_character_value")
                .label(FileLabels.SOURCE_QUOTE_CHARACTER_VALUE_LABEL.format())
                .description(FileLabels.SOURCE_QUOTE_CHARACTER_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("row_delimiter_value").label(FileLabels.SOURCE_ROW_DELIMITER_VALUE_LABEL.format())
                        .description(FileLabels.SOURCE_ROW_DELIMITER_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("time_zone_format").label(FileLabels.SOURCE_TIME_ZONE_FORMAT_LABEL.format())
                        .description(FileLabels.SOURCE_TIME_ZONE_FORMAT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("timestamp_format").label(FileLabels.SOURCE_TIMESTAMP_FORMAT_LABEL.format())
                        .description(FileLabels.SOURCE_TIMESTAMP_FORMAT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        // XML options
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("row_tag").label(FileLabels.SOURCE_ROW_TAG_LABEL.format())
                .description(FileLabels.SOURCE_ROW_TAG_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));

        // Define the target interaction properties.
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("file_name").label(FileLabels.TARGET_FILE_NAME_LABEL.format())
                .description(FileLabels.TARGET_FILE_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(true));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("file_format").label(FileLabels.TARGET_FILE_FORMAT_LABEL.format())
                .description(FileLabels.TARGET_FILE_FORMAT_DESCRIPTION.format()).type(TypeEnum.ENUM).required(false)
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("avro").label(FileLabels.TARGET_FILE_FORMAT_VALUE_AVRO_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("csv").label(FileLabels.TARGET_FILE_FORMAT_VALUE_CSV_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("delimited")
                        .label(FileLabels.TARGET_FILE_FORMAT_VALUE_DELIMITED_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("json").label(FileLabels.TARGET_FILE_FORMAT_VALUE_JSON_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("orc").label(FileLabels.TARGET_FILE_FORMAT_VALUE_ORC_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("parquet")
                        .label(FileLabels.TARGET_FILE_FORMAT_VALUE_PARQUET_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("xml").label(FileLabels.TARGET_FILE_FORMAT_VALUE_XML_LABEL.format())));
        // CSV and delimited options
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("date_format").label(FileLabels.TARGET_DATE_FORMAT_LABEL.format())
                .description(FileLabels.TARGET_DATE_FORMAT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("encoding").label(FileLabels.TARGET_ENCODING_LABEL.format())
                .description(FileLabels.TARGET_ENCODING_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("escape_character_value")
                .label(FileLabels.TARGET_ESCAPE_CHARACTER_VALUE_LABEL.format())
                .description(FileLabels.TARGET_ESCAPE_CHARACTER_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("field_delimiter_value")
                .label(FileLabels.TARGET_FIELD_DELIMITER_VALUE_LABEL.format())
                .description(FileLabels.TARGET_FIELD_DELIMITER_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addTargetItem(
                new CustomDatasourceTypeProperty().name("first_line_header").label(FileLabels.TARGET_FIRST_LINE_HEADER_LABEL.format())
                        .description(FileLabels.TARGET_FIRST_LINE_HEADER_DESCRIPTION.format()).type(TypeEnum.BOOLEAN).required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("null_value").label(FileLabels.TARGET_NULL_VALUE_LABEL.format())
                .description(FileLabels.TARGET_NULL_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("quote_character_value")
                .label(FileLabels.TARGET_QUOTE_CHARACTER_VALUE_LABEL.format())
                .description(FileLabels.TARGET_QUOTE_CHARACTER_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addTargetItem(
                new CustomDatasourceTypeProperty().name("row_delimiter_value").label(FileLabels.TARGET_ROW_DELIMITER_VALUE_LABEL.format())
                        .description(FileLabels.TARGET_ROW_DELIMITER_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addTargetItem(
                new CustomDatasourceTypeProperty().name("time_zone_format").label(FileLabels.TARGET_TIME_ZONE_FORMAT_LABEL.format())
                        .description(FileLabels.TARGET_TIME_ZONE_FORMAT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addTargetItem(
                new CustomDatasourceTypeProperty().name("timestamp_format").label(FileLabels.TARGET_TIMESTAMP_FORMAT_LABEL.format())
                        .description(FileLabels.TARGET_TIMESTAMP_FORMAT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        // XML options
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("row_tag").label(FileLabels.TARGET_ROW_TAG_LABEL.format())
                .description(FileLabels.TARGET_ROW_TAG_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        // ORC and Parquet options
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("compression").label(FileLabels.TARGET_COMPRESSION_LABEL.format())
                .description(FileLabels.TARGET_COMPRESSION_DESCRIPTION.format()).type(TypeEnum.ENUM).required(false)
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("none").label(FileLabels.TARGET_COMPRESSION_VALUE_NONE_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("brotli").label(FileLabels.TARGET_COMPRESSION_VALUE_BROTLI_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("gzip").label(FileLabels.TARGET_COMPRESSION_VALUE_GZIP_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("lzo").label(FileLabels.TARGET_COMPRESSION_VALUE_LZO_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("lz4").label(FileLabels.TARGET_COMPRESSION_VALUE_LZ4_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("lz4_raw")
                        .label(FileLabels.TARGET_COMPRESSION_VALUE_LZ4_RAW_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("snappy").label(FileLabels.TARGET_COMPRESSION_VALUE_SNAPPY_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("uncompressed")
                        .label(FileLabels.TARGET_COMPRESSION_VALUE_UNCOMPRESSED_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("zlib").label(FileLabels.TARGET_COMPRESSION_VALUE_ZLIB_LABEL.format()))
                .addValuesItem(
                        new DatasourceTypePropertyValues().value("zstd").label(FileLabels.TARGET_COMPRESSION_VALUE_ZSTD_LABEL.format())));

        // Define the asset types that can be discovered.
        final DatasourceTypeDiscovery discovery = new DatasourceTypeDiscovery();
        setDiscovery(discovery);
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("folder").label(FileLabels.ASSET_TYPE_FOLDER_LABEL.format()));
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("file").label(FileLabels.ASSET_TYPE_FILE_LABEL.format()));

        // Define which properties form the asset path.
        discovery.addPathPropertiesItem(new DiscoveryPathProperty().propertyName("file_name")
                .addSegmentsItem(new DiscoveryPathSegment().assetTypes("folder").repeatable(true))
                .addSegmentsItem(new DiscoveryPathSegment().assetTypes("file").repeatable(false)));
    }

}
