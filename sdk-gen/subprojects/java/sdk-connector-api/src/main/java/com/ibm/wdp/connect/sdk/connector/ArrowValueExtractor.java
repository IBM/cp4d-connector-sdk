/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.wdp.connect.sdk.connector;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.SmallIntVector;
import org.apache.arrow.vector.TimeMicroVector;
import org.apache.arrow.vector.TimeMilliVector;
import org.apache.arrow.vector.TimeStampMicroTZVector;
import org.apache.arrow.vector.TinyIntVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;

/**
 * Package-private helper that converts values between Java types and Arrow vectors.
 *
 * <p>Used internally by {@link ArrowBatchWriter}, {@link ColumnarArrowBatchWriter},
 * {@link ArrowBatchReader}, and {@link ColumnarArrowBatchReader}. Not part of the public API.
 */
final class ArrowValueExtractor
{
    private ArrowValueExtractor()
    {
        // utility class
    }

    /**
     * Extracts the value at {@code index} from {@code vector} as a plain Java object.
     *
     * @param vector
     *            the field vector
     * @param index
     *            the row index (must not be null at this index)
     * @return the Java value
     */
    static Object extract(FieldVector vector, int index)
    {
        if (vector instanceof VarCharVector) {
            final byte[] bytes = ((VarCharVector) vector).get(index);
            return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
        }
        if (vector instanceof IntVector) {
            return ((IntVector) vector).get(index);
        }
        if (vector instanceof BigIntVector) {
            return ((BigIntVector) vector).get(index);
        }
        if (vector instanceof Float8Vector) {
            return ((Float8Vector) vector).get(index);
        }
        if (vector instanceof Float4Vector) {
            return ((Float4Vector) vector).get(index);
        }
        if (vector instanceof BitVector) {
            return ((BitVector) vector).get(index) != 0;
        }
        if (vector instanceof SmallIntVector) {
            return (int) ((SmallIntVector) vector).get(index);
        }
        if (vector instanceof TinyIntVector) {
            return (int) ((TinyIntVector) vector).get(index);
        }
        if (vector instanceof DateDayVector) {
            final int days = ((DateDayVector) vector).get(index);
            return new Date(TimeUnit.DAYS.toMillis(days));
        }
        if (vector instanceof TimeStampMicroTZVector) {
            final long micros = ((TimeStampMicroTZVector) vector).get(index);
            final long millis = TimeUnit.MICROSECONDS.toMillis(micros);
            final int nanos = (int) TimeUnit.MICROSECONDS.toNanos(micros % 1000);
            final Timestamp ts = new Timestamp(millis);
            ts.setNanos(nanos >= 0 ? nanos : nanos + 1_000_000_000);
            return ts;
        }
        if (vector instanceof TimeMilliVector) {
            return new Time(((TimeMilliVector) vector).get(index));
        }
        if (vector instanceof TimeMicroVector) {
            return new Time(TimeUnit.MICROSECONDS.toMillis(((TimeMicroVector) vector).get(index)));
        }
        if (vector instanceof DecimalVector) {
            return ((DecimalVector) vector).getObject(index);
        }
        if (vector instanceof VarBinaryVector) {
            return ((VarBinaryVector) vector).get(index);
        }
        // Fallback: use getObject if available
        return vector.getObject(index);
    }

