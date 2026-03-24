package security.sqli;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.object.BatchSqlUpdate;
import org.springframework.jdbc.object.MappingSqlQuery;
import org.springframework.jdbc.object.MappingSqlQueryWithParameters;
import org.springframework.jdbc.object.SqlCall;
import org.springframework.jdbc.object.SqlFunction;
import org.springframework.jdbc.object.SqlQuery;
import org.springframework.jdbc.object.SqlUpdate;
import org.springframework.jdbc.object.UpdatableSqlQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SQL injection sink samples for newly added patterns (Spring + JDBC).
 */
public class SqlInjectionSinksSpringSamples {

    // ── Spring JdbcTemplate.queryForStream ──────────────────────────────

    @RestController
    @RequestMapping("/sqli-queryForStream")
    public static class JdbcTemplateQueryForStreamController {

        private final JdbcTemplate jdbcTemplate;

        public JdbcTemplateQueryForStreamController(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @GetMapping("/unsafe")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeQueryForStream(@RequestParam("table") String table) {
            String sql = "SELECT * FROM " + table;
            jdbcTemplate.queryForStream(sql, (rs, rowNum) -> rs.getString(1)).close();
            return "done";
        }
    }

    // ── Spring NamedParameterJdbcOperations ──────────────────────────────

    @RestController
    @RequestMapping("/sqli-namedJdbc")
    public static class NamedParameterJdbcController {

        private final NamedParameterJdbcTemplate namedJdbc;

        public NamedParameterJdbcController(NamedParameterJdbcTemplate namedJdbc) {
            this.namedJdbc = namedJdbc;
        }

        @GetMapping("/query")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeQuery(@RequestParam("filter") String filter) {
            String sql = "SELECT * FROM users WHERE " + filter;
            namedJdbc.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> rs.getString(1));
            return "done";
        }

        @GetMapping("/queryForList")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeQueryForList(@RequestParam("filter") String filter) {
            String sql = "SELECT * FROM users WHERE " + filter;
            namedJdbc.queryForList(sql, new MapSqlParameterSource());
            return "done";
        }

        @GetMapping("/update")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeUpdate(@RequestParam("table") String table) {
            String sql = "DELETE FROM " + table;
            namedJdbc.update(sql, new MapSqlParameterSource());
            return "done";
        }

        @GetMapping("/execute")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeExecute(@RequestParam("stmt") String stmt) {
            namedJdbc.execute(stmt, (org.springframework.jdbc.core.PreparedStatementCallback<Object>) ps -> null);
            return "done";
        }

        @GetMapping("/queryForStream")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeQueryForStream(@RequestParam("filter") String filter) {
            String sql = "SELECT * FROM users WHERE " + filter;
            namedJdbc.queryForStream(sql, new MapSqlParameterSource(), (rs, rowNum) -> rs.getString(1)).close();
            return "done";
        }
    }

    // ── Spring jdbc.object (MappingSqlQuery, SqlUpdate, RdbmsOperation.setSql) ──

    @RestController
    @RequestMapping("/sqli-jdbcObject")
    public static class JdbcObjectController {

        private final DataSource dataSource;

        public JdbcObjectController(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @GetMapping("/mappingSqlQuery")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeMappingSqlQuery(@RequestParam("table") String table) {
            String sql = "SELECT * FROM " + table;
            new MappingSqlQuery<String>(dataSource, sql) {
                @Override
                protected String mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return rs.getString(1);
                }
            };
            return "done";
        }

        @GetMapping("/sqlUpdate")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeSqlUpdate(@RequestParam("table") String table) {
            String sql = "DELETE FROM " + table;
            new SqlUpdate(dataSource, sql);
            return "done";
        }

        @GetMapping("/setSql")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeSetSql(@RequestParam("query") String query) {
            SqlUpdate update = new SqlUpdate();
            update.setDataSource(dataSource);
            update.setSql(query);
            return "done";
        }

        @GetMapping("/batchSqlUpdate")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeBatchSqlUpdate(@RequestParam("table") String table) {
            String sql = "INSERT INTO " + table + " VALUES (?)";
            new BatchSqlUpdate(dataSource, sql);
            return "done";
        }

        @GetMapping("/mappingSqlQueryWithParameters")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeMappingSqlQueryWithParameters(@RequestParam("table") String table) {
            String sql = "SELECT * FROM " + table;
            new MappingSqlQueryWithParameters<String>(dataSource, sql) {
                @Override
                protected String mapRow(ResultSet rs, int rowNum, Object[] params, java.util.Map<?, ?> context) throws SQLException {
                    return rs.getString(1);
                }
            };
            return "done";
        }

        @GetMapping("/sqlCall")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeSqlCall(@RequestParam("proc") String proc) {
            String sql = "CALL " + proc;
            new SqlCall(dataSource, sql) {};
            return "done";
        }

        @GetMapping("/sqlFunction")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeSqlFunction(@RequestParam("func") String func) {
            String sql = "SELECT " + func + " FROM dual";
            new SqlFunction<String>(dataSource, sql) {};
            return "done";
        }

        @GetMapping("/updatableSqlQuery")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeUpdatableSqlQuery(@RequestParam("table") String table) {
            String sql = "SELECT * FROM " + table + " FOR UPDATE";
            new UpdatableSqlQuery<String>(dataSource, sql) {
                @Override
                protected String updateRow(ResultSet rs, int rowNum, java.util.Map<?, ?> context) throws SQLException {
                    return rs.getString(1);
                }
            };
            return "done";
        }
    }

    // ── java.sql.DatabaseMetaData ──────────────────────────────────────

    @RestController
    @RequestMapping("/sqli-metadata")
    public static class DatabaseMetaDataController {

        private final DataSource dataSource;

        public DatabaseMetaDataController(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @GetMapping("/getColumns")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeGetColumns(@RequestParam("table") String table) throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                meta.getColumns(null, null, table, null);
            }
            return "done";
        }

        @GetMapping("/getPrimaryKeys")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeGetPrimaryKeys(@RequestParam("table") String table) throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                meta.getPrimaryKeys(null, null, table);
            }
            return "done";
        }
    }

    // ── Negative sample ────────────────────────────────────────────────

    @RestController
    @RequestMapping("/sqli-safe-named")
    public static class SafeNamedParameterJdbcController {

        private final NamedParameterJdbcTemplate namedJdbc;

        public SafeNamedParameterJdbcController(NamedParameterJdbcTemplate namedJdbc) {
            this.namedJdbc = namedJdbc;
        }

        @GetMapping("/safe")
        @NegativeRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String safeQuery(@RequestParam("username") String username) {
            String sql = "SELECT * FROM users WHERE username = :username";
            MapSqlParameterSource params = new MapSqlParameterSource("username", username);
            namedJdbc.queryForList(sql, params);
            return "done";
        }
    }
}
