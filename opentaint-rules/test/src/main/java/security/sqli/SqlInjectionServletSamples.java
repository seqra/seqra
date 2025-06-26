package security.sqli;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

/**
 * Samples for sql-injection-in-servlet.
 */
public class SqlInjectionServletSamples {

    /**
     * Unsafe servlet that concatenates untrusted request parameters into a SQL query.
     */
    @WebServlet("/sql-injection-in-servlet/unsafe")
    public static class UnsafeSqlServlet extends HttpServlet {

        private DataSource dataSource;

        @Override
        @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String userId = request.getParameter("userId"); // untrusted input

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {

                // VULNERABLE: directly concatenating user input into SQL
                String sql = "SELECT id, username FROM users WHERE id = '" + userId + "'";
                ResultSet rs = stmt.executeQuery(sql);

                PrintWriter out = response.getWriter();
                while (rs.next()) {
                    out.println(rs.getInt("id") + ":" + rs.getString("username"));
                }
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }

    /**
     * Safe servlet that uses a parameterized PreparedStatement and simple input validation.
     */
    @WebServlet("/sql-injection-in-servlet/safe")
    public static class SafeSqlServlet extends HttpServlet {

        private DataSource dataSource;

        @Override
        @NegativeRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String userId = request.getParameter("userId");

            // Basic validation: expect a numeric identifier
            if (userId == null || !userId.matches("\\d+")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid user id");
                return;
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT id, username FROM users WHERE id = ?")) {

                ps.setInt(1, Integer.parseInt(userId));
                ResultSet rs = ps.executeQuery();

                PrintWriter out = response.getWriter();
                while (rs.next()) {
                    out.println(rs.getInt("id") + ":" + rs.getString("username"));
                }
            } catch (SQLException e) {
                throw new ServletException(e);
            }
        }
    }
}
