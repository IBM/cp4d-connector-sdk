/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

/**
 * Describes a URL path variable that is resolved dynamically by fetching values from another
 * API endpoint at discovery time.
 *
 * <p>This is the DSL representation of a foreign-key-like path variable: the variable appears
 * as a {@code $name} placeholder inside a table's {@code $path}, and its set of possible values
 * is obtained by querying a separate "lookup" endpoint.  During asset discovery the connector
 * fetches that endpoint, extracts the field named {@link #getSourceField()} from each object in
 * the response, and emits one discovered asset per value.  At read time the concrete value chosen
 * during discovery is passed back via the {@code path_key_value} interaction property and
 * substituted into the path before the HTTP request is sent.
 *
 * <p>Example DSL:
 * <pre>
 * "$path_key": {
 *   "variable":         "org_slug",
 *   "source_path":      "/api/0/organizations/",
 *   "source_field":     "slug",
 *   "source_data_path": "data"
 * }
 * </pre>
 *
 * <p>{@code source_data_path} is optional; when absent the lookup response is assumed to be
 * a top-level JSON array of objects.
 */
public class PathKeyDef
{
    private final String variable;
    private final String sourcePath;
    private final String sourceField;
    private final String sourceDataPath; // nullable

    /**
     * Creates a path key definition.
     *
     * @param variable
     *            the {@code $name} placeholder used in the table's {@code $path}
     * @param sourcePath
     *            the API path of the endpoint that lists all possible values
     * @param sourceField
     *            the JSON field name within each object returned by the lookup endpoint
     *            whose value will be used as the path variable
     * @param sourceDataPath
     *            optional dot-notation path to navigate to the array inside the lookup
     *            response (e.g. {@code "data"}); {@code null} if the response is a
     *            top-level array
     */
    public PathKeyDef(String variable, String sourcePath, String sourceField, String sourceDataPath)
    {
        this.variable       = variable;
        this.sourcePath     = sourcePath;
        this.sourceField    = sourceField;
        this.sourceDataPath = sourceDataPath;
    }

    /** Returns the placeholder variable name (without the leading {@code $}). */
    public String getVariable()       { return variable;       }

    /** Returns the path of the lookup endpoint. */
    public String getSourcePath()     { return sourcePath;     }

    /** Returns the JSON field name that supplies the variable value. */
    public String getSourceField()    { return sourceField;    }

    /**
     * Returns the optional dot-notation path to the array inside the lookup response,
     * or {@code null} if the response is a direct top-level array.
     */
    public String getSourceDataPath() { return sourceDataPath; }

    @Override
    public String toString()
    {
        return "PathKeyDef{variable='" + variable + "', sourcePath='" + sourcePath
                + "', sourceField='" + sourceField + "', sourceDataPath='" + sourceDataPath + "'}";
    }
}

// Made with Bob
