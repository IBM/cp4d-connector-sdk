/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.s3;

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
 * The definition of a custom Amazon S3 data source type.
 */
public class AWSS3DatasourceType extends CustomFlightDatasourceType
{
    /**
     * An instance of the custom Amazon S3 data source type.
     */
    public static final AWSS3DatasourceType INSTANCE = new AWSS3DatasourceType();

    /**
     * The unique identifier name of the data source type.
     */
    public static final String DATASOURCE_TYPE_NAME = "custom_s3";

    /**
     * File format constant for raw/unstructured binary reads.
     */
    public static final String FILE_FORMAT_BINARY = "binary";

    /**
     * Action name for retrieving the ACL of an S3 object.
     * Matches {@code AWSS3Connector.ACTION_GET_ACL}.
     */
    public static final String ACTION_GET_ACL = AWSS3Connector.ACTION_GET_ACL;

    /**
     * Action name for retrieving file metadata (last_modified, size) of an S3 object.
     * Matches {@code AWSS3Connector.ACTION_GET_FILE_METADATA}.
     */
    public static final String ACTION_GET_FILE_METADATA = AWSS3Connector.ACTION_GET_FILE_METADATA;

    /**
     * Defines a custom data source type for Amazon S3.
     */
    public AWSS3DatasourceType()
    {
        super();

        // Set the data source type attributes.
        setName(DATASOURCE_TYPE_NAME);
        setLabel(AWSS3Labels.DATASOURCE_TYPE_LABEL.format());
        setDescription(AWSS3Labels.DATASOURCE_TYPE_DESCRIPTION.format());
        setAllowedAsSource(true);
        setAllowedAsTarget(false);
        setStatus(CustomFlightDatasourceType.StatusEnum.ACTIVE);
        setTags(Collections.emptyList());
        final CustomFlightDatasourceTypeProperties properties = new CustomFlightDatasourceTypeProperties();
        setProperties(properties);

        // Define the connection properties.
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("bucket").label(AWSS3Labels.CONNECTION_BUCKET_LABEL.format())
                        .description(AWSS3Labels.CONNECTION_BUCKET_DESCRIPTION.format()).type(TypeEnum.STRING).required(true));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("region").label(AWSS3Labels.CONNECTION_REGION_LABEL.format())
                        .description(AWSS3Labels.CONNECTION_REGION_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("endpoint_url").label(AWSS3Labels.CONNECTION_ENDPOINT_URL_LABEL.format())
                        .description(AWSS3Labels.CONNECTION_ENDPOINT_URL_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("access_key_id").label(AWSS3Labels.CONNECTION_ACCESS_KEY_ID_LABEL.format())
                        .description(AWSS3Labels.CONNECTION_ACCESS_KEY_ID_DESCRIPTION.format()).type(TypeEnum.STRING).required(false)
                        .group("credentials"));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("secret_access_key").label(AWSS3Labels.CONNECTION_SECRET_ACCESS_KEY_LABEL.format())
                        .description(AWSS3Labels.CONNECTION_SECRET_ACCESS_KEY_DESCRIPTION.format()).type(TypeEnum.STRING).required(false)
                        .masked(true).group("credentials"));

