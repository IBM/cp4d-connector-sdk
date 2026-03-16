/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.utils;

import static com.ibm.connect.sdk.rest.utils.RestUtils.isNullOrEmpty;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import com.ibm.connect.sdk.rest.RestMsgs;

public class PropertiesHelper {
    private final Properties properties;

    public PropertiesHelper()
    {
        properties = new Properties();
    }

    /**
     * Convert all prop key into lower case.
     *
     * @param props
     */
    public PropertiesHelper(Properties... props)
    {
        this();
        Arrays.stream(props)
                .filter(Objects::nonNull)
                .forEach(propsItem -> properties.putAll(propsItem.stringPropertyNames().stream()
                        .collect(Collectors.toMap(
                                String::toLowerCase,
                                propsItem::getProperty
                        ))));
    }

    /**
     * Get the value of an optional property
     *
     * @param name
     *          the property name
     * @return  @return the value of the property, or null if the property does not exist
     */
    public String getProperty(String name)
    {
        return findProperty(name);
    }

    public String getRequiredProperty(String name)
    {
        final String value = findProperty(name);
        if (isNullOrEmpty(value)) {
            throw new IllegalArgumentException(RestMsgs.MISSING_REQUIRED_PROPERTY.format(name));
        }
        return value;
    }

    protected String findProperty(String name)
    {
        if (name == null) {
            return null;
        }
        return properties.getProperty(name.toLowerCase(Locale.ENGLISH));
    }

}
