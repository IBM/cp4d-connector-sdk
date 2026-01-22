/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest;

import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_AUTH_PASSWORD;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_AUTH_TYPE;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_AUTH_USERNAME;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_BASE_URL;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_CUSTOM_REST_CONFIG_YAML;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_OAUTH2_CLIENT_ID;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_OAUTH2_CLIENT_SECRET;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_OAUTH2_GRANT_TYPE;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_OAUTH2_REFRESH_TOKEN;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_OAUTH2_SCOPE;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_OAUTH2_TOKEN_URL;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_PREDEFINED_REST_CONFIG_IDENTIFIER;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_SSL_CERTIFICATE;

import java.util.Collections;
import java.util.List;

import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeComplexCondition;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeComplexPropertyCondition;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeComplexPropertyConditions;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty;
import com.ibm.wdp.connect.common.sdk.api.models.CustomDatasourceTypeProperty.TypeEnum;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceType;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightDatasourceTypeProperties;
import com.ibm.wdp.connect.common.sdk.api.models.DatasourceTypePropertyValues;

@SuppressWarnings({ "PMD.AvoidDollarSigns", "PMD.ClassNamingConventions" })
public class RestDatasourceType extends CustomFlightDatasourceType
{
    /**
     * An instance of the custom Apache Derby data source type.
     */
    public static final RestDatasourceType INSTANCE = new RestDatasourceType();

    /**
     * The unique identifier name of the data source type.
     */
    public static final String DATASOURCE_TYPE_NAME = "rest";

