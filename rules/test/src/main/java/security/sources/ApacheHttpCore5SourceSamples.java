package security.sources;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for Apache HTTP Core 5 components.
 */
public class ApacheHttpCore5SourceSamples implements HttpRequestHandler {

    private DataSource dataSource;

    @Override
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
            throws HttpException, IOException {
        String uri = request.getRequestUri();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + uri + "'");
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
