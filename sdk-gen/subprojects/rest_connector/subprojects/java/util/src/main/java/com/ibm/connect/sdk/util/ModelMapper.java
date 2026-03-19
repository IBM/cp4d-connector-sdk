/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.util;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * A mapper for converting between model objects and bytes.
 */
public class ModelMapper
{

    private final ObjectMapper mapper;

    /**
     * Creates a mapper for converting between model objects and bytes.
     */
    public ModelMapper()
    {
        mapper = new ObjectMapper().configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Converts a model object to a JSON string.
     *
     * @param object
     *            a model object
     * @return the model object as a JSON string
     * @throws Exception
     */
    public String toJsonString(Object object) throws Exception
    {
        return mapper.writeValueAsString(object);
    }

    /**
     * Converts a model object to a JSON object.
     *
     * @param object
     *            a model object
     * @return the model object as a JSON object
     * @throws Exception
     */
    public JsonObject toJsonObject(Object object) throws Exception
    {
        return new Gson().fromJson(toJsonString(object), JsonObject.class);
    }

    /**
     * Converts a model object to bytes.
     *
     * @param object
     *            a model object
     * @return the model object as bytes
     * @throws Exception
     */
    public byte[] toBytes(Object object) throws Exception
    {
        final String json = mapper.writeValueAsString(object);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Converts bytes to a model object.
     *
     * @param bytes
     *            the bytes to convert
     * @param modelClass
     *            the class of the model object
     * @return the model object
     * @throws Exception
     */
    public <T> T fromBytes(byte[] bytes, Class<T> modelClass) throws Exception
    {
        final String json = new String(bytes, StandardCharsets.UTF_8);
        return mapper.readValue(json, modelClass);
    }

    /**
     * Converts a model map to a Properties object.
     *
     * @param modelMap
     *            model map
     * @return a Properties object
     */
    public static Properties toProperties(Map<String, Object> modelMap)
    {
        final Properties properties = new Properties();
        if (modelMap != null) {
            for (final Map.Entry<String, Object> entry : modelMap.entrySet()) {
                properties.setProperty(entry.getKey(), entry.getValue().toString());
            }
        }
        return properties;
    }
}
