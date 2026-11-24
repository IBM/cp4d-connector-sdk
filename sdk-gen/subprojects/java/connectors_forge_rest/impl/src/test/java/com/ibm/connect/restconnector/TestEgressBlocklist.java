/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link EgressBlocklist}. Only IP literals are used, so no DNS is involved.
 */
public class TestEgressBlocklist
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private EgressBlocklist blocklistWith(String... lines) throws IOException
    {
        final File file = folder.newFile();
        Files.write(file.toPath(), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        return new EgressBlocklist(file.toPath());
    }

    private static boolean matches(String entry, String address) throws Exception
    {
        return EgressBlocklist.parseEntry(entry).matches(InetAddress.getByName(address).getAddress());
    }

    private static void assertBlocked(EgressBlocklist blocklist, String url)
    {
        try {
            blocklist.check(URI.create(url));
            fail("Expected " + url + " to be blocked");
        } catch (IOException expected) {
            // the message reaches the end user, so it must not reveal the address or the rule
            assertEquals("Outbound request blocked by policy.", expected.getMessage());
        }
    }

    // ---- default: nothing blocked ----

    @Test
    public void testMissingFileBlocksNothing() throws Exception
    {
        final EgressBlocklist blocklist = new EgressBlocklist(folder.getRoot().toPath().resolve("does-not-exist.txt"));
        assertEquals(0, blocklist.entryCount());
        blocklist.check(URI.create("http://127.0.0.1/"));
        blocklist.check(URI.create("http://169.254.169.254/"));
    }

    @Test
    public void testMissingDirectoryBlocksNothing() throws Exception
    {
        final Path file = folder.getRoot().toPath().resolve("no-such-dir").resolve(EgressBlocklist.FILE_NAME);
        assertEquals(0, new EgressBlocklist(file).entryCount());
    }

    @Test
    public void testEmptyAndCommentOnlyFileBlocksNothing() throws Exception
    {
        final EgressBlocklist blocklist = blocklistWith("", "   ", "# 127.0.0.0/8", "   # 10.0.0.0/8", "\t");
        assertEquals(0, blocklist.entryCount());
        blocklist.check(URI.create("http://127.0.0.1/"));
        blocklist.check(URI.create("http://10.1.2.3/"));
    }

    // ---- loading ----

    @Test
    public void testCommentsAreIgnored() throws Exception
    {
        final EgressBlocklist blocklist = blocklistWith(
                "# blocked metadata service",
                "169.254.169.254   # trailing comment",
                "#10.0.0.0/8");
        assertEquals(1, blocklist.entryCount());
        assertBlocked(blocklist, "http://169.254.169.254/latest");
        blocklist.check(URI.create("http://10.1.2.3/"));
    }

    @Test
    public void testMalformedLinesAreSkippedAndValidOnesKept() throws Exception
    {
        final EgressBlocklist blocklist = blocklistWith(
                "10.0.0.0/8",
                "not-an-ip",
                "999.1.1.1",
                "10.0.0.0/33",
                "10.0.0.0/abc",
                "1.2.3",
                "evil.com",
                "fd00::/129",
                "a:b",
                "::ffff:1.2.3.4",
                "127.0.0.1");
        assertEquals(2, blocklist.entryCount());
        assertBlocked(blocklist, "http://10.9.9.9/");
        assertBlocked(blocklist, "http://127.0.0.1/");
        blocklist.check(URI.create("http://8.8.8.8/"));
    }

    @Test
    public void testWindowsLineEndingsAndSurroundingWhitespace() throws Exception
    {
        final File file = folder.newFile();
        Files.write(file.toPath(), "  10.0.0.0/8  \r\n\t192.168.0.1\r\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(2, new EgressBlocklist(file.toPath()).entryCount());
    }

    @Test
    public void testFileIsReadOnlyOnce() throws Exception
    {
        final File file = folder.newFile();
        Files.write(file.toPath(), "10.0.0.0/8".getBytes(StandardCharsets.UTF_8));
        final EgressBlocklist blocklist = new EgressBlocklist(file.toPath());
        Files.write(file.toPath(), "".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, blocklist.entryCount());
        assertBlocked(blocklist, "http://10.0.0.1/");
    }

    // ---- matching ----

    @Test
    public void testSingleIpv4() throws Exception
    {
        assertTrue(matches("169.254.169.254", "169.254.169.254"));
        assertFalse(matches("169.254.169.254", "169.254.169.253"));
        assertTrue(matches("169.254.169.254/32", "169.254.169.254"));
        assertFalse(matches("169.254.169.254/32", "169.254.169.255"));
    }

    @Test
    public void testIpv4Ranges() throws Exception
    {
        assertTrue(matches("10.0.0.0/8", "10.255.255.255"));
        assertFalse(matches("10.0.0.0/8", "11.0.0.0"));
        assertTrue(matches("127.0.0.0/8", "127.0.0.1"));
        assertTrue(matches("169.254.0.0/16", "169.254.1.1"));
        assertFalse(matches("169.254.0.0/16", "169.255.0.1"));
        // prefix that is not a multiple of 8
        assertTrue(matches("192.168.1.0/25", "192.168.1.127"));
        assertFalse(matches("192.168.1.0/25", "192.168.1.128"));
        assertTrue(matches("172.16.0.0/12", "172.31.255.255"));
        assertFalse(matches("172.16.0.0/12", "172.32.0.0"));
        // the host bits of the entry are ignored
        assertTrue(matches("10.1.2.3/8", "10.200.0.1"));
        // /0 covers every IPv4 address
        assertTrue(matches("0.0.0.0/0", "8.8.8.8"));
    }

    @Test
    public void testIpv6() throws Exception
    {
        assertTrue(matches("::1", "::1"));
        assertFalse(matches("::1", "::2"));
        assertTrue(matches("fd00::/8", "fd12:3456::1"));
        assertFalse(matches("fd00::/8", "fe80::1"));
        assertTrue(matches("fe80::/10", "febf::1"));
        assertFalse(matches("fe80::/10", "fec0::1"));
        assertTrue(matches("2001:db8::/32", "2001:db8:ffff::1"));
        assertFalse(matches("2001:db8::/32", "2001:db9::1"));
    }

    @Test
    public void testIpv4AndIpv6DoNotMatchEachOther() throws Exception
    {
        assertFalse(matches("0.0.0.0/0", "::1"));
        assertFalse(matches("::/0", "127.0.0.1"));
    }

    // ---- check() ----

    @Test
    public void testIpv4MappedIpv6IsMatchedAsIpv4() throws Exception
    {
        final EgressBlocklist blocklist = blocklistWith("127.0.0.0/8");
        assertBlocked(blocklist, "http://[::ffff:127.0.0.1]/");
        assertBlocked(blocklist, "http://[::ffff:7f00:1]/");
    }

    @Test
    public void testCheckIgnoresPortPathAndScheme() throws Exception
    {
        final EgressBlocklist blocklist = blocklistWith("10.0.0.0/8", "fd00::/8");
        assertBlocked(blocklist, "https://10.0.0.5:8443/a/b?c=d");
        assertBlocked(blocklist, "http://[fd00::1]:9000/");
        blocklist.check(URI.create("https://93.184.216.34/"));
    }

    @Test
    public void testUriWithoutHostIsNotChecked() throws Exception
    {
        blocklistWith("10.0.0.0/8").check(URI.create("/relative"));
    }

    @Test
    public void testSharedUsesTheDefaultLocationWhenNothingIsConfigured()
    {
        // /config/blocklist/blocked-ips.txt is not present in the build environment
        assertEquals(0, EgressBlocklist.shared().entryCount());
    }
}
