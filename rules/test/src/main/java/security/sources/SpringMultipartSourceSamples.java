package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for Spring MultipartFile and MultipartRequest.
 */
@RestController
public class SpringMultipartSourceSamples {

    private DataSource dataSource;

    @PostMapping("/multipart-file-source")
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
    public String multipartFileGetOriginalFilename(@RequestParam("file") MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + filename + "'");
        }
        return "";
    }

    @PostMapping("/multipart-request-source")
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
    public String multipartRequestGetFile(MultipartRequest request) throws Exception {
        MultipartFile file = request.getFile("upload");
        String name = file.getOriginalFilename();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + name + "'");
        }
        return "";
    }
}
