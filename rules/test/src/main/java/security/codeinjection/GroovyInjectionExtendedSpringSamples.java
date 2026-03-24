package security.codeinjection;

import groovy.text.SimpleTemplateEngine;
import groovy.text.TemplateEngine;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for extended Groovy injection sinks.
 */
public class GroovyInjectionExtendedSpringSamples {

    @RestController
    @RequestMapping("/groovy-injection-extended")
    public static class UnsafeTemplateEngineController {

        @GetMapping("/template-engine")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "groovy-injection-in-spring-app")
        public String unsafeTemplateEngine(@RequestParam("template") String template) throws Exception {
            TemplateEngine engine = new SimpleTemplateEngine();
            // VULNERABLE: creating Groovy template from user input
            groovy.text.Template t = engine.createTemplate(template);
            return t.make().toString();
        }
    }
}
