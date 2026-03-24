package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for Spring WebRequest.
 */
@RestController
public class SpringWebRequestSourceSamples {

    private DataSource dataSource;

    @GetMapping("/web-request-source")
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
    public String webRequestGetParameter(WebRequest request) throws Exception {
        String param = request.getParameter("name");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + param + "'");
        }
        return "";
    }
}
