package security.hardcodedcredentials;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.kerberos.KerberosKey;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

/**
 * Samples for hardcoded-credentials.yaml rules.
 */
public class HardcodedCredentialsSamples {

    // constant-db-password

    @PositiveRuleSample(value = "java/security/hardcoded-credentials.yaml", id = "constant-db-password")
    public Connection connectWithHardcodedDbPassword(String uri, String user) throws Exception {
        // VULNERABLE: hardcoded password literal in getConnection
        return DriverManager.getConnection(uri, user, "superSecretP@ss!");
    }

    @NegativeRuleSample(value = "java/security/hardcoded-credentials.yaml", id = "constant-db-password")
    public Connection connectWithExternalDbPassword(String uri, String user) throws Exception {
        // SAFE: password loaded from environment / config, not hardcoded
        String password = System.getenv("DB_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException("DB_PASSWORD not configured");
        }
        return DriverManager.getConnection(uri, user, password);
    }

    // java-empty-db-password

    @PositiveRuleSample(value = "java/security/hardcoded-credentials.yaml", id = "java-empty-db-password")
    public Connection connectWithEmptyDbPassword(String uri, String user) throws Exception {
        // VULNERABLE: explicitly using an empty password
        return DriverManager.getConnection(uri, user, "");
    }

    @NegativeRuleSample(value = "java/security/hardcoded-credentials.yaml", id = "java-empty-db-password")
    public Connection connectWithNonEmptyDbPassword(String uri, String user, String password) throws Exception {
        // SAFE: require a non-empty password that comes from caller/config
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        return DriverManager.getConnection(uri, user, password);
    }

    // hardcoded-password

    @PositiveRuleSample(value = "java/security/hardcoded-credentials.yaml", id = "hardcoded-password")
    public void useHardcodedPasswordsEverywhere() throws Exception {
        // KeyStore password protection
        new java.security.KeyStore.PasswordProtection("changeit".toCharArray());

        // DriverManager getConnection with password only pattern
        DriverManager.getConnection("jdbc:mysql://localhost/db", "username", "hardcodedPassword");

        // JJWT builder with hardcoded secret (also relevant to jwt-hardcoded-secret)
        Jwts.builder()
                .setSubject("user")
                .signWith(SignatureAlgorithm.HS256, "my-super-secret-key-123")
                .compact();

        // Example PBEKeySpec with hardcoded password
        new PBEKeySpec("hardcodedPwd".toCharArray(), new byte[]{1, 2, 3}, 1000, 256);

        // Kerberos key with hardcoded password
        new KerberosKey(null, "kerbSecret".toCharArray(), null);
    }

    @NegativeRuleSample(value = "java/security/hardcoded-credentials.yaml", id = "hardcoded-password")
    public void useExternalPasswords() throws Exception {
        char[] keystorePassword = loadSecret("KEYSTORE_PASSWORD");
        new java.security.KeyStore.PasswordProtection(keystorePassword);

        String dbPassword = System.getenv("DB_PASSWORD");
        if (dbPassword == null || dbPassword.isEmpty()) {
            throw new IllegalStateException("DB_PASSWORD not configured");
        }
        DriverManager.getConnection("jdbc:mysql://localhost/db", "username", dbPassword);

        char[] pbePassword = loadSecret("PBE_PASSWORD");
        new PBEKeySpec(pbePassword, new byte[]{1, 2, 3}, 1000, 256);

        char[] kerberosSecret = loadSecret("KERBEROS_PASSWORD");
        new KerberosKey(null, kerberosSecret, null);
    }

    // jwt-hardcoded-secret

    @PositiveRuleSample(value = "java/security/hardcoded-credentials.yaml", id = "jwt-hardcoded-secret")
    public String issueJwtWithHardcodedSecret(String username) {
        // VULNERABLE: secret key hardcoded in source
        return Jwts.builder()
                .setSubject(username)
                .signWith(SignatureAlgorithm.HS256, "my-super-secret-key-123")
                .compact();
    }

    @NegativeRuleSample(value = "java/security/hardcoded-credentials.yaml", id = "jwt-hardcoded-secret")
    public String issueJwtWithExternalSecret(String username) {
        String rawSecret = System.getenv("JWT_SECRET");
        if (rawSecret == null || rawSecret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET is not set or too weak");
        }
        byte[] keyBytes = rawSecret.getBytes();
        // Use JJWT 0.11.x style: signWith(Key, SignatureAlgorithm)
        java.security.Key key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
                .setSubject(username)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();


    }

    // hardcoded-cryptographic-key

    @PositiveRuleSample(value = "java/security/hardcoded-credentials.yaml", id = "hardcoded-cryptographic-key")
    public byte[] encryptWithHardcodedKey(byte[] plaintext) throws Exception {
        // VULNERABLE: AES key bytes are hardcoded in the class
        byte[] AES_KEY_BYTES = new byte[]{
                0x01, 0x23, 0x45, 0x67,
                (byte) 0x89, (byte) 0xAB,
                (byte) 0xCD, (byte) 0xEF,
                0x10, 0x32, 0x54, 0x76,
                (byte) 0x98, (byte) 0xBA,
                (byte) 0xDC, (byte) 0xFE
        };

        SecretKeySpec key = new SecretKeySpec(AES_KEY_BYTES, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(plaintext);
    }

    @NegativeRuleSample(value = "java/security/hardcoded-credentials.yaml", id = "hardcoded-cryptographic-key")
    public byte[] encryptWithExternalKey(byte[] plaintext) throws Exception {
        // SAFE: key is loaded from configuration rather than hardcoded
        String keyB64 = System.getenv("APP_AES_KEY");
        if (keyB64 == null || keyB64.isEmpty()) {
            throw new IllegalStateException("APP_AES_KEY is not configured");
        }
        byte[] keyBytes = Base64.getDecoder().decode(keyB64);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(plaintext);
    }

    private char[] loadSecret(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(name + " not configured");
        }
        return value.toCharArray();
    }
}
