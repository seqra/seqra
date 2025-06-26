package security.crypto;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Samples for hashing, random number generation, and misc crypto helpers.
 */
public class HashRandomAndMiscCryptoSamples {

    // use-of-md5

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "use-of-md5")
    public byte[] md5DigestUtilsInsecure(byte[] input) {
        // VULNERABLE: MD5 via DigestUtils using the pattern expected by the rule
        return org.apache.commons.codec.digest.DigestUtils.getMd5Digest().digest(input);
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "use-of-md5")
    public byte[] sha256DigestUtilsSecure(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return sha256.digest(input);
    }

    // use-of-rc2

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "use-of-rc2")
    public void rc2CipherInsecure() throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RC2");
        cipher.toString();
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "use-of-rc2")
    public void aesInsteadOfRc2() throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.toString();
    }

    // use-of-rc4

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "use-of-rc4")
    public void rc4CipherInsecure() throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RC4");
        cipher.toString();
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "use-of-rc4")
    public void aesInsteadOfRc4() throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.toString();
    }

    // use-of-sha1

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "use-of-sha1")
    public byte[] sha1DigestInsecure(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        return sha1.digest(input);
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "use-of-sha1")
    public byte[] sha256DigestPreferred(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return sha256.digest(input);
    }

    // weak-random

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "weak-random")
    public int weakRandomInt() {
        // VULNERABLE: Math.random()
        return (int) (Math.random() * 1000);
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "weak-random")
    public int secureRandomInt() {
        return new SecureRandom().nextInt(1000);
    }

    // bad-hexa-conversion

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "bad-hexa-conversion")
    public String badHexaConversion(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input);
        // VULNERABLE: Integer.toHexString strips leading zeros
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(Integer.toHexString(b & 0xff));
        }
        return sb.toString();
    }

    @NegativeRuleSample(value = "java/security/crypto.yaml", id = "bad-hexa-conversion")
    public String goodHexaConversion(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input);
        // SAFE: always use 2 hex digits per byte
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    // avoid-implementing-custom-digests

    @PositiveRuleSample(value = "java/security/crypto.yaml", id = "avoid-implementing-custom-digests")
    public class CustomDigestInsecure extends MessageDigest {
        protected CustomDigestInsecure() {
            super("Custom");
        }

        @Override
        protected void engineUpdate(byte input) {}

        @Override
        protected void engineUpdate(byte[] input, int offset, int len) {}

        @Override
        protected byte[] engineDigest() {
            return new byte[0];
        }

        @Override
        protected void engineReset() {}
    }
}
