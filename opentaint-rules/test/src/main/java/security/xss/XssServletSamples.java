package security.xss;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

/**
 * Servlet-based samples for xss-in-servlet-app.
 */
public class XssServletSamples {

    /**
     * Unsafe servlet that writes untrusted input directly into the HTML response without encoding.
     */
    @WebServlet("/xss-in-servlet-app/unsafe")
    public static class UnsafeGreetingServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();

            // Untrusted input taken directly from the request
            String name = request.getParameter("name");

            // VULNERABLE: Unencoded user input is directly embedded into HTML
            out.println("<html>");
            out.println("<head><title>Greeting</title></head>");
            out.println("<body>");
            out.println("<h1>Hello, " + name + "!</h1>"); // XSS if 'name' contains HTML/JS
            out.println("</body>");
            out.println("</html>");
        }
    }

    /**
     * Safe servlet that encodes untrusted input before including it in the HTML response.
     */
    @WebServlet("/xss-in-servlet-app/safe")
    public static class SafeGreetingServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();

            String name = request.getParameter("name");
            if (name == null) {
                name = "";
            }

            // Encode untrusted input for HTML context
            String safeName = org.apache.commons.text.StringEscapeUtils.escapeHtml4(name);

            out.println("<html>");
            out.println("<head><title>Greeting</title></head>");
            out.println("<body>");
            out.println("<h1>Hello, " + safeName + "!</h1>");
            out.println("</body>");
            out.println("</html>");
        }
    }
}
