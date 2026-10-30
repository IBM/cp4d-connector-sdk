/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.box;

import java.util.Collections;

import com.ibm.connect.sdk.file.FileLabels;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeAction;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeActionProperties;
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
 * The definition of a custom Box data source type.
 */
public class BoxDatasourceType extends CustomFlightDatasourceType
{
    /**
     * An instance of the custom Box data source type.
     */
    public static final BoxDatasourceType INSTANCE = new BoxDatasourceType();

    /**
     * The unique identifier name of the data source type.
     */
    public static final String DATASOURCE_TYPE_NAME = "custom_box";

    /**
     * Action name for retrieving the ACL of a Box item.
     */
    public static final String ACTION_GET_ACL = BoxConnector.ACTION_GET_ACL;

    /**
     * Action name for retrieving file metadata (last_modified, size) of a Box file.
     */
    public static final String ACTION_GET_FILE_METADATA = BoxConnector.ACTION_GET_FILE_METADATA;

    /**
     * Defines a custom data source type for Box.
     */
    public BoxDatasourceType()
    {
        super();

        // Set the data source type attributes.
        setName(DATASOURCE_TYPE_NAME);
        setLabel(BoxLabels.DATASOURCE_TYPE_LABEL.format());
        setDescription(BoxLabels.DATASOURCE_TYPE_DESCRIPTION.format());
        setAllowedAsSource(true);
        setAllowedAsTarget(true);
        setStatus(CustomFlightDatasourceType.StatusEnum.ACTIVE);
        setTags(Collections.emptyList());
        final CustomFlightDatasourceTypeProperties properties = new CustomFlightDatasourceTypeProperties();
        setProperties(properties);

        // Define the connection properties.
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("client_id")
                        .label(BoxLabels.CONNECTION_CLIENT_ID_LABEL.format())
                        .description(BoxLabels.CONNECTION_CLIENT_ID_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true).masked(false).group("credentials"));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("client_secret")
                        .label(BoxLabels.CONNECTION_CLIENT_SECRET_LABEL.format())
                        .description(BoxLabels.CONNECTION_CLIENT_SECRET_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true).masked(true).group("credentials"));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("enterprise_id")
                        .label(BoxLabels.CONNECTION_ENTERPRISE_ID_LABEL.format())
                        .description(BoxLabels.CONNECTION_ENTERPRISE_ID_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true).masked(false).group("credentials"));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("private_key")
                        .label(BoxLabels.CONNECTION_PRIVATE_KEY_LABEL.format())
                        .description(BoxLabels.CONNECTION_PRIVATE_KEY_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true).masked(true).group("credentials"));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("private_key_password")
                        .label(BoxLabels.CONNECTION_PRIVATE_KEY_PASSWORD_LABEL.format())
                        .description(BoxLabels.CONNECTION_PRIVATE_KEY_PASSWORD_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(false).masked(true).group("credentials"));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("public_key")
                        .label(BoxLabels.CONNECTION_PUBLIC_KEY_LABEL.format())
                        .description(BoxLabels.CONNECTION_PUBLIC_KEY_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true).masked(false).group("credentials"));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("username")
                        .label(BoxLabels.CONNECTION_USERNAME_LABEL.format())
                        .description(BoxLabels.CONNECTION_USERNAME_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(false).masked(false).group("credentials"));

        // Define the target interaction properties.
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("file_name")
                .label(BoxLabels.TARGET_FILE_NAME_LABEL.format())
                .description(BoxLabels.TARGET_FILE_NAME_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(true));
        // file_format: "binary" writes raw bytes; structured formats delegate to Spark.
        properties.addTargetItem(
                new CustomDatasourceTypeProperty().name("file_format").label(FileLabels.TARGET_FILE_FORMAT_LABEL.format())
                        .description(FileLabels.TARGET_FILE_FORMAT_DESCRIPTION.format()).type(TypeEnum.ENUM).required(false)
                        .addValuesItem(
                                new DatasourceTypePropertyValues().value("avro").label(FileLabels.TARGET_FILE_FORMAT_VALUE_AVRO_LABEL.format()))
                        .addValuesItem(
                                new DatasourceTypePropertyValues().value("binary").label(BoxLabels.TARGET_FILE_FORMAT_BINARY_LABEL.format()))
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

        // Define the source interaction properties.
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("file_name")
                        .label(BoxLabels.SOURCE_FILE_NAME_LABEL.format())
                        .description(BoxLabels.SOURCE_FILE_NAME_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true));
        // file_format: "binary" triggers raw byte streaming; structured formats delegate to Spark.
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("file_format").label(FileLabels.SOURCE_FILE_FORMAT_LABEL.format())
                        .description(FileLabels.SOURCE_FILE_FORMAT_DESCRIPTION.format()).type(TypeEnum.ENUM).required(false)
                        .addValuesItem(
                                new DatasourceTypePropertyValues().value("avro").label(FileLabels.SOURCE_FILE_FORMAT_VALUE_AVRO_LABEL.format()))
                        .addValuesItem(
                                new DatasourceTypePropertyValues().value("binary").label(BoxLabels.SOURCE_FILE_FORMAT_BINARY_LABEL.format()))
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
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("byte_limit").label(FileLabels.SOURCE_BYTE_LIMIT_LABEL.format())
                        .description(FileLabels.SOURCE_BYTE_LIMIT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));

        // Define the asset types that can be discovered.
        final DatasourceTypeDiscovery discovery = new DatasourceTypeDiscovery();
        setDiscovery(discovery);
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("folder").label(FileLabels.ASSET_TYPE_FOLDER_LABEL.format()));
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("file").label(FileLabels.ASSET_TYPE_FILE_LABEL.format()));

        // Define which properties form the asset path.
        discovery.addPathPropertiesItem(new DiscoveryPathProperty().propertyName("file_name")
                .addSegmentsItem(new DiscoveryPathSegment().assetTypes("folder").repeatable(true))
                .addSegmentsItem(new DiscoveryPathSegment().assetTypes("file").repeatable(false)));

        // Define the get_acl action.
        final CustomDatasourceTypeActionProperties aclActionProperties = new CustomDatasourceTypeActionProperties();
        final CustomDatasourceTypeAction aclAction = new CustomDatasourceTypeAction().name(ACTION_GET_ACL)
                .description(BoxLabels.ACTION_GET_ACL_DESCRIPTION.format()).properties(aclActionProperties);
        aclActionProperties.addInputItem(
                new CustomDatasourceTypeProperty().name(BoxConnector.ACTION_PATH_PROP)
                        .label(BoxLabels.ACTION_GET_ACL_INPUT_PATH_LABEL.format())
                        .description(BoxLabels.ACTION_GET_ACL_INPUT_PATH_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true));
        aclActionProperties.addOutputItem(
                new CustomDatasourceTypeProperty().name("allow")
                        .label(BoxLabels.ACTION_GET_ACL_OUTPUT_ALLOW_LABEL.format())
                        .description(BoxLabels.ACTION_GET_ACL_OUTPUT_ALLOW_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true));
        aclActionProperties.addOutputItem(
                new CustomDatasourceTypeProperty().name("deny")
                        .label(BoxLabels.ACTION_GET_ACL_OUTPUT_DENY_LABEL.format())
                        .description(BoxLabels.ACTION_GET_ACL_OUTPUT_DENY_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true));
        addActionsItem(aclAction);

        // Define the get_file_metadata action.
        final CustomDatasourceTypeActionProperties metadataActionProperties = new CustomDatasourceTypeActionProperties();
        final CustomDatasourceTypeAction metadataAction = new CustomDatasourceTypeAction().name(ACTION_GET_FILE_METADATA)
                .description(BoxLabels.ACTION_GET_FILE_METADATA_DESCRIPTION.format()).properties(metadataActionProperties);
        metadataActionProperties.addInputItem(
                new CustomDatasourceTypeProperty().name(BoxConnector.ACTION_PATH_PROP)
                        .label(BoxLabels.ACTION_GET_FILE_METADATA_INPUT_PATH_LABEL.format())
                        .description(BoxLabels.ACTION_GET_FILE_METADATA_INPUT_PATH_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true));
        metadataActionProperties.addOutputItem(
                new CustomDatasourceTypeProperty().name("last_modified")
                        .label(BoxLabels.ACTION_GET_FILE_METADATA_OUTPUT_LAST_MODIFIED_LABEL.format())
                        .description(BoxLabels.ACTION_GET_FILE_METADATA_OUTPUT_LAST_MODIFIED_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true));
        metadataActionProperties.addOutputItem(
                new CustomDatasourceTypeProperty().name("size")
                        .label(BoxLabels.ACTION_GET_FILE_METADATA_OUTPUT_SIZE_LABEL.format())
                        .description(BoxLabels.ACTION_GET_FILE_METADATA_OUTPUT_SIZE_DESCRIPTION.format())
                        .type(TypeEnum.INTEGER).required(true));
        addActionsItem(metadataAction);
    }

}
