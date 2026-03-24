package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for Spring Security SavedRequest.
 */
@RestController
public class SpringSavedRequestSourceSamples {

    private DataSource dataSource;
    private SavedRequest savedRequest;

    @GetMapping("/saved-request-source")
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
    public String savedRequestGetRedirectUrl() throws Exception {
        String url = savedRequest.getRedirectUrl();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + url + "'");
        }
        return "";
    }
}
