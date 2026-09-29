/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.Collections;
import java.util.List;

/**
 * Represents a table (endpoint) definition parsed from a JSON mapping file.
 * Holds the API path, optional data path for nested responses, optional pagination configuration,
 * optional path-key definition for foreign-key-style dynamic paths, and the list of field definitions.
 */
public class RestTableDefinition
{
    private final String path;
    private final String dataPath;
    private final PaginationConfig paginationConfig;
    private final PathKeyDef pathKey;
    private final List<RestFieldDefinition> fields;

    /**
     * Creates a full table definition. Any of the optional parameters may be {@code null}.
     *
     * @param path
     *            the API path for this table (may contain {@code $var} placeholders)
     * @param dataPath
     *            optional dot-notation path to the data array inside the response; {@code null}
     *            if the response is a direct top-level array
     * @param paginationConfig
     *            optional pagination configuration; {@code null} for non-paginated tables
     * @param pathKey
     *            optional path-key definition for tables whose path contains a variable whose
     *            possible values are fetched from another endpoint at discovery time; {@code null}
     *            for ordinary tables
     * @param fields
     *            the ordered list of field definitions for this table
     */
    public RestTableDefinition(String path, String dataPath, PaginationConfig paginationConfig,
            PathKeyDef pathKey, List<RestFieldDefinition> fields)
    {
        this.path             = path;
        this.dataPath         = dataPath;
        this.paginationConfig = paginationConfig;
        this.pathKey          = pathKey;
        this.fields           = Collections.unmodifiableList(fields);
    }

    public String getPath()                        { return path;             }
    public String getDataPath()                    { return dataPath;         }
    public PaginationConfig getPaginationConfig()  { return paginationConfig; }
    /** Returns the path-key definition, or {@code null} if this table has no dynamic path variable. */
    public PathKeyDef getPathKey()                 { return pathKey;          }
    public List<RestFieldDefinition> getFields()   { return fields;           }

    @Override
    public String toString()
    {
        return "RestTableDefinition{path='" + path + "', dataPath='" + dataPath
                + "', paginationConfig=" + paginationConfig
                + ", pathKey=" + pathKey
                + ", fields=" + fields + "}";
    }
}
