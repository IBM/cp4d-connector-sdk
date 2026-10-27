/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.Collections;
import java.util.List;

/**
 * Represents a table (endpoint) definition parsed from a JSON mapping file.
 * Holds the API path, optional data path for nested responses, optional pagination configuration,
 * an ordered list of path-key definitions for foreign-key-style dynamic paths, and the list of
 * field definitions.
 */
public class RestTableDefinition
{
    private final String path;
    private final String dataPath;
    private final PaginationConfig paginationConfig;
    private final List<PathKeyDef> pathKeys;
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
     * @param pathKeys
     *            ordered list of path-key definitions for tables whose path contains variables
     *            whose values are fetched from other endpoints at discovery time; {@code null}
     *            or empty for ordinary tables. Order matters: each entry's {@code source_path}
     *            may reference {@code $variable} placeholders resolved by earlier entries.
     * @param fields
     *            the ordered list of field definitions for this table
     */
    public RestTableDefinition(String path, String dataPath, PaginationConfig paginationConfig,
            List<PathKeyDef> pathKeys, List<RestFieldDefinition> fields)
    {
        this.path             = path;
        this.dataPath         = dataPath;
        this.paginationConfig = paginationConfig;
        this.pathKeys         = (pathKeys != null && !pathKeys.isEmpty())
                ? Collections.unmodifiableList(pathKeys)
                : Collections.emptyList();
        this.fields           = Collections.unmodifiableList(fields);
    }

    public String getPath()                        { return path;             }
    public String getDataPath()                    { return dataPath;         }
    public PaginationConfig getPaginationConfig()  { return paginationConfig; }

    /**
     * Returns the ordered list of path-key definitions, or an empty list if this table
     * has no dynamic path variables.
     */
    public List<PathKeyDef> getPathKeys()          { return pathKeys;         }

    /** Returns {@code true} if this table has at least one path-key definition. */
    public boolean hasPathKeys()                   { return !pathKeys.isEmpty(); }

    public List<RestFieldDefinition> getFields()   { return fields;           }

    @Override
    public String toString()
    {
        return "RestTableDefinition{path='" + path + "', dataPath='" + dataPath
                + "', paginationConfig=" + paginationConfig
                + ", pathKeys=" + pathKeys
                + ", fields=" + fields + "}";
    }
}
