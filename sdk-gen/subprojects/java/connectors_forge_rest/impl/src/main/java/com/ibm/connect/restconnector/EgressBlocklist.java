/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;

/**
 * A list of IP addresses and CIDR ranges that the connector must not call.
 *
 * <p>The list is read once, when the blocklist is created, from the file {@value #FILE_NAME} in
 * the directory named by the environment variable {@value #DIR_ENV_VARIABLE} (default
 * {@value #DEFAULT_DIR}); in the deployments this is a mounted ConfigMap that is empty by default.
 * A missing directory, a missing file or an empty file means that nothing is blocked. The file is
 * not watched: to change the list, update the file and restart the pod.
 *
 * <p>File format - one entry per line:
 * <pre>
 * # a comment
 * 169.254.169.254        # a single IPv4 address, trailing comments are allowed
 * 10.0.0.0/8             # an IPv4 range
 * fd00::/8               # an IPv6 range
 * </pre>
 * Lines that are not a valid IP address or CIDR range are logged as errors and skipped.
 *
 * <p>Before a request is sent, the host of the target URL is resolved and the request is rejected
 * if any resulting address falls into a listed entry. IPv4-mapped IPv6 addresses
 * ({@code ::ffff:a.b.c.d}) are matched as the IPv4 address they carry.
 */
final class EgressBlocklist
{
    /** Environment variable naming the directory that holds the blocklist file. */
    static final String DIR_ENV_VARIABLE = "CF_BLOCKLIST_DIR";

    /** Directory used when {@value #DIR_ENV_VARIABLE} is not set. */
    static final String DEFAULT_DIR = "/config/blocklist";

    /** Name of the blocklist file. */
    static final String FILE_NAME = "blocked-ips.txt";

    private static final Logger LOGGER = getLogger(EgressBlocklist.class);

    private static final Pattern IPV4 =
            Pattern.compile("([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})");
    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9A-Fa-f:.]+");
    private static final Pattern PREFIX = Pattern.compile("[0-9]{1,3}");

    private static final int IPV4_BYTES = 4;
    private static final int BITS_PER_BYTE = 8;
    private static final int MAX_OCTET = 255;

    private static EgressBlocklist shared;

    private final List<Entry> entries;

    /**
     * Loads the blocklist from a file.
     *
     * @param file
     *            the blocklist file; it does not have to exist
     */
    EgressBlocklist(Path file)
    {
        this.entries = load(file);
    }

    /**
     * The blocklist configured through the environment, shared by all HTTP clients of the process.
     *
     * @return the shared blocklist
     */
    static synchronized EgressBlocklist shared()
    {
        if (shared == null) {
            final String dir = System.getenv(DIR_ENV_VARIABLE);
            final String directory = dir == null || dir.trim().isEmpty() ? DEFAULT_DIR : dir.trim();
            shared = new EgressBlocklist(Paths.get(directory, FILE_NAME));
        }
        return shared;
    }

    /**
     * Fails if the target of a request is on the blocklist.
     *
     * @param uri
     *            the request URI
     * @throws IOException
     *             if the host cannot be resolved or resolves to a blocked address. The message is
     *             generic because it can reach the end user; details are logged.
     */
    void check(URI uri) throws IOException
    {
        if (entries.isEmpty() || uri.getHost() == null) {
            return;
        }
        for (final InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            final byte[] raw = address.getAddress();
            for (final Entry entry : entries) {
                if (entry.matches(raw)) {
                    LOGGER.warn("Outbound request to host '{}' blocked: address {} matches blocklist entry '{}'",
                            uri.getHost(), address.getHostAddress(), entry.text);
                    throw new IOException("Outbound request blocked by policy.");
                }
            }
        }
    }

    /**
     * The number of valid entries that were loaded.
     *
     * @return the number of entries
     */
    int entryCount()
    {
        return entries.size();
    }

    // ---- loading ----

    private static List<Entry> load(Path file)
    {
        if (!Files.isRegularFile(file)) {
            LOGGER.info("No blocklist file at '{}'; no IP addresses are blocked", file);
            return Collections.emptyList();
        }
        final List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Cannot read blocklist file '{}'; no IP addresses are blocked: {}", file, e.getMessage());
            return Collections.emptyList();
        }
        final List<Entry> loaded = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            final int comment = line.indexOf('#');
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                loaded.add(parseEntry(line));
            } catch (IllegalArgumentException e) {
                LOGGER.error("Blocklist '{}' line {}: invalid entry '{}' skipped: {}", file, i + 1, line, e.getMessage());
            }
        }
        LOGGER.info("Loaded {} blocklist entr{} from '{}'", loaded.size(), loaded.size() == 1 ? "y" : "ies", file);
        return Collections.unmodifiableList(loaded);
    }

    // ---- parsing ----

    /**
     * Parses a single blocklist entry.
     *
     * @param text
     *            an IPv4 or IPv6 address, optionally followed by {@code /prefix}
     * @return the entry
     * @throws IllegalArgumentException
     *             if the text is not an IP address or CIDR range
     */
    static Entry parseEntry(String text)
    {
        final int slash = text.indexOf('/');
        final byte[] address = parseAddress(slash < 0 ? text : text.substring(0, slash));
        int prefix = address.length * BITS_PER_BYTE;
        if (slash >= 0) {
            final String prefixText = text.substring(slash + 1);
            if (!PREFIX.matcher(prefixText).matches()) {
                throw new IllegalArgumentException("invalid prefix length");
            }
            prefix = Integer.parseInt(prefixText);
            if (prefix > address.length * BITS_PER_BYTE) {
                throw new IllegalArgumentException("prefix length out of range");
            }
        }
        return new Entry(text, address, prefix);
    }

    private static byte[] parseAddress(String text)
    {
        final Matcher v4 = IPV4.matcher(text);
        if (v4.matches()) {
            final byte[] bytes = new byte[IPV4_BYTES];
            for (int i = 0; i < IPV4_BYTES; i++) {
                final int octet = Integer.parseInt(v4.group(i + 1));
                if (octet > MAX_OCTET) {
                    throw new IllegalArgumentException("octet out of range");
                }
                bytes[i] = (byte) octet;
            }
            return bytes;
        }
        if (text.indexOf(':') >= 0 && IPV6_CHARS.matcher(text).matches()) {
            final byte[] bytes;
            try {
                // contains ':' and only hex digits, so this is parsed as a literal and never resolved
                bytes = InetAddress.getByName(text).getAddress();
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("not a valid IPv6 address", e);
            }
            if (bytes.length == IPV4_BYTES) {
                throw new IllegalArgumentException("IPv4-mapped IPv6 address, use the IPv4 notation instead");
            }
            return bytes;
        }
        throw new IllegalArgumentException("not an IP address or CIDR range");
    }

    // ---- model ----

    /** One blocked address or range. */
    static final class Entry
    {
        private final String text;
        private final byte[] network;
        private final int prefix;

        Entry(String text, byte[] network, int prefix)
        {
            this.text = text;
            this.network = network;
            this.prefix = prefix;
        }

        boolean matches(byte[] address)
        {
            if (address.length != network.length) {
                return false;
            }
            final int wholeBytes = prefix / BITS_PER_BYTE;
            for (int i = 0; i < wholeBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            final int remainingBits = prefix % BITS_PER_BYTE;
            if (remainingBits == 0) {
                return true;
            }
            final int mask = (MAX_OCTET << (BITS_PER_BYTE - remainingBits)) & MAX_OCTET;
            return (address[wholeBytes] & mask) == (network[wholeBytes] & mask);
        }
    }
}
