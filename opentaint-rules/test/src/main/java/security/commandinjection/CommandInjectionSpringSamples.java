package security.commandinjection;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for os-command-injection-in-spring.
 */
public class CommandInjectionSpringSamples {

    @RestController
    public static class UnsafeCommandInjectionController {

        /**
         * Unsafe endpoint that concatenates untrusted input into an OS command
         * executed via Runtime.exec.
         */
        @GetMapping("/os-command-injection-in-spring/unsafe")
        @PositiveRuleSample(value = "java/security/command-injection.yaml", id = "os-command-injection-in-spring-app")
        public String unsafePing(@RequestParam String host) {
            // VULNERABLE: direct concatenation of untrusted input into OS command
            String command = "ping -c 4 " + host;

            StringBuilder output = new StringBuilder();
            try {
                Process process = Runtime.getRuntime().exec(command);
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                }
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
            return output.toString();
        }
    }

    @RestController
    public static class SafeCommandInjectionController {

        @GetMapping("/os-command-injection-in-spring/safe")
//      TODO: restore this when conditional validators are implemented
//        @NegativeRuleSample(value = "java/security/command-injection.yaml", id = "os-command-injection-in-spring-app")
        public String safePing(@RequestParam String host) {
            // Strict validation / whitelisting of the host value
            if (host == null || !host.matches("^[a-zA-Z0-9._-]{1,255}$")) {
                return "Invalid host";
            }

            StringBuilder output = new StringBuilder();
            try {
                ProcessBuilder pb = new ProcessBuilder("ping", "-c", "4", host);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                }
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
            return output.toString();
        }
    }
}
