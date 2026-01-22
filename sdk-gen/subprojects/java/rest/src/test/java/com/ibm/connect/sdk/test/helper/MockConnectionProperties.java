/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test.helper;

import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_AUTH_TYPE;
import static com.ibm.connect.sdk.rest.utils.RestConnectionProperties.PROPERTY_PREDEFINED_REST_CONFIG_IDENTIFIER;

import java.util.Properties;

import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.ConnectionProperties;

public class MockConnectionProperties {

    public static ConnectionProperties createWith(Properties... properties) {
        return getConnectionProperties(properties);
    }

    public static ConnectionProperties getConnectionProperties(Properties... properties) {
        final ConnectionProperties connectionProperties =  new ConnectionProperties();
        connectionProperties.put(PROPERTY_PREDEFINED_REST_CONFIG_IDENTIFIER, "custom_model");
        connectionProperties.put(PROPERTY_AUTH_TYPE, "none");
        if(properties != null) {
            for (final Properties prop : properties) {
                if (prop != null && !prop.isEmpty()) {
                    prop.forEach((key, value) -> {
                        connectionProperties.put(String.valueOf(key), value);
                    });

                }
            }
        }
        return connectionProperties;
    }

    public static Properties getProperties() {
        return ModelMapper.toProperties(getConnectionProperties());
    }
}
