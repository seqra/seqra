package security.codeinjection;

import java.io.IOException;
import java.io.PrintWriter;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

/**
 * Servlet-based samples for EL injection rules.
 */
public class ElInjectionSamples {

    @WebServlet("/code-injection/el/unsafe")
    public static class UnsafeElServlet extends HttpServlet {

        private final ExpressionFactory factory = ExpressionFactory.newInstance(); // using javax.el factory

        @Override
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "el-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String expr = request.getParameter("expr");

            ServletContext servletContext = getServletContext();
            ELContext elContext = (ELContext) servletContext.getAttribute("elContext");

            // VULNERABLE: evaluating raw user input as an EL expression
            ValueExpression ve = factory.createValueExpression(elContext, expr, Object.class);
            Object result = ve.getValue(elContext);

            PrintWriter writer = response.getWriter();
            writer.println("Result: " + result);
        }
    }

    @WebServlet("/code-injection/el/safe")
    public static class SafeElServlet extends HttpServlet {

        private final ExpressionFactory factory = ExpressionFactory.newInstance(); // using javax.el factory

        @Override
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "el-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String userName = request.getParameter("name");
            if (userName == null) {
                userName = "";
            }

            ServletContext servletContext = getServletContext();
            ELContext elContext = (ELContext) servletContext.getAttribute("elContext");
            elContext.getELResolver().setValue(elContext, null, "name", userName);

            // SAFE: expression is static, attacker cannot modify evaluated expression
            String template = "Hello ${name}";
            ValueExpression ve = factory.createValueExpression(elContext, template, String.class);
            String output = (String) ve.getValue(elContext);

            PrintWriter writer = response.getWriter();
            writer.println(output);
        }
    }
}
