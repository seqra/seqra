package security.sources;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for Apache HTTP components.
 */
public class ApacheHttpSourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void httpEntityGetContent(HttpEntity entity) throws Exception {
        InputStream is = entity.getContent();
        byte[] bytes = is.readAllBytes();
        String str = new String(bytes);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + str + "'");
        }
    }

    /** HttpRequestHandler.handle callback */
    public static class LegacyHandler implements HttpRequestHandler {

        private DataSource dataSource;

        @Override
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
        public void handle(HttpRequest request, HttpResponse response, HttpContext context)
                throws HttpException, IOException {
            String uri = request.getRequestLine().getUri();
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
                s.executeQuery("SELECT * FROM t WHERE x = '" + uri + "'");
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }
}
