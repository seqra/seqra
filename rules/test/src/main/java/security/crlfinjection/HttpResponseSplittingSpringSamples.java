package security.crlfinjection;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
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
        @PositiveRuleSample(value = "java/security/crlf-injection.yaml", id = "http-response-splitting")
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
}
