package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UrlPathHelper;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for Spring UrlPathHelper.
 */
@RestController
public class SpringUrlPathHelperSourceSamples {

    private DataSource dataSource;
    private UrlPathHelper urlPathHelper = new UrlPathHelper();

    @GetMapping("/url-path-helper-source")
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
    public String urlPathHelperGetRequestUri(HttpServletRequest request) throws Exception {
        String uri = urlPathHelper.getRequestUri(request);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + uri + "'");
        }
        return "";
    }
}
