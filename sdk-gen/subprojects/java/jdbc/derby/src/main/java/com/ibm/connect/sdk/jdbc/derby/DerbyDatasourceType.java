/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.jdbc.derby;

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
 * The definition of a custom Apache Derby data source type.
 */
public class DerbyDatasourceType extends CustomFlightDatasourceType
{
    /**
     * An instance of the custom Apache Derby data source type.
     */
    public static final DerbyDatasourceType INSTANCE = new DerbyDatasourceType();

    /**
     * The unique identifier name of the data source type.
     */
    public static final String DATASOURCE_TYPE_NAME = "custom_derby";

    private static final String DATASOURCE_TYPE_LABEL = "Apache Derby (custom)";
    private static final String DATASOURCE_TYPE_DESCRIPTION = "A custom connection type for Apache Derby";

    /**
     * Defines a custom data source type for Apache Derby.
     */
    private DerbyDatasourceType()
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
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("host").label("Host").description("Name of server")
                .type(TypeEnum.STRING).required(true).group("domain"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("port").label("Port").description("Port number")
                .type(TypeEnum.INTEGER).required(true).group("domain"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("database").label("Database").description("Name of database")
                .type(TypeEnum.STRING).required(true));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("username").label("User name").description("User name")
                .type(TypeEnum.STRING).required(true).group("credentials"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("password").label("Password").description("Password")
                .type(TypeEnum.STRING).required(true).masked(true).group("credentials"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("ssl").label("Port is SSL-enabled")
                .description("The port is configured to accept SSL connections").type(TypeEnum.BOOLEAN).required(false)
                .defaultValue("false").group("ssl"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name("create_database").label("Create database")
                .description("Whether database should be created").type(TypeEnum.BOOLEAN).required(false).defaultValue("false")
                .group("other"));

        // Define the source interaction properties.
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("schema_name").label("Schema name")
                .description("The name of the schema that contains the table to read from").type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("table_name").label("Table name")
                .description("The name of the table to read from").type(TypeEnum.STRING).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("row_limit").label("Row limit")
                .description("The maximum number of rows to return").type(TypeEnum.INTEGER).required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("byte_limit").label("Byte limit")
                .description("The maximum number of bytes to return. Use any of these suffixes; KB, MB, GB, or TB").type(TypeEnum.STRING)
                .required(false));
        properties.addSourceItem(new CustomDatasourceTypeProperty().name("select_statement").label("Select statement")
                .description("The SQL SELECT statement for retrieving data from the table").type(TypeEnum.STRING).required(false));

        // Define the target interaction properties.
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("schema_name").label("Schema name")
                .description("The name of the schema that contains the table to write to").type(TypeEnum.STRING).required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("table_name").label("Table name")
                .description("The name of the table to write to").type(TypeEnum.STRING).required(true));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("write_mode").label("Write mode")
                .description("The mode for writing records to the target table").type(TypeEnum.ENUM).required(false).defaultValue("insert")
                .addValuesItem(new DatasourceTypePropertyValues().value("insert").label("Insert"))
                .addValuesItem(new DatasourceTypePropertyValues().value("update").label("Update"))
                .addValuesItem(new DatasourceTypePropertyValues().value("update_statement").label("Update statement"))
                .addValuesItem(new DatasourceTypePropertyValues().value("static_statement").label("Static statement")));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("key_column_names").label("Key column names")
                .description("A comma separated list of column names to override the primary key used during an update or merge")
                .type(TypeEnum.STRING).required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("update_statement").label("Update statement")
                .description("The SQL INSERT, UPDATE, MERGE, or DELETE statement for updating data in the table").type(TypeEnum.STRING)
                .required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("table_action").label("Table action")
                .description("The action to take on the target table to handle the new data set").type(TypeEnum.ENUM).required(false)
                .defaultValue("append").addValuesItem(new DatasourceTypePropertyValues().value("append").label("Append"))
                .addValuesItem(new DatasourceTypePropertyValues().value("replace").label("Replace"))
                .addValuesItem(new DatasourceTypePropertyValues().value("truncate").label("Truncate")));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("create_statement").label("Create statement")
                .description("The Create DDL statement for creating the target table").type(TypeEnum.STRING).required(false));
        properties.addTargetItem(new CustomDatasourceTypeProperty().name("update_statistics").label("Update statistics")
                .description("Whether to update table statistics after writing").type(TypeEnum.BOOLEAN).required(false)
                .defaultValue("false"));

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
