package security.codeinjection;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

import freemarker.template.Configuration;
import freemarker.template.Template;

/**
 * Servlet-based samples for ssti-in-servlet.
 */
public class SstiServletSamples {

    @WebServlet("/code-injection/servlet/unsafe")
    public static class UnsafeTemplateServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-servlet-app")
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            // Attacker controls the entire template content
            String templateSource = request.getParameter("messageTemplate");

            ServletContext servletContext = getServletContext();
            Configuration cfg = (Configuration) servletContext.getAttribute("freemarkerCfg");

            try {
                // VULNERABLE: compiling a template directly from user input
                Template t = new Template("userTemplate", new StringReader(templateSource), cfg);

                Map<String, Object> model = new HashMap<>();
                model.put("username", request.getParameter("username"));

                response.setContentType("text/html;charset=UTF-8");
                Writer writer = response.getWriter();
                t.process(model, writer);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/code-injection/servlet/safe")
    public static class SafeTemplateServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-servlet-app")
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            ServletContext servletContext = getServletContext();
            Configuration cfg = (Configuration) servletContext.getAttribute("freemarkerCfg");

            try {
                // Use only server-controlled template names (e.g., stored on disk)
                String templateName = "message.ftl";
                Template t = cfg.getTemplate(templateName);

                String username = request.getParameter("username");
                String messageText = request.getParameter("messageText");

                Map<String, Object> model = new HashMap<>();
                model.put("username", username);
                model.put("messageText", messageText);

                response.setContentType("text/html;charset=UTF-8");
                Writer writer = response.getWriter();
                t.process(model, writer);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }
}
