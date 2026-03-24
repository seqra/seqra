package security.codeinjection;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.Expression;
import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Servlet-based samples for JEXL injection.
 */
public class JexlInjectionServletSamples {

    @WebServlet("/code-injection/jexl/unsafe")
    public static class UnsafeJexlServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "jexl-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String expr = request.getParameter("expr");

            JexlEngine engine = new JexlEngine();
            // VULNERABLE: creating JEXL expression from user input
            Expression expression = engine.createExpression(expr);
            Object result = expression.evaluate(null);

            PrintWriter writer = response.getWriter();
            writer.println("Result: " + result);
        }
    }
}
