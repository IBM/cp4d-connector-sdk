/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file.github;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized labels for GitHub.
 */
public enum GitHubLabels implements ResourceBundleHelper.MessageFormatter<GitHubLabels>
{
    /**
     * Data source type label.
     */
    DATASOURCE_TYPE_LABEL,

    /**
     * Data source type description.
     */
    DATASOURCE_TYPE_DESCRIPTION,

    /**
     * Label for connection property host.
     */
    CONNECTION_HOST_LABEL,

    /**
     * Description for connection property host.
     */
    CONNECTION_HOST_DESCRIPTION,

    /**
     * Label for connection property repository_owner.
     */
    CONNECTION_REPOSITORY_OWNER_LABEL,

    /**
     * Description for connection property repository_owner.
     */
    CONNECTION_REPOSITORY_OWNER_DESCRIPTION,

    /**
     * Label for connection property repository_name.
     */
    CONNECTION_REPOSITORY_NAME_LABEL,

    /**
     * Description for connection property repository_name.
     */
    CONNECTION_REPOSITORY_NAME_DESCRIPTION,

    /**
     * Label for connection property branch_name.
     */
    CONNECTION_BRANCH_NAME_LABEL,

    /**
     * Description for connection property branch_name.
     */
    CONNECTION_BRANCH_NAME_DESCRIPTION,

    /**
     * Label for connection property access_token.
     */
    CONNECTION_ACCESS_TOKEN_LABEL,

    /**
     * Description for connection property access_token.
     */
    CONNECTION_ACCESS_TOKEN_DESCRIPTION,

    /**
     * Label for source property branch_name.
     */
    SOURCE_BRANCH_NAME_LABEL,

    /**
     * Description for source property branch_name.
     */
    SOURCE_BRANCH_NAME_DESCRIPTION,

    /**
     * Label for source property file_name.
     */
    SOURCE_FILE_NAME_LABEL,

    /**
     * Description for source property file_name.
     */
    SOURCE_FILE_NAME_DESCRIPTION,

    /**
     * Label for source property file_format.
     */
    SOURCE_FILE_FORMAT_LABEL,

    /**
     * Description for source property file_format.
     */
    SOURCE_FILE_FORMAT_DESCRIPTION,

    /**
     * Label for source property file_format value avro.
     */
    SOURCE_FILE_FORMAT_VALUE_AVRO_LABEL,

    /**
     * Label for source property file_format value csv.
     */
    SOURCE_FILE_FORMAT_VALUE_CSV_LABEL,

    /**
     * Label for source property file_format value delimited.
     */
    SOURCE_FILE_FORMAT_VALUE_DELIMITED_LABEL,

    /**
     * Label for source property file_format value json.
     */
    SOURCE_FILE_FORMAT_VALUE_JSON_LABEL,

    /**
     * Label for source property file_format value orc.
     */
    SOURCE_FILE_FORMAT_VALUE_ORC_LABEL,

    /**
     * Label for source property file_format value parquet.
     */
    SOURCE_FILE_FORMAT_VALUE_PARQUET_LABEL,

    /**
     * Label for source property file_format value xml.
     */
    SOURCE_FILE_FORMAT_VALUE_XML_LABEL,

    /**
     * Label for source property row_limit.
     */
    SOURCE_ROW_LIMIT_LABEL,

    /**
     * Description for source property row_limit.
     */
    SOURCE_ROW_LIMIT_DESCRIPTION,

    /**
     * Label for source property byte_limit.
     */
    SOURCE_BYTE_LIMIT_LABEL,

    /**
     * Description for source property byte_limit.
     */
    SOURCE_BYTE_LIMIT_DESCRIPTION,

    /**
     * Label for source property comment_character_value.
     */
    SOURCE_COMMENT_CHARACTER_VALUE_LABEL,

    /**
     * Description for source property comment_character_value.
     */
    SOURCE_COMMENT_CHARACTER_VALUE_DESCRIPTION,

    /**
     * Label for source property date_format.
     */
    SOURCE_DATE_FORMAT_LABEL,