        // Define the source interaction properties.
        // file_name is the S3 object key (path within the bucket).
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("file_name").label(AWSS3Labels.SOURCE_FILE_NAME_LABEL.format())
                        .description(AWSS3Labels.SOURCE_FILE_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(true));
        // file_format: "binary" triggers raw byte streaming; structured formats delegate to Spark.
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("file_format").label(FileLabels.SOURCE_FILE_FORMAT_LABEL.format())
                        .description(FileLabels.SOURCE_FILE_FORMAT_DESCRIPTION.format()).type(TypeEnum.ENUM).required(false)
                        .addValuesItem(
                                new DatasourceTypePropertyValues().value("avro").label(FileLabels.SOURCE_FILE_FORMAT_VALUE_AVRO_LABEL.format()))
                        .addValuesItem(
                                new DatasourceTypePropertyValues().value("binary").label(AWSS3Labels.SOURCE_FILE_FORMAT_BINARY_LABEL.format()))
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
                new CustomDatasourceTypeProperty().name("row_limit").label(FileLabels.SOURCE_ROW_LIMIT_LABEL.format())
                        .description(FileLabels.SOURCE_ROW_LIMIT_DESCRIPTION.format()).type(TypeEnum.INTEGER).required(false));
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("byte_limit").label(FileLabels.SOURCE_BYTE_LIMIT_LABEL.format())
                        .description(FileLabels.SOURCE_BYTE_LIMIT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        // CSV / delimited options.
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("comment_character_value")
                .label(FileLabels.SOURCE_COMMENT_CHARACTER_VALUE_LABEL.format())
                .description(FileLabels.SOURCE_COMMENT_CHARACTER_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("date_format").label(FileLabels.SOURCE_DATE_FORMAT_LABEL.format())
                        .description(FileLabels.SOURCE_DATE_FORMAT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("encoding").label(FileLabels.SOURCE_ENCODING_LABEL.format())
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
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("infer_schema").label(FileLabels.SOURCE_INFER_SCHEMA_LABEL.format())
                        .description(FileLabels.SOURCE_INFER_SCHEMA_DESCRIPTION.format()).type(TypeEnum.BOOLEAN).required(false));
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("nan_value").label(FileLabels.SOURCE_NAN_VALUE_LABEL.format())
                        .description(FileLabels.SOURCE_NAN_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("negative_infinity_value")
                .label(FileLabels.SOURCE_NEGATIVE_INFINITY_VALUE_LABEL.format())
                .description(FileLabels.SOURCE_NEGATIVE_INFINITY_VALUE_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("null_value").label(FileLabels.SOURCE_NULL_VALUE_LABEL.format())
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
        // XML options.
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("row_tag").label(FileLabels.SOURCE_ROW_TAG_LABEL.format())
                        .description(FileLabels.SOURCE_ROW_TAG_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));

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
        // Input/output property names match the ACLProvider contract in wdp-connect-library.
        final CustomDatasourceTypeActionProperties aclActionProperties = new CustomDatasourceTypeActionProperties();
        final CustomDatasourceTypeAction aclAction = new CustomDatasourceTypeAction().name(ACTION_GET_ACL)
                .description(AWSS3Labels.ACTION_GET_ACL_DESCRIPTION.format()).properties(aclActionProperties);
        aclActionProperties.addInputItem(
                new CustomDatasourceTypeProperty().name(AWSS3Connector.ACTION_PATH_PROP)
                        .label(AWSS3Labels.ACTION_GET_ACL_INPUT_PATH_LABEL.format())
                        .description(AWSS3Labels.ACTION_GET_ACL_INPUT_PATH_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true));
        aclActionProperties.addOutputItem(
                new CustomDatasourceTypeProperty().name("allow")
                        .label(AWSS3Labels.ACTION_GET_ACL_OUTPUT_ALLOW_LABEL.format())
                        .description(AWSS3Labels.ACTION_GET_ACL_OUTPUT_ALLOW_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true));
        aclActionProperties.addOutputItem(
                new CustomDatasourceTypeProperty().name("deny")
                        .label(AWSS3Labels.ACTION_GET_ACL_OUTPUT_DENY_LABEL.format())
                        .description(AWSS3Labels.ACTION_GET_ACL_OUTPUT_DENY_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true));
        addActionsItem(aclAction);

        // Define the get_file_metadata action.
        final CustomDatasourceTypeActionProperties metadataActionProperties = new CustomDatasourceTypeActionProperties();
        final CustomDatasourceTypeAction metadataAction = new CustomDatasourceTypeAction().name(ACTION_GET_FILE_METADATA)
                .description(AWSS3Labels.ACTION_GET_FILE_METADATA_DESCRIPTION.format()).properties(metadataActionProperties);
        metadataActionProperties.addInputItem(
                new CustomDatasourceTypeProperty().name(AWSS3Connector.ACTION_PATH_PROP)
                        .label(AWSS3Labels.ACTION_GET_FILE_METADATA_INPUT_PATH_LABEL.format())
                        .description(AWSS3Labels.ACTION_GET_FILE_METADATA_INPUT_PATH_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true));
        metadataActionProperties.addOutputItem(
                new CustomDatasourceTypeProperty().name("last_modified")
                        .label(AWSS3Labels.ACTION_GET_FILE_METADATA_OUTPUT_LAST_MODIFIED_LABEL.format())
                        .description(AWSS3Labels.ACTION_GET_FILE_METADATA_OUTPUT_LAST_MODIFIED_DESCRIPTION.format())
                        .type(TypeEnum.STRING).required(true));
        metadataActionProperties.addOutputItem(
                new CustomDatasourceTypeProperty().name("size")
                        .label(AWSS3Labels.ACTION_GET_FILE_METADATA_OUTPUT_SIZE_LABEL.format())
                        .description(AWSS3Labels.ACTION_GET_FILE_METADATA_OUTPUT_SIZE_DESCRIPTION.format())
                        .type(TypeEnum.INTEGER).required(true));
        addActionsItem(metadataAction);
    }

}
