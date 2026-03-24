package security.loginjection;

import java.util.logging.Level;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test samples for additional log injection sink patterns (task-06).
 * Each inner controller tests a distinct logging framework / API covered by java-logging-sinks.
 */
public class LogInjectionAdditionalSinksSamples {

    // --- Log4j 1.x Category ---

    @RestController
    @RequestMapping("/log-injection/log4j1-category")
    public static class Log4j1CategoryController {

        private static final org.apache.log4j.Category cat =
                org.apache.log4j.Category.getInstance(Log4j1CategoryController.class);

        @GetMapping("/fatal")
        @PositiveRuleSample(value = "java/security/log-injection.yaml", id = "log-injection-in-spring-app")
        public ResponseEntity<String> fatal(@RequestParam String input) {
            cat.fatal("User data: " + input);
            return ResponseEntity.ok("ok");
        }
    }

    // --- Log4j 2.x LogBuilder (fluent API) ---

    @RestController
    @RequestMapping("/log-injection/log4j2-logbuilder")
    public static class Log4j2LogBuilderController {

        private static final org.apache.logging.log4j.Logger logger =
                org.apache.logging.log4j.LogManager.getLogger(Log4j2LogBuilderController.class);

        @GetMapping("/fluent")
        @PositiveRuleSample(value = "java/security/log-injection.yaml", id = "log-injection-in-spring-app")
        public ResponseEntity<String> fluent(@RequestParam String input) {
            logger.atInfo().log("User data: " + input);
            return ResponseEntity.ok("ok");
        }
    }

    // --- Log4j 2.x Logger extra methods ---

    @RestController
    @RequestMapping("/log-injection/log4j2-extras")
    public static class Log4j2ExtrasController {

        private static final org.apache.logging.log4j.Logger logger =
                org.apache.logging.log4j.LogManager.getLogger(Log4j2ExtrasController.class);

        @GetMapping("/printf")
        @PositiveRuleSample(value = "java/security/log-injection.yaml", id = "log-injection-in-spring-app")
        public ResponseEntity<String> printf(@RequestParam String input) {
            logger.printf(org.apache.logging.log4j.Level.INFO, "User data: %s", input);
            return ResponseEntity.ok("ok");
        }
    }

    // --- Google Flogger ---

    @RestController
    @RequestMapping("/log-injection/flogger")
    public static class FloggerController {

        private static final com.google.common.flogger.FluentLogger flogger =
                com.google.common.flogger.FluentLogger.forEnclosingClass();

        @GetMapping("/log")
        @PositiveRuleSample(value = "java/security/log-injection.yaml", id = "log-injection-in-spring-app")
        public ResponseEntity<String> log(@RequestParam String input) {
            flogger.atInfo().log("User data: " + input);
            return ResponseEntity.ok("ok");
        }
    }

    // --- JBoss Logging (Logger) ---

    @RestController
    @RequestMapping("/log-injection/jboss-logger")
    public static class JBossLoggerController {

        private static final org.jboss.logging.Logger jbossLogger =
                org.jboss.logging.Logger.getLogger(JBossLoggerController.class);

        @GetMapping("/infof")
        @PositiveRuleSample(value = "java/security/log-injection.yaml", id = "log-injection-in-spring-app")
        public ResponseEntity<String> infof(@RequestParam String input) {
            jbossLogger.infof("User data: %s", input);
            return ResponseEntity.ok("ok");
        }
    }

    // --- JBoss Logging (BasicLogger interface) ---

    @RestController
    @RequestMapping("/log-injection/jboss-basiclogger")
    public static class JBossBasicLoggerController {

        @GetMapping("/warnv")
        @PositiveRuleSample(value = "java/security/log-injection.yaml", id = "log-injection-in-spring-app")
        public ResponseEntity<String> warnv(@RequestParam String input) {
            org.jboss.logging.BasicLogger logger = org.jboss.logging.Logger.getLogger(JBossBasicLoggerController.class);
            logger.warnv("User data: {0}", input);
            return ResponseEntity.ok("ok");
        }
    }

    // --- SLF4J LoggingEventBuilder (fluent API, SLF4J 2.x) ---

    @RestController
    @RequestMapping("/log-injection/slf4j-fluent")
    public static class Slf4jFluentController {

        private static final org.slf4j.Logger slf4jLogger =
                org.slf4j.LoggerFactory.getLogger(Slf4jFluentController.class);

        @GetMapping("/log")
        @PositiveRuleSample(value = "java/security/log-injection.yaml", id = "log-injection-in-spring-app")
        public ResponseEntity<String> log(@RequestParam String input) {
            slf4jLogger.atInfo().log("User data: " + input);
            return ResponseEntity.ok("ok");
        }
    }

    // --- Apache CXF LogUtils ---

    @RestController
    @RequestMapping("/log-injection/cxf-logutils")
    public static class CxfLogUtilsController {

        private static final java.util.logging.Logger julLogger =
                java.util.logging.Logger.getLogger(CxfLogUtilsController.class.getName());

        @GetMapping("/log")
        @PositiveRuleSample(value = "java/security/log-injection.yaml", id = "log-injection-in-spring-app")
        public ResponseEntity<String> log(@RequestParam String input) {
            org.apache.cxf.common.logging.LogUtils.log(julLogger, Level.INFO, "User data: " + input);
            return ResponseEntity.ok("ok");
        }
    }

    // --- SciJava Logger ---

    @RestController
    @RequestMapping("/log-injection/scijava")
    public static class SciJavaLoggerController {

        @GetMapping("/alwaysLog")
        @PositiveRuleSample(value = "java/security/log-injection.yaml", id = "log-injection-in-spring-app")
        public ResponseEntity<String> alwaysLog(@RequestParam String input) {
            org.scijava.log.Logger logger = new org.scijava.log.StderrLogService();
            logger.alwaysLog(0, "User data: " + input, null);
            return ResponseEntity.ok("ok");
        }
    }
}
