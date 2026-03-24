package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Http.RequestHeader;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for Play Framework Http.Request / Http.RequestHeader.
 */
public class PlaySourceSamples {

    private DataSource dataSource;

    // ANALYZER LIMITATION: Typed metavariable patterns with Java inner class syntax
    // (play.mvc.Http.RequestHeader) are not resolved by the analyzer for cast-style
    // source patterns like ($TYPE $VAR).$METHOD(...). The rule is correct but the
    // analyzer cannot match the inner class type in the pattern to the actual code.
    // TODO: Re-enable when analyzer supports inner class types in typed metavariables.
    // @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void httpRequestHeaderUri(RequestHeader request) throws Exception {
        String uri = request.uri();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + uri + "'");
        }
    }

    // ANALYZER LIMITATION: Same as above — inner class type play.mvc.Http.Request
    // is not resolved in typed metavariable patterns.
    // TODO: Re-enable when analyzer supports inner class types in typed metavariables.
    // @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void httpRequestBody(Request request) throws Exception {
        Http.RequestBody requestBody = request.body();
        String str = requestBody.asText();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + str + "'");
        }
    }

    // ANALYZER LIMITATION: Same as above — inner class type play.mvc.Http.RequestHeader.
    // TODO: Re-enable when analyzer supports inner class types in typed metavariables.
    // @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void httpRequestHeaderHost(RequestHeader request) throws Exception {
        String host = request.host();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + host + "'");
        }
    }

    // ANALYZER LIMITATION: Same as above — inner class type play.mvc.Http.RequestHeader.
    // TODO: Re-enable when analyzer supports inner class types in typed metavariables.
    // @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void httpRequestHeaderPath(RequestHeader request) throws Exception {
        String path = request.path();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + path + "'");
        }
    }
}
