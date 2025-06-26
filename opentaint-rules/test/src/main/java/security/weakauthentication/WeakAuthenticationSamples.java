package security.weakauthentication;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.opensaml.xml.parse.BasicParserPool;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.seqra.sast.test.util.NegativeRuleSample;

/**
 * Samples for weak-authentication rules: SAML ignore-comments and JWT decode without verify.
 */
public class WeakAuthenticationSamples {

    // java-saml-ignore-comments

    @PositiveRuleSample(value = "java/security/weak-authentication.yaml", id = "java-saml-ignore-comments")
    public BasicParserPool samlParserPoolIgnoreCommentsInsecure() {
        BasicParserPool pool = new BasicParserPool();
        // VULNERABLE: explicitly disable comment ignoring, which can break SAML assertions
        pool.setIgnoreComments(false);
        return pool;
    }

    @NegativeRuleSample(value = "java/security/weak-authentication.yaml", id = "java-saml-ignore-comments")
    public BasicParserPool samlParserPoolDefaultCommentsSecure() {
        // SAFE: rely on the default ignoreComments=true behavior
        BasicParserPool pool = new BasicParserPool();
        // no call to setIgnoreComments(false)
        return pool;
    }

    // java-jwt-decode-without-verify

    @PositiveRuleSample(value = "java/security/weak-authentication.yaml", id = "java-jwt-decode-without-verify")
    public void decodeJwtWithoutVerifyInsecure(String token) {
        // VULNERABLE: decode the token without any verification and trust its claims
        var decoded = JWT.decode(token);
        String userId = decoded.getSubject();
        // In a real app, userId would now be trusted without verification
        System.out.println("Authenticated user (insecure): " + userId);
    }

    @NegativeRuleSample(value = "java/security/weak-authentication.yaml", id = "java-jwt-decode-without-verify")
    public void verifyJwtBeforeUseSecure(String token, Algorithm algorithm) {
        // SAFE: build a verifier with the expected algorithm and verify before using the token
        var verifier = JWT.require(algorithm).build();

        var decoded = verifier.verify(token);
        String userId = decoded.getSubject();
        System.out.println("Authenticated user (secure): " + userId);
    }
}
