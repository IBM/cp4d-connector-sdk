/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022, 2026                  */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

import org.slf4j.Logger;

import com.ibm.connect.sdk.util.Utils;

/**
 * Test configuration properties.
 *
 * <p>Loads properties from (in ascending priority order):
 * <ol>
 *   <li>A {@code tests.properties} resource on the classpath (bundled defaults).</li>
 *   <li>A {@code tests.properties} file in the working directory (gitignored overrides).</li>
 *   <li>A JVM system property {@code sdk.test.<key>}.</li>
 *   <li>An environment variable {@code SDK_TEST_<KEY>} (dots replaced by underscores,
 *       uppercased).</li>
 * </ol>
 *
 * <p><strong>Property interpolation:</strong> values may reference other properties
 * using the {@code #other.key#} syntax — the same convention used by the
 * {@code wdp-connect-library} test framework.  References are resolved lazily when
 * {@link #get(String)} is called, so forward references work as long as the referenced
 * key is also present in the loaded properties.
 *
 * <p>Example {@code tests.properties} fragment:
 * <pre>
 *   file_s3.s3.test_base=test-data/
 *   file_s3.s3.test_csv_key=#file_s3.s3.test_base#cars.csv
 *   file_s3.s3.test_binary_key=#file_s3.s3.test_base#logo.png
 * </pre>
 */
public class TestConfig
{
    private static final Logger LOGGER = getLogger(TestConfig.class);

    private static final String CONFIG_FILE_NAME = "tests.properties";

    private static final String SYSTEM_PROPERTY_PREFIX = "sdk.test.";

    /**
     * The name of the property containing the decryption key.
     */
    public static final String DECRYPT_KEY_PROPERTY = "decrypt.key";

    private static final TestConfig INSTANCE = new TestConfig();

    private final Properties props = new Properties();
    private final EncryptUtil encryptUtil;

    // -----------------------------------------------------------------------
    // Public static accessors
    // -----------------------------------------------------------------------

    /**
     * Returns the value of the given configuration property or null if not found.
     * Property-reference tokens ({@code #key#}) in the value are resolved
     * recursively.
     *
     * @param key
     *            the property key
     * @return the resolved value, or {@code null} if not found
     */
    public static String get(String key)
    {
        return INSTANCE.getProperty(key);
    }

    /**
     * Returns the value of the given configuration property or the default value if
     * not found.
     *
     * @param key
     *            the property key
     * @param defaultValue
     *            a default value
     * @return the resolved value, or {@code defaultValue} if not found
     */
    public static String get(String key, String defaultValue)
    {
        return INSTANCE.getProperty(key, defaultValue);
    }

