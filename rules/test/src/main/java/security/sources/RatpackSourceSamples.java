package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import ratpack.http.Request;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for Ratpack Request.
 */
public class RatpackSourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void ratpackRequestGetRawUri(Request request) throws Exception {
        String uri = request.getRawUri();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + uri + "'");
        }
    }
}
