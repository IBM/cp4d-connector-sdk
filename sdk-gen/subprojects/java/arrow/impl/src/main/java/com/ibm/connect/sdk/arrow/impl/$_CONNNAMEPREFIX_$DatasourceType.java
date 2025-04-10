/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

/* *************************************************** */
package com.ibm.connect.sdk.arrow.impl;

import java.util.Collections;

import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeAction;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeActionProperties;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty.TypeEnum;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypeProperties;
import com.ibm.wdp.connect.common.sdk.api.models.DatasourceTypeDiscovery;
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
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("host").label("Host").description("Name of server")
                .type(TypeEnum.STRING).required(true).group("domain"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("port").label("Port").description("Port number")
                .type(TypeEnum.INTEGER).required(true).group("domain"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("username").label("User name").description("User name")
                .type(TypeEnum.STRING).required(true).group("credentials"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("password").label("Password").description("Password")
                .type(TypeEnum.STRING).required(true).masked(true).group("credentials"));

        // Define the source interaction properties.
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("schema_name").label("Schema name")
                .description("The name of the schema that contains the table to read from").type(TypeEnum.STRING).required(true));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("table_name").label("Table name")
                .description("The name of the table to read from").type(TypeEnum.STRING).required(true));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("row_limit").label("Row limit")
                .description("The maximum number of rows to return").type(TypeEnum.INTEGER).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("byte_limit").label("Byte limit")
                .description("The maximum number of bytes to return. Use any of these suffixes; KB, MB, GB, or TB").type(TypeEnum.STRING)
                .required(false));

        // Define the target interaction properties.
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("schema_name").label("Schema name")
                .description("The name of the schema that contains the table to write to").type(TypeEnum.STRING).required(true));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("table_name").label("Table name")
                .description("The name of the table to write to").type(TypeEnum.STRING).required(true));

        // Define the filter properties.
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("include_system").label("Include system")
                .description("Whether to include system objects").type(TypeEnum.BOOLEAN).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("include_table").label("Include tables")
                .description("Whether to include tables").type(TypeEnum.BOOLEAN).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("include_view").label("Include views")
                .description("Whether to include views").type(TypeEnum.BOOLEAN).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("name_pattern").label("Name pattern")
                .description("A name pattern to filter on").type(TypeEnum.STRING).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("primary_key").label("Include primary key list")
                .description("Whether to include a list of primary keys").type(TypeEnum.BOOLEAN).required(false));
        properties.addFilterItem(new CustomDatasourceTypeProperty().name("schema_name_pattern").label("Schema name pattern")
                .description("A name pattern for schema filtering").type(TypeEnum.STRING).required(false));

        // Define which filters can be applied at the top level.
        final DatasourceTypeDiscovery discovery = new DatasourceTypeDiscovery();
        setDiscovery(discovery);
        discovery.addTopLevelFiltersItem("include_system");
        discovery.addTopLevelFiltersItem("name_pattern");

        // Define the asset types that can be discovered and the filters they support
        // for discovering the next level.
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("schema").label("Schema").addNextLevelFiltersItem("include_system")
                .addNextLevelFiltersItem("include_table").addNextLevelFiltersItem("include_view").addNextLevelFiltersItem("name_pattern"));
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("table").label("Table").addNextLevelFiltersItem("include_system")
                .addNextLevelFiltersItem("include_table").addNextLevelFiltersItem("include_view").addNextLevelFiltersItem("name_pattern")
                .addNextLevelFiltersItem("primary_key").addNextLevelFiltersItem("schema_name_pattern"));
        discovery.addAssetTypesItem(new DiscoveryAssetType().name("primary_key").label("Primary key"));

        // Define which properties form the asset path.
        discovery.addPathPropertiesItem(new DiscoveryPathProperty().propertyName("schema_name")
                .addSegmentsItem(new DiscoveryPathSegment().assetTypes("schema").repeatable(false)));
        discovery.addPathPropertiesItem(new DiscoveryPathProperty().propertyName("table_name")
                .addSegmentsItem(new DiscoveryPathSegment().assetTypes("table").repeatable(false)));

        // Define custom actions.
        final CustomDatasourceTypeActionProperties actionProperties = new CustomDatasourceTypeActionProperties();
        final CustomDatasourceTypeAction action = new CustomDatasourceTypeAction().name("get_record_count")
                .description("Get the number of rows in the specified table").properties(actionProperties);
        actionProperties.addInputItem(new CustomDatasourceTypeProperty().name("schema_name").label("Schema name")
                .description("The name of the schema that contains the table").type(TypeEnum.STRING).required(false));
        actionProperties.addInputItem(new CustomDatasourceTypeProperty().name("table_name").label("Table name")
                .description("Name of the table for which to obtain the number of rows").type(TypeEnum.STRING).required(true));
        actionProperties.addOutputItem(new CustomDatasourceTypeProperty().name("record_count").label("Record count")
                .description("Number of available rows").type(TypeEnum.INTEGER).required(true));
        addActionsItem(action);
    }
}
