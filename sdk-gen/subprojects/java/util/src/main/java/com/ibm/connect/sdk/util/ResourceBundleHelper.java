/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2025                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.util;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * A helper for formatting messages.
 * 
 * @param <E>
 *            the enumeration of the message IDs
 */
public class ResourceBundleHelper<E extends Enum<E>>
{
    private static final ThreadLocal<MessageFormat> MESSAGE_FORMAT = ThreadLocal.withInitial(() -> new MessageFormat(""));

    private final String enumClassName;
    private final Map<Locale, ResourceBundle> bundles;

    /**
     * An object that formats messages.
     *
     * @param <E>
     *            the enumeration of the message IDs
     */
    public interface MessageFormatter<E>
    {
        /**
         * Formats the given message arguments.
         *
         * @param args
         *            message arguments
         * @return the formatted message
         */
        String format(Object... args);
    }

    /**
     * Constructs a helper for formatting messages.
     *
     * @param enumClass
     *            the class that has enumerated the message IDs
     *
     */
    public ResourceBundleHelper(Class<E> enumClass)
    {
        enumClassName = enumClass.getName();
        bundles = new HashMap<>();
        getBundle(Locale.getDefault());
    }

    private ResourceBundle getBundle(Locale locale)
    {
        return bundles.computeIfAbsent(locale, l -> ResourceBundle.getBundle(enumClassName, l != null ? l : Locale.getDefault()));
    }

    private ResourceBundle getBundle(Locale locale, String msgId)
    {
        final ResourceBundle bundle = getBundle(locale);
        if (!bundle.containsKey(msgId)) {
            return getBundle(Locale.getDefault());
        }
        return bundle;
    }

    /**
     * Formats the given message ID and arguments.
     *
     * @param enumValue
     *            the message ID enum value
     * @param args
     *            the message arguments
     * @return the formatted message
     */
    public String format(E enumValue, Object... args)
    {
        final String id = enumValue.toString();
        final ResourceBundle bundle = getBundle(ThreadLocale.getLocale(), id);
        if (!bundle.containsKey(id)) {
            throw new IllegalArgumentException("Message id [" + id + "] not found.");
        }

        final String pattern = bundle.getString(id);
        if (args != null && args.length > 0) {
            final MessageFormat format = MESSAGE_FORMAT.get();
            format.setLocale(bundle.getLocale());
            format.applyPattern(pattern);
            return format.format(args);
        }
        return pattern;
    }
}
