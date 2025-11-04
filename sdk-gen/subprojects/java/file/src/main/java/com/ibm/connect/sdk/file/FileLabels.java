/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.file;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized labels for files.
 */
public enum FileLabels implements ResourceBundleHelper.MessageFormatter<FileLabels>
{
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
     * Label for target property file_name.
     */
    TARGET_FILE_NAME_LABEL,

    /**
     * Description for target property file_name.
     */
    TARGET_FILE_NAME_DESCRIPTION,

    /**
     * Label for target property file_format.
     */
    TARGET_FILE_FORMAT_LABEL,

    /**
     * Description for target property file_format.
     */
    TARGET_FILE_FORMAT_DESCRIPTION,

    /**
     * Label for target property file_format value avro.
     */
    TARGET_FILE_FORMAT_VALUE_AVRO_LABEL,

    /**
     * Label for target property file_format value csv.
     */
    TARGET_FILE_FORMAT_VALUE_CSV_LABEL,

    /**
     * Label for target property file_format value delimited.
     */
    TARGET_FILE_FORMAT_VALUE_DELIMITED_LABEL,

    /**
     * Label for target property file_format value json.
     */
    TARGET_FILE_FORMAT_VALUE_JSON_LABEL,

    /**
     * Label for target property file_format value orc.
     */
    TARGET_FILE_FORMAT_VALUE_ORC_LABEL,

    /**
     * Label for target property file_format value parquet.
     */
    TARGET_FILE_FORMAT_VALUE_PARQUET_LABEL,

    /**
     * Label for target property file_format value xml.
     */
    TARGET_FILE_FORMAT_VALUE_XML_LABEL,

    /**
     * Label for target property date_format.
     */
    TARGET_DATE_FORMAT_LABEL,

    /**
     * Description for target property date_format.
     */
    TARGET_DATE_FORMAT_DESCRIPTION,

    /**
     * Label for target property encoding.
     */
    TARGET_ENCODING_LABEL,

    /**
     * Description for target property encoding.
     */
    TARGET_ENCODING_DESCRIPTION,

    /**
     * Label for target property escape_character_value.
     */
    TARGET_ESCAPE_CHARACTER_VALUE_LABEL,

    /**
     * Description for target property escape_character_value.
     */
    TARGET_ESCAPE_CHARACTER_VALUE_DESCRIPTION,

    /**
     * Label for target property field_delimiter_value.
     */
    TARGET_FIELD_DELIMITER_VALUE_LABEL,

    /**
     * Description for target property field_delimiter_value.
     */
    TARGET_FIELD_DELIMITER_VALUE_DESCRIPTION,

    /**
     * Label for target property first_line_header.
     */
    TARGET_FIRST_LINE_HEADER_LABEL,

    /**
     * Description for target property first_line_header.
     */
    TARGET_FIRST_LINE_HEADER_DESCRIPTION,

    /**
     * Label for target property null_value.
     */
    TARGET_NULL_VALUE_LABEL,

    /**
     * Description for target property null_value.
     */
    TARGET_NULL_VALUE_DESCRIPTION,

    /**
     * Label for target property quote_character_value.
     */
    TARGET_QUOTE_CHARACTER_VALUE_LABEL,

    /**
     * Description for target property quote_character_value.
     */
    TARGET_QUOTE_CHARACTER_VALUE_DESCRIPTION,

    /**
     * Label for target property row_delimiter_value.
     */
    TARGET_ROW_DELIMITER_VALUE_LABEL,

    /**
     * Description for target property row_delimiter_value.
     */
    TARGET_ROW_DELIMITER_VALUE_DESCRIPTION,

    /**
     * Label for target property time_zone_format.
     */
    TARGET_TIME_ZONE_FORMAT_LABEL,

    /**
     * Description for target property time_zone_format.
     */
    TARGET_TIME_ZONE_FORMAT_DESCRIPTION,

    /**
     * Label for target property timestamp_format.
     */
    TARGET_TIMESTAMP_FORMAT_LABEL,

    /**
     * Description for target property timestamp_format.
     */
    TARGET_TIMESTAMP_FORMAT_DESCRIPTION,

    /**
     * Label for target property row_tag.
     */
    TARGET_ROW_TAG_LABEL,

    /**
     * Description for target property row_tag.
     */
    TARGET_ROW_TAG_DESCRIPTION,

    /**
     * Label for target property compression.
     */
    TARGET_COMPRESSION_LABEL,

    /**
     * Description for target property compression.
     */
    TARGET_COMPRESSION_DESCRIPTION,

    /**
     * Label for target property compression value none.
     */
    TARGET_COMPRESSION_VALUE_NONE_LABEL,

    /**
     * Label for target property compression value brotli.
     */
    TARGET_COMPRESSION_VALUE_BROTLI_LABEL,

    /**
     * Label for target property compression value gzip.
     */
    TARGET_COMPRESSION_VALUE_GZIP_LABEL,

    /**
     * Label for target property compression value lzo.
     */
    TARGET_COMPRESSION_VALUE_LZO_LABEL,

    /**
     * Label for target property compression value lz4.
     */
    TARGET_COMPRESSION_VALUE_LZ4_LABEL,

    /**
     * Label for target property compression value lz4_raw.
     */
    TARGET_COMPRESSION_VALUE_LZ4_RAW_LABEL,

    /**
     * Label for target property compression value snappy.
     */
    TARGET_COMPRESSION_VALUE_SNAPPY_LABEL,

    /**
     * Label for target property compression value uncompressed.
     */
    TARGET_COMPRESSION_VALUE_UNCOMPRESSED_LABEL,

    /**
     * Label for target property compression value zlib.
     */
    TARGET_COMPRESSION_VALUE_ZLIB_LABEL,

    /**
     * Label for target property compression value zstd.
     */
    TARGET_COMPRESSION_VALUE_ZSTD_LABEL,

    /**
     * Label for asset type folder.
     */
    ASSET_TYPE_FOLDER_LABEL,

    /**
     * Label for asset type file.
     */
    ASSET_TYPE_FILE_LABEL;

    private static final ResourceBundleHelper<FileLabels> BUNDLE = new ResourceBundleHelper<>(FileLabels.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
