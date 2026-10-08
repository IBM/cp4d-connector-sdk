/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2026                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test.file;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;

import com.ibm.connect.sdk.test.TestConfig;

/**
 * Manages the isolated work directory (or bucket / schema) used by the file
 * connector test suite to ensure test writes do not touch user data.
 *
 * <h3>Configuration</h3>
 * <p>
 * Each connector namespace exposes a single {@code test_work_dir} property:
 *
 * <pre>
 * # Connector-specific key — replace "file_localfs" / "file_s3" with your prefix
 * file_localfs.test_work_dir=TEMP          # auto-provision a temp directory
 * file_s3.test_work_dir=TEMP              # auto-provision a dedicated test bucket
 * file_s3.test_work_dir=my-scratch-bucket # use an existing, pre-created empty bucket
 * file_localfs.test_work_dir=/home/user/sdk-test-scratch
 * </pre>
 *
 * <p>
 * The special keyword {@code TEMP} instructs the manager to create a new
 * temporary container (directory or bucket) on {@link #provision()} and to
 * remove it entirely on {@link #clean()}. A user-supplied non-{@code TEMP}
 * value is treated as an existing container that must be empty before tests
 * start; it is emptied (but <em>not</em> deleted) on {@link #clean()}.
 *
 * <h3>Resolved path file</h3>
 * <p>
 * After provisioning, the resolved container name is written to
 * {@code <gradleProjectBuildDir>/test-work-dir.txt}. The test classes read
 * this file via {@link #getResolvedWorkDir()} so they always receive the
 * correct name even when {@code TEMP} was used and a UUID-based name was
 * generated.
 */
public final class WorkDirManager
{
    private static final Logger LOGGER = getLogger(WorkDirManager.class);

    /** The keyword that triggers automatic provisioning. */
    public static final String TEMP_KEYWORD = "TEMP";

    /** File name written under the Gradle build directory. */
    public static final String RESOLVED_FILE_NAME = "test-work-dir.txt";

    private final String configKey;
    private final ConnectorType connectorType;
    private final Path resolvedFile;
    private final WorkDirOperations operations;

    /**
     * Supported connector storage models — determines how "empty check",
     * "provision", and "clean" are implemented.
     */
    public enum ConnectorType
    {
        /** Local (or remote) hierarchical file system — isolation unit is a directory. */
        HIERARCHICAL_FS,
        /**
         * Flat object store (S3-compatible) — isolation unit is a bucket.
         * The {@link WorkDirManager} itself does not perform S3 API calls; it
         * delegates to the {@link WorkDirOperations} strategy supplied at
         * construction time.
         */
        OBJECT_STORE,
        /**
         * Read-only connector (e.g. GitHub) — no work directory needed;
         * all operations are no-ops and {@link #isRequired()} returns
         * {@code false}.
         */
        READ_ONLY
    }

    /**
     * Strategy interface that abstracts connector-specific storage operations.
     * Implementations are supplied by each connector's test class.
     */
    public interface WorkDirOperations
    {
        /**
         * Creates a new, empty container with the given name. For object stores
         * this is a bucket; for file systems this is a directory.
         *
         * @param name
         *            the container name to create
         * @throws Exception
         *             if creation fails
         */
        void create(String name) throws Exception;

        /**
         * Returns {@code true} if the container with the given name is empty
         * (contains no objects or files).
         *
         * @param name
         *            the container name to check
         * @return {@code true} if empty
         * @throws Exception
         *             if the check fails
         */
        boolean isEmpty(String name) throws Exception;

        /**
         * Removes all objects/files inside the container but does not destroy
         * the container itself (used for user-supplied named containers).
         *
         * @param name
         *            the container name to empty
         * @throws Exception
         *             if cleanup fails
         */
        void emptyContainer(String name) throws Exception;

        /**
         * Removes all content <em>and</em> destroys the container (used for
         * {@code TEMP}-provisioned containers).
         *
         * @param name
         *            the container name to destroy
         * @throws Exception
         *             if destruction fails
         */
        void destroyContainer(String name) throws Exception;

