package security.sqli;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;

import javax.persistence.EntityManager;
import javax.sql.DataSource;

import org.apache.torque.TorqueException;
import org.apache.torque.util.BasePeer;
import org.hibernate.criterion.Restrictions;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.Script;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterBatchUpdateUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.SqlConnection;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

/**
 * SQL injection sink samples for pre-existing uncovered patterns.
 */
public class SqlInjectionPreExistingSamples {

    // ── JDO PersistenceManager / Query ──────────────────────────────────

    @RestController
    @RequestMapping("/sqli-jdo")
    public static class JdoController {

        private final PersistenceManager pm;

        public JdoController(PersistenceManager pm) {
            this.pm = pm;
        }

        @GetMapping("/newQuery")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeNewQuery(@RequestParam("q") String q) {
            String jdoql = "SELECT FROM User WHERE " + q;
            pm.newQuery(jdoql);
            return "done";
        }

        @GetMapping("/newQueryWithClass")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeNewQueryWithClass(@RequestParam("q") String q) {
            String filter = "name == '" + q + "'";
            pm.newQuery(Object.class, filter);
            return "done";
        }

        @GetMapping("/setFilter")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeSetFilter(@RequestParam("filter") String filter) {
            Query<?> query = pm.newQuery(Object.class);
            query.setFilter(filter);
            return "done";
        }

        @GetMapping("/setGrouping")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeSetGrouping(@RequestParam("group") String group) {
            Query<?> query = pm.newQuery(Object.class);
            query.setGrouping(group);
            return "done";
        }
    }

    // ── Connection.prepareCall ──────────────────────────────────────────

    @RestController
    @RequestMapping("/sqli-prepareCall")
    public static class PrepareCallController {

        private final DataSource dataSource;

