/* *************************************************** */

/* (C) Copyright IBM Corp. 2025                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.Collections;
import java.util.List;

/**
 * Represents a table (endpoint) definition parsed from a .rest mapping file.
 * Holds the API path, optional data path for nested responses, and the list of field definitions.
 */
public class RestTableDefinition
{
    private final String path;
    private final String dataPath;
    private final List<RestFieldDefinition> fields;

    /**
     * Creates a table definition.
     *
     * @param path
     *            the URL path to append to the base URL (e.g. "/v4/rockets")
     * @param fields
     *            the list of field definitions for this table
     */
    public RestTableDefinition(String path, List<RestFieldDefinition> fields)
    {
        this(path, null, fields);
    }

    /**
     * Creates a table definition with an optional data path for nested responses.
     *
     * @param path
     *            the URL path to append to the base URL (e.g. "/v4/rockets")
     * @param dataPath
     *            optional JSON path to the data array (e.g. "result" for {"result": [...]})
     * @param fields
     *            the list of field definitions for this table
     */
    public RestTableDefinition(String path, String dataPath, List<RestFieldDefinition> fields)
    {
        this.path = path;
        this.dataPath = dataPath;
        this.fields = Collections.unmodifiableList(fields);
    }

    /**
     * Returns the URL path for this table's endpoint.
     *
     * @return the URL path (e.g. "/v4/rockets")
     */
    public String getPath()
    {
        return path;
    }

    /**
     * Returns the JSON path to the data array in nested responses.
     *
     * @return the data path (e.g. "result"), or null if data is at root level
     */
    public String getDataPath()
    {
        return dataPath;
    }

    /**
     * Returns the list of field definitions for this table.
     *
     * @return an unmodifiable list of field definitions
     */
    public List<RestFieldDefinition> getFields()
    {
        return fields;
    }

    @Override
    public String toString()
    {
        return "RestTableDefinition{path='" + path + "', dataPath='" + dataPath + "', fields=" + fields + "}";
    }
}

// Made with Bob