        /**
         * Generates a unique temporary container name. Default implementation
         * returns {@code "sdk-test-" + UUID}.
         *
         * @return a unique container name suitable for creation
         */
        default String generateTempName()
        {
            return "sdk-test-" + java.util.UUID.randomUUID().toString();
        }
    }

    /**
     * Constructs a manager for a hierarchical file system connector. Directory
     * operations are handled natively via {@link java.nio.file.Files}.
     *
     * @param configKey
     *            the {@code tests.properties} key, e.g. {@code "file_localfs.test_work_dir"}
     * @param buildDir
     *            path to the Gradle project build directory (for writing the
     *            resolved-path file)
     */
    public WorkDirManager(String configKey, Path buildDir)
    {
        this(configKey, ConnectorType.HIERARCHICAL_FS, buildDir, null);
    }

    /**
     * Constructs a manager with a custom {@link WorkDirOperations} strategy
     * (for object stores and other non-FS connectors).
     *
     * @param configKey
     *            the {@code tests.properties} key
     * @param connectorType
     *            the storage model
     * @param buildDir
     *            Gradle build directory
     * @param operations
     *            strategy for container create/empty/destroy; may be
     *            {@code null} for {@link ConnectorType#READ_ONLY}
     */
    public WorkDirManager(String configKey, ConnectorType connectorType, Path buildDir, WorkDirOperations operations)
    {
        this.configKey = configKey;
        this.connectorType = connectorType;
        this.resolvedFile = buildDir.resolve(RESOLVED_FILE_NAME);
        this.operations = operations;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} when this connector requires an isolated work
     * directory (i.e. is not read-only).
     *
     * @return {@code false} for {@link ConnectorType#READ_ONLY}
     */
    public boolean isRequired()
    {
        return connectorType != ConnectorType.READ_ONLY;
    }

    /**
     * Provisions the work directory:
     * <ul>
     * <li>If the configured value is {@link #TEMP_KEYWORD}: creates a new
     * temporary container, writes its resolved name to the build-dir file.</li>
     * <li>Otherwise: verifies the named container exists and is empty, then
     * writes its name to the build-dir file.</li>
     * </ul>
     * <p>
     * Exits with a non-zero status code when a precondition fails so that the
     * Gradle {@code verifyTestWorkDir} task fails the build cleanly.
     *
     * @throws Exception
     *             if any storage operation fails
     */
    public void provision() throws Exception
    {
        if (!isRequired()) {
            LOGGER.info("WorkDirManager: connector is read-only, skipping work-dir provisioning");
            return;
        }

        final String configuredValue = TestConfig.get(configKey);
        if (configuredValue == null || configuredValue.isBlank()) {
            if (connectorType == ConnectorType.HIERARCHICAL_FS) {
                // Hierarchical FS defaults to TEMP when the property is absent.
                // This allows LocalFS tests to run without any tests.properties entry.
                LOGGER.info("WorkDirManager: '{}' not set — defaulting to TEMP for HIERARCHICAL_FS", configKey);
                writeResolvedFile(provisionTemp());
                return;
            }
            // Object store: no config key set — tests will be skipped by the test class guard.
            LOGGER.info("WorkDirManager: '{}' not set — skipping work-dir provisioning for OBJECT_STORE", configKey);
            return;
        }

        // Object store: config key is present but no operations strategy was supplied
        // (credentials absent from tests.properties). Skip gracefully — the test class
        // setUp() guard will skip tests via assumeNotNull.
        if (connectorType == ConnectorType.OBJECT_STORE && operations == null) {
            LOGGER.info("WorkDirManager: no WorkDirOperations supplied for OBJECT_STORE — skipping provisioning");
            return;
        }

        final String resolved;
        if (TEMP_KEYWORD.equalsIgnoreCase(configuredValue.trim())) {
            resolved = provisionTemp();
        } else {
            resolved = verifyNamedContainer(configuredValue.trim());
        }

        writeResolvedFile(resolved);
        LOGGER.info("WorkDirManager: work directory resolved to '{}'", resolved);
    }

    /**
     * Cleans the work directory after a test run:
     * <ul>
     * <li>For {@code TEMP} containers: destroys the container entirely.</li>
     * <li>For user-supplied named containers: empties the container but does
     * not delete it.</li>
     * </ul>
     *
     * @throws Exception
     *             if cleanup fails
     */
    public void clean() throws Exception
    {
        if (!isRequired()) {
            return;
        }
        final String resolved = getResolvedWorkDir();
        if (resolved == null) {
            LOGGER.warn("WorkDirManager: no resolved work-dir file found — nothing to clean");
            return;
        }

        final String configuredValue = TestConfig.get(configKey);
        final boolean isTemp = configuredValue == null || TEMP_KEYWORD.equalsIgnoreCase(configuredValue.trim());

        LOGGER.info("WorkDirManager: cleaning work directory '{}' (destroyContainer={})", resolved, isTemp);
        if (isTemp) {
            destroyContainer(resolved);
        } else {
            emptyContainer(resolved);
        }

        // Remove the resolved-path file so stale state is not reused
        Files.deleteIfExists(resolvedFile);
    }

    /**
     * Returns the resolved work directory name written by {@link #provision()},
     * or {@code null} if the file does not exist.
     *
     * @return the resolved container name, or {@code null}
     */
    public String getResolvedWorkDir()
    {
        if (!Files.exists(resolvedFile)) {
            return null;
        }
        try {
            return Files.readString(resolvedFile).trim();
        }
        catch (IOException e) {
            LOGGER.warn("WorkDirManager: could not read resolved file '{}': {}", resolvedFile, e.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private String provisionTemp() throws Exception
    {
        if (connectorType == ConnectorType.HIERARCHICAL_FS && operations == null) {
            // Native temp-directory provisioning
            final Path tempDir = Files.createTempDirectory("sdk-test-");
            LOGGER.info("WorkDirManager: created temp directory '{}'", tempDir);
            return tempDir.toAbsolutePath().toString();
        }
        final String name = operations.generateTempName();
        operations.create(name);
        LOGGER.info("WorkDirManager: created temp container '{}'", name);
        return name;
    }

    private String verifyNamedContainer(String name) throws Exception
    {
        final boolean empty;
        if (connectorType == ConnectorType.HIERARCHICAL_FS && operations == null) {
            final Path dir = Paths.get(name);
            if (!Files.isDirectory(dir)) {
                System.err.println("[WorkDirManager] ERROR: configured work directory '" + name
                        + "' does not exist or is not a directory.\n"
                        + "  Please create it before running tests, or set the property to TEMP.");
                System.exit(1);
            }
            empty = !Files.list(dir).findAny().isPresent();
        } else {
            empty = operations.isEmpty(name);
        }

        if (!empty) {
            System.err.println("[WorkDirManager] ERROR: work directory '" + name + "' (configured via '" + configKey
                    + "') is not empty.\n"
                    + "  Please clear it before running tests, or set the property to TEMP for automatic provisioning.");
            System.exit(1);
        }
        return name;
    }

    private void destroyContainer(String name) throws Exception
    {
        if (connectorType == ConnectorType.HIERARCHICAL_FS && operations == null) {
            deleteDirectoryRecursively(Paths.get(name));
        } else if (operations != null) {
            operations.destroyContainer(name);
        }
    }

    private void emptyContainer(String name) throws Exception
    {
        if (connectorType == ConnectorType.HIERARCHICAL_FS && operations == null) {
            deleteContentsRecursively(Paths.get(name));
        } else if (operations != null) {
            operations.emptyContainer(name);
        }
    }

    private void writeResolvedFile(String resolved) throws IOException
    {
        Files.createDirectories(resolvedFile.getParent());
        Files.writeString(resolvedFile, resolved);
    }

    // -----------------------------------------------------------------------
    // Static filesystem helpers
    // -----------------------------------------------------------------------

    private static void deleteDirectoryRecursively(Path path) throws IOException
    {
        if (!Files.exists(path)) {
            return;
        }
        deleteContentsRecursively(path);
        Files.delete(path);
    }

    private static void deleteContentsRecursively(Path dir) throws IOException
    {
        if (!Files.exists(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .filter(p -> !p.equals(dir))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        }
                        catch (IOException e) {
                            throw new RuntimeException("Failed to delete " + p, e);
                        }
                    });
        }
    }
}
