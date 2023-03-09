/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
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

    /**
     * Returns the value of the given configuration property or null if not found.
     *
     * @param key
     *            the property key
     * @return the value of the given configuration property or null if not found
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
     * @return the value of the given configuration property or the default value if
     *         not found
     */
    public static String get(String key, String defaultValue)
    {
        return INSTANCE.getProperty(key, defaultValue);
    }

    /**
     * Returns the port specified by the given configuration property or an
     * available port if not found.
     *
     * @param key
     *            the property key
     * @return the port specified by the the given configuration property or an
     *         available port if not found
     */
    public static int getPort(String key)
    {
        final String value = get(key);
        return value != null ? Integer.parseInt(value) : Utils.getFreePort();
    }

    /**
     * Constructs a test configuration.
     */
    private TestConfig()
    {
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

        encryptUtil = new EncryptUtil(getProperty(DECRYPT_KEY_PROPERTY));
    }

    private String getProperty(String key)
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

    private String getProperty(String key, String defaultValue)
    {
        final String value = getProperty(key);
        return value == null ? decrypt(defaultValue) : value;
    }

    private String decrypt(String value)
    {
        return encryptUtil != null ? encryptUtil.decrypt(value) : value;
    }

}
