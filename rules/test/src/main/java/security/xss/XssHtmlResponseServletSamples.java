package security.xss;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Servlet-based samples for xss-in-servlet-app (ERROR).
 *
 * XSS in servlets is ERROR by default because the Servlet spec defaults
 * to text/html when no content type is set.
 */
public class XssHtmlResponseServletSamples {

    // ── Positive: explicit text/html ────────────────────────────────────────

    @WebServlet("/xss-in-servlet-app/unsafe-html-explicit")
    public static class UnsafeHtmlServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();
            String name = request.getParameter("name");
            out.println("<h1>Hello, " + name + "!</h1>");
        }
    }

    // ── Positive: no content type (servlet defaults to text/html) ───────────

    @WebServlet("/xss-in-servlet-app/unsafe-no-content-type")
    public static class UnsafeNoContentTypeServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            PrintWriter out = response.getWriter();
            String name = request.getParameter("name");
            out.println("Hello, " + name + "!");
        }
    }

    // ── Negative: sanitized output ──────────────────────────────────────────

    @WebServlet("/xss-in-servlet-app/safe-html-explicit")
    public static class SafeHtmlServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();
            String name = request.getParameter("name");
            if (name == null) { name = ""; }
            String safeName = org.apache.commons.text.StringEscapeUtils.escapeHtml4(name);
            out.println("<h1>Hello, " + safeName + "!</h1>");
        }
    }
}
