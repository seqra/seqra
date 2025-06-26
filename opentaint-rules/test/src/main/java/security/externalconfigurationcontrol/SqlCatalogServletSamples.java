package security.externalconfigurationcontrol;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

/**
 * Servlet-based samples for sql-catalog-external-manipulation-in-servlet-app rule.
 */
public class SqlCatalogServletSamples {

    private final DataSource dataSource;

    public SqlCatalogServletSamples(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @WebServlet("/external-config/catalog/unsafe")
    public static class UnsafeCatalogServlet extends HttpServlet {

        private final DataSource dataSource;

        public UnsafeCatalogServlet(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        @PositiveRuleSample(value = "java/security/external-configuration-control.yaml", id = "sql-catalog-external-manipulation-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String catalog = request.getParameter("catalog");

            try (Connection conn = dataSource.getConnection()) {
                // VULNERABLE: user-controlled catalog name
                conn.setCatalog(catalog);

                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE id = ?")) {
                    ps.setInt(1, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            response.getWriter().println("ok");
                        } else {
                            response.getWriter().println("empty");
                        }
                    }
                }
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/external-config/catalog/safe")
    public static class SafeCatalogServlet extends HttpServlet {

        private final DataSource dataSource;

        // Map from tenant id (e.g., authenticated user context) to allowed catalog name
        private static final Map<String, String> TENANT_CATALOGS = new HashMap<>();

        static {
            TENANT_CATALOGS.put("tenantA", "tenant_a_db");
            TENANT_CATALOGS.put("tenantB", "tenant_b_db");
        }

        public SafeCatalogServlet(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        @NegativeRuleSample(value = "java/security/external-configuration-control.yaml", id = "sql-catalog-external-manipulation-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String tenantId = (String) request.getAttribute("tenantId");
            String catalog = TENANT_CATALOGS.get(tenantId);
            if (catalog == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized tenant");
                return;
            }

            try (Connection conn = dataSource.getConnection()) {
                // Safe: catalog comes from trusted mapping, not from user input
                conn.setCatalog(catalog);

                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE id = ?")) {
                    ps.setInt(1, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            response.getWriter().println("ok");
                        } else {
                            response.getWriter().println("empty");
                        }
                    }
                }
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }
}