    /**
     * Sets the value at {@code index} in {@code vector} from a plain Java object.
     *
     * @param vector
     *            the field vector
     * @param index
     *            the row index
     * @param value
     *            the value to write; must not be null
     */
    static void setValue(FieldVector vector, int index, Object value)
    {
        if (vector instanceof VarCharVector) {
            final byte[] bytes = value instanceof byte[] ? (byte[]) value
                    : value.toString().getBytes(StandardCharsets.UTF_8);
            ((VarCharVector) vector).setSafe(index, bytes, 0, bytes.length);
        } else if (vector instanceof IntVector) {
            ((IntVector) vector).setSafe(index, toInt(value));
        } else if (vector instanceof BigIntVector) {
            ((BigIntVector) vector).setSafe(index, toLong(value));
        } else if (vector instanceof Float8Vector) {
            ((Float8Vector) vector).setSafe(index, toDouble(value));
        } else if (vector instanceof Float4Vector) {
            ((Float4Vector) vector).setSafe(index, toFloat(value));
        } else if (vector instanceof BitVector) {
            ((BitVector) vector).setSafe(index, toBit(value));
        } else if (vector instanceof SmallIntVector) {
            ((SmallIntVector) vector).setSafe(index, toInt(value));
        } else if (vector instanceof TinyIntVector) {
            ((TinyIntVector) vector).setSafe(index, toInt(value));
        } else if (vector instanceof DateDayVector) {
            ((DateDayVector) vector).setSafe(index, toDateDay(value));
        } else if (vector instanceof TimeStampMicroTZVector) {
            ((TimeStampMicroTZVector) vector).setSafe(index, toTimestampMicros(value));
        } else if (vector instanceof TimeMilliVector) {
            ((TimeMilliVector) vector).setSafe(index, toTimeMilli(value));
        } else if (vector instanceof TimeMicroVector) {
            ((TimeMicroVector) vector).setSafe(index, toTimeMicro(value));
        } else if (vector instanceof DecimalVector) {
            final DecimalVector dv = (DecimalVector) vector;
            dv.setSafe(index, toBigDecimal(value, dv.getScale()));
        } else if (vector instanceof VarBinaryVector) {
            final byte[] bytes = value instanceof byte[] ? (byte[]) value
                    : value.toString().getBytes(StandardCharsets.UTF_8);
            ((VarBinaryVector) vector).setSafe(index, bytes, 0, bytes.length);
        } else {
            // Fallback: attempt varchar
            final byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
            if (vector instanceof VarCharVector) {
                ((VarCharVector) vector).setSafe(index, bytes, 0, bytes.length);
            }
        }
    }

    static int toInt(Object v)
    {
        if (v instanceof Number) return ((Number) v).intValue();
        return Integer.parseInt(v.toString());
    }

    static long toLong(Object v)
    {
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(v.toString());
    }

    static double toDouble(Object v)
    {
        if (v instanceof Number) return ((Number) v).doubleValue();
        return Double.parseDouble(v.toString());
    }

    static float toFloat(Object v)
    {
        if (v instanceof Number) return ((Number) v).floatValue();
        return Float.parseFloat(v.toString());
    }

    static int toBit(Object v)
    {
        if (v instanceof Boolean) return ((Boolean) v) ? 1 : 0;
        if (v instanceof Number) return ((Number) v).intValue() != 0 ? 1 : 0;
        return Boolean.parseBoolean(v.toString()) ? 1 : 0;
    }

    static int toDateDay(Object v)
    {
        if (v instanceof Date) {
            return (int) TimeUnit.MILLISECONDS.toDays(((Date) v).getTime());
        }
        if (v instanceof java.util.Date) {
            return (int) TimeUnit.MILLISECONDS.toDays(((java.util.Date) v).getTime());
        }
        return (int) TimeUnit.MILLISECONDS.toDays(Date.valueOf(v.toString()).getTime());
    }

    static long toTimestampMicros(Object v)
    {
        if (v instanceof Timestamp) {
            final Timestamp ts = (Timestamp) v;
            return TimeUnit.MILLISECONDS.toMicros(ts.getTime())
                    + TimeUnit.NANOSECONDS.toMicros(ts.getNanos() % 1_000_000L);
        }
        if (v instanceof java.util.Date) {
            return TimeUnit.MILLISECONDS.toMicros(((java.util.Date) v).getTime());
        }
        return TimeUnit.MILLISECONDS.toMicros(Timestamp.valueOf(v.toString()).getTime());
    }

    static int toTimeMilli(Object v)
    {
        if (v instanceof Time) return (int) (((Time) v).getTime() % 86_400_000L);
        return (int) (Time.valueOf(v.toString()).getTime() % 86_400_000L);
    }

    static long toTimeMicro(Object v)
    {
        if (v instanceof Time) return TimeUnit.MILLISECONDS.toMicros(((Time) v).getTime() % 86_400_000L);
        return TimeUnit.MILLISECONDS.toMicros(Time.valueOf(v.toString()).getTime() % 86_400_000L);
    }

    static BigDecimal toBigDecimal(Object v, int scale)
    {
        final BigDecimal bd = v instanceof BigDecimal ? (BigDecimal) v : new BigDecimal(v.toString());
        return bd.setScale(scale, java.math.RoundingMode.HALF_UP);
    }
}

// Made with Bob
