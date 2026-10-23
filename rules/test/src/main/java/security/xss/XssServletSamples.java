package security.xss;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class XssServletSamples {

    @WebServlet("/xss-in-servlet-app/unsafe")
    public static class UnsafeGreetingServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();

            String name = request.getParameter("name");

            out.println("<html>");
            out.println("<head><title>Greeting</title></head>");
            out.println("<body>");
            out.println("<h1>Hello, " + name + "!</h1>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    @WebServlet("/xss-in-servlet-app/safe")
    public static class SafeGreetingServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();

            String name = request.getParameter("name");
            if (name == null) {
                name = "";
            }

            String safeName = org.apache.commons.text.StringEscapeUtils.escapeHtml4(name);

            out.println("<html>");
            out.println("<head><title>Greeting</title></head>");
            out.println("<body>");
            out.println("<h1>Hello, " + safeName + "!</h1>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    /**
     * The sanitizer clears the string, and the byte content of that string has to be
     * cleared with it: JIRTaintCleanActionEvaluator appends the String bytes slot to every
     * clean action on a String position. A getBytes/new String round trip after the
     * sanitizer therefore has to stay clean - if the engine's slot and the slot the
     * java.lang.String model writes ever drift apart, the round trip reads the stale slot
     * back and this reports.
     */
    @WebServlet("/xss-in-servlet-app/safe-bytes-round-trip")
    public static class SafeGreetingBytesRoundTripServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();

            String name = request.getParameter("name");
            if (name == null) {
                name = "";
            }

            String safeName = org.apache.commons.text.StringEscapeUtils.escapeHtml4(name);
            String roundTripped = new String(safeName.getBytes());

            out.println("<html>");
            out.println("<body>");
            out.println("<h1>Hello, " + roundTripped + "!</h1>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    @WebServlet("/response-injection-in-servlet-app/unsafe-json")
    public static class UnsafeJsonInfoServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("application/json;charset=UTF-8");
            PrintWriter out = response.getWriter();
            String name = request.getParameter("name");
            out.println("{\"greeting\": \"Hello, " + name + "\"}");
        }
    }
}
