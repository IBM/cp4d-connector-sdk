/* *************************************************** */

/* (C) Copyright IBM Corp. 2026                        */

/* *************************************************** */
package com.ibm.connect.restconnector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.rules.TemporaryFolder;

/**
 * Helpers for tests that need an {@link EgressBlocklist} backed by a real file.
 */
final class BlocklistTestSupport
{
    private BlocklistTestSupport()
    {
    }

    /**
     * Writes a new file with the given content into the folder.
     *
     * @param folder
     *            the folder that owns (and cleans up) the file
     * @param content
     *            the exact content of the file
     * @return the file
     * @throws IOException
     *             if the file cannot be written
     */
    static Path fileWith(TemporaryFolder folder, String content) throws IOException
    {
        final Path file = folder.newFile().toPath();
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /**
     * Creates a blocklist loaded from a new file with the given lines.
     *
     * @param folder
     *            the folder that owns (and cleans up) the file
     * @param lines
     *            the lines of the file
     * @return the loaded blocklist
     * @throws IOException
     *             if the file cannot be written
     */
    static EgressBlocklist blocklistWith(TemporaryFolder folder, String... lines) throws IOException
    {
        return new EgressBlocklist(fileWith(folder, String.join("\n", lines)));
    }
}
