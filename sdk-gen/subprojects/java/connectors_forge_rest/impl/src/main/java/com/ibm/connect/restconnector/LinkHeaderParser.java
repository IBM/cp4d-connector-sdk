/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.util.List;
import java.util.Locale;

/**
 * Minimal RFC 8288 {@code Link} header parser that locates the target of {@code rel="next"}.
 *
 * <p>Handles multiple header lines, commas inside {@code <URI>} and inside quoted parameter values,
 * quoted-string escapes, case-insensitive parameter names, unquoted parameter values, and
 * space-separated relation lists such as {@code rel="prev next"}. Malformed entries are ignored.
 */
final class LinkHeaderParser
{
    private LinkHeaderParser()
    {
    }

    /**
     * Returns the URI reference of the first link whose relation types include {@code next}.
     *
     * @param headerValues
     *            all values of the {@code Link} header (may be empty)
     * @return the URI reference, or {@code null} if there is none
     */
    static String findNext(List<String> headerValues)
    {
        for (final String value : headerValues) {
            final String next = findNext(value);
            if (next != null) {
                return next;
            }
        }
        return null;
    }

    private static String findNext(String header)
    {
        final int length = header.length();
        int pos = 0;
        while (pos < length) {
            pos = skipSeparators(header, pos);
            if (pos >= length) {
                break;
            }
            if (header.charAt(pos) != '<') {
                pos = skipToNextLink(header, pos);
                continue;
            }
            final int end = header.indexOf('>', pos + 1);
            if (end < 0) {
                break;
            }
            final String target = header.substring(pos + 1, end).trim();
            pos = end + 1;

            boolean isNext = false;
            while (pos < length && skipWhitespace(header, pos) < length && header.charAt(skipWhitespace(header, pos)) == ';') {
                pos = skipWhitespace(header, pos) + 1;
                pos = skipWhitespace(header, pos);
                final int nameStart = pos;
                while (pos < length && header.charAt(pos) != '=' && header.charAt(pos) != ';'
                        && header.charAt(pos) != ',') {
                    pos++;
                }
                final String name = header.substring(nameStart, pos).trim();
                String paramValue = "";
                if (pos < length && header.charAt(pos) == '=') {
                    pos = skipWhitespace(header, pos + 1);
                    final StringBuilder sb = new StringBuilder();
                    if (pos < length && header.charAt(pos) == '"') {
                        pos++;
                        while (pos < length && header.charAt(pos) != '"') {
                            if (header.charAt(pos) == '\\' && pos + 1 < length) {
                                pos++;
                            }
                            sb.append(header.charAt(pos++));
                        }
                        pos++; // closing quote
                    } else {
                        while (pos < length && header.charAt(pos) != ';' && header.charAt(pos) != ',') {
                            sb.append(header.charAt(pos++));
                        }
                    }
                    paramValue = sb.toString().trim();
                }
                if ("rel".equalsIgnoreCase(name) && hasRelation(paramValue, "next")) {
                    isNext = true;
                }
            }
            if (isNext && !target.isEmpty()) {
                return target;
            }
        }
        return null;
    }

    private static boolean hasRelation(String relValue, String relation)
    {
        for (final String token : relValue.split("\\s+")) {
            if (relation.equals(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static int skipWhitespace(String s, int pos)
    {
        int i = pos;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    private static int skipSeparators(String s, int pos)
    {
        int i = pos;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t' || s.charAt(i) == ',')) {
            i++;
        }
        return i;
    }

    /** Skips a malformed entry up to the next top-level comma. */
    private static int skipToNextLink(String s, int pos)
    {
        int i = pos;
        boolean quoted = false;
        while (i < s.length()) {
            final char c = s.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                break;
            }
            i++;
        }
        return i;
    }
}
