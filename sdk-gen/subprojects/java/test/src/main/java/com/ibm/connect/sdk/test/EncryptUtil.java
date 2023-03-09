/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.test;

import java.nio.charset.StandardCharsets;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.KeySpec;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

/**
 * Utility for encrypting and decrypting test configuration properties.
 */
public class EncryptUtil
{
    private static final String ENCRYPT_PREFIX = "encrypted.{{";
    private static final String ENCRYPT_SUFFIX = "}}";

    private static final String ENCRYPTION_ALGO = "PBEWithMD5AndDES";
    private static final byte[] SALT = "saltsalt".getBytes(StandardCharsets.UTF_8);
    private static final int ITERATION_CNT = 19;

    private final String decryptKey;

    /**
     * Supply no args for usage info.
     *
     * -e &lt;text&gt; to encrypt text.
     *
     * -d &lt;text&gt; to decrypt text.
     *
     * @param args
     *            arguments
     * @throws Exception
     */
    public static void main(String[] args) throws Exception
    {
        final EncryptUtil encryptUtil = new EncryptUtil(TestConfig.get(TestConfig.DECRYPT_KEY_PROPERTY));

        if (args.length != 2) {
            usage();
            return;
        }
        final String option = args[0].trim();
        final String text = args[1].trim();
        if ("-d".equalsIgnoreCase(option)) {
            // Decrypt text
            System.out.println("Attempting to decrypt string: " + text);
            final String decrypted = encryptUtil.decrypt(text);
            System.out.println("Decrypted value: " + decrypted);
        } else if ("-e".equalsIgnoreCase(option)) {
            // Encrypt text
            System.out.println("Attempting to encrypt string: " + text);
            final String encrypted = encryptUtil.encrypt(text);
            System.out.println("Encrypted value: " + encrypted);
        } else {
            usage();
        }
    }

    /**
     * Prints usage information to the console.
     */
    private static void usage()
    {
        final String className = EncryptUtil.class.getName();
        System.out.println("Usage: java " + className + " [OPTION...] [TEXT]");
        System.out.println("   Options:");
        System.out.println("     -e <text> - Encrypts given text.");
        System.out.println("     -d <text> - Decrypts given text.");
    }

    /**
     * Constructs the encryption utility.
     *
     * @param decryptKey
     *            decryption key
     */
    public EncryptUtil(String decryptKey)
    {
        this.decryptKey = decryptKey;
    }

    private static boolean isNullOrEmptyString(String s)
    {
        return s == null || s.isEmpty();
    }

    /**
     * Returns true if the given string is an encrypted value.
     *
     * @param text
     *            string to check whether it is encrypted
     * @return true if the given string is an encrypted value
     */
    public static boolean isEncrypted(String text)
    {
        return !isNullOrEmptyString(text) && text.startsWith(ENCRYPT_PREFIX) && text.endsWith(ENCRYPT_SUFFIX);
    }

    /**
     * Adds the prefix and suffix to the encrypted string.
     *
     * @param encrypted
     *            the encrypted string
     * @return the encrypted string with delimiters
     */
    private String addEncryptionDelimiters(String encrypted)
    {
        return !isNullOrEmptyString(encrypted) && !isEncrypted(encrypted) ? ENCRYPT_PREFIX + encrypted + ENCRYPT_SUFFIX : encrypted;
    }

    /**
     * Removes the prefix and suffix from the given encrypted string.
     *
     * @param encrypted
     *            encrypted string to strip of its delimiters
     * @return the encrypted string stripped of delimiters
     */
    private static String removeEncryptionDelimiters(String encrypted)
    {
        final String returnValue;
        if (!isNullOrEmptyString(encrypted) && isEncrypted(encrypted)) {
            final int startIndex = ENCRYPT_PREFIX.length();
            final int endIndex = encrypted.length() - ENCRYPT_SUFFIX.length();
            returnValue = encrypted.substring(startIndex, endIndex);
        } else {
            returnValue = encrypted;
        }
        return returnValue;
    }