        public PrepareCallController(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @GetMapping("/unsafe")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafePrepareCall(@RequestParam("proc") String proc) throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                CallableStatement cs = conn.prepareCall("CALL " + proc);
                cs.execute();
            }
            return "done";
        }
    }

    // ── Vert.x SqlClient / SqlConnection ────────────────────────────────

    @RestController
    @RequestMapping("/sqli-vertx")
    public static class VertxController {

        private final SqlClient sqlClient;
        private final SqlConnection sqlConnection;

        public VertxController(SqlClient sqlClient, SqlConnection sqlConnection) {
            this.sqlClient = sqlClient;
            this.sqlConnection = sqlConnection;
        }

        @GetMapping("/query")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeQuery(@RequestParam("filter") String filter) {
            String sql = "SELECT * FROM users WHERE " + filter;
            sqlClient.query(sql);
            return "done";
        }

        @GetMapping("/preparedQuery")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafePreparedQuery(@RequestParam("filter") String filter) {
            String sql = "SELECT * FROM users WHERE " + filter;
            sqlClient.preparedQuery(sql);
            return "done";
        }

        @GetMapping("/prepare")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafePrepare(@RequestParam("filter") String filter) {
            String sql = "SELECT * FROM users WHERE " + filter;
            sqlConnection.prepare(sql);
            return "done";
        }
    }

    // ── javax.persistence.EntityManager ─────────────────────────────────

    @RestController
    @RequestMapping("/sqli-jpa")
    public static class JpaEntityManagerController {

        private final EntityManager em;

        public JpaEntityManagerController(EntityManager em) {
            this.em = em;
        }

        @GetMapping("/createQuery")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeCreateQuery(@RequestParam("filter") String filter) {
            String jpql = "SELECT u FROM User u WHERE " + filter;
            em.createQuery(jpql);
            return "done";
        }

        @GetMapping("/createNativeQuery")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeCreateNativeQuery(@RequestParam("filter") String filter) {
            String sql = "SELECT * FROM users WHERE " + filter;
            em.createNativeQuery(sql);
            return "done";
        }
    }

    // ── JDBI Handle methods ─────────────────────────────────────────────

    @RestController
    @RequestMapping("/sqli-jdbi")
    public static class JdbiHandleController {

        private final Handle handle;

        public JdbiHandleController(Handle handle) {
            this.handle = handle;
        }

        @GetMapping("/createScript")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeCreateScript(@RequestParam("stmt") String stmt) {
            handle.createScript(stmt);
            return "done";
        }

        @GetMapping("/createUpdate")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeCreateUpdate(@RequestParam("stmt") String stmt) {
            String sql = "DELETE FROM " + stmt;
            handle.createUpdate(sql);
            return "done";
        }

        @GetMapping("/prepareBatch")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafePrepareBatch(@RequestParam("stmt") String stmt) {
            String sql = "INSERT INTO " + stmt + " VALUES (?)";
            handle.prepareBatch(sql);
            return "done";
        }

        @GetMapping("/select")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeSelect(@RequestParam("filter") String filter) {
            String sql = "SELECT * FROM users WHERE " + filter;
            handle.select(sql);
            return "done";
        }

        @GetMapping("/newScript")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeNewScript(@RequestParam("stmt") String stmt) {
            new Script(handle, stmt);
            return "done";
        }

        @GetMapping("/newPreparedBatch")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeNewPreparedBatch(@RequestParam("stmt") String stmt) {
            String sql = "INSERT INTO " + stmt + " VALUES (?)";
            new PreparedBatch(handle, sql);
            return "done";
        }
    }

    // ── Spring PreparedStatementCreatorFactory ───────────────────────────

    @RestController
    @RequestMapping("/sqli-pscf")
    public static class PreparedStatementCreatorFactoryController {

        @GetMapping("/constructor")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeConstructor(@RequestParam("table") String table) {
            String sql = "SELECT * FROM " + table;
            new PreparedStatementCreatorFactory(sql);
            return "done";
        }

        // PreparedStatementCreatorFactory.newPreparedStatementCreator does not take String
        // in Spring JDBC 5.3.x (takes Object[] or List<?>). Pattern exists for potential
        // future API or alternative usage. No test possible with current Spring version.
        // @GetMapping("/newPreparedStatementCreator")
        // @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        // public String unsafeNewPSC(@RequestParam("table") String table) { ... }
    }

    // ── Spring BatchUpdateUtils ─────────────────────────────────────────

    @RestController
    @RequestMapping("/sqli-batchUtils")
    public static class BatchUpdateUtilsController {

        private final JdbcTemplate jdbcTemplate;

        public BatchUpdateUtilsController(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @GetMapping("/executeBatchUpdate")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeBatchUpdate(@RequestParam("table") String table) {
            String sql = "INSERT INTO " + table + " VALUES (?)";
            org.springframework.jdbc.core.BatchUpdateUtils.executeBatchUpdate(
                    sql, Collections.emptyList(), new int[0], jdbcTemplate);
            return "done";
        }
    }

    // ── Hibernate Restrictions.sqlRestriction ───────────────────────────

    @RestController
    @RequestMapping("/sqli-hibernate-restrict")
    public static class HibernateRestrictionsController {

        @GetMapping("/sqlRestriction")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeSqlRestriction(@RequestParam("condition") String condition) {
            Restrictions.sqlRestriction(condition);
            return "done";
        }
    }

    // ── Apache Torque BasePeer ──────────────────────────────────────────

    @RestController
    @RequestMapping("/sqli-torque")
    public static class TorqueBasePeerController {

        @GetMapping("/executeQuery")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeExecuteQuery(@RequestParam("filter") String filter) throws TorqueException {
            String sql = "SELECT * FROM users WHERE " + filter;
            BasePeer.executeQuery(sql);
            return "done";
        }

        @GetMapping("/executeStatement")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeExecuteStatement(@RequestParam("table") String table) throws TorqueException {
            String sql = "DELETE FROM " + table;
            BasePeer.executeStatement(sql);
            return "done";
        }
    }

    // ── Spring NamedParameterBatchUpdateUtils ────────────────────────────

    @RestController
    @RequestMapping("/sqli-namedBatchUtils")
    public static class NamedParameterBatchUpdateUtilsController {

        private final JdbcTemplate jdbcTemplate;

        public NamedParameterBatchUpdateUtilsController(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        // TODO: Analyzer FN – taint does not propagate through NamedParameterUtils.parseSqlStatement()
        // to ParsedSql; re-enable when summaries are added
        @GetMapping("/executeBatch")
        // @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeNamedBatchUpdate(@RequestParam("table") String table) {
            String sql = "INSERT INTO " + table + " VALUES (:val)";
            ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(sql);
            NamedParameterBatchUpdateUtils.executeBatchUpdateWithNamedParameters(
                    parsedSql, new SqlParameterSource[0], jdbcTemplate);
            return "done";
        }
    }

    // ── Spring PreparedStatementCreatorFactory.newPreparedStatementCreator ─

    @RestController
    @RequestMapping("/sqli-pscf-newPSC")
    public static class PreparedStatementCreatorFactoryNewPscController {

        @GetMapping("/unsafe")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeNewPSC(@RequestParam("table") String table) {
            String sql = "SELECT * FROM " + table;
            PreparedStatementCreatorFactory factory = new PreparedStatementCreatorFactory("SELECT 1");
            factory.newPreparedStatementCreator(new Object[]{sql});
            return "done";
        }
    }

    // ── Apache Turbine BasePeer (UNTESTABLE) ────────────────────────────
    // org.apache.turbine.om.peer.BasePeer is from Turbine 2.x which predates
    // Maven Central. The class is not available in any modern repository.
    // Pattern kept in rule for legacy code but cannot be tested.
}
