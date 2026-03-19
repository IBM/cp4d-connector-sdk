/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.arrow.flight.FlightConstants;
import org.apache.arrow.flight.FlightProducer.CallContext;
import org.apache.arrow.flight.ServerHeaderMiddleware;
import org.apache.http.HttpHeaders;

/**
 * The locale of the current thread.
 */
public class ThreadLocale
{
    private static final List<Locale> SUPPORTED_LOCALES = Collections.unmodifiableList(Arrays.asList(Locale.ENGLISH, Locale.FRENCH,
            Locale.GERMAN, Locale.ITALIAN, Locale.JAPANESE, Locale.KOREAN, Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE,
            Locale.forLanguageTag("es"), Locale.forLanguageTag("pt-BR"), Locale.forLanguageTag("ru"), Locale.forLanguageTag("sv")));

    private static final ThreadLocal<Locale> THREAD_LOCALE = ThreadLocal.withInitial(() -> Locale.getDefault());

    private ThreadLocale()
    {
        // prevent instantiation
    }

    /**
     * Sets the locale of the current thread.
     *
     * @param locale the locale of the current thread
     */
    public static void setLocale(Locale locale)
    {
        THREAD_LOCALE.set(locale);
    }

    /**
     * Sets the locale of the current thread from the given call context.
     *
     * @param context the call context
     */
    public static void setLocale(CallContext context)
    {
        final Locale locale = getLocaleFromContext(context);
        setLocale(locale != null ? locale : Locale.getDefault());
    }

    /**
     * Returns the locale from the given call context or null.
     *
     * @param context the call context
     * @return the locale from the given call context or null
     */
    private static Locale getLocaleFromContext(CallContext context)
    {
        if (context != null) {
            final ServerHeaderMiddleware middleware = context.getMiddleware(FlightConstants.HEADER_KEY);
            if (middleware != null) {
                final String acceptLanguage = middleware.headers().get(HttpHeaders.ACCEPT_LANGUAGE.toLowerCase(Locale.ENGLISH));
                if (acceptLanguage != null) {
                    return getLocaleFromAcceptLanguage(acceptLanguage);
                }
            }
        }
        return null;
    }

    private static Locale getLocaleFromAcceptLanguage(String acceptLanguage)
    {
        if (acceptLanguage != null && !acceptLanguage.isEmpty()) {
            return Locale.lookup(Locale.LanguageRange.parse(acceptLanguage), SUPPORTED_LOCALES);
        }
        return null;
    }

    /**
     * Returns the locale of the current thread.
     *
     * @return the locale of the current thread
     */
    public static Locale getLocale()
    {
        return THREAD_LOCALE.get();
    }
}
