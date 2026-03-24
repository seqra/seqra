package security.commandinjection;

import org.apache.commons.exec.CommandLine;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for command injection via Apache Commons Exec.
 */
public class CommonsExecCommandInjectionSamples {

    @RestController
    public static class UnsafeCommonsExecParseController {

        @GetMapping("/command-injection/commons-exec/parse")
        @PositiveRuleSample(value = "java/security/command-injection.yaml", id = "os-command-injection-in-spring-app")
        public String unsafeParse(@RequestParam("cmd") String cmd) throws Exception {
            // VULNERABLE: parsing user-controlled command string
            CommandLine cmdLine = CommandLine.parse(cmd);
            return cmdLine.toString();
        }
    }

    @RestController
    public static class UnsafeCommonsExecAddArgumentsController {

        @GetMapping("/command-injection/commons-exec/add-args")
        @PositiveRuleSample(value = "java/security/command-injection.yaml", id = "os-command-injection-in-spring-app")
        public String unsafeAddArguments(@RequestParam("args") String args) throws Exception {
            // VULNERABLE: adding user-controlled arguments
            CommandLine cmdLine = new CommandLine("mycommand");
            cmdLine.addArguments(args);
            return cmdLine.toString();
        }
    }
}
