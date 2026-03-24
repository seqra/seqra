package security.sources;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for JAX-RS ContainerRequestContext methods.
 */
public class JaxRsSourceSamples implements ContainerRequestFilter {

    private DataSource dataSource;

    @Override
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String header = requestContext.getHeaderString("X-Custom");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + header + "'");
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
