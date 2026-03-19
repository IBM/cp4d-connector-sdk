/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Interface for field setters.
 */
@FunctionalInterface
public interface FieldSetter
{
    /**
     * zero length byte array.
     */
    byte[] EMPTY_BYTES = new byte[0];

    /**
     * Sets field at position index to value.
     *
     * @param index
     * @param value
     */
    void setValue(int index, Serializable value);

    /**
     * Set value bytes
     *
     * @param index
     * @param buffer
     * @param start
     * @param length
     */
    default void setBytes(int index, byte[] buffer, int start, int length)
    {
        if (length == 0) {
            setValue(index, EMPTY_BYTES);
        } else if (start == 0 && buffer.length == length) {
            setValue(index, buffer);
        } else {
            setValue(index, Arrays.copyOfRange(buffer, start, start + length));
        }
    }

    /**
     * Sets a field to null.
     *
     * @param index
     */
    default void setNull(int index)
    {
        setValue(index, null);
    }

    /**
     * Indicates if varCharAsBytes() is set.
     *
     * @return default of false
     */
    default boolean varCharAsBytes()
    {
        return false;
    }

    /**
     * @param rejectFlag
     *            rejection state.
     */
    default void setReject(boolean rejectFlag)
    {
        // default is do nothing.
    }

    /**
     * Indicates if the current row has been rejected.
     *
     * @return default of no.
     */
    default boolean isRejected()
    {
        return false;
    }
}