    /**
     * Returns the boolean value of the given configuration property, or
     * {@code defaultValue} if the property is absent.
     *
     * <p>Delegates to {@link Boolean#parseBoolean(String)}, so {@code "true"}
     * (case-insensitive) returns {@code true}; everything else returns
     * {@code false}.
     *
     * @param key
     *            the property key
     * @param defaultValue
     *            value to use when the property is absent
     * @return the parsed boolean, or {@code defaultValue}
     */
    public static boolean getBoolean(String key, boolean defaultValue)
    {
        final String value = get(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    /**
     * Returns the boolean value of the given configuration property, or
     * {@code false} if absent.
     *
     * @param key
     *            the property key
     * @return the parsed boolean, or {@code false}
     */
    public static boolean getBoolean(String key)
    {
        return getBoolean(key, false);
    }

    /**
     * Returns the port specified by the given configuration property or an
     * available port if not found.
     *
     * @param key
     *            the property key
     * @return the configured port, or a random free port
     */
    public static int getPort(String key)
    {
        final String value = get(key);
        return value != null ? Integer.parseInt(value) : Utils.getFreePort();
    }

    /**
     * Loads a connector-specific properties file from the classpath into the
     * supplied {@link Properties} object, decrypts any encrypted values, and
     * resolves {@code #ref#} interpolation against the global configuration.
     *
     * <p>This is the recommended pattern for connector test classes that want to
     * ship a template properties file alongside their test sources:
     * <pre>
     *   Properties p = new Properties();
     *   TestConfig.loadConnectorProperties(p, getClass(), "s3-test.properties");
     * </pre>
     *
     * @param target
     *            the {@link Properties} object to populate
     * @param clazz
     *            the class whose class-loader is used to locate the resource
     * @param resource
     *            classpath resource name (e.g. {@code "s3-test.properties"})
     * @throws Exception
     *             if the resource cannot be found or loaded
     */
    public static void loadConnectorProperties(Properties target, Class<?> clazz, String resource) throws Exception
    {
        INSTANCE.doLoadConnectorProperties(target, clazz, resource);
    }

    // -----------------------------------------------------------------------
    // Private construction + property resolution
    // -----------------------------------------------------------------------

    /**
     * Constructs the singleton test configuration.
     */
    private TestConfig()
    {
        // 1. Load from classpath resource
        final URL testPropsResource = ClassLoader.getSystemResource(CONFIG_FILE_NAME);
        if (testPropsResource != null) {
            LOGGER.info("Loading properties from " + testPropsResource);
            try (InputStream is = testPropsResource.openStream()) {
                props.load(is);
            }
            catch (final Exception e) {
                throw new IllegalArgumentException(e);
            }
        }

        // 2. Overlay with file in working directory (gitignored developer overrides)
        final Path testPropsFile = Paths.get(CONFIG_FILE_NAME);
        if (testPropsFile.toFile().exists()) {
            if (props.isEmpty()) {
                LOGGER.info("Loading properties from " + testPropsFile.toAbsolutePath());
            } else {
                LOGGER.info("Overlaying properties from " + testPropsFile.toAbsolutePath());
            }
            try (InputStream is = Files.newInputStream(testPropsFile)) {
                props.load(is);
            }
            catch (final Exception e) {
                throw new IllegalArgumentException(e);
            }
        }

        encryptUtil = new EncryptUtil(getRawProperty(DECRYPT_KEY_PROPERTY));
    }

    /**
     * Returns the raw (un-interpolated) value for a key, checking env vars and
     * system properties before the loaded properties file.
     */
    private String getRawProperty(String key)
    {
        final String systemKey = SYSTEM_PROPERTY_PREFIX + key;
        final String envKey = systemKey.replaceAll("\\.", "_").toUpperCase(Locale.ENGLISH);
        String value = System.getenv(envKey);
        if (value == null) {
            value = System.getProperty(systemKey, null);
        }
        if (value == null) {
            value = props.getProperty(key, null);
        }
        return decrypt(value);
    }

    private String getProperty(String key)
    {
        return resolve(getRawProperty(key));
    }

    private String getProperty(String key, String defaultValue)
    {
        final String value = getProperty(key);
        return value == null ? resolve(decrypt(defaultValue)) : value;
    }

    private String decrypt(String value)
    {
        return encryptUtil != null ? encryptUtil.decrypt(value) : value;
    }

    /**
     * Resolves {@code #other.key#} tokens in {@code value} by looking up each
     * referenced key recursively through {@link #getProperty(String)}.
     * Un-terminated or unknown references are left as-is and logged as warnings.
     *
     * @param value
     *            the string to resolve; may be {@code null}
     * @return the resolved string, or {@code null} if {@code value} was {@code null}
     */
    private String resolve(String value)
    {
        if (value == null || !value.contains("#")) {
            return value;
        }

        final StringBuilder out = new StringBuilder(value.length());
        final int len = value.length();
        int i = 0;
        while (i < len) {
            final char ch = value.charAt(i);
            if (ch == '#' && i + 1 < len && Character.isLetterOrDigit(value.charAt(i + 1))) {
                // find closing '#'
                final int end = value.indexOf('#', i + 1);
                if (end < 0) {
                    // no closing '#' — emit the rest verbatim
                    out.append(value, i, len);
                    break;
                }
                final String refKey = value.substring(i + 1, end);
                final String refValue = getRawProperty(refKey); // avoid re-resolving recursively forever
                if (refValue == null) {
                    LOGGER.warn("TestConfig: cannot resolve property reference '#{}#' — key not found; leaving as-is", refKey);
                    out.append(value, i, end + 1);
                } else {
                    // resolve the referenced value too (supports chained refs)
                    out.append(resolve(refValue));
                }
                i = end + 1;
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * Loads a connector-specific properties resource, decrypts encrypted values,
     * and resolves {@code #ref#} tokens against the global configuration.
     */
    private void doLoadConnectorProperties(Properties target, Class<?> clazz, String resource) throws Exception
    {
        try (InputStream is = clazz.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new java.io.IOException("TestConfig: connector properties resource not found: " + resource);
            }
            target.clear();
            target.load(is);

            // Decrypt + resolve all values
            for (final String key : target.stringPropertyNames()) {
                String val = target.getProperty(key);
                val = decrypt(val);
                val = resolve(val);
                target.setProperty(key, val != null ? val : "");
            }
        }
    }

}
