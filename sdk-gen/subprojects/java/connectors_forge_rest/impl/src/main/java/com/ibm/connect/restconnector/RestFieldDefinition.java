/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

/**
 * Represents a field definition parsed from a JSON configuration file.
 * Holds the field name (which may be a nested path like "headquarters.address"),
 * the raw type string (e.g. "VARCHAR"), and flags.
 */
public class RestFieldDefinition
{
    private final String name;
    private final String typeString;
    private final boolean isKey;
    private final boolean isNotNull;

    public RestFieldDefinition(String name, String typeString, boolean isKey, boolean isNotNull)
    {
        this.name = name;
        this.typeString = typeString;
        this.isKey = isKey;
        this.isNotNull = isNotNull;
    }

    public String getName() { return name; }
    public String getTypeString() { return typeString; }
    public boolean isKey() { return isKey; }
    public boolean isNotNull() { return isNotNull; }

    @Override
    public String toString()
    {
        return "RestFieldDefinition{name='" + name + "', typeString='" + typeString + "', isKey=" + isKey
                + ", isNotNull=" + isNotNull + "}";
    }
}
