package security.codeinjection;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mvel2.MVEL;
import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Servlet-based samples for MVEL injection.
 */
public class MvelInjectionServletSamples {

    @WebServlet("/code-injection/mvel/unsafe")
    public static class UnsafeMvelServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "mvel-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String expr = request.getParameter("expr");

            // VULNERABLE: evaluating user-controlled MVEL expression
            Object result = MVEL.eval(expr);

            PrintWriter writer = response.getWriter();
            writer.println("Result: " + result);
        }
    }
}
