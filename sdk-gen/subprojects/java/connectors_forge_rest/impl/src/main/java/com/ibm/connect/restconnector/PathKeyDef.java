/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

/**
 * Describes a URL path variable that is resolved dynamically by fetching values from another
 * API endpoint at read time.
 *
 * <p>This is the DSL representation of a foreign-key-like path variable: the variable appears
 * as a {@code $name} placeholder inside a table's {@code $path}, and its set of possible values
 * is obtained by querying a separate "lookup" endpoint.  During data streaming the connector
 * fetches that endpoint, extracts the field named {@link #getSourceField()} from each object in
 * the response, and iterates over every resolved URL.
 *
 * <p>Example DSL:
 * <pre>
 * {
 *   "variable":         "org_slug",
 *   "source_path":      "/api/0/organizations/",
 *   "source_field":     "slug",
 *   "source_data_path": "data",
 *   "lookup_delay_ms":  200
 * }
 * </pre>
 *
 * <p>{@code source_data_path} is optional; when absent the lookup response is assumed to be
 * a top-level JSON array of objects.
 *
 * <p>{@code lookup_delay_ms} is optional (default 0).  When greater than zero, the connector
 * sleeps that many milliseconds between consecutive lookup requests for this step.  Use this
 * to avoid HTTP 429 rate-limit errors when the lookup endpoint is called many times in rapid
 * succession.
 */
public class PathKeyDef
{
    private final String variable;
    private final String sourcePath;
    private final String sourceField;
    private final String sourceDataPath; // nullable
    private final long lookupDelayMs;

    /**
     * Creates a path key definition.
     *
     * @param variable
     *            the {@code $name} placeholder used in the table's {@code $path}
     * @param sourcePath
     *            the API path of the endpoint that lists all possible values
     * @param sourceField
     *            the JSON field name within each object returned by the lookup endpoint
     *            whose value will be used as the path variable; supports dot notation
     *            for nested fields (e.g. {@code "user.id"})
     * @param sourceDataPath
     *            optional dot-notation path to navigate to the array inside the lookup
     *            response (e.g. {@code "data"}); {@code null} if the response is a
     *            top-level array
     * @param lookupDelayMs
     *            milliseconds to sleep between consecutive lookup requests for this step;
     *            {@code null} or negative values are treated as 0 (no delay)
     */
    public PathKeyDef(String variable, String sourcePath, String sourceField,
            String sourceDataPath, Long lookupDelayMs)
    {
        this.variable       = variable;
        this.sourcePath     = sourcePath;
        this.sourceField    = sourceField;
        this.sourceDataPath = sourceDataPath;
        this.lookupDelayMs  = (lookupDelayMs != null && lookupDelayMs > 0) ? lookupDelayMs : 0L;
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

    /**
     * Returns the number of milliseconds to sleep between consecutive lookup requests
     * for this step. Always {@code >= 0}; 0 means no delay.
     */
    public long getLookupDelayMs()    { return lookupDelayMs;  }

    @Override
    public String toString()
    {
        return "PathKeyDef{variable='" + variable + "', sourcePath='" + sourcePath
                + "', sourceField='" + sourceField + "', sourceDataPath='" + sourceDataPath
                + "', lookupDelayMs=" + lookupDelayMs + "}";
    }
}

// Made with Bob
