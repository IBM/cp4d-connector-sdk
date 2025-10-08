/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.derby;

import com.ibm.connect.sdk.util.ResourceBundleHelper;

/**
 * Localized labels for Apache Derby.
 */
public enum DerbyLabels implements ResourceBundleHelper.MessageFormatter<DerbyLabels>
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
     * Label for connection property port.
     */
    CONNECTION_PORT_LABEL,

    /**
     * Description for connection property port.
     */
    CONNECTION_PORT_DESCRIPTION,

    /**
     * Label for connection property database.
     */
    CONNECTION_DATABASE_LABEL,

    /**
     * Description for connection property database.
     */
    CONNECTION_DATABASE_DESCRIPTION,

    /**
     * Label for connection property username.
     */
    CONNECTION_USERNAME_LABEL,

    /**
     * Description for connection property username.
     */
    CONNECTION_USERNAME_DESCRIPTION,

    /**
     * Label for connection property password.
     */
    CONNECTION_PASSWORD_LABEL,

    /**
     * Description for connection property password.
     */
    CONNECTION_PASSWORD_DESCRIPTION,

    /**
     * Label for connection property ssl.
     */
    CONNECTION_SSL_LABEL,

    /**
     * Description for connection property ssl.
     */
    CONNECTION_SSL_DESCRIPTION,

    /**
     * Label for connection property create_database.
     */
    CONNECTION_CREATE_DATABASE_LABEL,

    /**
     * Description for connection property create_database.
     */
    CONNECTION_CREATE_DATABASE_DESCRIPTION,

    /**
     * Label for source property schema_name.
     */
    SOURCE_SCHEMA_NAME_LABEL,

    /**
     * Description for source property schema_name.
     */
    SOURCE_SCHEMA_NAME_DESCRIPTION,

    /**
     * Label for source property table_name.
     */
    SOURCE_TABLE_NAME_LABEL,

    /**
     * Description for source property table_name.
     */
    SOURCE_TABLE_NAME_DESCRIPTION,

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
     * Label for source property select_statement.
     */
    SOURCE_SELECT_STATEMENT_LABEL,

    /**
     * Description for source property select_statement.
     */
    SOURCE_SELECT_STATEMENT_DESCRIPTION,

    /**
     * Label for target property schema_name.
     */
    TARGET_SCHEMA_NAME_LABEL,

    /**
     * Description for target property schema_name.
     */
    TARGET_SCHEMA_NAME_DESCRIPTION,

    /**
     * Label for target property table_name.
     */
    TARGET_TABLE_NAME_LABEL,

    /**
     * Description for target property table_name.
     */
    TARGET_TABLE_NAME_DESCRIPTION,

    /**
     * Label for target property write_mode.
     */
    TARGET_WRITE_MODE_LABEL,

    /**
     * Description for target property write_mode.
     */
    TARGET_WRITE_MODE_DESCRIPTION,

    /**
     * Label for target property write_mode value insert.
     */
    TARGET_WRITE_MODE_VALUE_INSERT_LABEL,

    /**
     * Label for target property write_mode value update.
     */
    TARGET_WRITE_MODE_VALUE_UPDATE_LABEL,

    /**
     * Label for target property write_mode value update_statement.
     */
    TARGET_WRITE_MODE_VALUE_UPDATE_STATEMENT_LABEL,

    /**
     * Label for target property write_mode value static_statement.
     */
    TARGET_WRITE_MODE_VALUE_STATIC_STATEMENT_LABEL,

    /**
     * Label for target property key_column_names.
     */
    TARGET_KEY_COLUMN_NAMES_LABEL,

    /**
     * Description for target property key_column_names.
     */
    TARGET_KEY_COLUMN_NAMES_DESCRIPTION,

    /**
     * Label for target property update_statement.
     */
    TARGET_UPDATE_STATEMENT_LABEL,

    /**
     * Description for target property update_statement.
     */
    TARGET_UPDATE_STATEMENT_DESCRIPTION,

    /**
     * Label for target property table_action.
     */
    TARGET_TABLE_ACTION_LABEL,

    /**
     * Description for target property table_action.
     */
    TARGET_TABLE_ACTION_DESCRIPTION,

    /**
     * Label for target property table_action value append.
     */
    TARGET_TABLE_ACTION_VALUE_APPEND_LABEL,

    /**
     * Label for target property table_action value replace.
     */
    TARGET_TABLE_ACTION_VALUE_REPLACE_LABEL,

    /**
     * Label for target property table_action value truncate.
     */
    TARGET_TABLE_ACTION_VALUE_TRUNCATE_LABEL,

    /**
     * Label for target property create_statement.
     */
    TARGET_CREATE_STATEMENT_LABEL,

    /**
     * Description for target property create_statement.
     */
    TARGET_CREATE_STATEMENT_DESCRIPTION,

    /**
     * Label for target property update_statistics.
     */
    TARGET_UPDATE_STATISTICS_LABEL,

    /**
     * Description for target property update_statistics.
     */
    TARGET_UPDATE_STATISTICS_DESCRIPTION,

    /**
     * Label for filter property include_system.
     */
    FILTER_INCLUDE_SYSTEM_LABEL,

    /**
     * Description for filter property include_system.
     */
    FILTER_INCLUDE_SYSTEM_DESCRIPTION,

    /**
     * Label for filter property include_table.
     */
    FILTER_INCLUDE_TABLE_LABEL,

    /**
     * Description for filter property include_table.
     */
    FILTER_INCLUDE_TABLE_DESCRIPTION,

    /**
     * Label for filter property include_view.
     */
    FILTER_INCLUDE_VIEW_LABEL,

    /**
     * Description for filter property include_view.
     */
    FILTER_INCLUDE_VIEW_DESCRIPTION,

    /**
     * Label for filter property name_pattern.
     */
    FILTER_NAME_PATTERN_LABEL,

    /**
     * Description for filter property name_pattern.
     */
    FILTER_NAME_PATTERN_DESCRIPTION,

    /**
     * Label for filter property primary_key.
     */
    FILTER_PRIMARY_KEY_LABEL,

    /**
     * Description for filter property primary_key.
     */
    FILTER_PRIMARY_KEY_DESCRIPTION,

    /**
     * Label for filter property schema_name_pattern.
     */
    FILTER_SCHEMA_NAME_PATTERN_LABEL,

    /**
     * Description for filter property schema_name_pattern.
     */
    FILTER_SCHEMA_NAME_PATTERN_DESCRIPTION,

    /**
     * Label for asset type schema.
     */
    ASSET_TYPE_SCHEMA_LABEL,

    /**
     * Label for asset type table.
     */
    ASSET_TYPE_TABLE_LABEL,

    /**
     * Label for asset type primary_key.
     */
    ASSET_TYPE_PRIMARY_KEY_LABEL,

    /**
     * Description for action get_record_count.
     */
    ACTION_GET_RECORD_COUNT_DESCRIPTION,

    /**
     * Label for action get_record_count input property schema_name.
     */
    ACTION_GET_RECORD_COUNT_INPUT_SCHEMA_NAME_LABEL,

    /**
     * Description for action get_record_count input property schema_name.
     */
    ACTION_GET_RECORD_COUNT_INPUT_SCHEMA_NAME_DESCRIPTION,

    /**
     * Label for action get_record_count input property table_name.
     */
    ACTION_GET_RECORD_COUNT_INPUT_TABLE_NAME_LABEL,

    /**
     * Description for action get_record_count input property table_name.
     */
    ACTION_GET_RECORD_COUNT_INPUT_TABLE_NAME_DESCRIPTION,

    /**
     * Label for action get_record_count output property record_count.
     */
    ACTION_GET_RECORD_COUNT_OUTPUT_RECORD_COUNT_LABEL,

    /**
     * Description for action get_record_count output property record_count.
     */
    ACTION_GET_RECORD_COUNT_OUTPUT_RECORD_COUNT_DESCRIPTION;

    private static final ResourceBundleHelper<DerbyLabels> BUNDLE = new ResourceBundleHelper<>(DerbyLabels.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String format(Object... args)
    {
        return BUNDLE.format(this, args);
    }

}
