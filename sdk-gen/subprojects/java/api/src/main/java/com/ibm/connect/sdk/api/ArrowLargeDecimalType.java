/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.util.Properties;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.ArrowType.ExtensionType;
import org.apache.arrow.vector.types.pojo.FieldType;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Arrow extension type to handle decimal values outside of standard range of
 * Arrow decimal with 128 bit width.
 */
public class ArrowLargeDecimalType extends ExtensionType
{
    private static final Gson GSON = new Gson();
    private final int precision;
    private final int scale;

    public ArrowLargeDecimalType(int precision, int scale)
    {
        super();
        this.precision = precision;
        this.scale = scale;
    }

    /**
     * Arrow data type of the array used to store values
     *
     * @return Arrow type that is used as the storage vector.
     */
    @Override
    public ArrowType storageType()
    {
        return new ArrowType.Utf8();
    }

    /**
     * Extension type name used in Arrow type registry to identify this type.
     *
     * @return Unique string to identify an `ArrowLargeDecimalType` instance.
     */
    @Override
    public String extensionName()
    {
        return "com.ibm.connect:LargeDecimal";
    }

    /**
     * Check if another Arrow type equal to this `ArrowLargeDecimalType`.
     *
     * @param other
     *            Another Arrow type.
     * @return True if the types are equal.
     */
    @Override
    public boolean extensionEquals(ExtensionType other)
    {
        return other instanceof ArrowLargeDecimalType;
    }

    /**
     * Deserialize data to get an instance of `ArrowLargeDecimalType`.
     *
     * @param storageType
     *            The underlying type of the storage vector.
     * @param serializedData
     *            Data used to deserialize an instance.
     * @return A new instance of `ArrowLargeDecimalType`.
     */
    @Override
    public ArrowType deserialize(ArrowType storageType, String serializedData)
    {
        if (!storageType.equals(storageType())) {
            throw new UnsupportedOperationException("Cannot construct ArrowLargeDecimalType from underlying type " + storageType);
        }
        final Properties props = GSON.fromJson(serializedData, Properties.class);
        final int precisionDeser = Integer.parseInt(props.getProperty("precision"));
        final int scaleDeser = Integer.parseInt(props.getProperty("scale"));
        return new ArrowLargeDecimalType(precisionDeser, scaleDeser);
    }

    /**
     * Serialize this instance of `ArrowLargeDecimalType`.
     *
     * @return Serialized data for this instance as a String
     */
    @Override
    public String serialize()
    {
        final JsonObject obj = new JsonObject();
        obj.addProperty("precision", precision);
        obj.addProperty("scale", scale);
        return obj.toString();
    }

    /**
     * Create a new `ArrowLargeDecimalVector` from this type instance.
     *
     * @param name
     *            The vector name.
     * @param fieldType
     *            The FieldType definition of the vector.
     * @param allocator
     *            The Arrow allocator used to allocate the underlying vector.
     * @return A new instance of `ArrowLargeDecimalVector`.
     */
    @Override
    public FieldVector getNewVector(String name, FieldType fieldType, BufferAllocator allocator)
    {
        return new ArrowLargeDecimalVector(name, fieldType, allocator);
    }

    /**
     * Get the precision for this decimal type.
     */
    public int getPrecision()
    {
        return precision;
    }

    /**
     * Get the scale for this decimal type.
     */
    public int getScale()
    {
        return scale;
    }
}
