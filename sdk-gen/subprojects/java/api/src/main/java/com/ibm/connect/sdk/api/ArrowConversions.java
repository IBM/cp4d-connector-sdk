/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DateMilliVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.DurationVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.FixedSizeBinaryVector;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.IntervalDayVector;
import org.apache.arrow.vector.IntervalYearVector;
import org.apache.arrow.vector.NullVector;
import org.apache.arrow.vector.SmallIntVector;
import org.apache.arrow.vector.TimeMicroVector;
import org.apache.arrow.vector.TimeMilliVector;
import org.apache.arrow.vector.TimeNanoVector;
import org.apache.arrow.vector.TimeSecVector;
import org.apache.arrow.vector.TimeStampMicroTZVector;
import org.apache.arrow.vector.TimeStampMicroVector;
import org.apache.arrow.vector.TimeStampMilliTZVector;
import org.apache.arrow.vector.TimeStampMilliVector;
import org.apache.arrow.vector.TimeStampNanoTZVector;
import org.apache.arrow.vector.TimeStampNanoVector;
import org.apache.arrow.vector.TimeStampSecTZVector;
import org.apache.arrow.vector.TimeStampSecVector;
import org.apache.arrow.vector.TinyIntVector;
import org.apache.arrow.vector.UInt1Vector;
import org.apache.arrow.vector.UInt2Vector;
import org.apache.arrow.vector.UInt4Vector;
import org.apache.arrow.vector.UInt8Vector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.complex.reader.FieldReader;
import org.apache.arrow.vector.holders.NullableTimeMilliHolder;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;

import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.primitives.Shorts;
import com.google.common.primitives.UnsignedBytes;
import com.google.common.primitives.UnsignedInts;
import com.ibm.connect.sdk.util.ModelMapper;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;
import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetFieldMetadata;

/***/
public class ArrowConversions
{
    /***/
    public static final int NO_LIMIT_BATCH_SIZE = -1;

    private static final String METADATA_KEY_COLUMN_TYPE = "type";
    private static final String METADATA_KEY_COLUMN_LENGTH = "length";
    private static final int UNSIGNED_BIGINT_DECIMAL_PRECISION = 38;
    private static final int UNSIGNED_BIGINT_DECIMAL_SCALE = 0;
    private static final int DEFAULT_VARCHAR_PRECISION = 1024;
    private static final int DECIMAL_BITWIDTH = 128;
    private static final int DECIMAL_MAX_PRECISION = DECIMAL_BITWIDTH == 128 ? 38 : 77;
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final ModelMapper MODEL_MAPPER = new ModelMapper();

    private ArrowConversions()
    {
        // prevent instantiation
    }

