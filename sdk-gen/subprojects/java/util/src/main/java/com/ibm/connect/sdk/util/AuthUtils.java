/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.util;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.slf4j.Logger;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Authentication utilities.
 */
public class AuthUtils
{
    private static final Logger LOGGER = getLogger(AuthUtils.class);

    /**
     * Authorization header prefix for a bearer token.
     */
    public static final String AUTHORIZATION_BEARER = "Bearer ";

    private static final String USERNAME = "admin";
    private static final String UID = "999";
    private static final String ROLE = "Admin";
    private static final String ISSUER = "KNOXSSO";
    private static final String AUDIENCE = "DSX";
    private static final String[] PERMISSIONS = { "administrator" };
    private static final String BEARER_HEADER_PATTERN_STRING
            = "(?i)^bearer\\s+([\\w\"!#\\$%&'\\(\\)\\*\\+\\-\\./:<=>\\?@\\[\\]\\^`\\{\\|\\}~\\\\,;]+)$";
    private static final Pattern BEARER_HEADER_PATTERN = Pattern.compile(BEARER_HEADER_PATTERN_STRING);
    private static final String WDP_PUBLIC_KEY_URLS_ENVVAR = "WDP_PUBLIC_KEY_URLS";
    private static final String WDP_PUBLIC_KEY_URLS_DEFAULT = "etc/wdp_public_key_urls.txt";
    private static final String WDP_PUBLIC_KEYS_PATH_ENVVAR = "WDP_PUBLIC_KEYS_PATH";
    private static final String WDP_PUBLIC_KEYS_PATH_DEFAULT = "etc/wdp_public_keys";
    private static final int HTTP_CONNECTION_SOCKET_TIMEOUT = 50 * 1000;
    private static final int HTTP_CONNECTION_READ_TIMEOUT = 50 * 1000;

    /**
     * Generates a public/private key pair.
     *
     * @return a public/private key pair
     *
     * @throws Exception
     */
    public static final KeyPair generateKeyPair() throws Exception
    {
        // Create an RSA 4096-byte key pair.
        final SecureRandom random = new SecureRandom();
        final KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(4096, random);
        return keyPairGen.generateKeyPair();
    }

    /**
     * Get the public keys to be used for token verification.
     *
     * @return a list of public keys
     * @throws Exception
     */
    public static List<PublicKey> getVerificationKeys() throws Exception
    {
        final List<PublicKey> publicKeys = new ArrayList<>();
        final Path publicKeyUrlsFile = Paths.get(getEnv(WDP_PUBLIC_KEY_URLS_ENVVAR, WDP_PUBLIC_KEY_URLS_DEFAULT)).toAbsolutePath();
        LOGGER.info("WDP public key URLs file: " + publicKeyUrlsFile);
        if (Files.exists(publicKeyUrlsFile)) {
            try (BufferedReader reader = Files.newBufferedReader(publicKeyUrlsFile)) {
                String endpoint;
                while ((endpoint = reader.readLine()) != null) {
                    final String content = executeHttpGet(endpoint);
                    addPublicKeys(content, publicKeys);
                }
            }
        }
        final File publicKeysDir = new File(getEnv(WDP_PUBLIC_KEYS_PATH_ENVVAR, WDP_PUBLIC_KEYS_PATH_DEFAULT)).getAbsoluteFile();
        LOGGER.info("WDP public keys path: " + publicKeysDir);
        final File[] publicKeyFiles = publicKeysDir.listFiles();
        if (publicKeyFiles != null) {
            for (final File publicKeyFile : publicKeyFiles) {
                LOGGER.info("WDP public key file: " + publicKeyFile.getAbsolutePath());
                final String content = new String(Files.readAllBytes(Paths.get(publicKeyFile.toString())), StandardCharsets.UTF_8);
                addPublicKeys(content, publicKeys);
            }
        }
        return publicKeys;
    }