    public RestDatasourceType()
    {
        super();

        // Set the data source type attributes.
        setName(DATASOURCE_TYPE_NAME);
        setLabel(RestLabels.REST_CONNECTOR_LABEL.format());
        setDescription(RestLabels.REST_CONNECTOR_DESCRIPTION.format());
        setAllowedAsSource(true);
        setAllowedAsTarget(false);
        setStatus(StatusEnum.ACTIVE);
        setTags(Collections.emptyList());
        final CustomFlightDatasourceTypeProperties properties = new CustomFlightDatasourceTypeProperties();
        setProperties(properties);

        // Define the connection properties.
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name(PROPERTY_BASE_URL).label(RestLabels.API_BASE_URL_LABEL.format()).description(RestLabels.API_BASE_URL_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(true).group("domain"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name(PROPERTY_SSL_CERTIFICATE).label(RestLabels.API_SSL_CERTIFICATE_LABEL.format()).description(RestLabels.API_SSL_CERTIFICATE_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(false).multiline(true));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name(PROPERTY_AUTH_TYPE).label(RestLabels.API_AUTH_TYPE_LABEL.format()).description(RestLabels.API_AUTH_TYPE_DESCRIPTION.format())
                .type(TypeEnum.ENUM).required(false).group("credentials").defaultValue("none")
                .addValuesItem(new DatasourceTypePropertyValues().value("none").label(RestLabels.API_AUTH_TYPE_NONE_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("basic").label(RestLabels.API_AUTH_TYPE_BASIC_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("oauth2").label(RestLabels.API_AUTH_TYPE_OAUTH2_LABEL.format())));

        properties.addConnectionItem(new CustomDatasourceTypeProperty().name(PROPERTY_AUTH_USERNAME).label(RestLabels.API_AUTH_USERNAME_LABEL.format()).description(RestLabels.API_AUTH_USERNAME_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(true).group("credentials"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name(PROPERTY_AUTH_PASSWORD).label(RestLabels.API_AUTH_PASSWORD_LABEL.format()).description(RestLabels.API_AUTH_PASSWORD_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(true).group("credentials").masked(true));

        properties.addConnectionItem(new CustomDatasourceTypeProperty().name(PROPERTY_OAUTH2_GRANT_TYPE).label(RestLabels.API_AUTH_OAUTH2_GRANT_TYPE_LABEL.format()).description(RestLabels.API_AUTH_OAUTH2_GRANT_TYPE_DESCRIPTION.format())
                .type(TypeEnum.ENUM).required(true).group("credentials").defaultValue("client_credentials")
                .addValuesItem(new DatasourceTypePropertyValues().value("client_credentials").label(RestLabels.API_AUTH_OAUTH2_GRANT_TYPE_CLIENT_CREDENTIALS_LABEL.format()))
                .addValuesItem(new DatasourceTypePropertyValues().value("refresh_token").label(RestLabels.API_AUTH_OAUTH2_GRANT_TYPE_REFRESH_TOKEN_LABEL.format())));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name(PROPERTY_OAUTH2_TOKEN_URL).label(RestLabels.API_AUTH_OAUTH2_TOKEN_URL_LABEL.format()).description(RestLabels.API_AUTH_OAUTH2_TOKEN_URL_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(true).group("credentials"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name(PROPERTY_OAUTH2_CLIENT_ID).label(RestLabels.API_AUTH_OAUTH2_CLIENT_ID_LABEL.format()).description(RestLabels.API_AUTH_OAUTH2_CLIENT_ID_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(true).group("credentials"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name(PROPERTY_OAUTH2_CLIENT_SECRET).label(RestLabels.API_AUTH_OAUTH2_CLIENT_SECRET_LABEL.format()).description(RestLabels.API_AUTH_OAUTH2_CLIENT_SECRET_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(true).group("credentials").masked(true));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name(PROPERTY_OAUTH2_SCOPE).label(RestLabels.API_AUTH_OAUTH2_SCOPE_LABEL.format()).description(RestLabels.API_AUTH_OAUTH2_SCOPE_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(false).group("credentials"));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name(PROPERTY_OAUTH2_REFRESH_TOKEN).label(RestLabels.API_AUTH_OAUTH2_REFRESH_TOKEN_LABEL.format()).description(RestLabels.API_AUTH_OAUTH2_REFRESH_TOKEN_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(false).group("credentials").masked(true));


        properties.addConnectionItem(new CustomDatasourceTypeProperty().name(PROPERTY_PREDEFINED_REST_CONFIG_IDENTIFIER).label("Rest Model Definition").description("Select the predefined REST model configuration to apply.")
                .type(TypeEnum.ENUM).required(true).group("config").defaultValue("custom_model")
                .addValuesItem(new DatasourceTypePropertyValues().value("custom_model").label("Define custom model"))
                .addValuesItem(new DatasourceTypePropertyValues().value("github_repo_model").label("Github - Repositories"))
                .addValuesItem(new DatasourceTypePropertyValues().value("github_branch_model").label("Github - Branches")));
        properties.addConnectionItem(new CustomDatasourceTypeProperty().name(PROPERTY_CUSTOM_REST_CONFIG_YAML).label(RestLabels.API_REST_CONFIG_YAML_LABEL.format()).description(RestLabels.API_REST_CONFIG_YAML_DESCRIPTION.format())
                .type(TypeEnum.STRING).required(true).group("config").multiline(true));

        //Conditions for basic auth type rendering
        final CustomDatasourceTypeComplexPropertyConditions complexConditions = new CustomDatasourceTypeComplexPropertyConditions();
        final CustomDatasourceTypeComplexCondition authTypeEqualsBasicCondition = new CustomDatasourceTypeComplexCondition()
                .propertyName(PROPERTY_AUTH_TYPE)
                .condition(CustomDatasourceTypeComplexCondition.ConditionEnum.EQUALS)
                .values(List.of("basic")).uiOnly(false);
        complexConditions.addConnectionItem(new CustomDatasourceTypeComplexPropertyCondition().propertyName(PROPERTY_AUTH_USERNAME)
                .evaluate(authTypeEqualsBasicCondition));
        complexConditions.addConnectionItem(new CustomDatasourceTypeComplexPropertyCondition().propertyName(PROPERTY_AUTH_PASSWORD)
                .evaluate(authTypeEqualsBasicCondition));

        //Conditions for Oauth2 auth type rendering
        final CustomDatasourceTypeComplexCondition authTypeEqualsOauth2Condition = new CustomDatasourceTypeComplexCondition()
                .propertyName(PROPERTY_AUTH_TYPE)
                .condition(CustomDatasourceTypeComplexCondition.ConditionEnum.EQUALS)
                .values(List.of("oauth2")).uiOnly(false);
        complexConditions.addConnectionItem(new CustomDatasourceTypeComplexPropertyCondition().propertyName(PROPERTY_OAUTH2_GRANT_TYPE)
                .evaluate(authTypeEqualsOauth2Condition));
        complexConditions.addConnectionItem(new CustomDatasourceTypeComplexPropertyCondition().propertyName(PROPERTY_OAUTH2_TOKEN_URL)
                .evaluate(authTypeEqualsOauth2Condition));
        complexConditions.addConnectionItem(new CustomDatasourceTypeComplexPropertyCondition().propertyName(PROPERTY_OAUTH2_CLIENT_ID)
                .evaluate(authTypeEqualsOauth2Condition));
        complexConditions.addConnectionItem(new CustomDatasourceTypeComplexPropertyCondition().propertyName(PROPERTY_OAUTH2_CLIENT_SECRET)
                .evaluate(authTypeEqualsOauth2Condition));
        complexConditions.addConnectionItem(new CustomDatasourceTypeComplexPropertyCondition().propertyName(PROPERTY_OAUTH2_SCOPE)
                .evaluate(authTypeEqualsOauth2Condition));
        //Conditions for Oauth2 auth type rendering on RefreshToken kind.
        final CustomDatasourceTypeComplexCondition oauth2GrantTypeEqualsRefreshToken = new CustomDatasourceTypeComplexCondition()
                .propertyName(PROPERTY_OAUTH2_GRANT_TYPE)
                .condition(CustomDatasourceTypeComplexCondition.ConditionEnum.EQUALS)
                .values(List.of("refresh_token")).uiOnly(false);
        complexConditions.addConnectionItem(new CustomDatasourceTypeComplexPropertyCondition().propertyName(PROPERTY_OAUTH2_REFRESH_TOKEN)
                .evaluate(oauth2GrantTypeEqualsRefreshToken));

        //Condition for rendering custom rest model text area based on the Rest Model type
        final CustomDatasourceTypeComplexCondition customRestModelCondition = new CustomDatasourceTypeComplexCondition()
                .propertyName(PROPERTY_PREDEFINED_REST_CONFIG_IDENTIFIER)
                .condition(CustomDatasourceTypeComplexCondition.ConditionEnum.EQUALS)
                .values(List.of("custom_model")).uiOnly(false);
        complexConditions.addConnectionItem(new CustomDatasourceTypeComplexPropertyCondition().propertyName(PROPERTY_CUSTOM_REST_CONFIG_YAML)
                .evaluate(customRestModelCondition));

        properties.complexConditions(complexConditions);

        // Define the source interaction properties.*/
    }
}
