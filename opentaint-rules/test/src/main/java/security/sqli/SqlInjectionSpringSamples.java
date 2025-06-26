package security.sqli;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for sql-injection-in-spring.
 */
public class SqlInjectionSpringSamples {

    @RestController
    @RequestMapping("/sql-injection-in-spring-app")
    public static class UnsafeSqlSpringController {

        private final JdbcTemplate jdbcTemplate;

        public UnsafeSqlSpringController(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        /**
         * Unsafe endpoint that concatenates untrusted request parameters into a SQL query.
         */
        @GetMapping("/unsafe")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeSearch(@RequestParam("username") String username) {
            // VULNERABLE: username is directly concatenated into the SQL string
            String sql = "SELECT id, username FROM users WHERE username = '" + username + "'";

            return jdbcTemplate.query(sql, (rs) -> {
                StringBuilder builder = new StringBuilder();
                while (rs.next()) {
                    if (builder.length() > 0) {
                        builder.append(",");
                    }
                    builder.append(rs.getLong("id"))
                           .append(":")
                           .append(rs.getString("username"));
                }
                return builder.toString();
            });
        }
    }

    @RestController
    @RequestMapping("/sql-injection-in-spring-app")
    public static class SafeSqlSpringController {

        private final JdbcTemplate jdbcTemplate;

        public SafeSqlSpringController(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        /**
         * Safe endpoint that uses parameterized queries and basic validation.
         */
        @GetMapping("/safe")
        @NegativeRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String safeSearch(@RequestParam("username") String username) {
            if (username == null || username.isBlank()) {
                return ""; // simple guard; in a real app, you might return 400 or an error body
            }

            String sql = "SELECT id, username FROM users WHERE username = ?";

            return jdbcTemplate.query(sql, new Object[]{username}, (rs) -> {
                StringBuilder builder = new StringBuilder();
                while (rs.next()) {
                    if (builder.length() > 0) {
                        builder.append(",");
                    }
                    builder.append(rs.getLong("id"))
                           .append(":")
                           .append(rs.getString("username"));
                }
                return builder.toString();
            });
        }
    }
}
