package security.externalconfigurationcontrol;

import java.util.HashMap;
import java.util.Map;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for unsafe-reflection-in-spring rule.
 */
public class UnsafeReflectionSpringSamples {

    @RestController
    @RequestMapping("/spring/external-config/reflection")
    public static class UnsafeReflectionController {

        @GetMapping("/unsafe")
        @PositiveRuleSample(value = "java/security/external-configuration-control.yaml", id = "unsafe-reflection-in-spring-app")
        public String loadClass(@RequestParam String className) throws Exception {
            // UNSAFE: user input directly controls Class.forName
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            return "Loaded class: " + instance.getClass().getName();
        }
    }

    @RestController
    @RequestMapping("/spring/external-config/reflection")
    public static class SafeReflectionController {

        private static final Map<String, Class<?>> ALLOWED_CLASSES = new HashMap<>();

        static {
            ALLOWED_CLASSES.put("basicReport", SafeReport.class);
            ALLOWED_CLASSES.put("summaryReport", SafeReport.class);
        }

        @GetMapping("/safe")
        @NegativeRuleSample(value = "java/security/external-configuration-control.yaml", id = "unsafe-reflection-in-spring-app")
        public String loadClass(@RequestParam String type) throws Exception {
            Class<?> clazz = ALLOWED_CLASSES.get(type);
            if (clazz == null) {
                return "Invalid type";
            }
            Object instance = clazz.getDeclaredConstructor().newInstance();
            // Safe: only instances of known, vetted classes are created
            return "Generated report of type: " + type + " -> " + instance.getClass().getName();
        }
    }

    /**
     * Simple dummy class used in allowlist.
     */
    public static class SafeReport {
    }
}
