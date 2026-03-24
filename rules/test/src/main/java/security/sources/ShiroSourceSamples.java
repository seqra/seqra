package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.shiro.authc.AuthenticationToken;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for Shiro AuthenticationToken.
 */
public class ShiroSourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void authTokenGetCredentials(AuthenticationToken token) throws Exception {
        Object creds = token.getCredentials();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + creds + "'");
        }
    }
}
