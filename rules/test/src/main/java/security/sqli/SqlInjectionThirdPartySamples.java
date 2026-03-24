package security.sqli;

import com.alibaba.druid.sql.repository.SchemaRepository;
import com.couchbase.client.java.Cluster;
import liquibase.database.jvm.JdbcConnection;
import liquibase.statement.core.RawSqlStatement;
import org.apache.ibatis.jdbc.SqlRunner;
import org.hibernate.SharedSessionContract;
import org.hibernate.query.QueryProducer;
import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * SQL injection sink samples for third-party libraries.
 */
public class SqlInjectionThirdPartySamples {

    // ── Hibernate SharedSessionContract ─────────────────────────────────

    @RestController
    @RequestMapping("/sqli-hibernate-shared")
    public static class HibernateSharedSessionController {

        private final SharedSessionContract session;

        public HibernateSharedSessionController(SharedSessionContract session) {
            this.session = session;
        }

        @GetMapping("/createQuery")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeCreateQuery(@RequestParam("filter") String filter) {
            String hql = "FROM User WHERE " + filter;
            session.createQuery(hql);
            return "done";
        }

        @GetMapping("/createSQLQuery")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeCreateSQLQuery(@RequestParam("filter") String filter) {
            String sql = "SELECT * FROM users WHERE " + filter;
            session.createSQLQuery(sql);
            return "done";
        }
    }

    // ── Hibernate QueryProducer ─────────────────────────────────────────

    @RestController
    @RequestMapping("/sqli-hibernate-qp")
    public static class HibernateQueryProducerController {

        private final QueryProducer queryProducer;

        public HibernateQueryProducerController(QueryProducer queryProducer) {
            this.queryProducer = queryProducer;
        }

        @GetMapping("/createQuery")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeCreateQuery(@RequestParam("filter") String filter) {
            String hql = "FROM User WHERE " + filter;
            queryProducer.createQuery(hql);
            return "done";
        }

        @GetMapping("/createNativeQuery")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeCreateNativeQuery(@RequestParam("filter") String filter) {
            String sql = "SELECT * FROM users WHERE " + filter;
            queryProducer.createNativeQuery(sql);
            return "done";
        }

        @SuppressWarnings("deprecation")
        @GetMapping("/createSQLQuery")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeCreateSQLQuery(@RequestParam("filter") String filter) {
            String sql = "SELECT * FROM users WHERE " + filter;
            queryProducer.createSQLQuery(sql);
            return "done";
        }
    }

    // ── MyBatis SqlRunner ───────────────────────────────────────────────

    @RestController
    @RequestMapping("/sqli-mybatis")
    public static class MyBatisSqlRunnerController {

        private final Connection connection;

        public MyBatisSqlRunnerController(Connection connection) {
            this.connection = connection;
        }

        @GetMapping("/selectOne")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeSelectOne(@RequestParam("filter") String filter) throws SQLException {
            SqlRunner runner = new SqlRunner(connection);
            String sql = "SELECT * FROM users WHERE " + filter;
            runner.selectOne(sql);
            return "done";
        }

        @GetMapping("/delete")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeDelete(@RequestParam("table") String table) throws SQLException {
            SqlRunner runner = new SqlRunner(connection);
            String sql = "DELETE FROM " + table;
            runner.delete(sql);
            return "done";
        }

        @GetMapping("/run")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeRun(@RequestParam("stmt") String stmt) throws SQLException {
            SqlRunner runner = new SqlRunner(connection);
            runner.run(stmt);
            return "done";
        }
    }

    // ── Couchbase Cluster ───────────────────────────────────────────────

    @RestController
    @RequestMapping("/sqli-couchbase")
    public static class CouchbaseClusterController {

        private final Cluster cluster;

        public CouchbaseClusterController(Cluster cluster) {
            this.cluster = cluster;
        }

        @GetMapping("/query")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeQuery(@RequestParam("filter") String filter) {
            String n1ql = "SELECT * FROM bucket WHERE " + filter;
            cluster.query(n1ql);
            return "done";
        }

        @GetMapping("/analyticsQuery")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeAnalyticsQuery(@RequestParam("filter") String filter) {
            String n1ql = "SELECT * FROM dataset WHERE " + filter;
            cluster.analyticsQuery(n1ql);
            return "done";
        }

        // Couchbase Cluster.queryStreaming is not available in SDK 3.x (may be from SDK 2.x).
        // Pattern kept in rule for backward compatibility, no test possible with current dependency.
        // @GetMapping("/queryStreaming")
        // @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        // public String unsafeQueryStreaming(@RequestParam("filter") String filter) {
        //     String n1ql = "SELECT * FROM bucket WHERE " + filter;
        //     cluster.queryStreaming(n1ql, row -> {});
        //     return "done";
        // }
    }

    // ── Liquibase ───────────────────────────────────────────────────────

    @RestController
    @RequestMapping("/sqli-liquibase")
    public static class LiquibaseController {

        private final Connection connection;

        public LiquibaseController(Connection connection) {
            this.connection = connection;
        }

        @GetMapping("/prepareStatement")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafePrepareStatement(@RequestParam("stmt") String stmt) throws Exception {
            JdbcConnection jdbcConn = new JdbcConnection(connection);
            jdbcConn.prepareStatement(stmt);
            return "done";
        }

        @GetMapping("/rawSqlStatement")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeRawSqlStatement(@RequestParam("stmt") String stmt) {
            new RawSqlStatement(stmt);
            return "done";
        }
    }

    // ── Alibaba Druid ───────────────────────────────────────────────────

    @RestController
    @RequestMapping("/sqli-druid")
    public static class DruidController {

        @GetMapping("/console")
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String unsafeConsole(@RequestParam("sql") String sql) {
            SchemaRepository repo = new SchemaRepository();
            repo.console(sql);
            return "done";
        }
    }

    // ── Negative sample (MyBatis with parameterized query) ──────────────

    @RestController
    @RequestMapping("/sqli-safe-mybatis")
    public static class SafeMyBatisController {

        private final Connection connection;

        public SafeMyBatisController(Connection connection) {
            this.connection = connection;
        }

        @GetMapping("/safe")
        @NegativeRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-spring-app")
        public String safeSelectOne(@RequestParam("id") String id) throws SQLException {
            SqlRunner runner = new SqlRunner(connection);
            runner.selectOne("SELECT * FROM users WHERE id = ?", id);
            return "done";
        }
    }
}
