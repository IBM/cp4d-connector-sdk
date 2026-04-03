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
    private final boolean isNestedArray;

    /**
     * Creates a field definition.
     *
     * @param name
     *            the field name (may be a nested path like "headquarters.address")
     * @param typeString
     *            the raw type string from the JSON file (e.g. "VARCHAR")
     * @param isKey
     *            true if this field is marked as a key ($key modifier)
     * @param isNotNull
     *            true if this field is marked as not null ($notnull modifier)
     * @param isNestedArray
     *            true if this field represents a nested array (key ended with [])
     */
    public RestFieldDefinition(String name, String typeString, boolean isKey, boolean isNotNull, boolean isNestedArray)
    {
        this.name = name;
        this.typeString = typeString;
        this.isKey = isKey;
        this.isNotNull = isNotNull;
        this.isNestedArray = isNestedArray;
    }

    /**
     * Returns the field name (may be a nested path like "headquarters.address").
     *
     * @return the field name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns the raw type string from the JSON file.
     *
     * @return the raw type string
     */
    public String getTypeString()
    {
        return typeString;
    }

    /**
     * Returns true if this field is a primary key.
     *
     * @return true if this field is a primary key
     */
    public boolean isKey()
    {
        return isKey;
    }

    /**
     * Returns true if this field is marked as not null.
     *
     * @return true if this field is not null
     */
    public boolean isNotNull()
    {
        return isNotNull;
    }

    /**
     * Returns true if this field represents a nested array serialized as JSON string.
     *
     * @return true if this field is a nested array
     */
    public boolean isNestedArray()
    {
        return isNestedArray;
    }

    @Override
    public String toString()
    {
        return "RestFieldDefinition{name='" + name + "', typeString='" + typeString + "', isKey=" + isKey
                + ", isNotNull=" + isNotNull + ", isNestedArray=" + isNestedArray + "}";
    }
}

// Made with Bob