    /**
     * Returns the field definition as an arrow field
     *
     * @param field
     *            the field definition.
     * @return the arrow field.
     * @throws Exception
     */
    public static Field toArrow(CustomFlightAssetField field)
    {
        final ArrowType arrowType;
        // final int sqlType = getSQLType(field.getType());
        switch (field.getType().toLowerCase(Locale.ENGLISH)) {
        case "boolean":
        case "bit":
            arrowType = new ArrowType.Bool();
            break;
        case "tinyint":
            arrowType = new ArrowType.Int(8, field.isSigned());
            break;
        case "smallint":
            arrowType = new ArrowType.Int(16, field.isSigned());
            break;
        case "integer":
            arrowType = new ArrowType.Int(32, field.isSigned());
            break;
        case "bigint":
            if (field.isSigned()) {
                arrowType = new ArrowType.Int(64, true);
            } else {
                arrowType = new ArrowType.Decimal(UNSIGNED_BIGINT_DECIMAL_PRECISION, UNSIGNED_BIGINT_DECIMAL_SCALE, DECIMAL_BITWIDTH);
            }
            break;
        case "numeric":
        case "decimal": {
            int precision = field.getLength();
            // Check if decimal type is out of range to fit in Arrow DecimalVector
            if (precision <= DECIMAL_MAX_PRECISION) {
                if (precision == 0) {
                    precision = DECIMAL_MAX_PRECISION;
                }
                arrowType = new ArrowType.Decimal(precision, field.getScale(), DECIMAL_BITWIDTH);
            } else {
                arrowType = new ArrowLargeDecimalType(field.getLength(), field.getScale());
            }
            break;
        }
        case "real":
            arrowType = new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE);
            break;
        case "float":
        case "double":
            arrowType = new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE);
            break;
        case "char":
        case "nchar":
        case "varchar":
        case "nvarchar":
        case "longvarchar":
        case "longnvarchar":
        case "clob":
        case "nclob":
            arrowType = new ArrowType.Utf8();
            break;
        case "date":
            arrowType = new ArrowType.Date(DateUnit.DAY);
            break;
        case "time":
        case "time_with_timezone":
            arrowType = new ArrowType.Time(TimeUnit.MILLISECOND, 32);
            break;
        case "timestamp":
        case "timestamp_with_timezone":
            arrowType = new ArrowType.Timestamp(TimeUnit.MILLISECOND, null);
            break;
        case "binary":
        case "varbinary":
        case "longvarbinary":
        case "blob":
            arrowType = new ArrowType.Binary();
            break;
        case "null":
            arrowType = new ArrowType.Null();
            break;
        default:
            arrowType = new ArrowType.Utf8();
            break;
        }
        final Map<String, String> metadata = new HashMap<>();
        try {
            final CustomFlightAssetFieldMetadata fieldMetadata
                    = MODEL_MAPPER.fromBytes(MODEL_MAPPER.toBytes(field), CustomFlightAssetFieldMetadata.class);
            for (final Entry<String, String> entry : fieldMetadata.entrySet()) {
                if (entry.getValue() != null) {
                    metadata.put(entry.getKey(), entry.getValue());
                }
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new Field(field.getName(), new org.apache.arrow.vector.types.pojo.FieldType(
                field.isNullable() != null ? field.isNullable() : true, arrowType, null, metadata), null);
    }

    /**
     * Constructs a field definition from the given arrow field
     *
     * @param field
     *            the arrow field.
     * @return the field definition.
     */
    public static CustomFlightAssetField fromArrow(Field field)
    {
        final String fieldName = field.getName();
        final Map<String, String> metadata = field.getFieldType().getMetadata();
        final String fieldType = metadata.containsKey(METADATA_KEY_COLUMN_TYPE) ? metadata.get(METADATA_KEY_COLUMN_TYPE) : null;
        final CustomFlightAssetField fieldDefinition;

        // Check for Arrow extension types
        if (field.getType() instanceof ArrowLargeDecimalType) {
            final ArrowLargeDecimalType decimalType = (ArrowLargeDecimalType) field.getType();
            return new CustomFlightAssetField().name(fieldName).type("decimal").length(decimalType.getPrecision())
                    .scale(decimalType.getScale()).nullable(field.getFieldType().isNullable());
        }

        switch (field.getType().getTypeID()) {
        case Binary:
            fieldDefinition = new CustomFlightAssetField().name(fieldName).type("varbinary");
            if (metadata.containsKey(METADATA_KEY_COLUMN_LENGTH)) {
                fieldDefinition.setLength(Integer.parseInt(metadata.get(METADATA_KEY_COLUMN_LENGTH)));
            }
            break;
        case Bool:
            fieldDefinition
                    = new CustomFlightAssetField().name(fieldName).type((fieldType == null || fieldType.equals("bit")) ? "bit" : "boolean");
            break;
        case Date:
            fieldDefinition = new CustomFlightAssetField().name(fieldName).type("date");
            break;
        case Decimal:
            final ArrowType.Decimal decimalType = (ArrowType.Decimal) field.getType();
            if (fieldType == null || fieldType.equals("bigint") && decimalType.getPrecision() == UNSIGNED_BIGINT_DECIMAL_PRECISION
                    && decimalType.getScale() == UNSIGNED_BIGINT_DECIMAL_SCALE) {
                fieldDefinition = new CustomFlightAssetField().name(fieldName).type("bigint");
                fieldDefinition.setSigned(false);
            } else {
                fieldDefinition = new CustomFlightAssetField().name(fieldName).type("decimal");
                fieldDefinition.setLength(decimalType.getPrecision() == 0 ? DECIMAL_MAX_PRECISION : decimalType.getPrecision());
                fieldDefinition.setScale(decimalType.getScale());
            }
            break;
        case Duration:
            fieldDefinition = new CustomFlightAssetField().name(fieldName).type("bigint");
            break;
        case FixedSizeBinary:
            fieldDefinition = new CustomFlightAssetField().name(fieldName).type("varbinary");
            fieldDefinition.setLength(((ArrowType.FixedSizeBinary) field.getType()).getByteWidth());
            break;
        case FloatingPoint:
            final ArrowType.FloatingPoint floatingPointType = (ArrowType.FloatingPoint) field.getType();
            if (FloatingPointPrecision.SINGLE.equals(floatingPointType.getPrecision())) {
                fieldDefinition = new CustomFlightAssetField().name(fieldName).type("real");
            } else if (fieldType != null && fieldType.equals("float")) {
                fieldDefinition = new CustomFlightAssetField().name(fieldName).type("float");
            } else {
                fieldDefinition = new CustomFlightAssetField().name(fieldName).type("double");
            }
            break;
        case Int:
            final ArrowType.Int intType = (ArrowType.Int) field.getType();
            switch (intType.getBitWidth()) {
            case 8:
                fieldDefinition = new CustomFlightAssetField().name(fieldName).type(intType.getIsSigned() ? "tinyint" : "smallint");
                break;
            case 16:
                fieldDefinition = new CustomFlightAssetField().name(fieldName).type(intType.getIsSigned() ? "smallint" : "integer");
                break;
            case 32:
                fieldDefinition = new CustomFlightAssetField().name(fieldName).type(intType.getIsSigned() ? "integer" : "bigint");
                break;
            default:
                fieldDefinition = new CustomFlightAssetField().name(fieldName).type("bigint");
                break;
            }
            fieldDefinition.setSigned(intType.getIsSigned());
            break;
        case Interval:
            fieldDefinition = new CustomFlightAssetField().name(fieldName).type("integer");
            break;
        case Null:
            fieldDefinition = new CustomFlightAssetField().name(fieldName).type("null");
            break;
        case Time:
            fieldDefinition = new CustomFlightAssetField().name(fieldName).type("time");
            break;
        case Timestamp:
            fieldDefinition = new CustomFlightAssetField().name(fieldName).type("timestamp");
            break;
        default:
            if (fieldType == null) {
                fieldDefinition = new CustomFlightAssetField().name(fieldName).type("varchar");
            } else {
                switch (fieldType) {
                case "char":
                case "nchar":
                case "varchar":
                case "nvarchar":
                case "longvarchar":
                case "longnvarchar":
                case "clob":
                case "nclob":
                case "array":
                case "struct":
                    fieldDefinition = new CustomFlightAssetField().name(fieldName).type(fieldType);
                    break;
                default:
                    fieldDefinition = new CustomFlightAssetField().name(fieldName).type("varchar");
                    fieldDefinition.setLength(DEFAULT_VARCHAR_PRECISION);
                    break;
                }
            }
            if (metadata.containsKey(METADATA_KEY_COLUMN_LENGTH)) {
                fieldDefinition.setLength(Integer.parseInt(metadata.get(METADATA_KEY_COLUMN_LENGTH)));
            }
            break;
        }
        fieldDefinition.setNullable(field.getFieldType().isNullable());
        return fieldDefinition;
    }

    /**
     * Returns the list of field definitions as an arrow schema
     *
     * @param CustomFlightAssetFields
     *            the field definitions.
     * @return the arrow schema.
     */
    public static Schema toArrow(List<CustomFlightAssetField> fields)
    {
        return new Schema(fields.stream().map(fd -> ArrowConversions.toArrow(fd)).collect(Collectors.toList()), null);
    }

    /**
     * Constructs a record definition from the given arrow schema
     *
     * @param schema
     *            the arrow schema.
     * @return the record definition.
     */
    public static List<CustomFlightAssetField> fromArrow(Schema schema)
    {
        final List<CustomFlightAssetField> recordDefinition = new ArrayList<>();
        schema.getFields().stream().map(ArrowConversions::fromArrow).forEach(recordDefinition::add);
        return recordDefinition;
    }

    /**
     * Convert a record iterator to an arrow vector schema root iterator.
     *
     * @param root
     *            the arrow vector schema root.
     * @param records
     *            the record iterator.
     * @param batchSize
     *            the number of records to include in each batch, or -1 for all.
     * @return The arrow vector schema root iterator.
     */
    public static Iterator<VectorSchemaRoot> toArrow(VectorSchemaRoot root, Iterator<Record> records, int batchSize)
    {
        return new AbstractIterator<VectorSchemaRoot>() {
            private final List<ArrowSetterBase> setters = initSetters(root);

            @Override
            protected VectorSchemaRoot computeNext()
            {
                if (!records.hasNext()) {
                    return endOfData();
                }

                root.allocateNew();

                int vectorIndex = 0;
                try {
                    while ((batchSize == NO_LIMIT_BATCH_SIZE || vectorIndex < batchSize) && records.hasNext()) {
                        final List<Serializable> values = records.next().getValues();
                        for (int fieldIdx = 0; fieldIdx < values.size(); fieldIdx++) {
                            final Serializable value = values.get(fieldIdx);
                            if (value != null) {
                                setters.get(fieldIdx).setValue(vectorIndex, value);
                            } else {
                                setters.get(fieldIdx).handleNull(vectorIndex);
                            }
                        }
                        vectorIndex++;
                    }
                }
                catch (final Exception e) {
                    throw new RuntimeException(e);
                }

                root.setRowCount(vectorIndex);
                return root;
            }
        };
    }

    /**
     * Convert an arrow vector schema root iterator to a record iterator.
     *
     * @param customFlightAssetFields
     *            the field definitions.
     * @param roots
     *            the arrow vector schema root iterator.
     * @param commitFrequency
     *            how often to call commit.
     * @param connector
     *            the Connector being used.
     * @return The record iterator.
     */
    public static Iterator<Record> fromArrow(List<CustomFlightAssetField> customFlightAssetFields, Iterator<VectorSchemaRoot> roots,
            int commitFrequency, RowBasedConnector<?, ?> connector)
    {
        return new AbstractIterator<Record>() {
            private final Queue<Record> queuedRecords = new ArrayDeque<>();
            private int uncommittedCount;

            @Override
            protected Record computeNext()
            {
                if (queuedRecords.isEmpty() && roots.hasNext()) {
                    final VectorSchemaRoot root = roots.next(); // NOPMD root should be closed by owner
                    final ArrowFieldGetter getter = new ArrowFieldGetter(customFlightAssetFields, root);
                    for (int vectorIndex = 0; vectorIndex < root.getRowCount(); vectorIndex++) {
                        getter.vectorIndex = vectorIndex;

                        final Record record = new Record(getter.fields.length);
                        for (int fieldIdx = 0; fieldIdx < getter.fields.length; fieldIdx++) {
                            record.appendValue(getter.getValue(fieldIdx));
                        }

                        queuedRecords.add(record);
                    }
                }
                if (queuedRecords.isEmpty()) {
                    return endOfData();
                }
                if (commitFrequency < uncommittedCount) {
                    connector.commit();
                    uncommittedCount = 0;
                }
                uncommittedCount++;
                return queuedRecords.poll();
            }
        };
    }

    /**
     * Create FieldSetters for each field in the VectorSchemaRoot.
     */
    public static List<ArrowSetterBase> initSetters(VectorSchemaRoot root)
    {
        final List<FieldVector> vectors = root.getFieldVectors();
        final List<ArrowSetterBase> setters = new ArrayList<>(vectors.size());
        for (final FieldVector vector : vectors) { // NOPMD CloseResource, it is closed with root
            setters.add(getArrowSetter(vector));
        }
        return setters;
    }

    public static ArrowSetterBase getArrowSetter(FieldVector vector)
    {
        switch (vector.getMinorType()) {
        case BIT:
            return new ArrowBitSetter((BitVector) vector);
        case TINYINT:
            return new ArrowByteSetter((TinyIntVector) vector);
        case SMALLINT:
            return new ArrowShortSetter((SmallIntVector) vector);
        case INT:
            return new ArrowIntSetter((IntVector) vector);
        case BIGINT:
            return new ArrowLongSetter((BigIntVector) vector);
        case UINT1:
            return new ArrowUInt1Setter((UInt1Vector) vector);
        case UINT2:
            return new ArrowUInt2Setter((UInt2Vector) vector);
        case UINT4:
            return new ArrowUInt4Setter((UInt4Vector) vector);
        case UINT8:
            return new ArrowUInt8Setter((UInt8Vector) vector);
        case FLOAT4:
            return new ArrowFloatSetter((Float4Vector) vector);
        case FLOAT8:
            return new ArrowDoubleSetter((Float8Vector) vector);
        case DECIMAL:
            return new ArrowDecimalSetter((DecimalVector) vector);
        case DATEMILLI:
            return new ArrowDateMilliSetter((DateMilliVector) vector);
        case DATEDAY:
            return new ArrowDateDaySetter((DateDayVector) vector);
        case DURATION:
            return new ArrowDurationSetter((DurationVector) vector);
        case INTERVALDAY:
            return new ArrowIntervalDaySetter((IntervalDayVector) vector);
        case INTERVALYEAR:
            return new ArrowIntervalYearSetter((IntervalYearVector) vector);
        case TIMENANO:
            return new ArrowTimeNanoSetter((TimeNanoVector) vector);
        case TIMEMICRO:
            return new ArrowTimeMicroSetter((TimeMicroVector) vector);
        case TIMEMILLI:
            return new ArrowTimeMilliSetter((TimeMilliVector) vector);
        case TIMESEC:
            return new ArrowTimeSecSetter((TimeSecVector) vector);
        case TIMESTAMPNANO:
            return new ArrowTimeStampNanoSetter((TimeStampNanoVector) vector);
        case TIMESTAMPMICRO:
            return new ArrowTimeStampMicroSetter((TimeStampMicroVector) vector);
        case TIMESTAMPMILLI:
            return new ArrowTimeStampMilliSetter((TimeStampMilliVector) vector);
        case TIMESTAMPSEC:
            return new ArrowTimeStampSecSetter((TimeStampSecVector) vector);
        case TIMESTAMPNANOTZ:
            return new ArrowTimeStampNanoTZSetter((TimeStampNanoTZVector) vector);
        case TIMESTAMPMICROTZ:
            return new ArrowTimeStampMicroTZSetter((TimeStampMicroTZVector) vector);
        case TIMESTAMPMILLITZ:
            return new ArrowTimeStampMilliTZSetter((TimeStampMilliTZVector) vector);
        case TIMESTAMPSECTZ:
            return new ArrowTimeStampSecTZSetter((TimeStampSecTZVector) vector);
        case FIXEDSIZEBINARY:
            return new ArrowFixedSizeBinarySetter((FixedSizeBinaryVector) vector);
        case VARBINARY:
            return new ArrowVarBinarySetter((VarBinaryVector) vector);
        case VARCHAR:
            return new ArrowVarCharSetter((VarCharVector) vector);
        case EXTENSIONTYPE:
            if (vector.getField().getType() instanceof ArrowLargeDecimalType) {
                return new ArrowLargeDecimalSetter((ArrowLargeDecimalVector) vector);
            }
            throw new UnsupportedOperationException("Unsupported arrow extension type: " + vector.getField().getType());
        case NULL:
            return new ArrowNullSetter((NullVector) vector);
        default:
            throw new UnsupportedOperationException("Unsupported arrow type: " + vector.getMinorType().name());
        }
    }

    public abstract static class ArrowSetterBase implements FieldSetter
    {
        private boolean rejected;
        private final Consumer<Integer> nullHandler;

        public ArrowSetterBase(FieldVector vector)
        {
            if (vector.getField().isNullable()) {
                nullHandler = this::setNull;
            } else {
                nullHandler = this::fillValue;
            }
        }

        /**
         * Handle a null value for this vector, if the field is non-nullable, then a
         * fill-value will be set.
         */
        public void handleNull(int index)
        {
            nullHandler.accept(index);
        }

        @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
        @Override
        public void setNull(int index)
        {
            // NO-OP by default because validity buffer should be cleared on reset()
        }

        /**
         * If a value is null but field is non-nullable, then fill vector at the given
         * index with an appropriate value, e.g. 0 for numeric.
         */
        public abstract void fillValue(int index);

        /**
         * {@inheritDoc}
         */
        @Override
        public void setBytes(int index, byte[] buffer, int start, int length)
        {
            throw new UnsupportedOperationException("Can not set bytes for this arrow type");
        }

        /**
         * @param rejectFlag
         *            reject flag.
         */
        @Override
        public void setReject(boolean rejectFlag)
        {
            rejected = rejectFlag;
        }

        /**
         * @return reject flag.
         */
        @Override
        public boolean isRejected()
        {
            return rejected;
        }

        /**
         * Reset validity bits set so vector can be written to again. Avoid
         * vector.reset() because not necessary to zero value buffer, implementations
         * only need to zero validity buffer and set counts to 0.
         */
        public abstract void reset();
    }

    public static class ArrowBitSetter extends ArrowSetterBase
    {
        private final BitVector vector;

        public ArrowBitSetter(BitVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final int bitValue;
            if (value instanceof Boolean) {
                bitValue = (Boolean) value ? 1 : 0;
            } else if (value instanceof Number) {
                bitValue = ((Number) value).intValue() != 0 ? 1 : 0;
            } else {
                bitValue = Boolean.parseBoolean(value.toString()) ? 1 : 0;
            }
            vector.setSafe(index, bitValue);
        }
    }

    public static class ArrowByteSetter extends ArrowSetterBase
    {
        private final TinyIntVector vector;

        public ArrowByteSetter(TinyIntVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            vector.setSafe(index, ((Number) value).byteValue());
        }
    }

    public static class ArrowShortSetter extends ArrowSetterBase
    {
        private final SmallIntVector vector;

        public ArrowShortSetter(SmallIntVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            vector.setSafe(index, ((Number) value).shortValue());
        }
    }

    public static class ArrowIntSetter extends ArrowSetterBase
    {
        private final IntVector vector;

        public ArrowIntSetter(IntVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            vector.setSafe(index, ((Number) value).intValue());
        }
    }

    public static class ArrowLongSetter extends ArrowSetterBase
    {
        private final BigIntVector vector;

        public ArrowLongSetter(BigIntVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            vector.setSafe(index, ((Number) value).longValue());
        }
    }

    public static class ArrowUInt1Setter extends ArrowSetterBase
    {
        private final UInt1Vector vector;

        public ArrowUInt1Setter(UInt1Vector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final byte uint1Value = UnsignedBytes.checkedCast(((Number) value).shortValue());
            vector.setSafe(index, uint1Value);
        }
    }

    public static class ArrowUInt2Setter extends ArrowSetterBase
    {
        private final UInt2Vector vector;

        public ArrowUInt2Setter(UInt2Vector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            @SuppressWarnings("PMD.AvoidUsingShortType")
            final short uint2Value;
            if (value instanceof Integer) {
                final int uint2IntValue = (Integer) value;
                Preconditions.checkArgument(uint2IntValue >> 16 == 0L, "out of range: %s", uint2IntValue);
                uint2Value = Shorts.checkedCast(uint2IntValue);
            } else {
                uint2Value = ((Number) value).shortValue();
            }
            vector.setSafe(index, uint2Value);
        }
    }

    public static class ArrowUInt4Setter extends ArrowSetterBase
    {
        private final UInt4Vector vector;

        public ArrowUInt4Setter(UInt4Vector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final int uint4Value = UnsignedInts.checkedCast(((Number) value).longValue());
            vector.setSafe(index, uint4Value);
        }
    }

    public static class ArrowUInt8Setter extends ArrowSetterBase
    {
        private final UInt8Vector vector;

        public ArrowUInt8Setter(UInt8Vector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final long uint8Value;
            if (value instanceof BigInteger) {
                uint8Value = ((BigInteger) value).longValueExact();
            } else {
                uint8Value = ((Number) value).longValue();
            }
            vector.setSafe(index, uint8Value);
        }
    }

    public static class ArrowFloatSetter extends ArrowSetterBase
    {
        private final Float4Vector vector;

        public ArrowFloatSetter(Float4Vector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0.f);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            vector.setSafe(index, ((Number) value).floatValue());
        }
    }

    public static class ArrowDoubleSetter extends ArrowSetterBase
    {
        private final Float8Vector vector;

        public ArrowDoubleSetter(Float8Vector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0.);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            vector.setSafe(index, ((Number) value).doubleValue());
        }
    }

    public static class ArrowDecimalSetter extends ArrowSetterBase
    {
        private final DecimalVector vector;

        public ArrowDecimalSetter(DecimalVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, BigDecimal.ZERO);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final BigDecimal decimalValue;
            if (value instanceof BigDecimal) {
                decimalValue = (BigDecimal) value;
            } else if (value instanceof BigInteger) {
                decimalValue = new BigDecimal((BigInteger) value);
            } else {
                decimalValue = new BigDecimal(value.toString());
            }
            final int decimalScale = vector.getScale();
            if (decimalScale >= 0) {
                vector.setSafe(index, decimalValue.setScale(decimalScale, RoundingMode.FLOOR));
            } else {
                vector.setSafe(index, decimalValue);
            }
        }
    }

    public static class ArrowDateMilliSetter extends ArrowSetterBase
    {
        private final DateMilliVector vector;

        public ArrowDateMilliSetter(DateMilliVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final java.sql.Date dateMilliValue = TemporalUtils.javaDateToDate(value);
            vector.setSafe(index, dateMilliValue.getTime());
        }
    }

    public static class ArrowDateDaySetter extends ArrowSetterBase
    {
        private final DateDayVector vector;

        public ArrowDateDaySetter(DateDayVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final java.sql.Date dateDayValue = TemporalUtils.javaDateToDate(value);
            vector.setSafe(index, TemporalUtils.millisToDays(dateDayValue.getTime()));
        }
    }

    public static class ArrowDurationSetter extends ArrowSetterBase
    {
        private final DurationVector vector;

        public ArrowDurationSetter(DurationVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final TimeUnit durationUnit = ((ArrowType.Duration) vector.getField().getType()).getUnit();
            final Duration durationValue;
            if (value instanceof Duration) {
                durationValue = (Duration) value;
            } else if (value instanceof Number) {
                durationValue = DurationVector.toDuration(((Number) value).longValue(), durationUnit);
            } else {
                throw new IllegalArgumentException("Unrecognized Duration value: " + value);
            }
            final long durationNanos = java.util.concurrent.TimeUnit.SECONDS.toNanos(durationValue.getSeconds()) + durationValue.getNano();
            switch (durationUnit) {
            case SECOND:
                vector.setSafe(index, java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(durationNanos));
                break;
            case MICROSECOND:
                vector.setSafe(index, java.util.concurrent.TimeUnit.NANOSECONDS.toMicros(durationNanos));
                break;
            case MILLISECOND:
                vector.setSafe(index, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(durationNanos));
                break;
            case NANOSECOND:
                vector.setSafe(index, durationNanos);
                break;
            default:
                break;
            }
        }
    }

    public static class ArrowIntervalDaySetter extends ArrowSetterBase
    {
        private final IntervalDayVector vector;

        public ArrowIntervalDaySetter(IntervalDayVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final Duration intervalDayValue = (Duration) value;
            final long intervalDayDays = intervalDayValue.toDays();
            final long intervalDayMillis = intervalDayValue.minusDays(intervalDayDays).toMillis();
            vector.setSafe(index, (int) intervalDayDays, (int) intervalDayMillis);
        }
    }

    public static class ArrowIntervalYearSetter extends ArrowSetterBase
    {
        private final IntervalYearVector vector;

        public ArrowIntervalYearSetter(IntervalYearVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final Period intervalYearValue = (Period) value;
            vector.setSafe(index, intervalYearValue.getMonths());
        }
    }

    public static class ArrowTimeNanoSetter extends ArrowSetterBase
    {
        private final TimeNanoVector vector;

        public ArrowTimeNanoSetter(TimeNanoVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final Instant timeNanoValue = TemporalUtils.instantToUtc(TemporalUtils.timeToInstant(TemporalUtils.javaDateToTime(value)));
            vector.setSafe(index, TemporalUtils.instantToNanos(timeNanoValue));
        }
    }

    public static class ArrowTimeMicroSetter extends ArrowSetterBase
    {
        private final TimeMicroVector vector;

        public ArrowTimeMicroSetter(TimeMicroVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final Time timeOfDay = TemporalUtils.javaDateToTime(value);
            final long millis = timeOfDay.getTime();
            long totalMicros = java.util.concurrent.TimeUnit.MILLISECONDS.toMicros(millis);
            if (timeOfDay instanceof MicrosecondTime) {
                // Need to back out the microseconds stored, but not returned in getTime()
                final long micros = ((MicrosecondTime) timeOfDay).getMicroseconds();
                final long microToMillis = java.util.concurrent.TimeUnit.MICROSECONDS.toMillis(totalMicros);
                final long microTruncated = java.util.concurrent.TimeUnit.MILLISECONDS.toMicros(microToMillis);
                final long microRemainder = micros - microTruncated;
                totalMicros += microRemainder;
            }
            vector.setSafe(index, totalMicros);
        }
    }

    public static class ArrowTimeMilliSetter extends ArrowSetterBase
    {
        private final TimeMilliVector vector;

        public ArrowTimeMilliSetter(TimeMilliVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            vector.setSafe(index, (int) TemporalUtils.javaDateToTime(value).getTime());
        }
    }

    public static class ArrowTimeSecSetter extends ArrowSetterBase
    {
        private final TimeSecVector vector;

        public ArrowTimeSecSetter(TimeSecVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final Instant timeSecondValue = TemporalUtils.instantToUtc(TemporalUtils.timeToInstant(TemporalUtils.javaDateToTime(value)));
            vector.setSafe(index, (int) TemporalUtils.instantToSeconds(timeSecondValue));
        }
    }

    public static class ArrowTimeStampNanoSetter extends ArrowSetterBase
    {
        private final TimeStampNanoVector vector;

        public ArrowTimeStampNanoSetter(TimeStampNanoVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final java.sql.Timestamp timestamp = TemporalUtils.javaDateToTimestamp(value);
            vector.setSafe(index, TemporalUtils.instantToNanos(timestamp.toInstant()));
        }
    }

    public static class ArrowTimeStampMicroSetter extends ArrowSetterBase
    {
        private final TimeStampMicroVector vector;

        public ArrowTimeStampMicroSetter(TimeStampMicroVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final java.sql.Timestamp timestamp = TemporalUtils.javaDateToTimestamp(value);
            vector.setSafe(index, TemporalUtils.instantToMicros(timestamp.toInstant()));
        }
    }

    public static class ArrowTimeStampMilliSetter extends ArrowSetterBase
    {
        private final TimeStampMilliVector vector;

        public ArrowTimeStampMilliSetter(TimeStampMilliVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final java.sql.Timestamp timestamp = TemporalUtils.javaDateToTimestamp(value);
            vector.setSafe(index, TemporalUtils.instantToMillis(timestamp.toInstant()));
        }
    }

    public static class ArrowTimeStampSecSetter extends ArrowSetterBase
    {
        private final TimeStampSecVector vector;

        public ArrowTimeStampSecSetter(TimeStampSecVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final java.sql.Timestamp timestamp = TemporalUtils.javaDateToTimestamp(value);
            vector.setSafe(index, TemporalUtils.instantToSeconds(timestamp.toInstant()));
        }
    }

    public static class ArrowTimeStampNanoTZSetter extends ArrowSetterBase
    {
        private final TimeStampNanoTZVector vector;

        public ArrowTimeStampNanoTZSetter(TimeStampNanoTZVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final java.sql.Timestamp timestamp = TemporalUtils.javaDateToTimestamp(value);
            vector.setSafe(index, TemporalUtils.instantToNanos(timestamp.toInstant()));
        }
    }

    public static class ArrowTimeStampMicroTZSetter extends ArrowSetterBase
    {
        private final TimeStampMicroTZVector vector;

        public ArrowTimeStampMicroTZSetter(TimeStampMicroTZVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final java.sql.Timestamp timestamp = TemporalUtils.javaDateToTimestamp(value);
            vector.setSafe(index, TemporalUtils.instantToMicros(timestamp.toInstant()));
        }
    }

    public static class ArrowTimeStampMilliTZSetter extends ArrowSetterBase
    {
        private final TimeStampMilliTZVector vector;

        public ArrowTimeStampMilliTZSetter(TimeStampMilliTZVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final java.sql.Timestamp timestamp = TemporalUtils.javaDateToTimestamp(value);
            vector.setSafe(index, TemporalUtils.instantToMillis(timestamp.toInstant()));
        }
    }

    public static class ArrowTimeStampSecTZSetter extends ArrowSetterBase
    {
        private final TimeStampSecTZVector vector;

        public ArrowTimeStampSecTZSetter(TimeStampSecTZVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, 0);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final java.sql.Timestamp timestamp = TemporalUtils.javaDateToTimestamp(value);
            vector.setSafe(index, TemporalUtils.instantToSeconds(timestamp.toInstant()));
        }
    }

    public static class ArrowFixedSizeBinarySetter extends ArrowSetterBase
    {
        private final FixedSizeBinaryVector vector;

        public ArrowFixedSizeBinarySetter(FixedSizeBinaryVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, EMPTY_BYTE_ARRAY);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            vector.setSafe(index, (byte[]) value);
        }

        @Override
        public void setBytes(int index, byte[] buffer, int start, int length)
        {
            if (buffer == null || length == 0) {
                vector.setSafe(index, EMPTY_BYTE_ARRAY);
            } else {
                vector.setSafe(index, buffer, start, length);
            }
        }
    }

    public static class ArrowVarBinarySetter extends ArrowSetterBase
    {
        private final VarBinaryVector vector;

        public ArrowVarBinarySetter(VarBinaryVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, EMPTY_BYTE_ARRAY);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            vector.setSafe(index, (byte[]) value);
        }

        @Override
        public void setBytes(int index, byte[] buffer, int start, int length)
        {
            if (buffer == null || length == 0) {
                vector.setSafe(index, EMPTY_BYTE_ARRAY);
            } else {
                vector.setSafe(index, buffer, start, length);
            }
        }
    }

    public static class ArrowVarCharSetter extends ArrowSetterBase
    {
        private final VarCharVector vector;

        public ArrowVarCharSetter(VarCharVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, EMPTY_BYTE_ARRAY);
        }

        @Override
        public void reset()
        {
            vector.getValidityBuffer().setZero(0, vector.getValidityBuffer().capacity());
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            if (value instanceof byte[]) {
                vector.setSafe(index, (byte[]) value);
            } else {
                vector.setSafe(index, value.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        @Override
        public void setBytes(int index, byte[] buffer, int start, int length)
        {
            if (buffer == null || length == 0) {
                vector.setSafe(index, EMPTY_BYTE_ARRAY);
            } else {
                vector.setSafe(index, buffer, start, length);
            }
        }
    }

    public static class ArrowLargeDecimalSetter extends ArrowSetterBase
    {
        private final ArrowLargeDecimalVector vector;

        public ArrowLargeDecimalSetter(ArrowLargeDecimalVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @Override
        public void fillValue(int index)
        {
            vector.setSafe(index, BigDecimal.ZERO);
        }

        @Override
        public void reset()
        {
            vector.setValueCount(0);
        }

        @Override
        public void setValue(int index, Serializable value)
        {
            final BigDecimal largeDecimalValue;
            if (value instanceof BigDecimal) {
                largeDecimalValue = (BigDecimal) value;
            } else if (value instanceof BigInteger) {
                largeDecimalValue = new BigDecimal((BigInteger) value);
            } else if (value != null) {
                largeDecimalValue = new BigDecimal(value.toString());
            } else {
                largeDecimalValue = BigDecimal.ZERO;
            }
            vector.setSafe(index, largeDecimalValue);
        }
    }

    public static class ArrowNullSetter extends ArrowSetterBase
    {
        private final NullVector vector;

        public ArrowNullSetter(NullVector vector)
        {
            super(vector);
            this.vector = vector;
        }

        @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
        @Override
        public void fillValue(int index)
        {
            // Nothing to fill
        }

        @Override
        public void reset()
        {
            vector.setValueCount(0);
        }

        @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
        @Override
        public void setValue(int index, Serializable value)
        {
            // Nothing to set for NullVector
        }
    }

    /**
     * Setter for fields to arrow.
     */
    public static class ArrowFieldSetter implements FieldSetter
    {
        private final List<ArrowSetterBase> setters;
        private boolean rejected;
        private int vectorIndex;
        private boolean varCharAsBytes;

        /**
         * Construct a single field setter from list of individual field setters.
         *
         * @param setters
         */
        public ArrowFieldSetter(List<ArrowSetterBase> setters)
        {
            this.setters = setters;
            this.vectorIndex = 0;
        }

        /**
         * Construct a field setter from a VectorSchemaRoot.
         *
         * @param root
         * @param varCharAsBytes
         *            flag to indicate VarChar values should be set as bytes, should
         *            only be set if bytes are UTF-8 to match the Arrow spec.
         */
        public ArrowFieldSetter(VectorSchemaRoot root, boolean varCharAsBytes)
        {
            this(initSetters(root));
            this.varCharAsBytes = varCharAsBytes;
        }

        /**
         * Construct a field setter from a VectorSchemaRoot.
         */
        public ArrowFieldSetter(VectorSchemaRoot root)
        {
            this(root, false);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setValue(int index, Serializable value)
        {
            if (value == null) {
                setNull(index);
            } else {
                setters.get(index).setValue(vectorIndex, value);
            }
        }

        @Override
        public void setNull(int index)
        {
            setters.get(index).handleNull(index);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setBytes(int index, byte[] buffer, int start, int length)
        {
            setters.get(index).setBytes(vectorIndex, buffer, start, length);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean varCharAsBytes()
        {
            return varCharAsBytes;
        }

        /**
         * @param vectorIndex
         */
        public void setVectorIndex(int vectorIndex)
        {
            this.vectorIndex = vectorIndex;
        }

        /**
         * @param rejectFlag
         */
        @Override
        public void setReject(boolean rejectFlag)
        {
            rejected = rejectFlag;
        }

        /**
         * @return reject flag.
         */
        @Override
        public boolean isRejected()
        {
            return rejected;
        }

        /**
         * Reset previous set vector values.
         */
        public void reset()
        {
            for (final ArrowSetterBase setter : setters) {
                setter.reset();
            }
        }
    }

    private static class ArrowFieldGetter
    {
        private final CustomFlightAssetField[] fields;
        private final FieldReader[] readers;
        private int vectorIndex;

        public ArrowFieldGetter(List<CustomFlightAssetField> customFlightAssetFields, VectorSchemaRoot root)
        {
            fields = customFlightAssetFields.toArray(new CustomFlightAssetField[0]);
            readers = customFlightAssetFields.stream().map(f -> root.getVector(f.getName()).getReader()).toArray(FieldReader[]::new);
        }

        public Serializable getValue(int index)
        {
            final FieldReader reader = readers[index];
            reader.setPosition(vectorIndex);
            if (!reader.isSet()) {
                return null;
            }

            return getValueByReader(reader, index);
        }

        public Serializable getValueByReader(FieldReader reader, int index)
        {
            switch (reader.getMinorType()) {
            case BIGINT:
                return reader.readLong();
            case BIT:
                return reader.readBoolean();
            case DATEDAY:
                return TemporalUtils.millisToDate(TemporalUtils.daysToMillis(reader.readInteger()));
            case DATEMILLI:
                return TemporalUtils.localDateToDate(reader.readLocalDateTime().toLocalDate());
            case DECIMAL:
                final BigDecimal decimalValue = reader.readBigDecimal();
                final CustomFlightAssetField field = fields[index];
                if ("bigint".equals(field.getType())) {
                    return decimalValue.toBigIntegerExact();
                } else if (field.getScale() >= 0) {
                    return decimalValue.setScale(field.getScale(), RoundingMode.FLOOR);
                } else {
                    return decimalValue;
                }
            case DURATION:
                return reader.readDuration();
            case FIXEDSIZEBINARY:
                return reader.readByteArray();
            case FLOAT4:
                return reader.readFloat();
            case FLOAT8:
                return reader.readDouble();
            case INT:
                return reader.readInteger();
            case INTERVALDAY:
                return reader.readDuration();
            case INTERVALYEAR:
                return reader.readPeriod();
            case LIST: {
                final ArrayList<Serializable> arrayList = new ArrayList<>();
                while (reader.next()) {
                    final FieldReader fieldReader = reader.reader();
                    if (fieldReader.isSet()) {
                        arrayList.add(getValueByReader(fieldReader, fieldReader.getPosition()));
                    }
                }
                return arrayList;
            }
            case SMALLINT:
                return reader.readShort();
            case TIMEMICRO:
                final long micros = reader.readLong();
                final long millis = java.util.concurrent.TimeUnit.MICROSECONDS.toMillis(micros);
                return new Time(millis);
            case TIMEMILLI:
                final NullableTimeMilliHolder holder = new NullableTimeMilliHolder();
                reader.read(holder);
                return TemporalUtils.millisToTime(holder.value);
            case TIMENANO:
                return TemporalUtils.instantToTime(TemporalUtils.instantFromUtc(TemporalUtils.nanosToInstant(reader.readLong())), false);
            case TIMESEC:
                return TemporalUtils.instantToTime(TemporalUtils.instantFromUtc(Instant.ofEpochSecond(reader.readInteger())), false);
            case TIMESTAMPMICRO:
                return java.sql.Timestamp.from(TemporalUtils.microsToInstant(reader.readLong()));
            case TIMESTAMPMICROTZ:
                return java.sql.Timestamp.from(TemporalUtils.instantFromUtc(TemporalUtils.microsToInstant(reader.readLong())));
            case TIMESTAMPMILLI:
                return java.sql.Timestamp.from(TemporalUtils.millisToInstant(reader.readLong()));
            case TIMESTAMPMILLITZ:
                return java.sql.Timestamp.from(TemporalUtils.instantFromUtc(TemporalUtils.millisToInstant(reader.readLong())));
            case TIMESTAMPNANO:
                return java.sql.Timestamp.from(TemporalUtils.nanosToInstant(reader.readLong()));
            case TIMESTAMPNANOTZ:
                return java.sql.Timestamp.from(TemporalUtils.instantFromUtc(TemporalUtils.nanosToInstant(reader.readLong())));
            case TIMESTAMPSEC:
                return java.sql.Timestamp.from(TemporalUtils.secondsToInstant(reader.readLong()));
            case TIMESTAMPSECTZ:
                return java.sql.Timestamp.from(TemporalUtils.instantFromUtc(TemporalUtils.secondsToInstant(reader.readLong())));
            case TINYINT:
                return reader.readByte();
            case UINT1:
                return reader.readByte();
            case UINT2:
                return Shorts.checkedCast(reader.readCharacter());
            case UINT4:
                return reader.readInteger();
            case UINT8:
                return reader.readLong();
            case VARBINARY:
                return reader.readByteArray();
            case VARCHAR:
                return reader.readText().toString();
            default:
                throw new UnsupportedOperationException("Unsupported arrow type: " + reader.getMinorType().name());
            }
        }
    }
}
