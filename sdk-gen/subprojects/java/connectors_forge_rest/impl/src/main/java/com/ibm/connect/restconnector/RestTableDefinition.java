/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.Collections;
import java.util.List;

/**
 * Represents a table (endpoint) definition parsed from a JSON mapping file.
 * Holds the API path, optional data path for nested responses, optional request body
 * (for POST-based APIs such as GraphQL), optional pagination configuration,
 * and the list of field definitions.
 */
public class RestTableDefinition
{
    private final String path;
    private final String dataPath;
    private final String requestBody;
    private final PaginationConfig paginationConfig;
    private final List<RestFieldDefinition> fields;

    public RestTableDefinition(String path, List<RestFieldDefinition> fields)
    { this(path, null, null, null, fields); }

    public RestTableDefinition(String path, String dataPath, List<RestFieldDefinition> fields)
    { this(path, dataPath, null, null, fields); }

    public RestTableDefinition(String path, String dataPath, PaginationConfig paginationConfig,
            List<RestFieldDefinition> fields)
    { this(path, dataPath, null, paginationConfig, fields); }

    public RestTableDefinition(String path, String dataPath, String requestBody,
            PaginationConfig paginationConfig, List<RestFieldDefinition> fields)
    {
        this.path = path;
        this.dataPath = dataPath;
        this.requestBody = requestBody;
        this.paginationConfig = paginationConfig;
        this.fields = Collections.unmodifiableList(fields);
    }

    public String getPath() { return path; }
    public String getDataPath() { return dataPath; }
    /** Returns the JSON request body for POST requests, or {@code null} for GET requests. */
    public String getRequestBody() { return requestBody; }
    public PaginationConfig getPaginationConfig() { return paginationConfig; }
    public List<RestFieldDefinition> getFields() { return fields; }

    @Override
    public String toString()
    {
        return "RestTableDefinition{path='" + path + "', dataPath='" + dataPath
                + "', requestBody=" + (requestBody != null ? "'<body>'" : "null")
                + ", paginationConfig=" + paginationConfig + ", fields=" + fields + "}";
    }
}
