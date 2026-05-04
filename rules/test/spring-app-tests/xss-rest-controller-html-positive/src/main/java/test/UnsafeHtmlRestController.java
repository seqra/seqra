package test;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `@RestController` + explicit `produces = "text/html"`.
 *
 * The class-level `@RestController` exclusion would suppress this if it
 * were the only mechanism in play, but the rule's dedicated
 * explicit-HTML-producer branch (Branch 1b) fires regardless of the class
 * annotation — explicit `produces` overrides Spring's converter default.
 * This sample pins that recovery path and contrasts with the
 * `xss-rest-controller-string-negative` sub-project.
 */
@RestController
public class UnsafeHtmlRestController {

    @GetMapping(value = "/unsafe-html", produces = "text/html")
    @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-spring-app")
    public String unsafeHtml(@RequestParam(required = false, defaultValue = "") String name) {
        return "<h1>Hello, " + name + "!</h1>";
    }
}
