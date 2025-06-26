package security.crlfinjection;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Spring MVC samples for http-response-splitting-in-spring.
 */
public class HttpResponseSplittingSpringSamples {

    @Controller
    public static class UnsafeHttpResponseSplittingController {

        /**
         * Unsafe endpoint that uses untrusted input directly in headers and redirect URLs.
         */
        @GetMapping("/http-response-splitting-in-spring/unsafe")
        @PositiveRuleSample(value = "java/security/crlf-injection.yaml", id = "http-response-splitting-in-spring-app")
        public void unsafe(@RequestParam(name = "user", required = false) String user,
                           @RequestParam(name = "next", required = false) String next,
                           HttpServletResponse response) throws IOException {

            if (user == null) {
                user = "anonymous";
            }

            // VULNERABLE: unvalidated input written directly into header
            response.setHeader("X-User", user);

            if (next == null) {
                next = "/";
            }

            // VULNERABLE: user-controlled path concatenated into redirect target
            response.sendRedirect("/home?next=" + next);
        }
    }

    @Controller
    public static class SafeHttpResponseSplittingController {

        /**
         * Safe endpoint that validates header and redirect values.
         */
        @GetMapping("/http-response-splitting-in-spring/safe")
// TODO: restore this when conditional validators are implemented
//        @NegativeRuleSample(value = "java/security/crlf-injection.yaml", id = "http-response-splitting-in-spring-app")
        public void safe(@RequestParam(name = "user", required = false) String user,
                         @RequestParam(name = "next", required = false) String next,
                         HttpServletResponse response) throws IOException {

            if (user == null) {
                user = "anonymous";
            }

            if (user.contains("\r") || user.contains("\n")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid user");
                return;
            }

            if (!user.matches("^[A-Za-z0-9_-]{1,32}$")) {
                user = "anonymous";
            }

            response.setHeader("X-User", user);

            if (next == null || next.isBlank()) {
                next = "/home";
            }

            if (next.contains("\r") || next.contains("\n")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid next");
                return;
            }

            if (!next.startsWith("/")) {
                next = "/home";
            }

            response.sendRedirect(next);
        }
    }
}
