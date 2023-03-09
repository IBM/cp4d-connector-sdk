/* *************************************************** */
/*                                                     */
/* (C) Copyright IBM Corp. 2022                        */
/*                                                     */
/* *************************************************** */
package com.ibm.connect.sdk.util;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;

/**
 * SSL utilities.
 */
public class SSLUtils
{
    private static final String SIGNATURE_ALGORITHM = "SHA256WithRSA";

    /**
     * Generates a self-signed certificate.
     *
     * @param keyPair
     *            public/private key pair
     * @param cn
     *            subject and issuer common name
     * @return a self-signed certificate
     * @throws Exception
     */
    public static final Certificate generateSelfSignedCert(final KeyPair keyPair, final String cn) throws Exception
    {
        final long now = System.currentTimeMillis();
        final Date startDate = new Date(now);
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.add(Calendar.YEAR, 10);
        final Date endDate = calendar.getTime();
        final ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keyPair.getPrivate());
        final X500Name commonName = new X500Name("CN=" + cn);
        final BigInteger serial = BigInteger.valueOf(now);
        final X509v3CertificateBuilder certificateBuilder
                = new JcaX509v3CertificateBuilder(commonName, serial, startDate, endDate, commonName, keyPair.getPublic())
                        .addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyId(keyPair.getPublic()))
                        .addExtension(Extension.authorityKeyIdentifier, false, createAuthorityKeyId(keyPair.getPublic()))
                        .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        return new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(signer));
    }

    private static SubjectKeyIdentifier createSubjectKeyId(final PublicKey publicKey) throws Exception
    {
        final SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
        final DigestCalculator digCalc = new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1));
        return new X509ExtensionUtils(digCalc).createSubjectKeyIdentifier(publicKeyInfo);
    }

    private static AuthorityKeyIdentifier createAuthorityKeyId(final PublicKey publicKey) throws Exception
    {
        final SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
        final DigestCalculator digCalc = new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1));
        return new X509ExtensionUtils(digCalc).createAuthorityKeyIdentifier(publicKeyInfo);
    }

    /**
     * Build a server SSLContext with a self-signed certificate.
     *
     * @param certificate
     *            self-signed certificate
     * @param keyPair
     *            public/private key pair
     * @param cn
     *            common name
     * @param password
     *            key store password
     * @return an SSLContext for a self-signed certificate
     * @throws Exception
     */
    public static final SSLContext buildSSLContext(Certificate certificate, KeyPair keyPair, String cn, String password) throws Exception
    {
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        final KeyStore.Entry entry = new KeyStore.PrivateKeyEntry(keyPair.getPrivate(), new Certificate[] { certificate });
        final char[] keyPassword = password.toCharArray();
        final KeyStore.ProtectionParameter param = new KeyStore.PasswordProtection(keyPassword);
        keyStore.setEntry(cn, entry, param);
        return createSSLContextBuilder().loadKeyMaterial(keyStore, keyPassword).loadTrustMaterial(TrustAllStrategy.INSTANCE).build();
    }

    /**
     * Builds a client SSLContext with the given certificates.
     *
     * @param certificates
     *            SSL certificates
     * @return an SSLContext for the given certificates
     * @throws Exception
     */
    public static final SSLContext buildSSLContext(String certificates) throws Exception
    {
        final SSLContextBuilder ctxtBuilder = createSSLContextBuilder();
        if (certificates != null && !certificates.isEmpty()) {
            ctxtBuilder.loadTrustMaterial(getTrustStore(certificates), null);
        }
        return ctxtBuilder.build();
    }

    /**
     * Builds a client SSLContext with the specified trust strategy.
     *
     * @param trustAll
     *            true if all certificates should be trusted
     * @return an SSLContext with the specified trust strategy
     * @throws Exception
     */
    public static final SSLContext buildSSLContext(boolean trustAll) throws Exception
    {
        final SSLContextBuilder ctxtBuilder = createSSLContextBuilder();
        if (trustAll) {
            ctxtBuilder.loadTrustMaterial(TrustAllStrategy.INSTANCE);
        }
        return ctxtBuilder.build();
    }

    private static SSLContextBuilder createSSLContextBuilder()
    {
        return SSLContextBuilder.create().setProtocol("TLSv1.2").setSecureRandom(new SecureRandom());
    }

    /**
     * Converts a private key to PEM format
     *
     * @param key
     *            a private key
     * @return the private key in PEM format
     * @throws Exception
     */
    public static final String convertPrivateKeyToPEM(final PrivateKey key) throws Exception
    {
        return convertObjectToPEM(new PemObject("PRIVATE KEY", key.getEncoded()));
    }

    /**
     * Converts a certificate to PEM format
     *
     * @param cert
     *            a certificate
     * @return the certificate in PEM format
     * @throws Exception
     */
    public static final String convertCertToPEM(final Certificate cert) throws Exception
    {
        return convertObjectToPEM(cert);
    }

    /**
     * Converts an array of certificates to PEM format
     *
     * @param certs
     *            an array of certificates
     * @return the certificates in PEM format
     * @throws Exception
     */
    public static final String convertCertsToPEM(final Certificate[] certs) throws Exception
    {
        final StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            for (final Certificate cert : certs) {
                pemWriter.writeObject(cert);
            }
        }
        return stringWriter.toString();
    }

    private static String convertObjectToPEM(final Object certOrKey) throws Exception
    {
        final StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(certOrKey);
        }
        return stringWriter.toString();
    }

    /**
     * This method returns the trust store filename in the JKS format protected with
     * the provided password. The user must delete the trust store if it is not
     * needed anymore.
     *
     * @param userCertificates
     *            user certificates that will be stored in the trust store, must be
     *            in X.509 PEM format
     * @param password
     *            password that protects the trust store
     * @return returns the filename of the JKS trust store
     * @throws Exception
     */
    public static String getTrustStoreFile(String userCertificates, String password) throws Exception
    {
        final KeyStore truststore = getTrustStore(userCertificates);
        if (password == null) {
            // Should not happen
            throw new IllegalArgumentException("Missing trust store password");
        }
        final Path f = Files.createTempFile("truststore", ".jks").toAbsolutePath(); // NOSONAR
        try (OutputStream fos = Files.newOutputStream(f)) {
            truststore.store(fos, password.toCharArray());
        }
        catch (final Exception e) {
            Files.deleteIfExists(f);
            throw e;
        }
        return f.toString();
    }

    private static KeyStore getTrustStore(String sslCertificates) throws Exception
    {
        final X509Certificate[] certificates = getDefaultCACerts();
        final String alias = "ca_alias";
        final KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(null, null);
        int counter = 0;
        for (final X509Certificate cert : certificates) {
            trustStore.setCertificateEntry(alias + counter, cert);
            counter++;
        }
        if (sslCertificates != null && !sslCertificates.isEmpty()) {
            final Certificate[] sslcerts = generateCertificates(sslCertificates);
            for (final Certificate cert : sslcerts) {
                trustStore.setCertificateEntry(alias + counter, cert);
                counter++;
            }
        }
        return trustStore;
    }

    private static Certificate[] generateCertificates(String certificates) throws Exception
    {
        final List<Certificate> certs = new ArrayList<>();
        certificates = certificates.trim();
        final byte[] certBytes = certificates.startsWith("-----") ? certificates.getBytes(StandardCharsets.UTF_8)
                : Base64.getDecoder().decode(certificates);
        try (ByteArrayInputStream bas = new ByteArrayInputStream(certBytes)) {
            final CertificateFactory cf = CertificateFactory.getInstance("X.509");
            while (bas.available() > 0) {
                final Certificate cert = cf.generateCertificate(bas);
                certs.add(cert);
            }
        }
        return certs.toArray(new Certificate[0]);
    }

    private static X509Certificate[] getDefaultCACerts() throws Exception
    {
        X509Certificate[] defaultCACertificates = null;
        final KeyStore truststore = null;
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(truststore);
        final TrustManager[] trustManagers = tmf.getTrustManagers();
        for (final TrustManager tm : trustManagers) {
            if (tm instanceof X509TrustManager) {
                final X509TrustManager trustManager = (X509TrustManager) tm;
                defaultCACertificates = trustManager.getAcceptedIssuers();
                break;
            }
        }
        return defaultCACertificates;
    }
}
