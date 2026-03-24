package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for Spring RestTemplate.
 */
@RestController
public class SpringRestTemplateSourceSamples {

    private DataSource dataSource;
    private RestTemplate restTemplate;

    @GetMapping("/rest-template-source")
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
    public String restTemplateGetForEntity() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("http://external-service/data", String.class);
        String body = response.getBody();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + body + "'");
        }
        return "";
    }
}