    /**
     * Encrypts the given string.
     *
     * @param toEncrypt
     *            the string to encrypt
     * @return the encrypted string
     * @throws Exception
     */
    public String encrypt(String toEncrypt) throws Exception
    {
        if (isEncrypted(toEncrypt)) {
            return toEncrypt;
        }
        final String encrypted = encrypt(toEncrypt.getBytes(StandardCharsets.UTF_8));
        return addEncryptionDelimiters(encrypted);
    }

    /**
     * Decrypts the given string.
     *
     * @param encrypted
     *            a string that can be encrypted
     * @return the decrypted string
     */
    public String decrypt(String encrypted)
    {
        if (!isEncrypted(encrypted)) {
            return encrypted;
        }
        try {
            encrypted = removeEncryptionDelimiters(encrypted);
            return decrypt(encrypted.getBytes(StandardCharsets.UTF_8));
        }
        catch (final Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Encrypts the given bytes and returns the encrypted string.
     *
     * @param toEncrypt
     *            the bytes to encrypt
     * @return the encrypted string
     * @throws Exception
     */
    private String encrypt(byte[] toEncrypt) throws Exception
    {
        if (isNullOrEmptyString(decryptKey)) {
            throw new IllegalArgumentException("Decryption key not set to encrypt properties.");
        }

        return encrypt(toEncrypt, decryptKey);
    }

    /**
     * Encrypts the given bytes and returns the encrypted string.
     *
     * @param toEncrypt
     *            the bytes to encrypt
     * @param password
     *            the encryption key
     * @return the encrypted string
     * @throws Exception
     */
    private String encrypt(byte[] toEncrypt, String password) throws Exception
    {
        final Cipher cipher = getCipher(Cipher.ENCRYPT_MODE, password);

        // Encrypt then encode.
        final byte[] out = cipher.doFinal(toEncrypt);
        final Encoder urlEncoder = java.util.Base64.getUrlEncoder();
        return urlEncoder.encodeToString(out);
    }

    /**
     * Decrypts the given bytes and returns the decrypted string.
     *
     * @param encryptedText
     *            encrypted string in UTF-8 bytes
     * @return the decrypted string
     * @throws Exception
     */
    private String decrypt(byte[] encryptedText) throws Exception
    {
        if (isNullOrEmptyString(decryptKey)) {
            throw new IllegalArgumentException("Decryption key not set to decrypt properties.");
        }

        final Cipher cipher = getCipher(Cipher.DECRYPT_MODE, decryptKey);

        try {
            // Decode then decrypt.
            final Decoder urlDecoder = java.util.Base64.getUrlDecoder();
            final byte[] decodedEncrypted = urlDecoder.decode(encryptedText);

            // Now decrypt the decoded bytes...
            final byte[] decrypted = cipher.doFinal(decodedEncrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        }
        catch (final BadPaddingException e) {
            throw new IllegalArgumentException("Something went wrong trying to decrypt properties. You may be using the wrong decrypt key",
                    e);
        }
    }

    /**
     * Returns an encryption or decryption cipher.
     *
     * @param mode
     *            the operation mode of the cipher
     * @param password
     *            the encryption key
     * @return the cipher
     * @throws Exception
     */
    private Cipher getCipher(int mode, String password) throws Exception
    {
        final KeySpec keySpec = new PBEKeySpec(password.toCharArray(), SALT, ITERATION_CNT);
        final SecretKey key = SecretKeyFactory.getInstance(ENCRYPTION_ALGO).generateSecret(keySpec);
        final AlgorithmParameterSpec paramSpec = new PBEParameterSpec(SALT, ITERATION_CNT);
        final Cipher cipher = Cipher.getInstance(key.getAlgorithm());
        cipher.init(mode, key, paramSpec);
        return cipher;
    }

}
