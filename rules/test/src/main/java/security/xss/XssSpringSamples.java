package security.xss;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

public class XssSpringSamples {

    @Controller
    public static class UnsafeXssSpringController {

        @GetMapping("/xss-in-spring-app/unsafe")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void unsafeGreet(@RequestParam(required = false) String name, HttpServletResponse response) throws IOException {
            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();

            out.println("<html>");
            out.println("<body>");
            out.println("<h1>Hello, " + name + "!</h1>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    @Controller
    public static class SafeXssSpringController {

        @GetMapping("/xss-in-spring-app/safe")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void safeGreet(@RequestParam(required = false, defaultValue = "") String name, HttpServletResponse response) throws IOException {
            if (name == null) {
                name = "";
            }

            String safeName = HtmlUtils.htmlEscape(name, "UTF-8");

            response.setContentType("text/html;charset=UTF-8");

            PrintWriter out = response.getWriter();

            out.println("<html>");
            out.println("<body>");
            out.println("<h1>Hello, " + safeName + "!</h1>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    @Controller
    public static class UnsafeNoContentTypeSpringController {

        @GetMapping("/xss-in-spring-app/unsafe-no-content-type")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-spring-app")
        public void unsafeNoContentType(@RequestParam(required = false) String name, HttpServletResponse response) throws IOException {
            PrintWriter out = response.getWriter();

            out.println("Hello, " + name + "!");
        }
    }

    @Controller
    public static class UnsafeResponseEntityController {

        @PostMapping("/xss-in-spring-app/unsafe-response-entity")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "response-injection-in-spring-app")
        public ResponseEntity<byte[]> unsafeResponseEntity(@RequestParam String filename) {
            String errorMessage = "Conversion failed for " + filename;
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorMessage.getBytes(StandardCharsets.UTF_8));
        }
    }

}
