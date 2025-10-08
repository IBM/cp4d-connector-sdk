/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022, 2025                  */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.generic;

import java.util.Collections;

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
 * The definition of a custom generic JDBC data source type.
 */
public class GenericJdbcDatasourceType extends CustomFlightDatasourceType
{
    /**
     * An instance of the custom generic JDBC data source type.
     */
    public static final GenericJdbcDatasourceType INSTANCE = new GenericJdbcDatasourceType();

    /**
     * The unique identifier name of the data source type.
     */
    public static final String DATASOURCE_TYPE_NAME = "custom_genericjdbc";

    /**
     * Defines a custom data source type for generic JDBC.
     */
    public GenericJdbcDatasourceType()
    {
        super();

        // Set the data source type attributes.
        setName(DATASOURCE_TYPE_NAME);
        setLabel(GenericJdbcLabels.DATASOURCE_TYPE_LABEL.format());
        setDescription(GenericJdbcLabels.DATASOURCE_TYPE_DESCRIPTION.format());
        setAllowedAsSource(true);
        setAllowedAsTarget(true);
        setStatus(CustomFlightDatasourceType.StatusEnum.ACTIVE);
        setTags(Collections.emptyList());
        final CustomFlightDatasourceTypeProperties properties = new CustomFlightDatasourceTypeProperties();
        setProperties(properties);

        // Define the connection properties.
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("jdbc_url").label(GenericJdbcLabels.CONNECTION_JDBC_URL_LABEL.format())
                .description(GenericJdbcLabels.CONNECTION_JDBC_URL_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(true).group("domain"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("jdbc_properties").label(GenericJdbcLabels.CONNECTION_JDBC_PROPERTIES_LABEL.format()).description(
                GenericJdbcLabels.CONNECTION_JDBC_PROPERTIES_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(false).multiline(true));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("username").label(GenericJdbcLabels.CONNECTION_USERNAME_LABEL.format()).description(GenericJdbcLabels.CONNECTION_USERNAME_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(false).group("credentials"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("password").label(GenericJdbcLabels.CONNECTION_PASSWORD_LABEL.format()).description(GenericJdbcLabels.CONNECTION_PASSWORD_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(false).masked(true).group("credentials"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("row_limit_support").label(GenericJdbcLabels.CONNECTION_ROW_LIMIT_SUPPORT_LABEL.format())
                .description(GenericJdbcLabels.CONNECTION_ROW_LIMIT_SUPPORT_DESCRIPTION.format()).type(TypeEnum.ENUM).required(false)
                .defaultValue("none").addValuesItem(new DatasourceTypePropertyValues().value("none").label(GenericJdbcLabels.CONNECTION_ROW_LIMIT_SUPPORT_VALUE_NONE_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("prefix").label(GenericJdbcLabels.CONNECTION_ROW_LIMIT_SUPPORT_VALUE_PREFIX_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("suffix").label(GenericJdbcLabels.CONNECTION_ROW_LIMIT_SUPPORT_VALUE_SUFFIX_LABEL.format())));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("row_limit_prefix").label(GenericJdbcLabels.CONNECTION_ROW_LIMIT_PREFIX_LABEL.format()).description(
                GenericJdbcLabels.CONNECTION_ROW_LIMIT_PREFIX_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(false));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("row_limit_suffix").label(GenericJdbcLabels.CONNECTION_ROW_LIMIT_SUFFIX_LABEL.format()).description(
                GenericJdbcLabels.CONNECTION_ROW_LIMIT_SUFFIX_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(false));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("ssl_certificate").label(GenericJdbcLabels.CONNECTION_SSL_CERTIFICATE_LABEL.format()).description(
                GenericJdbcLabels.CONNECTION_SSL_CERTIFICATE_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(false).group("ssl").multiline(true));

        // Define the source interaction properties.
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("schema_name").label(GenericJdbcLabels.SOURCE_SCHEMA_NAME_LABEL.format())
                .description(GenericJdbcLabels.SOURCE_SCHEMA_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("table_name").label(GenericJdbcLabels.SOURCE_TABLE_NAME_LABEL.format())
                .description(GenericJdbcLabels.SOURCE_TABLE_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("row_limit").label(GenericJdbcLabels.SOURCE_ROW_LIMIT_LABEL.format())
                .description(GenericJdbcLabels.SOURCE_ROW_LIMIT_DESCRIPTION.format()).type(TypeEnum.INTEGER).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("byte_limit").label(GenericJdbcLabels.SOURCE_BYTE_LIMIT_LABEL.format())
                .description(GenericJdbcLabels.SOURCE_BYTE_LIMIT_DESCRIPTION.format()).type(TypeEnum.STRING)
                .required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("select_statement").label(GenericJdbcLabels.SOURCE_SELECT_STATEMENT_LABEL.format())
                .description(GenericJdbcLabels.SOURCE_SELECT_STATEMENT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));

        // Define the target interaction properties.
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("schema_name").label(GenericJdbcLabels.TARGET_SCHEMA_NAME_LABEL.format())
                .description(GenericJdbcLabels.TARGET_SCHEMA_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("table_name").label(GenericJdbcLabels.TARGET_TABLE_NAME_LABEL.format())
                .description(GenericJdbcLabels.TARGET_TABLE_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(true));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("write_mode").label(GenericJdbcLabels.TARGET_WRITE_MODE_LABEL.format())
                .description(GenericJdbcLabels.TARGET_WRITE_MODE_DESCRIPTION.format()).type(TypeEnum.ENUM).required(false).defaultValue("insert")
                .addValuesItem(new DatasourceTypePropertyValues().value("insert").label(GenericJdbcLabels.TARGET_WRITE_MODE_VALUE_INSERT_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("update").label(GenericJdbcLabels.TARGET_WRITE_MODE_VALUE_UPDATE_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("update_statement").label(GenericJdbcLabels.TARGET_WRITE_MODE_VALUE_UPDATE_STATEMENT_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("static_statement").label(GenericJdbcLabels.TARGET_WRITE_MODE_VALUE_STATIC_STATEMENT_LABEL.format())));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("key_column_names").label(GenericJdbcLabels.TARGET_KEY_COLUMN_NAMES_LABEL.format())
                .description(GenericJdbcLabels.TARGET_KEY_COLUMN_NAMES_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("update_statement").label(GenericJdbcLabels.TARGET_UPDATE_STATEMENT_LABEL.format())
                .description(GenericJdbcLabels.TARGET_UPDATE_STATEMENT_DESCRIPTION.format()).type(TypeEnum.STRING)
                .required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("table_action").label(GenericJdbcLabels.TARGET_TABLE_ACTION_LABEL.format())
                .description(GenericJdbcLabels.TARGET_TABLE_ACTION_DESCRIPTION.format()).type(TypeEnum.ENUM).required(false)
                .defaultValue("append").addValuesItem(new DatasourceTypePropertyValues().value("append").label(GenericJdbcLabels.TARGET_TABLE_ACTION_VALUE_APPEND_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("replace").label(GenericJdbcLabels.TARGET_TABLE_ACTION_VALUE_REPLACE_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("truncate").label(GenericJdbcLabels.TARGET_TABLE_ACTION_VALUE_TRUNCATE_LABEL.format())));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("create_statement").label(GenericJdbcLabels.TARGET_CREATE_STATEMENT_LABEL.format())
                .description(GenericJdbcLabels.TARGET_CREATE_STATEMENT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));

        // Define the filter properties.
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("include_system").label(GenericJdbcLabels.FILTER_INCLUDE_SYSTEM_LABEL.format())
                .description(GenericJdbcLabels.FILTER_INCLUDE_SYSTEM_DESCRIPTION.format()).type(TypeEnum.BOOLEAN).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("include_table").label(GenericJdbcLabels.FILTER_INCLUDE_TABLE_LABEL.format())
                .description(GenericJdbcLabels.FILTER_INCLUDE_TABLE_DESCRIPTION.format()).type(TypeEnum.BOOLEAN).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("include_view").label(GenericJdbcLabels.FILTER_INCLUDE_VIEW_LABEL.format())
                .description(GenericJdbcLabels.FILTER_INCLUDE_VIEW_DESCRIPTION.format()).type(TypeEnum.BOOLEAN).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("name_pattern").label(GenericJdbcLabels.FILTER_NAME_PATTERN_LABEL.format())
                .description(GenericJdbcLabels.FILTER_NAME_PATTERN_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("primary_key").label(GenericJdbcLabels.FILTER_PRIMARY_KEY_LABEL.format())
                .description(GenericJdbcLabels.FILTER_PRIMARY_KEY_DESCRIPTION.format()).type(TypeEnum.BOOLEAN).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("schema_name_pattern").label(GenericJdbcLabels.FILTER_SCHEMA_NAME_PATTERN_LABEL.format())
                .description(GenericJdbcLabels.FILTER_SCHEMA_NAME_PATTERN_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));

        // Define which filters can be applied at the top level.
        final DatasourceTypeDiscovery discovery = new DatasourceTypeDiscovery();
        setDiscovery(discovery);
        discovery.addTopLevelFiltersItem("include_system");
        discovery.addTopLevelFiltersItem("name_pattern");

        // Define the asset types that can be discovered and the filters they support
        // for discovering the next level.
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("schema").label(GenericJdbcLabels.ASSET_TYPE_SCHEMA_LABEL.format()).addNextLevelFiltersItem("include_system")
                .addNextLevelFiltersItem("include_table").addNextLevelFiltersItem("include_view").addNextLevelFiltersItem("name_pattern"));
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("table").label(GenericJdbcLabels.ASSET_TYPE_TABLE_LABEL.format()).addNextLevelFiltersItem("include_system")
                .addNextLevelFiltersItem("include_table").addNextLevelFiltersItem("include_view").addNextLevelFiltersItem("name_pattern")
                .addNextLevelFiltersItem("primary_key").addNextLevelFiltersItem("schema_name_pattern"));
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("primary_key").label(GenericJdbcLabels.ASSET_TYPE_PRIMARY_KEY_LABEL.format()));

        // Define which properties form the asset path.
        discovery.addPathPropertiesItem(new DiscoveryPathProperty().propertyName("schema_name")
                .addSegmentsItem(new DiscoveryPathSegment().assetTypes("schema").repeatable(false)));
        discovery.addPathPropertiesItem(new DiscoveryPathProperty().propertyName("table_name")
                .addSegmentsItem(new DiscoveryPathSegment().assetTypes("table").repeatable(false)));

        // Define custom actions.
        final CustomDatasourceTypeActionProperties actionProperties = new CustomDatasourceTypeActionProperties();
        final CustomDatasourceTypeAction action = new CustomDatasourceTypeAction().name("get_record_count")
                .description(GenericJdbcLabels.ACTION_GET_RECORD_COUNT_DESCRIPTION.format()).properties(actionProperties);
        actionProperties.addInputItem(new CustomDatasourceTypeProperty().name("schema_name").label(GenericJdbcLabels.ACTION_GET_RECORD_COUNT_INPUT_SCHEMA_NAME_LABEL.format())
                .description(GenericJdbcLabels.ACTION_GET_RECORD_COUNT_INPUT_SCHEMA_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        actionProperties.addInputItem(new CustomDatasourceTypeProperty().name("table_name").label(GenericJdbcLabels.ACTION_GET_RECORD_COUNT_INPUT_TABLE_NAME_LABEL.format())
                .description(GenericJdbcLabels.ACTION_GET_RECORD_COUNT_INPUT_TABLE_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(true));
        actionProperties.addOutputItem(new CustomDatasourceTypeProperty().name("record_count").label(GenericJdbcLabels.ACTION_GET_RECORD_COUNT_OUTPUT_RECORD_COUNT_LABEL.format())
                .description(GenericJdbcLabels.ACTION_GET_RECORD_COUNT_OUTPUT_RECORD_COUNT_DESCRIPTION.format()).type(TypeEnum.INTEGER).required(true));
        addActionsItem(action);
    }

}
