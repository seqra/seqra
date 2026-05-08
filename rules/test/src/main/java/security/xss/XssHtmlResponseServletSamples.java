package security.xss;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
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

    // ── Positive: chained getWriter().write() (no local var) ────────────────

    @WebServlet("/xss-in-servlet-app/unsafe-chained-writer")
    public static class UnsafeChainedWriterServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String name = request.getParameter("name");
            response.getWriter().println("<h1>Hello, " + name + "!</h1>");
        }
    }

    // ── Positive: chained getOutputStream().write() ─────────────────────────

    @WebServlet("/xss-in-servlet-app/unsafe-chained-output-stream")
    public static class UnsafeChainedOutputStreamServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String name = request.getParameter("name");
            response.getOutputStream().write(("<h1>Hello, " + name + "!</h1>").getBytes());
        }
    }

    // ── Positive: getOutputStream() local-var assignment ────────────────────

    @WebServlet("/xss-in-servlet-app/unsafe-output-stream-local")
    public static class UnsafeOutputStreamLocalServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String name = request.getParameter("name");
            ServletOutputStream out = response.getOutputStream();
            out.write(("<h1>Hello, " + name + "!</h1>").getBytes());
        }
    }

    // ── Positive: sendError(code, message) renders message as text/html ─────

    @WebServlet("/xss-in-servlet-app/unsafe-send-error")
    public static class UnsafeSendErrorServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String name = request.getParameter("name");
            response.sendError(400, "Bad input: " + name);
        }
    }

    // ── Positive: pre-existing typed PrintWriter local ──────────────────────

    @WebServlet("/xss-in-servlet-app/unsafe-typed-print-writer")
    public static class UnsafeTypedPrintWriterServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String name = request.getParameter("name");
            PrintWriter out = obtainWriter(response);
            out.println("<h1>Hello, " + name + "!</h1>");
        }

        private static PrintWriter obtainWriter(HttpServletResponse response) throws IOException {
            return response.getWriter();
        }
    }

    // ── Negative: chained getWriter().write() with JSON content type ────────

    @WebServlet("/xss-in-servlet-app/safe-chained-writer-json")
    public static class SafeChainedWriterJsonServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("application/json;charset=UTF-8");
            String name = request.getParameter("name");
            response.getWriter().println("{\"greeting\": \"Hello, " + name + "\"}");
        }
    }

    // ── Negative: getOutputStream() with octet-stream content type ──────────

    @WebServlet("/xss-in-servlet-app/safe-output-stream-octet")
    public static class SafeOutputStreamOctetServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("application/octet-stream");
            String name = request.getParameter("name");
            response.getOutputStream().write(name.getBytes());
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