    private static String executeHttpGet(String endpoint) throws Exception
    {
        try (CloseableHttpClient httpClient = createHttpClient()) {
            final HttpGet request = new HttpGet(endpoint);
            request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
            LOGGER.info("Executing HTTP request " + request);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                final HttpEntity entity = response.getEntity();
                if (entity == null) {
                    return null;
                }
                final String responseString = EntityUtils.toString(entity);
                EntityUtils.consume(entity);
                final int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != HttpStatus.SC_OK) {
                    throw new Exception(responseString != null ? responseString : response.toString());
                }
                LOGGER.info("Response:\n" + responseString);
                return responseString;
            }
        }
    }

    private static CloseableHttpClient createHttpClient() throws Exception
    {
        final HttpClientBuilder builder = HttpClientBuilder.create().useSystemProperties();
        builder.setSSLContext(SSLUtils.buildSSLContext(null));
        builder.setDefaultRequestConfig(RequestConfig.custom().setNormalizeUri(false).setConnectTimeout(HTTP_CONNECTION_SOCKET_TIMEOUT)
                .setSocketTimeout(HTTP_CONNECTION_READ_TIMEOUT).build());
        return builder.build();
    }

    private static void addPublicKeys(String content, List<PublicKey> publicKeys) throws Exception
    {
        final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        if (content.startsWith("-")) {
            // CP4D public key PEM
            try (PemReader pemReader = new PemReader(new StringReader(content))) {
                final PemObject pemObject = pemReader.readPemObject();
                final byte[] decodedKey = pemObject.getContent();
                publicKeys.add(keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey)));
            }
        } else {
            // IBM Cloud JSON Web Key Set (JWKS)
            final ObjectMapper mapper = new ObjectMapper();
            final List<Map<String, Object>> keys = mapper.readValue(content, new TypeReference<List<Map<String, Object>>>() {
                // Empty block
            });
            for (final Map<String, Object> jsonKey : keys) {
                final BigInteger modulus = new BigInteger(1, Base64.decodeBase64((String) jsonKey.get("n")));
                final BigInteger publicExponent = new BigInteger(1, Base64.decodeBase64((String) jsonKey.get("e")));
                publicKeys.add(keyFactory.generatePublic(new RSAPublicKeySpec(modulus, publicExponent)));
            }
        }
    }

    private static String getEnv(String var, String defaultValue)
    {
        final String value = System.getenv(var);
        return value != null ? value : defaultValue;
    }

    /**
     * Creates an authentication token for the given key pair.
     *
     * @param keyPair
     *            key pair
     * @return an authentication token
     */
    public static String getAuthToken(KeyPair keyPair)
    {
        final Date iat = new Date();
        final Date exp = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
        final JWTCreator.Builder builder = JWT.create().withIssuer(ISSUER).withAudience(AUDIENCE).withSubject(USERNAME)
                .withClaim("username", USERNAME).withClaim("role", ROLE).withClaim("uid", UID).withClaim("iat", iat).withClaim("exp", exp)
                .withArrayClaim("permissions", PERMISSIONS);
        return AUTHORIZATION_BEARER
                + builder.sign(Algorithm.RSA256((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate()));
    }

    /**
     * Creates an authentication token for the given key pair.
     *
     * @param publicKey
     *            public key
     * @param privateKey
     *            private key
     * @return an authentication token
     */
    public static String getAuthToken(PublicKey publicKey, PrivateKey privateKey)
    {
        final Date iat = new Date();
        final Date exp = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
        final JWTCreator.Builder builder = JWT.create().withIssuer(ISSUER).withAudience(AUDIENCE).withSubject(USERNAME)
                .withClaim("username", USERNAME).withClaim("role", ROLE).withClaim("uid", UID).withClaim("iat", iat).withClaim("exp", exp)
                .withArrayClaim("permissions", PERMISSIONS);
        return AUTHORIZATION_BEARER + builder.sign(Algorithm.RSA256((RSAPublicKey) publicKey, (RSAPrivateKey) privateKey));
    }

    /**
     * Validates an authentication token and returns the authenticated subject.
     *
     * @param authToken
     *            an authentication token
     * @param publicKeys
     *            public keys for token verification
     * @return the authenticated subject
     * @throws Exception
     */
    public static String validateAuthToken(String authToken, PublicKey[] publicKeys) throws Exception
    {
        if (authToken == null || authToken.isEmpty()) {
            throw new IllegalArgumentException("Missing token");
        }
        final Matcher matcher = BEARER_HEADER_PATTERN.matcher(authToken.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid token");
        }
        final String jwtToken = matcher.group(1);
        if (jwtToken == null) {
            throw new IllegalArgumentException("Invalid token");
        }
        final DecodedJWT jwt = JWT.decode(jwtToken);
        if (publicKeys != null) {
            verifyJWTToken(jwtToken, publicKeys);
        }
        return jwt.getSubject();
    }

    private static void verifyJWTToken(final String jwtToken, PublicKey[] publicKeys) throws Exception
    {
        boolean verified = false;
        Exception lastException = null;
        for (final PublicKey publicKey : publicKeys) {
            try {
                final Algorithm algorithm = getAlgorithm((RSAPublicKey) publicKey, null);
                final JWTVerifier verifier = JWT.require(algorithm).acceptLeeway(5).build();
                verifier.verify(jwtToken);
                verified = true;
                break;
            }
            catch (final Exception e) {
                lastException = e;
            }
        }
        if (!verified && lastException != null) {
            throw lastException;
        }
    }

    private static Algorithm getAlgorithm(RSAPublicKey publicKey, RSAPrivateKey privateKey)
    {
        final Algorithm algorithm;
        switch (publicKey.getAlgorithm()) {
        case "RS256":
            algorithm = Algorithm.RSA256(publicKey, privateKey);
            break;
        case "RS384":
            algorithm = Algorithm.RSA384(publicKey, privateKey);
            break;
        case "RS512":
            algorithm = Algorithm.RSA512(publicKey, privateKey);
            break;
        default:
            algorithm = Algorithm.RSA256(publicKey, privateKey);
            break;
        }
        return algorithm;
    }

    /**
     * Returns all verification keys.
     *
     * @return all verification keys
     */
    public static PublicKey[] getAllVerificationKeys()
    {
        List<PublicKey> verificationKeys;
        try {
            verificationKeys = AuthUtils.getVerificationKeys();
            return verificationKeys.toArray(new PublicKey[0]);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
