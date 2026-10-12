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
 * Gradle-invocable main class that verifies (or provisions) the test work
 * directory before the {@code test} task runs.
 *
 * <p>
 * Invoked by the {@code verifyTestWorkDir} Gradle task defined in each
 * connector's {@code build.gradle}:
 *
 * <pre>
 * task verifyTestWorkDir(type: JavaExec) {
 *     classpath = sourceSets.test.runtimeClasspath
 *     mainClass = 'com.ibm.connect.sdk.test.file.VerifyWorkDir'
 *     systemProperties = System.properties
 *     args = [project.buildDir.absolutePath, 'file_localfs.test_work_dir']
 * }
 * test.dependsOn verifyTestWorkDir
 * </pre>
 *
 * <p>
 * Arguments:
 * <ol>
 * <li>{@code buildDir} — absolute path to the Gradle project build directory;
 * the resolved work-dir name is written to
 * {@code <buildDir>/test-work-dir.txt}.</li>
 * <li>{@code configKey} — the {@code tests.properties} key that holds the
 * work-dir value (e.g. {@code "file_localfs.test_work_dir"}).</li>
 * <li>{@code connectorType} — optional; one of {@code HIERARCHICAL_FS},
 * {@code OBJECT_STORE}, {@code READ_ONLY}. Defaults to
 * {@code HIERARCHICAL_FS}.</li>
 * </ol>
 *
 * <p>
 * Exits with status code {@code 0} on success and a non-zero code on failure,
 * which causes the Gradle build to fail before any test is executed.
 */
public final class VerifyWorkDir
{
    private VerifyWorkDir()
    {
    }

    /**
     * Entry point.
     *
     * @param args
     *            [0] buildDir, [1] configKey, [2] connectorType (optional)
     * @throws Exception
     *             if provisioning fails unexpectedly
     */
    public static void main(String[] args) throws Exception
    {
        if (args.length < 2) {
            System.err.println("[VerifyWorkDir] Usage: VerifyWorkDir <buildDir> <configKey> [connectorType]");
            System.exit(1);
        }
        final Path buildDir = Paths.get(args[0]);
        final String configKey = args[1];
        final WorkDirManager.ConnectorType type = args.length >= 3
                ? WorkDirManager.ConnectorType.valueOf(args[2].toUpperCase(Locale.ENGLISH))
                : WorkDirManager.ConnectorType.HIERARCHICAL_FS;

        final WorkDirManager manager = new WorkDirManager(configKey, type, buildDir, null);
        manager.provision();
    }
}
