/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.validator;


import static com.ibm.connect.sdk.rest.RestMsgs.INVALID_CONNECTION_PROPERTIES;

import java.net.MalformedURLException;
import java.net.URL;

import com.ibm.connect.sdk.rest.utils.RestApiConstants.SupportedOAuth2GrantType;
import com.ibm.connect.sdk.rest.utils.RestConnectionProperties;
import com.ibm.connect.sdk.rest.utils.RestUtils;

@SuppressWarnings("PMD")
public class RestApiConnectorValidator {

    private RestApiConnectorValidator() {}

    public static void validate(RestConnectionProperties properties) {
       validateBaseUrl(properties.getBasicUrl());
       if (properties.isAuthenticationBasic()) {
           properties.getUsername(); // Getter method ensure its value is present
           properties.getPassword(); // Getter method ensure its value is present
       } else if (properties.isAuthenticationOAuth2()) {
           properties.getOAuth2TokenUrl(); // Getter method ensures its value is present
           properties.getOAuth2ClientId();
           properties.getOAuth2ClientSecret();
           
           // Validate refresh_token grant type requirements
           SupportedOAuth2GrantType grantType = properties.getOAuth2GrantType();
           if (SupportedOAuth2GrantType.REFRESH_TOKEN.equals(grantType)) {
               String refreshToken = properties.getOAuth2RefreshToken();
               if (RestUtils.isNullOrEmpty(refreshToken)) {
                   throw new IllegalArgumentException(
                       INVALID_CONNECTION_PROPERTIES.format(RestConnectionProperties.PROPERTY_OAUTH2_REFRESH_TOKEN +
                       " is required when using 'refresh_token' grant type"));
               }
           }
       }
        validateModelConfigYaml(properties);
    }


    private static void validateModelConfigYaml(RestConnectionProperties properties) {
        properties.getPredefinedRestConfigIdentifier(); //Getter method ensure its value is present

        if(properties.isCustomRestModel()) {
            RestYamlValidator.validateSchema(properties.getCustomRestConfigYaml());
            RestYamlValidator.validateEntityTypeTree(properties.getRestConfiguration());
        }
    }

    private static void validateBaseUrl(String baseUrl) {
        try {
            new URL(baseUrl);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(INVALID_CONNECTION_PROPERTIES.format(RestConnectionProperties.PROPERTY_BASE_URL));
        }
    }
}
