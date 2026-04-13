package security.xss;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;


/**
 * Spring MVC samples for xss-in-spring-app (ERROR).
 *
 * XSS in Spring is ERROR by default because:
 * - StringHttpMessageConverter negotiates to text/html for String returns
 * - Servlet spec defaults to text/html for direct response writers
 * - ResponseEntity without explicit content type is subject to content sniffing
 */
public class XssHtmlResponseSpringSamples {

    // ── String return from @RestController (content negotiation → text/html) ─

    @RestController
    public static class UnsafeStringReturnController {

        /**
         * String return from @RestController without produces.
         * StringHttpMessageConverter negotiates to text/html with browser Accept header.
         */
        @GetMapping("/xss-in-spring-app/unsafe-string-return")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String unsafeStringReturn(@RequestParam(required = false) String name) {
            return "<h1>Hello, " + name + "!</h1>";
        }
    }

    // ── ResponseEntity<String> return ───────────────────────────────────────

    @Controller
    public static class UnsafeResponseEntityStringController {

        /**
         * ResponseEntity&lt;String&gt; — body goes through StringHttpMessageConverter.
         */
        @PostMapping("/xss-in-spring-app/unsafe-response-entity-string")
        @ResponseBody
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public ResponseEntity<String> unsafeResponseEntityString(@RequestParam String filename) {
            String errorMessage = "Conversion failed for " + filename;
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorMessage);
        }
    }

    // ── Direct response writer with text/html content type ──────────────────

    @Controller
    public static class UnsafeSetContentTypeController {

        @GetMapping("/xss-in-spring-app/unsafe-html")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void unsafeHtmlGreet(@RequestParam(required = false) String name, HttpServletResponse response) throws IOException {
            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();
            out.println("<h1>Hello, " + name + "!</h1>");
        }
    }

    // ── setHeader("Content-Type", "text/html") ──────────────────────────────

    @Controller
    public static class UnsafeSetHeaderController {

        @GetMapping("/xss-in-spring-app/unsafe-set-header")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void unsafeSetHeaderGreet(@RequestParam(required = false) String name, HttpServletResponse response) throws IOException {
            response.setHeader("Content-Type", "text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();
            out.println("<h1>Hello, " + name + "!</h1>");
        }
    }

    // ── Negative: sanitized HTML output ─────────────────────────────────────

    @Controller
    public static class SafeHtmlController {

        @GetMapping("/xss-in-spring-app/safe-html")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void safeHtmlGreet(@RequestParam(required = false, defaultValue = "") String name, HttpServletResponse response) throws IOException {
            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();
            String safeName = HtmlUtils.htmlEscape(name, "UTF-8");
            out.println("<h1>Hello, " + safeName + "!</h1>");
        }
    }

    // ── Negative: sanitized String return ────────────────────────────────────

    @RestController
    public static class SafeStringReturnController {

        @GetMapping("/xss-in-spring-app/safe-string-return")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public String safeStringReturn(@RequestParam(required = false, defaultValue = "") String name) {
            String safeName = HtmlUtils.htmlEscape(name, "UTF-8");
            return "<h1>Hello, " + safeName + "!</h1>";
        }
    }
}
