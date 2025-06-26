package security.externalconfigurationcontrol;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for sql-catalog-external-manipulation-in-spring-app rule.
 */
public class SqlCatalogSpringSamples {

    @RestController
    @RequestMapping("/spring/external-config/catalog")
    public static class UnsafeCatalogController {

        private final DataSource dataSource;

        public UnsafeCatalogController(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @GetMapping("/unsafe")
        @PositiveRuleSample(value = "java/security/external-configuration-control.yaml", id = "sql-catalog-external-manipulation-in-spring-app")
        public List<String> getUsers(@RequestParam String catalog, @RequestParam int id) throws SQLException {
            Connection conn = DataSourceUtils.getConnection(dataSource);
            try {
                // VULNERABLE: user-controlled catalog name
                conn.setCatalog(catalog);

                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE id = ?")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        List<String> result = new ArrayList<>();
                        while (rs.next()) {
                            result.add(String.valueOf(rs.getInt("id")));
                        }
                        return result;
                    }
                }
            } finally {
                DataSourceUtils.releaseConnection(conn, dataSource);
            }
        }
    }

    @RestController
    @RequestMapping("/spring/external-config/catalog")
    public static class SafeCatalogController {

        private final DataSource dataSource;
        private final TenantCatalogResolver tenantCatalogResolver;

        public SafeCatalogController(DataSource dataSource, TenantCatalogResolver tenantCatalogResolver) {
            this.dataSource = dataSource;
            this.tenantCatalogResolver = tenantCatalogResolver;
        }

        @GetMapping("/safe")
        @NegativeRuleSample(value = "java/security/external-configuration-control.yaml", id = "sql-catalog-external-manipulation-in-spring-app")
        public List<String> getUsers(@RequestParam int id, @AuthenticationPrincipal TenantPrincipal principal) throws SQLException {
            String tenantId = principal.getTenantId();
            String catalog = tenantCatalogResolver.resolveCatalogForTenant(tenantId);

            Connection conn = DataSourceUtils.getConnection(dataSource);
            try {
                // Safe: catalog comes from trusted, server-side mapping
                conn.setCatalog(catalog);

                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE id = ?")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        List<String> result = new ArrayList<>();
                        while (rs.next()) {
                            result.add(String.valueOf(rs.getInt("id")));
                        }
                        return result;
                    }
                }
            } finally {
                DataSourceUtils.releaseConnection(conn, dataSource);
            }
        }
    }

    /**
     * Simple server-side mapping from tenant id to catalog name.
     */
    public static class TenantCatalogResolver {

        private static final Map<String, String> TENANT_TO_CATALOG = new HashMap<>();

        static {
            TENANT_TO_CATALOG.put("tenantA", "tenant_a_db");
            TENANT_TO_CATALOG.put("tenantB", "tenant_b_db");
        }

        public String resolveCatalogForTenant(String tenantId) {
            String catalog = TENANT_TO_CATALOG.get(tenantId);
            if (catalog == null) {
                throw new IllegalArgumentException("Unknown tenant: " + tenantId);
            }
            return catalog;
        }
    }

    /**
     * Minimal principal abstraction exposing a tenant id.
     */
    public interface TenantPrincipal {
        String getTenantId();
    }
}
