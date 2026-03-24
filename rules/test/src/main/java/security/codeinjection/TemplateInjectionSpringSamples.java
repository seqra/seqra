package security.codeinjection;

import java.io.StringReader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;

/**
 * Spring MVC samples for ssti-in-spring.
 */
public class TemplateInjectionSpringSamples {

    @Controller
    @RequestMapping("/code-injection/ssti-spring")
    public static class UnsafeTemplateController {

        @Autowired
        private Configuration freemarkerConfiguration;

        @PostMapping("/unsafe")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti")
        protected void previewUnsafe(HttpServletRequest request, HttpServletResponse response) throws ServletException {
            // Attacker controls the entire template content
            String templateSource = request.getParameter("messageTemplate");

            try {
                // VULNERABLE: compiling a template directly from user input
                Template t = new Template("userTemplate", new StringReader(templateSource), freemarkerConfiguration);

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

    @Controller
    @RequestMapping("/code-injection/ssti-stringloader")
    public static class UnsafeStringTemplateLoaderController {

        @PostMapping("/unsafe")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-spring-app")
        protected void loadUnsafe(HttpServletRequest request, HttpServletResponse response) throws Exception {
            String templateContent = request.getParameter("template");

            // VULNERABLE: user-controlled template content loaded via StringTemplateLoader
            StringTemplateLoader loader = new StringTemplateLoader();
            loader.putTemplate("dynamic", templateContent);

            Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
            cfg.setTemplateLoader(loader);
            Template t = cfg.getTemplate("dynamic");

            response.setContentType("text/html;charset=UTF-8");
            t.process(new HashMap<>(), response.getWriter());
        }
    }

    @Controller
    @RequestMapping("/code-injection/ssti-spring")
    public static class SafeTemplateServlet extends HttpServlet {

        @Autowired
        private Configuration freemarkerConfiguration;

        @PostMapping("/safe")
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "ssti")
        protected void previewSafe(HttpServletRequest request, HttpServletResponse response) throws ServletException {
            try {
                // Use only server-controlled template names (e.g., stored on disk)
                String templateName = "message.ftl";
                Template t = freemarkerConfiguration.getTemplate(templateName);

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