    /**
     * Description for source property date_format.
     */
    SOURCE_DATE_FORMAT_DESCRIPTION,

    /**
     * Label for source property encoding.
     */
    SOURCE_ENCODING_LABEL,

    /**
     * Description for source property encoding.
     */
    SOURCE_ENCODING_DESCRIPTION,

    /**
     * Label for source property escape_character_value.
     */
    SOURCE_ESCAPE_CHARACTER_VALUE_LABEL,

    /**
     * Description for source property escape_character_value.
     */
    SOURCE_ESCAPE_CHARACTER_VALUE_DESCRIPTION,

    /**
     * Label for source property field_delimiter_value.
     */
    SOURCE_FIELD_DELIMITER_VALUE_LABEL,

    /**
     * Description for source property field_delimiter_value.
     */
    SOURCE_FIELD_DELIMITER_VALUE_DESCRIPTION,

    /**
     * Label for source property first_line_header.
     */
    SOURCE_FIRST_LINE_HEADER_LABEL,

    /**
     * Description for source property first_line_header.
     */
    SOURCE_FIRST_LINE_HEADER_DESCRIPTION,

    /**
     * Label for source property infer_schema.
     */
    SOURCE_INFER_SCHEMA_LABEL,

    /**
     * Description for source property infer_schema.
     */
    SOURCE_INFER_SCHEMA_DESCRIPTION,

    /**
     * Label for source property nan_value.
     */
    SOURCE_NAN_VALUE_LABEL,

    /**
     * Description for source property nan_value.
     */
    SOURCE_NAN_VALUE_DESCRIPTION,

    /**
     * Label for source property negative_infinity_value.
     */
    SOURCE_NEGATIVE_INFINITY_VALUE_LABEL,

    /**
     * Description for source property negative_infinity_value.
     */
    SOURCE_NEGATIVE_INFINITY_VALUE_DESCRIPTION,

    /**
     * Label for source property null_value.
     */
    SOURCE_NULL_VALUE_LABEL,

    /**
     * Description for source property null_value.
     */
    SOURCE_NULL_VALUE_DESCRIPTION,

    /**
     * Label for source property positive_infinity_value.
     */
    SOURCE_POSITIVE_INFINITY_VALUE_LABEL,

    /**
     * Description for source property positive_infinity_value.
     */
    SOURCE_POSITIVE_INFINITY_VALUE_DESCRIPTION,

    /**
     * Label for source property quote_character_value.
     */
    SOURCE_QUOTE_CHARACTER_VALUE_LABEL,

    /**
     * Description for source property quote_character_value.
     */
    SOURCE_QUOTE_CHARACTER_VALUE_DESCRIPTION,

    /**
     * Label for source property row_delimiter_value.
     */
    SOURCE_ROW_DELIMITER_VALUE_LABEL,

    /**
     * Description for source property row_delimiter_value.
     */
    SOURCE_ROW_DELIMITER_VALUE_DESCRIPTION,

    /**
     * Label for source property time_zone_format.
     */
    SOURCE_TIME_ZONE_FORMAT_LABEL,

    /**
     * Description for source property time_zone_format.
     */
    SOURCE_TIME_ZONE_FORMAT_DESCRIPTION,

    /**
     * Label for source property timestamp_format.
     */
    SOURCE_TIMESTAMP_FORMAT_LABEL,

    /**
     * Description for source property timestamp_format.
     */
    SOURCE_TIMESTAMP_FORMAT_DESCRIPTION,

    /**
     * Label for source property row_tag.
     */
    SOURCE_ROW_TAG_LABEL,

    /**
     * Description for source property row_tag.
     */
    SOURCE_ROW_TAG_DESCRIPTION,

    /**
     * Label for asset type branch.
     */
    ASSET_TYPE_BRANCH_LABEL,

    /**
     * Label for asset type folder.
     */
    ASSET_TYPE_FOLDER_LABEL,

    /**
     * Label for asset type file.
     */
    ASSET_TYPE_FILE_LABEL;

    private static final ResourceBundleHelper<GitHubLabels> BUNDLE = new ResourceBundleHelper<>(GitHubLabels.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
