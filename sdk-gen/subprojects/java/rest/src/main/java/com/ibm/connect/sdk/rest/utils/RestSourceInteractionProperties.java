/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.rest.utils;

import static com.ibm.connect.sdk.rest.RestMsgs.INVALID_SELECTED_ENTITY_PARENT_VALUES;

import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.core.JsonProcessingException;

public class RestSourceInteractionProperties extends PropertiesHelper {
    // Source interaction properties key
    public static final String PROPERTIES_SELECTED_PARENT_ENTITY_VALUES = "selected_parent_entity_values";

    public RestSourceInteractionProperties(Properties... properties)
    {
        super(properties);
    }

    public Map<String, String> getSelectedParentEntityValues()
    {
        try {
            return ObjectMapperUtils.toStringMap(this.getProperty(PROPERTIES_SELECTED_PARENT_ENTITY_VALUES));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(INVALID_SELECTED_ENTITY_PARENT_VALUES.format(PROPERTIES_SELECTED_PARENT_ENTITY_VALUES), ex);
        }
    }
}
