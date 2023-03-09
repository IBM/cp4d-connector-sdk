/* *************************************************** */

/* (C) Copyright IBM Corp. 2022                        */

/* *************************************************** */
package com.ibm.connect.sdk.api;

import java.sql.Time;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class MicrosecondTime extends Time
{
    private static final long serialVersionUID = 1L;
    private int microseconds;

    public MicrosecondTime(long time)
    {
        super(time);
        this.setTime(time);
    }

    public void setMicroseconds(int microseconds)
    {
        if (microseconds >= 0 && microseconds <= 999999) {
            this.microseconds = microseconds;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public int getMicroseconds()
    {
        return this.microseconds;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + this.microseconds;
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        } else if (!super.equals(obj)) {
            return false;
        } else if (this.getClass() != obj.getClass()) {
            return false;
        } else {
            final MicrosecondTime other = (MicrosecondTime) obj;
            return this.microseconds == other.microseconds;
        }
    }

    @Override
    public void setTime(long time)
    {
        long millisec = time % 1000L;
        time -= millisec;
        if (millisec < 0L) {
            millisec += 1000L;
            time -= 1000L;
        }

        this.setMicroseconds((int) millisec * 1000);
        super.setTime(time);
    }

    @Override
    public long getTime()
    {
        return super.getTime() + TimeUnit.MICROSECONDS.toMillis((long) this.microseconds);
    }

    @Override
    public Instant toInstant()
    {
        return Instant.ofEpochSecond(TimeUnit.MILLISECONDS.toSeconds(this.getTime()),
                TimeUnit.MICROSECONDS.toNanos((long) this.microseconds));
    }

    @Override
    public LocalTime toLocalTime()
    {
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(getTime());
        return LocalTime.of(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND),
                (int) TimeUnit.MICROSECONDS.toNanos((long) this.microseconds));
    }

    public static MicrosecondTime valueOf(Instant instant)
    {
        final MicrosecondTime microTime = new MicrosecondTime(instant.toEpochMilli());
        microTime.setMicroseconds((int) TimeUnit.NANOSECONDS.toMicros((long) instant.getNano()));
        return microTime;
    }

    public static MicrosecondTime valueOf(LocalTime localTime)
    {
        final MicrosecondTime microsTime = new MicrosecondTime(Time.valueOf(localTime).getTime());
        microsTime.setMicroseconds((int) TimeUnit.NANOSECONDS.toMicros((long) localTime.getNano()));
        return microsTime;
    }
}
