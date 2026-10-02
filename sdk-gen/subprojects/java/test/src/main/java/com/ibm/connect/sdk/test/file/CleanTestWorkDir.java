/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test.file;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Gradle-invocable main class that cleans the test work directory after the
 * {@code test} task completes (whether tests passed or failed).
 *
 * <p>
 * Invoked by the {@code cleanTestWorkDir} Gradle task defined in each
 * connector's {@code build.gradle}:
 *
 * <pre>
 * task cleanTestWorkDir(type: JavaExec) {
 *     classpath = sourceSets.test.runtimeClasspath
 *     mainClass = 'com.ibm.connect.sdk.test.file.CleanTestWorkDir'
 *     systemProperties = System.properties
 *     args = [project.buildDir.absolutePath, 'file_localfs.test_work_dir']
 * }
 * test.finalizedBy cleanTestWorkDir
 * </pre>
 *
 * <p>
 * Arguments: same as {@link VerifyWorkDir}.
 *
 * <p>
 * This task always runs after {@code test} — including when tests fail —
 * because it is registered with {@code finalizedBy}. This prevents orphaned
 * test data from blocking subsequent runs.
 */
public final class CleanTestWorkDir
{
    private CleanTestWorkDir()
    {
    }

    /**
     * Entry point.
     *
     * @param args
     *            [0] buildDir, [1] configKey, [2] connectorType (optional)
     * @throws Exception
     *             if cleanup fails unexpectedly
     */
    public static void main(String[] args) throws Exception
    {
        if (args.length < 2) {
            System.err.println("[CleanTestWorkDir] Usage: CleanTestWorkDir <buildDir> <configKey> [connectorType]");
            System.exit(1);
        }
        final Path buildDir = Paths.get(args[0]);
        final String configKey = args[1];
        final WorkDirManager.ConnectorType type = args.length >= 3
                ? WorkDirManager.ConnectorType.valueOf(args[2].toUpperCase(Locale.ENGLISH))
                : WorkDirManager.ConnectorType.HIERARCHICAL_FS;

        final WorkDirManager manager = new WorkDirManager(configKey, type, buildDir, null);
        manager.clean();
    }
}
