/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import java.util.Collections;
import java.util.List;

/**
 * Holds the list of datasource type names that an {@link SdkConnectorFactory} handles.
 *
 * <p>Returned by {@link SdkConnectorFactory#getDatasourceTypes()} so the runtime can route
 * connector creation requests to the correct factory.
 *
 * <p>Instances are immutable once constructed.
 */
public final class SdkDatasourceTypes
{
    private final List<String> typeNames;

    /**
     * Creates a datasource types holder.
     *
     * @param typeNames
     *            the list of datasource type names handled by the owning factory; must not be null or empty
     */
    public SdkDatasourceTypes(List<String> typeNames)
    {
        if (typeNames == null || typeNames.isEmpty()) {
            throw new IllegalArgumentException("typeNames must not be null or empty");
        }
        this.typeNames = Collections.unmodifiableList(typeNames);
    }

    /**
     * Returns the datasource type names.
     *
     * @return an unmodifiable list of type names; never null or empty
     */
    public List<String> getTypeNames()
    {
        return typeNames;
    }

    /**
     * Returns true if this holder contains the given type name (case-sensitive).
     *
     * @param typeName
     *            the type name to test
     * @return true if the type is handled
     */
    public boolean handles(String typeName)
    {
        return typeNames.contains(typeName);
    }

    @Override
    public String toString()
    {
        return "SdkDatasourceTypes{typeNames=" + typeNames + "}";
    }
}

// Made with Bob
