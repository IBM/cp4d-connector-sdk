/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.math.BigDecimal;

import org.apache.arrow.vector.complex.impl.VarCharReaderImpl;
import org.apache.arrow.vector.complex.reader.DecimalReader;
import org.apache.arrow.vector.types.Types.MinorType;

/**
 * Arrow FieldReader for an `ArrowLargeDecimalVector`.
 */
public class ArrowLargeDecimalReader extends VarCharReaderImpl implements DecimalReader
{
    private final ArrowLargeDecimalVector largeDecimalVector;

    /**
     * Construct a new ArrowLargeDecimalReader.
     *
     * @param vector
     *            ArrowLargeDecimalVector containing values to read.
     */
    public ArrowLargeDecimalReader(ArrowLargeDecimalVector vector)
    {
        super(vector.getUnderlyingVector());
        this.largeDecimalVector = vector;
    }

    /**
     * Identify this as a Decimal reader.
     */
    @Override
    public MinorType getMinorType()
    {
        return MinorType.DECIMAL;
    }

    /**
     * Read a BigDecimal value from the vector.
     *
     * @return BigDecimal value at the current index.
     */
    @Override
    public BigDecimal readBigDecimal()
    {
        return largeDecimalVector.getObject(idx());
    }
}
