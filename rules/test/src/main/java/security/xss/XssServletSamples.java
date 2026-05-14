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

public class XssServletSamples {

    @WebServlet("/xss-in-servlet-app/unsafe")
    public static class UnsafeGreetingServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
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
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
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

    @WebServlet("/response-injection-in-servlet-app/unsafe-json")
    public static class UnsafeJsonInfoServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("application/json;charset=UTF-8");
            PrintWriter out = response.getWriter();
            String name = request.getParameter("name");
            out.println("{\"greeting\": \"Hello, " + name + "\"}");
        }
    }
}
