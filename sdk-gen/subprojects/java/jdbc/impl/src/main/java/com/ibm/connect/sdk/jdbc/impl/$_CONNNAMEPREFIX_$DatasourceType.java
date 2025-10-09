/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

/* *************************************************** */
package com.ibm.connect.sdk.jdbc.impl;

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
 * The definition of a custom data source type.
 */
@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class $_CONNNAMEPREFIX_$DatasourceType extends CustomFlightDatasourceType
{
    /**
     * An instance of the custom data source type.
     */
    public static final $_CONNNAMEPREFIX_$DatasourceType INSTANCE = new $_CONNNAMEPREFIX_$DatasourceType();

    /**
     * The unique identifier name of the data source type.
     */
    public static final String DATASOURCE_TYPE_NAME = "$_CONNNAME_$";
    private static final String DATASOURCE_TYPE_LABEL = "$_CONNLABEL_$";
    private static final String DATASOURCE_TYPE_DESCRIPTION = "$_CONNDESCRIPTION_$";

    /**
     * Defines a custom data source type.
     */
    public $_CONNNAMEPREFIX_$DatasourceType()
    {
        super();

        // Set the data source type attributes.
        setName(DATASOURCE_TYPE_NAME);
        setLabel(DATASOURCE_TYPE_LABEL);
        setDescription(DATASOURCE_TYPE_DESCRIPTION);
        setAllowedAsSource(true);
        setAllowedAsTarget(true);
        setStatus(CustomFlightDatasourceType.StatusEnum.ACTIVE);
        setTags(Collections.emptyList());
        final CustomFlightDatasourceTypeProperties properties = new CustomFlightDatasourceTypeProperties();
        setProperties(properties);

        // Define the connection properties.
        // TODO adjust these properties for your scenario
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("host").label($_CONNNAMEPREFIX_$Labels.CONNECTION_HOST_LABEL.format()).description($_CONNNAMEPREFIX_$Labels.CONNECTION_HOST_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(true).group("domain"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("port").label($_CONNNAMEPREFIX_$Labels.CONNECTION_PORT_LABEL.format()).description($_CONNNAMEPREFIX_$Labels.CONNECTION_PORT_DESCRIPTION.format())
                .type(TypeEnum.INTEGER).required(true).group("domain"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("database").label($_CONNNAMEPREFIX_$Labels.CONNECTION_DATABASE_LABEL.format()).description($_CONNNAMEPREFIX_$Labels.CONNECTION_DATABASE_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(true));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("username").label($_CONNNAMEPREFIX_$Labels.CONNECTION_USERNAME_LABEL.format()).description($_CONNNAMEPREFIX_$Labels.CONNECTION_USERNAME_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(true).group("credentials"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("password").label($_CONNNAMEPREFIX_$Labels.CONNECTION_PASSWORD_LABEL.format()).description($_CONNNAMEPREFIX_$Labels.CONNECTION_PASSWORD_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(true).masked(true).group("credentials"));

        // Define the source interaction properties.
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("schema_name").label($_CONNNAMEPREFIX_$Labels.SOURCE_SCHEMA_NAME_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.SOURCE_SCHEMA_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("table_name").label($_CONNNAMEPREFIX_$Labels.SOURCE_TABLE_NAME_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.SOURCE_TABLE_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("row_limit").label($_CONNNAMEPREFIX_$Labels.SOURCE_ROW_LIMIT_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.SOURCE_ROW_LIMIT_DESCRIPTION.format()).type(TypeEnum.INTEGER).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("byte_limit").label($_CONNNAMEPREFIX_$Labels.SOURCE_BYTE_LIMIT_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.SOURCE_BYTE_LIMIT_DESCRIPTION.format()).type(TypeEnum.STRING)
                .required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("select_statement").label($_CONNNAMEPREFIX_$Labels.SOURCE_SELECT_STATEMENT_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.SOURCE_SELECT_STATEMENT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));

        // Define the target interaction properties.
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("schema_name").label($_CONNNAMEPREFIX_$Labels.TARGET_SCHEMA_NAME_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.TARGET_SCHEMA_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("table_name").label($_CONNNAMEPREFIX_$Labels.TARGET_TABLE_NAME_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.TARGET_TABLE_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(true));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("write_mode").label($_CONNNAMEPREFIX_$Labels.TARGET_WRITE_MODE_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.TARGET_WRITE_MODE_DESCRIPTION.format()).type(TypeEnum.ENUM).required(false).defaultValue("insert")
                .addValuesItem(new DatasourceTypePropertyValues().value("insert").label($_CONNNAMEPREFIX_$Labels.TARGET_WRITE_MODE_VALUE_INSERT_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("update").label($_CONNNAMEPREFIX_$Labels.TARGET_WRITE_MODE_VALUE_UPDATE_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("update_statement").label($_CONNNAMEPREFIX_$Labels.TARGET_WRITE_MODE_VALUE_UPDATE_STATEMENT_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("static_statement").label($_CONNNAMEPREFIX_$Labels.TARGET_WRITE_MODE_VALUE_STATIC_STATEMENT_LABEL.format())));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("key_column_names").label($_CONNNAMEPREFIX_$Labels.TARGET_KEY_COLUMN_NAMES_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.TARGET_KEY_COLUMN_NAMES_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("update_statement").label($_CONNNAMEPREFIX_$Labels.TARGET_UPDATE_STATEMENT_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.TARGET_UPDATE_STATEMENT_DESCRIPTION.format()).type(TypeEnum.STRING)
                .required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("table_action").label($_CONNNAMEPREFIX_$Labels.TARGET_TABLE_ACTION_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.TARGET_TABLE_ACTION_DESCRIPTION.format()).type(TypeEnum.ENUM).required(false)
                .defaultValue("append").addValuesItem(new DatasourceTypePropertyValues().value("append").label($_CONNNAMEPREFIX_$Labels.TARGET_TABLE_ACTION_VALUE_APPEND_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("replace").label($_CONNNAMEPREFIX_$Labels.TARGET_TABLE_ACTION_VALUE_REPLACE_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("truncate").label($_CONNNAMEPREFIX_$Labels.TARGET_TABLE_ACTION_VALUE_TRUNCATE_LABEL.format())));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("create_statement").label($_CONNNAMEPREFIX_$Labels.TARGET_CREATE_STATEMENT_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.TARGET_CREATE_STATEMENT_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));

        // Define the filter properties.
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("include_system").label($_CONNNAMEPREFIX_$Labels.FILTER_INCLUDE_SYSTEM_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.FILTER_INCLUDE_SYSTEM_DESCRIPTION.format()).type(TypeEnum.BOOLEAN).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("include_table").label($_CONNNAMEPREFIX_$Labels.FILTER_INCLUDE_TABLE_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.FILTER_INCLUDE_TABLE_DESCRIPTION.format()).type(TypeEnum.BOOLEAN).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("include_view").label($_CONNNAMEPREFIX_$Labels.FILTER_INCLUDE_VIEW_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.FILTER_INCLUDE_VIEW_DESCRIPTION.format()).type(TypeEnum.BOOLEAN).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("name_pattern").label($_CONNNAMEPREFIX_$Labels.FILTER_NAME_PATTERN_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.FILTER_NAME_PATTERN_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("primary_key").label($_CONNNAMEPREFIX_$Labels.FILTER_PRIMARY_KEY_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.FILTER_PRIMARY_KEY_DESCRIPTION.format()).type(TypeEnum.BOOLEAN).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("schema_name_pattern").label($_CONNNAMEPREFIX_$Labels.FILTER_SCHEMA_NAME_PATTERN_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.FILTER_SCHEMA_NAME_PATTERN_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));

        // Define which filters can be applied at the top level.
        final DatasourceTypeDiscovery discovery = new DatasourceTypeDiscovery();
        setDiscovery(discovery);
        discovery.addTopLevelFiltersItem("include_system");
        discovery.addTopLevelFiltersItem("name_pattern");

        // Define the asset types that can be discovered and the filters they support
        // for discovering the next level.
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("schema").label($_CONNNAMEPREFIX_$Labels.ASSET_TYPE_SCHEMA_LABEL.format()).addNextLevelFiltersItem("include_system")
                .addNextLevelFiltersItem("include_table").addNextLevelFiltersItem("include_view").addNextLevelFiltersItem("name_pattern"));
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("table").label($_CONNNAMEPREFIX_$Labels.ASSET_TYPE_TABLE_LABEL.format()).addNextLevelFiltersItem("include_system")
                .addNextLevelFiltersItem("include_table").addNextLevelFiltersItem("include_view").addNextLevelFiltersItem("name_pattern")
                .addNextLevelFiltersItem("primary_key").addNextLevelFiltersItem("schema_name_pattern"));
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("primary_key").label($_CONNNAMEPREFIX_$Labels.ASSET_TYPE_PRIMARY_KEY_LABEL.format()));

        // Define which properties form the asset path.
        discovery.addPathPropertiesItem(new DiscoveryPathProperty().propertyName("schema_name")
                .addSegmentsItem(new DiscoveryPathSegment().assetTypes("schema").repeatable(false)));
        discovery.addPathPropertiesItem(new DiscoveryPathProperty().propertyName("table_name")
                .addSegmentsItem(new DiscoveryPathSegment().assetTypes("table").repeatable(false)));

        // Define custom actions.
        final CustomDatasourceTypeActionProperties actionProperties = new CustomDatasourceTypeActionProperties();
        final CustomDatasourceTypeAction action = new CustomDatasourceTypeAction().name("get_record_count")
                .description($_CONNNAMEPREFIX_$Labels.ACTION_GET_RECORD_COUNT_DESCRIPTION.format()).properties(actionProperties);
        actionProperties.addInputItem(new CustomDatasourceTypeProperty().name("schema_name").label($_CONNNAMEPREFIX_$Labels.ACTION_GET_RECORD_COUNT_INPUT_SCHEMA_NAME_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.ACTION_GET_RECORD_COUNT_INPUT_SCHEMA_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(false));
        actionProperties.addInputItem(new CustomDatasourceTypeProperty().name("table_name").label($_CONNNAMEPREFIX_$Labels.ACTION_GET_RECORD_COUNT_INPUT_TABLE_NAME_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.ACTION_GET_RECORD_COUNT_INPUT_TABLE_NAME_DESCRIPTION.format()).type(TypeEnum.STRING).required(true));
        actionProperties.addOutputItem(new CustomDatasourceTypeProperty().name("record_count").label($_CONNNAMEPREFIX_$Labels.ACTION_GET_RECORD_COUNT_OUTPUT_RECORD_COUNT_LABEL.format())
                .description($_CONNNAMEPREFIX_$Labels.ACTION_GET_RECORD_COUNT_OUTPUT_RECORD_COUNT_DESCRIPTION.format()).type(TypeEnum.INTEGER).required(true));
        addActionsItem(action);
    }
}
