/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.github;

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
 * The definition of a custom GitHub data source type.
 */
public class GitHubDatasourceType extends CustomFlightDatasourceType
{
    /**
     * An instance of the custom GitHub data source type.
     */
    public static final GitHubDatasourceType INSTANCE = new GitHubDatasourceType();

    /**
     * The unique identifier name of the data source type.
     */
    public static final String DATASOURCE_TYPE_NAME = "custom_github";

    /**
     * Defines a custom data source type for GitHub.
     */
    public GitHubDatasourceType()
    {
        super();

        // Set the data source type attributes.
        setName(DATASOURCE_TYPE_NAME);
        setLabel(GitHubLabels.DATASOURCE_TYPE_LABEL.format());
        setDescription(GitHubLabels.DATASOURCE_TYPE_DESCRIPTION.format());
        setAllowedAsSource(true);
        setAllowedAsTarget(false);
        setStatus(CustomFlightDatasourceType.StatusEnum.ACTIVE);
        setTags(Collections.emptyList());
        final CustomFlightDatasourceTypeProperties properties = new CustomFlightDatasourceTypeProperties();
        setProperties(properties);

        // Define the connection properties.
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("host").label(GitHubLabels.CONNECTION_HOST_LABEL.format())
                .description(GitHubLabels.CONNECTION_HOST_DESCRIPTION.format()).type(TypeEnum.STRING).required(true).group("domain")
                .defaultValue("github.com"));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("repository_owner").label(GitHubLabels.CONNECTION_REPOSITORY_OWNER_LABEL.format())
                        .description(GitHubLabels.CONNECTION_REPOSITORY_OWNER_DESCRIPTION.format()).type(TypeEnum.STRING).required(true));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("repository_name").label(GitHubLabels.CONNECTION_REPOSITORY_NAME_LABEL.format())
                        .description(GitHubLabels.CONNECTION_REPOSITORY_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(true));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("branch_name").label(GitHubLabels.CONNECTION_BRANCH_NAME_LABEL.format())
                        .description(GitHubLabels.CONNECTION_BRANCH_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addConnectionItem(
                new CustomDatasourceTypeProperty().name("access_token").label(GitHubLabels.CONNECTION_ACCESS_TOKEN_LABEL.format())
                        .description(GitHubLabels.CONNECTION_ACCESS_TOKEN_DESCRIPTION.format()).type(TypeEnum.STRING).required(false)
                        .masked(true).group("credentials"));

        // Define the source interaction properties.
        properties
                .addSourceItem(new CustomDatasourceTypeProperty().name("branch_name").label(GitHubLabels.SOURCE_BRANCH_NAME_LABEL.format())
                        .description(GitHubLabels.SOURCE_BRANCH_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("file_name").label(FileLabels.SOURCE_FILE_NAME_LABEL.format())
                .description(FileLabels.SOURCE_FILE_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(true));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("file_format")
                .label(FileLabels.SOURCE_FILE_FORMAT_LABEL.format()).description(FileLabels.SOURCE_FILE_FORMAT_DESCRIPTION.format())
                .type(TypeEnum.ENUM).required(false)
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
        properties
                .addSourceItem(new CustomDatasourceTypeProperty().name("date_format").label(FileLabels.SOURCE_DATE_FORMAT_LABEL.format())
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
        properties.addSourceItem(
                new CustomDatasourceTypeProperty().name("infer_schema").label(FileLabels.SOURCE_INFER_SCHEMA_LABEL.format())
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

        // Define the asset types that can be discovered.
        final DatasourceTypeDiscovery discovery = new DatasourceTypeDiscovery();
        setDiscovery(discovery);
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("branch").label(GitHubLabels.ASSET_TYPE_BRANCH_LABEL.format()));
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("folder").label(FileLabels.ASSET_TYPE_FOLDER_LABEL.format()));
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("file").label(FileLabels.ASSET_TYPE_FILE_LABEL.format()));

        // Define which properties form the asset path.
        discovery.addPathPropertiesItem(new DiscoveryPathProperty().propertyName("branch_name")
                .addSegmentsItem(new DiscoveryPathSegment().assetTypes("branch").repeatable(false)));
        discovery.addPathPropertiesItem(new DiscoveryPathProperty().propertyName("file_name")
                .addSegmentsItem(new DiscoveryPathSegment().assetTypes("folder").repeatable(true))
                .addSegmentsItem(new DiscoveryPathSegment().assetTypes("file").repeatable(false)));
    }

}
