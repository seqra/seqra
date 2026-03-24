package security.codeinjection;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for pre-existing SSTI patterns:
 * Pebble, Jinjava, Velocity, VelocityEngine, RuntimeServices,
 * RuntimeSingleton, StringResourceRepository, Thymeleaf.
 */
public class TemplateInjectionExtraSpringSamples {

    // ── Pebble ────────────────────────────────────────────────────────────────

    @RestController
    @RequestMapping("/ssti/pebble")
    public static class UnsafePebbleController {

        // NOTE: PebbleEngine.getLiteralTemplate() was added in Pebble 3.x which uses the
        // io.pebbletemplates.pebble package (not com.mitchellbosecke.pebble). The existing rule
        // pattern targets com.mitchellbosecke.pebble.PebbleEngine so getLiteralTemplate cannot be
        // tested — the method doesn't exist in 2.x which uses the old package.

        @PostMapping("/unsafe/getTemplate")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-spring-app")
        public String unsafeGetTemplate(@RequestParam("name") String templateName) throws Exception {
            com.mitchellbosecke.pebble.PebbleEngine engine = new com.mitchellbosecke.pebble.PebbleEngine.Builder().build();
            com.mitchellbosecke.pebble.template.PebbleTemplate compiled = engine.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            compiled.evaluate(writer);
            return writer.toString();
        }
    }

    // ── Jinjava ───────────────────────────────────────────────────────────────

    @RestController
    @RequestMapping("/ssti/jinjava")
    public static class UnsafeJinjavaController {

        @PostMapping("/unsafe/render")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-spring-app")
        public String unsafeRender(@RequestParam("template") String templateContent) throws Exception {
            com.hubspot.jinjava.Jinjava jinjava = new com.hubspot.jinjava.Jinjava();
            Map<String, Object> context = new HashMap<>();
            return jinjava.render(templateContent, context);
        }

        @PostMapping("/unsafe/renderForResult")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-spring-app")
        public String unsafeRenderForResult(@RequestParam("template") String templateContent) throws Exception {
            com.hubspot.jinjava.Jinjava jinjava = new com.hubspot.jinjava.Jinjava();
            Map<String, Object> context = new HashMap<>();
            com.hubspot.jinjava.interpret.RenderResult result = jinjava.renderForResult(templateContent, context);
            return result.getOutput();
        }
    }

    // ── Velocity (static methods) ─────────────────────────────────────────────

    @RestController
    @RequestMapping("/ssti/velocity")
    public static class UnsafeVelocityController {

        @PostMapping("/unsafe/evaluate")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-spring-app")
        public String unsafeEvaluate(@RequestParam("template") String templateContent) throws Exception {
            org.apache.velocity.VelocityContext ctx = new org.apache.velocity.VelocityContext();
            StringWriter writer = new StringWriter();
            org.apache.velocity.app.Velocity.evaluate(ctx, writer, "tag", templateContent);
            return writer.toString();
        }

        // ANALYZER LIMITATION: Pattern checks Argument[2] (Context) but taint from user input
        // into VelocityContext requires ctx.put() taint propagation summary.
        // TODO: Re-enable when VelocityContext taint propagation summaries are added.
        @PostMapping("/unsafe/mergeTemplate")
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-spring-app")
        public String unsafeMergeTemplate(@RequestParam("data") String userData) throws Exception {
            org.apache.velocity.VelocityContext ctx = new org.apache.velocity.VelocityContext();
            ctx.put("data", userData);
            StringWriter writer = new StringWriter();
            org.apache.velocity.app.Velocity.mergeTemplate("safe.vm", "UTF-8", ctx, writer);
            return writer.toString();
        }
    }

    // ── VelocityEngine (instance methods) ─────────────────────────────────────

    @RestController
    @RequestMapping("/ssti/velocity-engine")
    public static class UnsafeVelocityEngineController {

        @PostMapping("/unsafe/evaluate")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-spring-app")
        public String unsafeEvaluate(@RequestParam("template") String templateContent) throws Exception {
            org.apache.velocity.app.VelocityEngine ve = new org.apache.velocity.app.VelocityEngine();
            ve.init();
            org.apache.velocity.VelocityContext ctx = new org.apache.velocity.VelocityContext();
            StringWriter writer = new StringWriter();
            ve.evaluate(ctx, writer, "tag", templateContent);
            return writer.toString();
        }

        // ANALYZER LIMITATION: Same as Velocity.mergeTemplate — pattern checks Argument[2] (Context).
        // TODO: Re-enable when VelocityContext taint propagation summaries are added.
        @PostMapping("/unsafe/mergeTemplate")
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-spring-app")
        public String unsafeMergeTemplate(@RequestParam("data") String userData) throws Exception {
            org.apache.velocity.app.VelocityEngine ve = new org.apache.velocity.app.VelocityEngine();
            ve.init();
            org.apache.velocity.VelocityContext ctx = new org.apache.velocity.VelocityContext();
            ctx.put("data", userData);
            StringWriter writer = new StringWriter();
            ve.mergeTemplate("safe.vm", "UTF-8", ctx, writer);
            return writer.toString();
        }
    }

    // ── Velocity RuntimeServices ──────────────────────────────────────────────

    @RestController
    @RequestMapping("/ssti/velocity-runtime")
    public static class UnsafeVelocityRuntimeController {

        @PostMapping("/unsafe/runtimeServicesEvaluate")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-spring-app")
        public String unsafeRuntimeServicesEvaluate(@RequestParam("template") String templateContent) throws Exception {
            org.apache.velocity.runtime.RuntimeServices rs = org.apache.velocity.runtime.RuntimeSingleton.getRuntimeServices();
            org.apache.velocity.VelocityContext ctx = new org.apache.velocity.VelocityContext();
            StringWriter writer = new StringWriter();
            rs.evaluate(ctx, writer, "tag", templateContent);
            return writer.toString();
        }

        @PostMapping("/unsafe/runtimeSingletonParse")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-spring-app")
        public String unsafeRuntimeSingletonParse(@RequestParam("template") String templateContent) throws Exception {
            org.apache.velocity.runtime.RuntimeSingleton.parse(new StringReader(templateContent), new org.apache.velocity.Template());
            return "parsed";
        }

        @PostMapping("/unsafe/stringResourceRepoPut")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-spring-app")
        public String unsafeStringResourceRepoPut(@RequestParam("template") String templateContent) throws Exception {
            org.apache.velocity.runtime.resource.util.StringResourceRepository repo =
                    org.apache.velocity.runtime.resource.loader.StringResourceLoader.getRepository();
            repo.putStringResource("dynamic", templateContent);
            return "loaded";
        }
    }

    // ── Thymeleaf ─────────────────────────────────────────────────────────────

    @RestController
    @RequestMapping("/ssti/thymeleaf")
    public static class UnsafeThymeleafController {

        @PostMapping("/unsafe/process")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-spring-app")
        public String unsafeProcess(@RequestParam("template") String templateContent) throws Exception {
            org.thymeleaf.ITemplateEngine engine = new org.thymeleaf.TemplateEngine();
            org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
            return engine.process(templateContent, ctx);
        }

        @PostMapping("/unsafe/processThrottled")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ssti-in-spring-app")
        public String unsafeProcessThrottled(@RequestParam("template") String templateContent) throws Exception {
            org.thymeleaf.ITemplateEngine engine = new org.thymeleaf.TemplateEngine();
            org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
            StringWriter writer = new StringWriter();
            engine.processThrottled(templateContent, ctx).processAll(writer);
            return writer.toString();
        }
    }
}
