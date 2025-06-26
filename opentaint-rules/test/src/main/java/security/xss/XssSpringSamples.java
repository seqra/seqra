package security.xss;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;



/**
 * Spring MVC samples for xss-in-spring-app.
 */
public class XssSpringSamples {

    @Controller
    public static class UnsafeXssSpringController {

        /**
         * Unsafe endpoint that writes untrusted data directly into the HTTP response without encoding.
         * This models a direct (reflected) XSS, which the rule is meant to detect.
         */
        @GetMapping("/xss-in-spring-app/unsafe")
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void unsafeGreet(@RequestParam(required = false) String name, HttpServletResponse response) throws IOException {
            response.setContentType("text/html;charset=UTF-8");
            PrintWriter out = response.getWriter();

            // VULNERABLE: untrusted input is written directly to the page
            out.println("<html>");
            out.println("<body>");
            out.println("<h1>Hello, " + name + "!</h1>");
            out.println("</body>");
            out.println("</html>");
        }
    }


    @Controller
    public static class SafeXssSpringController {

        /**
         * Safe endpoint that encodes user input before writing it to the HTTP response,
         * so no direct untrusted data flow reaches the page.
         */
        @GetMapping("/xss-in-spring-app/safe")
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
        public void safeGreet(@RequestParam(required = false, defaultValue = "") String name, HttpServletResponse response) throws IOException {
            if (name == null) {
                name = "";
            }

            // Use Spring's standard HTML escaper for safe output encoding
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

}
