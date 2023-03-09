/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * An object that carries the data that is exchanged with the connector.
 */
public class Record implements Serializable
{
    private static final long serialVersionUID = 2204206672140691349L;

    private long sizeInBytes;
    private final List<Serializable> values;

    /**
     * Construct the object. A newly constructed object has an empty list of values.
     */
    public Record()
    {
        values = new ArrayList<>();
    }

    /**
     * Construct the object. A newly constructed object has an empty list of values.
     *
     * @param numFields
     *            Expected number of fields in this record.
     */
    public Record(int numFields)
    {
        values = new ArrayList<>(numFields);
    }

    /**
     * Copy constructor
     *
     * @param rec
     *            the Record to be copied.
     */
    public Record(Record rec)
    {
        sizeInBytes = rec.sizeInBytes;
        values = new ArrayList<>(rec.values);
    }

    /**
     * Appends a value to the this row instance.
     *
     * @param value
     *            Java object representing the field value. The Java type of the
     *            value is determined by the field definition of the respective
     *            field according to the mapping table specified in
     *            {@link FieldDefinition#FieldDefinition(String, FieldType)}
     */
    public void appendValue(Serializable value)
    {
        values.add(value);
        sizeInBytes += getSizeInBytes(value);
    }

    /**
     * Returns the list of values in this row instance.
     *
     * @return List of Java objects representing field values. Java types of the
     *         values are determined by the field definitions of the respective
     *         fields according to the mapping table specified in
     *         {@link FieldDefinition#FieldDefinition(String, FieldType)}
     */
    public List<Serializable> getValues()
    {
        return values;
    }

    /**
     * Removes all values stored in this row instance.
     */
    public void clear()
    {
        values.clear();
        sizeInBytes = 0;
    }

    /**
     * @return <code>true</code> if there are no values.
     */
    public boolean isEmpty()
    {
        return values.isEmpty();
    }

    /**
     * Returns the number of bytes needed for this row.
     *
     * @return the number of bytes needed for this row.
     */
    public long getSizeInBytes()
    {
        return sizeInBytes;
    }

    /**
     * Returns the estimated size in bytes for this list of values.
     *
     * @param values
     *            List of Java objects representing the values.
     *
     * @return the estimated size in bytes for this list of values.
     */
    public static long computeSizeInBytes(List<Serializable> values)
    {
        return values.stream().mapToLong(Record::getSizeInBytes).sum();
    }

    /**
     * Returns the estimated size in bytes for this value.
     *
     * @param value
     *            Java object representing the value.
     *
     * @return the estimated size in bytes for this value.
     */
    public static int getSizeInBytes(Serializable value)
    {
        if (value == null) {
            return 0;
        }

        final Class<? extends Serializable> valueClass = value.getClass();
        if (valueClass == String.class) {
            // While it would be more accurate to return the number of bytes, the cost of
            // converting the string back to bytes is not worth the performance penalty
            // since we are only trying to get an estimated size in bytes, not exact.
            return ((String) value).length();
        } else if (valueClass == Boolean.class) {
            return 1;
        } else if (valueClass == Byte.class) {
            return 2;
        } else if (valueClass == Character.class) {
            return 2;
        } else if (valueClass == Short.class) {
            return 2;
        } else if (valueClass == Integer.class) {
            return 4;
        } else if (valueClass == Long.class) {
            return 8;
        } else if (valueClass == Float.class) {
            return 4;
        } else if (valueClass == Double.class) {
            return 8;
        } else if (valueClass == BigInteger.class) {
            return 8;
        } else if (valueClass == BigDecimal.class) {
            return 33;
        } else if (valueClass == Date.class) {
            return 6;
        } else if (valueClass == Time.class) {
            return 6;
        } else if (valueClass == Timestamp.class) {
            return 16;
        } else if (valueClass == byte[].class) {
            return ((byte[]) value).length;
        }
        return 0;
    }
}
