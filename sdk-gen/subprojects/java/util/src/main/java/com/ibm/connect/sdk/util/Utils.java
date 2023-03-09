/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.arrow.flight.Location;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.commons.text.StringSubstitutor;

import com.ibm.wdp.connect.common.sdk.api.models.CustomFlightAssetField;

/**
 * Utility methods.
 */
public class Utils
{
    private static final Pattern BYTE_LIMIT = Pattern.compile("(\\d*\\.?\\d*)([kmgtKMGT][bB])?"); // NOSONAR
    private static final String[] BYTE_LIMIT_SUFFIXES = { "KB", "MB", "GB", "TB" };

    /**
     * Returns a list of asset fields for the given schema.
     *
     * @param schema
     *            a Flight schema
     * @return a list of asset fields for the given schema
     * @throws Exception
     */
    public static List<CustomFlightAssetField> getAssetFields(Schema schema) throws Exception
    {
        final ModelMapper modelMapper = new ModelMapper();
        final List<CustomFlightAssetField> fields = new ArrayList<>();
        for (final Field field : schema.getFields()) {
            final CustomFlightAssetField assetField
                    = modelMapper.fromBytes(modelMapper.toBytes(field.getMetadata()), CustomFlightAssetField.class);
            assetField.setName(field.getName());
            fields.add(assetField);
        }
        return fields;
    }

    /**
     * Return an available local port number.
     *
     * @return an available local port number.
     */
    public static int getFreePort()
    {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
        catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Create a Flight server location.
     *
     * @param host
     *            Host name
     * @param port
     *            Port number
     * @param useSSL
     *            true if the server uses SSL
     * @return a Flight server location.
     */
    public static Location createLocation(String host, int port, boolean useSSL)
    {
        return useSSL ? Location.forGrpcTls(host, port) : Location.forGrpcInsecure(host, port);
    }

    /**
     * Parse a byte_limit property and return a long representing that limit.
     *
     * @param byteLimit
     *            the text of the byte_limit property.
     * @return the byte_limit.
     */
    public static long parseByteLimit(String byteLimit)
    {
        final Matcher m = BYTE_LIMIT.matcher(byteLimit);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid byte limit");
        }
        double value = Double.parseDouble(m.group(1));
        if (m.group(2) != null) {
            for (final String suffix : BYTE_LIMIT_SUFFIXES) {
                value = value * 1024;
                if (suffix.equalsIgnoreCase(m.group(2))) {
                    break;
                }
            }
        }
        return (long) value;
    }

    /**
     * Substitute matched tokens in the passed string, unmatched tokens are ignored
     *
     * @param str
     *            the string containing tokens
     * @param tokens
     *            the token keys and values
     * @return original string with matched tokens replaced
     */
    public static String substituteTokens(String str, Map<String, String> tokens)
    {
        final StringSubstitutor ss = new StringSubstitutor(tokens);
        ss.setEnableUndefinedVariableException(false);
        return ss.replace(str);
    }
}
