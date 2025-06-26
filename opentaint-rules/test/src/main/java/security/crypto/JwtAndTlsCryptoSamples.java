package security.crypto;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.bson.UuidRepresentation;
import com.mongodb.MongoClientSettings;
import com.mongodb.connection.SslSettings;
import com.hazelcast.config.SymmetricEncryptionConfig;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.SimpleEmail;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.seqra.sast.test.util.NegativeRuleSample;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import javax.crypto.NullCipher;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Properties;

/**
 * Samples for JWT-related, TLS, HTTP client, Mongo/Hazelcast and SMTP crypto rules.
 */
public class JwtAndTlsCryptoSamples {

    // jjwt-hs256

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "jjwt-hs256")
    public String signJwtWithHs256Insecure(String subject) {
        // VULNERABLE: HS256 is considered weak for JWT signing in this context
        return Jwts.builder()
                .setSubject(subject)
                .signWith(SignatureAlgorithm.HS256, "hardcoded-secret-key")
                .compact();
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "jjwt-hs256")
    public String signJwtWithRs256Secure(String subject) {
        // SAFE (for this rule): use a different algorithm than HS256
        return Jwts.builder()
                .setSubject(subject)
                .signWith(SignatureAlgorithm.RS256, "hardcoded-secret-key")
                .compact();
    }

    // jjwt-none-alg

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "jjwt-none-alg")
    public String buildJwtWithoutSigning(String subject) {
        // Source: builder()
        var jwt = Jwts.builder().setSubject(subject);
        // VULNERABLE: directly compacting without signWith(...)
        return jwt.compact();
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "jjwt-none-alg")
    public String buildJwtWithSigning1(String subject) {
        var jwt = Jwts.builder().setSubject(subject);
        // SAFE: signWith acts as a sanitizer for this rule
        jwt = jwt.signWith(SignatureAlgorithm.HS256, "safe-secret");
        return jwt.compact();
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "jjwt-none-alg")
    public String buildJwtWithSigning2(String subject) {
        var jwt = Jwts.builder().setSubject(subject);
        // SAFE: signWith acts as a sanitizer for this rule
        jwt.signWith(SignatureAlgorithm.HS256, "safe-secret");
        return jwt.compact();
    }

    // jwt-none-alg (auth0)

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "jwt-none-alg")
    public String signAuth0JwtWithNone(String subject) {
        // VULNERABLE: explicit use of Algorithm.none()
        return JWT.create()
                .withSubject(subject)
                .sign(Algorithm.none());
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "jwt-none-alg")
    public String signAuth0JwtWithHmac(String subject) {
        // SAFE for this rule: use a concrete HMAC algorithm instead of "none"
        return JWT.create()
                .withSubject(subject)
                .sign(Algorithm.HMAC256("secret"));
    }

    // defaulthttpclient-is-deprecated

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "defaulthttpclient-is-deprecated")
    public HttpClient createDeprecatedDefaultHttpClient() {
        // VULNERABLE: use of deprecated DefaultHttpClient
        return new DefaultHttpClient();
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "defaulthttpclient-is-deprecated")
    public HttpClient createModernHttpClient() {
        // SAFE: use HttpClientBuilder instead
        return HttpClientBuilder.create().build();
    }

    // insecure-hostname-verifier

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "insecure-hostname-verifier")
    public boolean insecureHostnameVerifierVerify(String hostname, SSLSession session) {
        // VULNERABLE: custom HostnameVerifier that accepts any hostname
        return insecureHostnameVerifier().verify(hostname, session);
    }

    private HostnameVerifier insecureHostnameVerifier() {
        // VULNERABLE: custom HostnameVerifier that accepts any hostname
        return new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true; // accept everything
            }
        };
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "insecure-hostname-verifier")
    public HostnameVerifier secureHostnameVerifier() {
        // SAFE: delegate to default verification or perform proper checks
        return HttpsURLConnection.getDefaultHostnameVerifier();
    }

    // insecure-trust-manager

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "insecure-trust-manager")
    public X509TrustManager insecureTrustManager() {
        // VULNERABLE: trust manager that blindly trusts all certificates
        return new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // no-op
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // no-op
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null; // VULNERABLE pattern
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) {}

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
        };
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "insecure-trust-manager")
    public X509TrustManager secureTrustManager(X509TrustManager delegate) {
        // SAFE: rely on a provided trust manager implementation
        return delegate;
    }

    // mongo-hostname-verification-disabled

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "mongo-hostname-verification-disabled")
    public MongoClientSettings insecureMongoSettings() {
        // VULNERABLE: SSL hostname verification disabled
        SslSettings sslSettings = SslSettings.builder()
                .enabled(true)
                .invalidHostNameAllowed(true)
                .build();

        return MongoClientSettings.builder()
                .applyToSslSettings(builder -> builder.invalidHostNameAllowed(true))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build();
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "mongo-hostname-verification-disabled")
    public MongoClientSettings secureMongoSettings() {
        // SAFE: keep invalidHostNameAllowed(false)
        return MongoClientSettings.builder()
                .applyToSslSettings(builder -> builder.invalidHostNameAllowed(false))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build();
    }

    // no-null-cipher

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "no-null-cipher")
    public Cipher nullCipherInsecure() {
        // VULNERABLE: NullCipher does no encryption
        return new NullCipher();
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "no-null-cipher")
    public Cipher aesGcmCipherSecure() throws Exception {
        // SAFE: use a real cipher instance
        return Cipher.getInstance("AES/GCM/NoPadding");
    }

    // gcm-detection and gcm-nonce-reuse

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "gcm-detection")
    public Cipher aesGcmCipherDetected() throws Exception {
        // INFO: use of GCM mode
        return Cipher.getInstance("AES/GCM/NoPadding");
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "gcm-detection")
    public Cipher otherCipherMode() throws Exception {
        return Cipher.getInstance("AES/CBC/PKCS5Padding");
    }

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "gcm-nonce-reuse")
    public GCMParameterSpec reusedGcmNonce() {
        // VULNERABLE: constant nonce bytes used
        byte[] nonce = "...".getBytes();
        return new GCMParameterSpec(128, nonce, 0, nonce.length);
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "gcm-nonce-reuse")
    public GCMParameterSpec randomGcmNonce() {
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        return new GCMParameterSpec(128, nonce, 0, nonce.length);
    }

    // use-of-weak-rsa-key

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "use-of-weak-rsa-key")
    public KeyPairGenerator weakRsaKey() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        // VULNERABLE: key size below 2048
        keyPairGenerator.initialize(1024);
        return keyPairGenerator;
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "use-of-weak-rsa-key")
    public KeyPairGenerator strongRsaKey() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(4096);
        return keyPairGenerator;
    }

    // weak-tls-protocol

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "weak-tls-protocol")
    public SSLContext weakSslContextProtocol() throws Exception {
        // VULNERABLE: use insecure "SSL" protocol
        return SSLContext.getInstance("SSL");
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "weak-tls-protocol")
    public SSLContext strongSslContextProtocol() throws Exception {
        // SAFE: use TLSv1.3
        return SSLContext.getInstance("TLSv1.3");
    }

    // weak-tls-protocol-version

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "weak-tls-protocol-version")
    public void enableWeakTlsVersions() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        SSLEngine engine = context.createSSLEngine();
        // VULNERABLE: enable TLSv1.0 explicitly without also forcing TLSv1.2+
        engine.setEnabledProtocols(new String[]{"TLSv1.0"});
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "weak-tls-protocol-version")
    public void enableStrongTlsVersions() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        SSLEngine engine = context.createSSLEngine();
        // SAFE: restrict to TLSv1.2
        engine.setEnabledProtocols(new String[]{"TLSv1.2"});
    }

    // hazelcast-symmetric-encryption

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "hazelcast-symmetric-encryption")
    public SymmetricEncryptionConfig insecureHazelcastConfig() {
        // VULNERABLE: using deprecated symmetric encryption config
        return new SymmetricEncryptionConfig();
    }

    // insecure-smtp

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "insecure-smtp")
    public Email insecureEmailClient() throws Exception {
        // VULNERABLE: SSL/TLS enabled but server identity not checked
        SimpleEmail email = new SimpleEmail();
        email.setHostName("smtp.example.com");
        email.setSmtpPort(465);
        email.setSSLOnConnect(true);
        // Missing setSSLCheckServerIdentity(true)
        return email;
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "insecure-smtp")
    public Email secureEmailClient() throws Exception {
        SimpleEmail email = new SimpleEmail();
        email.setHostName("smtp.example.com");
        email.setSmtpPort(465);
        email.setSSLOnConnect(true);
        email.setSSLCheckServerIdentity(true);
        return email;
    }
}
