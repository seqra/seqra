package security.xss;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// @RestController + explicit produces = "text/html". Branch 1b fires regardless of
// @RestController — explicit produces overrides Spring's converter default.
@RestController
public class UnsafeHtmlRestController {

    @GetMapping(value = "/unsafe-html", produces = "text/html")
    @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public String unsafeHtml(@RequestParam(required = false, defaultValue = "") String name) {
        return "<h1>Hello, " + name + "!</h1>";
    }
}
