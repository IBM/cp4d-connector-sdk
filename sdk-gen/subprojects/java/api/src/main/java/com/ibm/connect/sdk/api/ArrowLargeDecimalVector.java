/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.util.hash.ArrowBufHasher;
import org.apache.arrow.vector.ExtensionTypeVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.complex.reader.FieldReader;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.util.Text;

/**
 * Arrow vector to store decimal values that are too large for standard Arrow
 * DecimalVector classes.
 */
public class ArrowLargeDecimalVector extends ExtensionTypeVector<VarCharVector>
{
    private final FieldReader reader;
    protected final Field field;

    /**
     * Create a new `ArrowLargeDecimalVector`.
     *
     * @param name
     *            Name of the vector.
     * @param precision
     *            Precision defined in type metadata.
     * @param scale
     *            Scale defined in type metadata.
     * @param allocator
     *            Allocator used to allocate underlying vector.
     */
    public ArrowLargeDecimalVector(String name, int precision, int scale, BufferAllocator allocator)
    {
        this(name, FieldType.nullable(new ArrowLargeDecimalType(precision, scale)), allocator);
    }

    /**
     * Create a new `ArrowLargeDecimalVector` from a FieldType containing
     * `ArrowLargeDecimalType`.
     */
    public ArrowLargeDecimalVector(String name, FieldType fieldType, BufferAllocator allocator)
    {
        this(new Field(name, fieldType, null), allocator);
    }

    /**
     * Create a new `ArrowLargeDecimalVector` from a Field of type
     * `ArrowLargeDecimalType`.
     */
    public ArrowLargeDecimalVector(Field field, BufferAllocator allocator)
    {
        super(field.getName(), allocator, new VarCharVector(field.getName(), allocator));
        this.field = field;
        this.reader = new ArrowLargeDecimalReader(this);
    }

    /**
     * Hashcode of element in the given index.
     */
    @Override
    public int hashCode(int index)
    {
        return hashCode(index, null);
    }

    /**
     * Hashcode of the element in the given index using the given hasher.
     */
    @Override
    public int hashCode(int index, ArrowBufHasher hasher)
    {
        return getUnderlyingVector().hashCode(index, hasher);
    }

    /**
     * Get the Field for this vector.
     *
     * @return Field of type `ArrowLargeVectorType`.
     */
    @Override
    public Field getField()
    {
        return field;
    }

    /**
     * Get a FieldReader for this vector.
     *
     * @return ArrowLargeDecimalReader.
     */
    @Override
    public FieldReader getReader()
    {
        return reader;
    }

    /**
     * Get the value as a BigDecimal at the given index.
     *
     * @param index
     *            Index to retrieve value.
     * @return BigDecimal value.
     */
    @Override
    public BigDecimal getObject(int index)
    {
        final Text txt = getUnderlyingVector().getObject(index);
        return new BigDecimal(txt.toString());
    }

    /**
     * Set the value as a BigDecimal at the given index.
     *
     * @param index
     *            Index to set value.
     * @param value
     *            BigDecimal value to set in the underlying storage vector.
     */
    public void setSafe(int index, BigDecimal value)
    {
        final byte[] buf = value.toString().getBytes(StandardCharsets.UTF_8);
        getUnderlyingVector().setSafe(index, buf);
    }
}
