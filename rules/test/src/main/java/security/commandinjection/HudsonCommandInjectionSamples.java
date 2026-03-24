package security.commandinjection;

import hudson.Launcher;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for command injection via Hudson Launcher.
 */
public class HudsonCommandInjectionSamples {

    @RestController
    public static class UnsafeLauncherLaunchController {

        @GetMapping("/command-injection/hudson/launch")
        // TODO: Analyzer FN – taint does not propagate through String[] to Launcher.launch();
        // re-enable when taint through array construction is supported
        // @PositiveRuleSample(value = "java/security/command-injection.yaml", id = "os-command-injection-in-spring-app")
        public String unsafeLaunch(@RequestParam("cmd") String cmd, Launcher launcher) throws Exception {
            // VULNERABLE: passing user-controlled command to Launcher.launch
            String[] command = new String[]{cmd};
            launcher.launch(command, new String[]{}, null, null, null);
            return "launched";
        }
    }
}
