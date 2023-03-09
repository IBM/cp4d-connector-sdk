/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class TemporalUtils
{
    private static final long JULIAN_DAY_OF_EPOCH = 2440588;
    private static final long SECONDS_PER_DAY = TimeUnit.DAYS.toSeconds(1);
    private static final long MICROS_PER_DAY = TimeUnit.DAYS.toMicros(1);
    private static final long MILLIS_PER_DAY = TimeUnit.DAYS.toMillis(1);
    private static final int MIN_DAYS = -719162;
    private static final ThreadLocal<ZoneId> DEFAULT_ZONEID = ThreadLocal.withInitial(ZoneId::systemDefault);

    private TemporalUtils()
    {
        // prevent instantiation
    }

    public static java.sql.Date javaDateToDate(Serializable value)
    {
        if (value == null) {
            return null;
        } else if (value instanceof java.sql.Date) {
            return (java.sql.Date) value;
        } else if (value instanceof java.util.Date) {
            return new java.sql.Date(((java.util.Date) value).getTime());
        }
        throw new IllegalArgumentException("Unexpected date object type (" + value.getClass().getName() + ")");
    }

    public static java.sql.Time javaDateToTime(Serializable value)
    {
        if (value == null) {
            return null;
        } else if (value instanceof java.sql.Time) {
            // Convert to LocalTime to drop date portion stored with java.util.Date
            return MicrosecondTime.valueOf(timeToLocalTime((java.sql.Time) value, true));
        } else if (value instanceof java.util.Date) {
            // Convert to LocalTime to drop date portion stored with java.util.Date
            return MicrosecondTime.valueOf(new MicrosecondTime(((java.util.Date) value).getTime()).toLocalTime());
        }
        throw new IllegalArgumentException("Unexpected time object type (" + value.getClass().getName() + ")");
    }

    public static java.sql.Timestamp javaDateToTimestamp(Serializable value)
    {
        if (value == null) {
            return null;
        } else if (value instanceof java.sql.Timestamp) {
            return (java.sql.Timestamp) value;
        } else if (value instanceof java.util.Date) {
            return new java.sql.Timestamp(((java.util.Date) value).getTime());
        }
        throw new IllegalArgumentException("Unexpected timestamp object type (" + value.getClass().getName() + ")");
    }

    public static java.sql.Date millisToDate(long millis)
    {
        return new java.sql.Date(millis);
    }

    public static java.sql.Date instantToDate(Instant instant)
    {
        return new java.sql.Date(java.util.Date.from(instant).getTime());
    }

    public static LocalDate dateToLocalDate(java.sql.Date date)
    {
        return date.toLocalDate();
    }

    public static java.sql.Date localDateToDate(LocalDate localDate)
    {
        return java.sql.Date.valueOf(localDate);
    }

    public static int millisToDays(long millis)
    {
        final int days = (int) Math.floor(((double) millis + TimeZone.getDefault().getOffset(millis)) / MILLIS_PER_DAY);
        if (days < MIN_DAYS) {
            return MIN_DAYS;
        }
        return days;
    }

    public static long daysToMillis(int days)
    {
        final long daysMillis = TimeUnit.DAYS.toMillis(days);
        final long offsetMillis = getOffsetFromLocalMillis(daysMillis, TimeZone.getDefault());
        return daysMillis - offsetMillis;
    }

    public static java.sql.Time millisToTime(long millis)
    {
        return new java.sql.Time(millis);
    }

    public static Instant timeToInstant(java.sql.Time time)
    {
        return time instanceof MicrosecondTime ? time.toInstant() : Instant.ofEpochMilli(time.getTime());
    }

    public static java.sql.Time instantToTime(Instant instant, boolean microseconds)
    {
        return microseconds ? MicrosecondTime.valueOf(instant) : new java.sql.Time(java.util.Date.from(instant).getTime());
    }

    public static LocalTime timeToLocalTime(java.sql.Time time, boolean microseconds)
    {
        final LocalTime localTime;
        if (time instanceof MicrosecondTime) {
            localTime = time.toLocalTime();
        } else {
            localTime = new MicrosecondTime(time.getTime()).toLocalTime();
        }
        return microseconds ? localTime : localTime.truncatedTo(ChronoUnit.MILLIS);
    }

    public static java.sql.Time localTimeToTime(LocalTime localTime, boolean microseconds)
    {
        return microseconds ? MicrosecondTime.valueOf(localTime) : new java.sql.Time(localTime.truncatedTo(ChronoUnit.MILLIS)
                .atDate(LocalDate.ofEpochDay(0)).atZone(DEFAULT_ZONEID.get()).toInstant().toEpochMilli());
    }

    public static Instant timestampToInstant(java.sql.Timestamp timestamp)
    {
        return timestamp.toInstant();
    }

    public static java.sql.Timestamp instantToTimestamp(Instant instant)
    {
        return java.sql.Timestamp.from(instant);
    }

    public static LocalDateTime timestampToLocalDateTime(java.sql.Timestamp timestamp, boolean microseconds)
    {
        final LocalDateTime localDateTime = timestamp.toLocalDateTime();
        return microseconds ? localDateTime : localDateTime.truncatedTo(ChronoUnit.MILLIS);
    }

    public static java.sql.Timestamp localDateTimeToTimestamp(LocalDateTime localDateTime, boolean microseconds)
    {
        return java.sql.Timestamp.valueOf(!microseconds ? localDateTime.truncatedTo(ChronoUnit.MILLIS) : localDateTime);
    }

    public static long instantToSeconds(Instant instant)
    {
        return instant.getEpochSecond();
    }

    public static long instantToMillis(Instant instant)
    {
        return instant.toEpochMilli();
    }

    public static long instantToMicros(Instant instant)
    {
        return TimeUnit.SECONDS.toMicros(instantToSeconds(instant)) + TimeUnit.NANOSECONDS.toMicros(instant.getNano());
    }

    /**
     * Returns a long representing the number of nanoseconds from the Java epoch of
     * 1970-01-01T00:00:00Z.
     *
     * Note: Not all instants will fit into a long when converted to nanoseconds.
     * The earliest UTC timestamp would be "1677-09-21 00:12:43.145224192", and the
     * latest UTC timestamp would be "2262-04-11 23:47:16.854775807". Any instant
     * beyond that range will be returned as the lower or upper bound.
     *
     * Although the technical lower bound is "1677-09-21 00:12:43.145224192", the
     * practical lower bound is "1677-09-21 00:12:44", because converting the epoch
     * seconds for "1677-09-21 00:12:43" into nanoseconds will already cause an
     * underflow before we can add the nanoseconds.
     *
     * @param instant
     *            an instant in time
     * @return the nanoseconds from the epoch of 1970-01-01T00:00:00Z
     */
    public static long instantToNanos(Instant instant)
    {
        return TimeUnit.SECONDS.toNanos(instant.getEpochSecond()) + instant.getNano();
    }

    public static Instant secondsToInstant(long seconds)
    {
        return Instant.ofEpochSecond(seconds);
    }

    public static Instant millisToInstant(long millis)
    {
        return Instant.ofEpochMilli(millis);
    }

    public static Instant microsToInstant(long micros)
    {
        return microsToInstant(micros, true);
    }

    public static Instant microsToInstant(long micros, boolean microseconds)
    {
        final Instant instant = Instant.ofEpochSecond(TimeUnit.MICROSECONDS.toSeconds(micros),
                TimeUnit.MICROSECONDS.toNanos(micros % TimeUnit.SECONDS.toMicros(1)));
        return microseconds ? instant : instant.truncatedTo(ChronoUnit.MILLIS);
    }

    public static Instant nanosToInstant(long nanos)
    {
        return nanosToInstant(nanos, true);
    }

    public static Instant nanosToInstant(long nanos, boolean microseconds)
    {
        final Instant instant = Instant.ofEpochSecond(TimeUnit.NANOSECONDS.toSeconds(nanos), nanos % TimeUnit.SECONDS.toNanos(1));
        return microseconds ? instant : instant.truncatedTo(ChronoUnit.MILLIS);
    }

    public static Instant int96ToInstant(int julianDay, long timeOfDayNanos)
    {
        return int96ToInstant(julianDay, timeOfDayNanos, true);
    }

    public static Instant int96ToInstant(int julianDay, long timeOfDayNanos, boolean microseconds)
    {
        return microsToInstant(TimeUnit.SECONDS.toMicros((julianDay - JULIAN_DAY_OF_EPOCH) * SECONDS_PER_DAY)
                + TimeUnit.NANOSECONDS.toMicros(timeOfDayNanos), microseconds);
    }

    public static int microsToJulianDay(long micros)
    {
        return (int) ((micros + JULIAN_DAY_OF_EPOCH * MICROS_PER_DAY) / MICROS_PER_DAY);
    }

    public static long microsToTimeOfDayNanos(long micros)
    {
        return TimeUnit.MICROSECONDS.toNanos((micros + JULIAN_DAY_OF_EPOCH * MICROS_PER_DAY) % MICROS_PER_DAY);
    }

    public static Instant instantToUtc(Instant instant)
    {
        return instant.atZone(DEFAULT_ZONEID.get()).toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    public static Instant instantFromUtc(Instant instant)
    {
        return instant.atZone(ZoneOffset.UTC).toLocalDateTime().toInstant(OffsetDateTime.now(DEFAULT_ZONEID.get()).getOffset());
    }

    private static long getOffsetFromLocalMillis(long millis, TimeZone tz)
    {
        long guess = tz.getRawOffset();
        // the actual offset should be calculated based on milliseconds in UTC
        final long offset = tz.getOffset(millis - guess);
        if (offset != guess) {
            guess = tz.getOffset(millis - offset);
            if (guess != offset) {
                // fallback to do the reverse lookup using java.time.LocalDateTime this should
                // only happen near the start or end of DST
                final LocalDate localDate = LocalDate.ofEpochDay(TimeUnit.MILLISECONDS.toDays(millis));
                final LocalTime localTime = LocalTime.ofNanoOfDay(TimeUnit.MILLISECONDS.toNanos(Math.floorMod(millis, MILLIS_PER_DAY)));
                final LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime);
                final long millisEpoch = localDateTime.atZone(tz.toZoneId()).toInstant().toEpochMilli();
                guess = (int) (millis - millisEpoch);
            }
        }
        return guess;
    }
}
