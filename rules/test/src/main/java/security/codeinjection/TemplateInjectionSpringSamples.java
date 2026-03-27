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

import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;

/**
 * Spring MVC samples for ssti.
 */
public class TemplateInjectionSpringSamples {
    @Controller
    @RequestMapping("/code-injection/ssti-spring")
    public static class UnsafeTemplateControllerWithResolver {

        @PostMapping("/unsafe-resolver")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti")
        protected void previewUnsafeWithResolver(HttpServletRequest request, HttpServletResponse response) throws ServletException {
            String templateSource = request.getParameter("messageTemplate");

            Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);

            try {
                // UNSAFE: Configuration has not ALLOWS_NOTHING_RESOLVER set
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

    @Controller
    @RequestMapping("/code-injection/ssti-spring")
    public static class SafeTemplateControllerWithResolver {

        @PostMapping("/safe-resolver")
        @NegativeRuleSample(value = "java/security/code-injection.yaml", id = "ssti")
        protected void previewSafeWithResolver(HttpServletRequest request, HttpServletResponse response) throws ServletException {
            String templateSource = request.getParameter("messageTemplate");

            Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
            cfg.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);

            try {
                // SAFE: Configuration has ALLOWS_NOTHING_RESOLVER set
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
}
