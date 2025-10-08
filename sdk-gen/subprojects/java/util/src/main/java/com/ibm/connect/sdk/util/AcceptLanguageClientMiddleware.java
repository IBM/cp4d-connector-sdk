/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.util;

import java.util.Locale;

import org.apache.arrow.flight.CallHeaders;
import org.apache.arrow.flight.CallInfo;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.FlightClientMiddleware;
import org.apache.http.HttpHeaders;

/**
 * Flight client-side middleware to pass an Accept-Language header.
 */
public class AcceptLanguageClientMiddleware implements FlightClientMiddleware
{
    private final Locale locale;

    protected AcceptLanguageClientMiddleware(Locale locale)
    {
        this.locale = locale;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBeforeSendingHeaders(CallHeaders outgoingHeaders)
    {
        outgoingHeaders.insert(HttpHeaders.ACCEPT_LANGUAGE.toLowerCase(Locale.ENGLISH), locale.toLanguageTag());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onHeadersReceived(CallHeaders incomingHeaders)
    {
        // Do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCallCompleted(CallStatus status)
    {
        // Do nothing
    }

    /**
     * A factory for AcceptLanguageMiddleware instances.
     */
    public static class Factory implements FlightClientMiddleware.Factory
    {
        private final Locale locale;

        /**
         * Constructs a factory for AcceptLanguageMiddleware instances.
         *
         * @param locale 
         */
        public Factory(Locale locale)
        {
            this.locale = locale;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public FlightClientMiddleware onCallStarted(CallInfo info)
        {
             return new AcceptLanguageClientMiddleware(locale);
        }
        
    }
}
