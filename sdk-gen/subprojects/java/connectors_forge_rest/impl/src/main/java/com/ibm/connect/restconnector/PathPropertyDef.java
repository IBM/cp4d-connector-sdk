/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

/**
 * Describes a single connection property that supplies a value for a URL path variable.
 *
 * <p>Path properties are declared at the top level of the JSON DSL via {@code $path_properties}.
 * Each entry declares one property that the user must supply when configuring the connection,
 * and whose value is substituted into {@code $name} placeholders that appear inside any
 * {@code $path} string in the {@code $tables} section.
 *
 * <p>Example DSL:
 * <pre>
 * "$path_properties": [
 *   {
 *     "name": "merchant_id",
 *     "label": "Merchant ID",
 *     "description": "Your Braintree merchant ID",
 *     "masked": false
 *   }
 * ]
 * </pre>
 *
 * <p>The {@code name} becomes both the connection-property key registered in CP4D and the
 * placeholder token resolved in paths: a path {@code /merchants/$merchant_id/customers}
 * is resolved using the {@code merchant_id} connection property value.
 */
public class PathPropertyDef
{
    private final String  name;
    private final String  label;
    private final String  description;
    private final boolean masked;

    /**
     * Creates a path property definition.
     *
     * @param name
     *            the property key; also used as the {@code $name} placeholder in paths
     * @param label
     *            human-readable label shown in the CP4D UI
     * @param description
     *            optional hint text shown under the field in the CP4D UI
     * @param masked
     *            {@code true} if the value should be shown as password dots in the UI
     */
    public PathPropertyDef(String name, String label, String description, boolean masked)
    {
        this.name        = name;
        this.label       = label;
        this.description = description != null ? description : "";
        this.masked      = masked;
    }

    /** Returns the property key / placeholder name. */
    public String  getName()        { return name;        }
    /** Returns the human-readable UI label. */
    public String  getLabel()       { return label;       }
    /** Returns the optional hint text. */
    public String  getDescription() { return description; }
    /** Returns {@code true} if the value should be masked in the UI. */
    public boolean isMasked()       { return masked;      }
}

// Made with Bob
