package security.sources;

import java.security.Key;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.SigningKeyResolver;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for jsonwebtoken SigningKeyResolver.
 */
public class JsonWebTokenSourceSamples implements SigningKeyResolver {

    private DataSource dataSource;

    @Override
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public Key resolveSigningKey(JwsHeader header, Claims claims) {
        String kid = header.getKeyId();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM keys WHERE kid = '" + kid + "'");
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @Override
    public Key resolveSigningKey(JwsHeader header, String plaintext) {
        return null;
    }
}
