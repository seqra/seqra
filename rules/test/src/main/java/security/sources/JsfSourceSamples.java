package security.sources;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Map;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.sql.DataSource;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for JSF ExternalContext methods.
 */
public class JsfSourceSamples {

    private DataSource dataSource;

    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public void externalContextRequestPathInfo() throws Exception {
        ExternalContext ctx = FacesContext.getCurrentInstance().getExternalContext();
        String pathInfo = ctx.getRequestPathInfo();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + pathInfo + "'");
        }
    }
}
