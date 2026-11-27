/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Locale;

import org.slf4j.Logger;

/**
 * Redirect handling of the outbound HTTP calls, selected with the environment variable
 * {@value #ENV_VARIABLE}.
 *
 * <ul>
 *   <li>{@link #NEVER} - redirect responses are returned to the caller as they are;</li>
 *   <li>{@link #NORMAL} - redirects are followed, except those from https to http;</li>
 *   <li>{@link #ALWAYS} - all redirects are followed.</li>
 * </ul>
 *
 * <p>When the variable is not set (or has an unknown value) {@link #NORMAL} is used, which is
 * the behaviour of the connector before the variable was introduced.
 */
enum RedirectPolicy
{
    ALWAYS,
    NEVER,
    NORMAL;

    /** Name of the environment variable that selects the policy. */
    static final String ENV_VARIABLE = "CF_REDIRECT_POLICY";

    private static final Logger LOGGER = getLogger(RedirectPolicy.class);

    /**
     * Reads the policy from the environment.
     *
     * @param fallback
     *            the policy to use when the variable is not set or invalid
     * @return the configured policy, or {@code fallback}
     */
    static RedirectPolicy fromEnvironment(RedirectPolicy fallback)
    {
        return parse(System.getenv(ENV_VARIABLE), fallback);
    }

    /**
     * Parses a policy name (case-insensitive).
     *
     * @param value
     *            the raw value, may be {@code null} or blank
     * @param fallback
     *            the policy to use when {@code value} is {@code null}, blank or unknown
     * @return the parsed policy, or {@code fallback}
     */
    static RedirectPolicy parse(String value, RedirectPolicy fallback)
    {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Ignoring invalid value of {} (allowed: ALWAYS, NEVER, NORMAL); using {}", ENV_VARIABLE, fallback);
            return fallback;
        }
    }
}
