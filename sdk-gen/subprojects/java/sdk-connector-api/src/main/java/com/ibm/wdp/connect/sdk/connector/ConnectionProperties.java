/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Connection properties provided to a connector at connection time.
 *
 * <p>Wraps a {@code Map&lt;String, Object&gt;} of key/value pairs. Values are typically strings
 * (host, port, database name, credentials) but may be any serializable type.
 *
 * <p>Instances are immutable once constructed.
 */
public final class ConnectionProperties
{
    private final Map<String, Object> properties;

    /**
     * Creates connection properties from a map.
     *
     * @param properties
     *            the property map; may be null (treated as empty)
     */
    public ConnectionProperties(Map<String, Object> properties)
    {
        this.properties = properties != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(properties))
                : Collections.emptyMap();
    }

    /**
     * Returns the value of the named property.
     *
     * @param key
     *            the property name
     * @return the property value, or null if not present
     */
    public Object get(String key)
    {
        return properties.get(key);
    }

    /**
     * Returns the value of the named property as a String, or null.
     *
     * @param key
     *            the property name
     * @return the string value, or null if not present
     */
    public String getString(String key)
    {
        final Object value = properties.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Returns all properties as an unmodifiable map.
     *
     * @return the properties map; never null
     */
    public Map<String, Object> asMap()
    {
        return properties;
    }

    @Override
    public String toString()
    {
        return "ConnectionProperties{keys=" + properties.keySet() + "}";
    }
}

// Made with Bob
