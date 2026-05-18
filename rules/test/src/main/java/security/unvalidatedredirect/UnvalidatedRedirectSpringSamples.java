package security.unvalidatedredirect;

import java.util.Map;


import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Samples for unvalidated-redirect-in-spring rule.
 */
public class UnvalidatedRedirectSpringSamples {

    @Controller
    public static class UnsafeUnvalidatedRedirectController {

        @GetMapping("/redirect/unsafe")
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-spring-app")
        public String unsafeRedirect(@RequestParam("url") String url) {
            // VULNERABLE: unvalidated user-controlled URL in redirect
            return "redirect:" + url;
        }

        @GetMapping("/redirect/unsafe-view")
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-spring-app")
        public RedirectView unsafeRedirectView(@RequestParam("url") String url) {
            // VULNERABLE: unvalidated user-controlled URL in RedirectView
            return new RedirectView(url);
        }

        @GetMapping("/redirect/unsafe-model-and-view")
        @PositiveRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-spring-app")
        public ModelAndView unsafeModelAndViewRedirect(@RequestParam("url") String url) {
            // VULNERABLE: unvalidated user-controlled URL in ModelAndView redirect
            return new ModelAndView("redirect:" + url);
        }
    }

    @Controller
    public static class SafeMapLookupRedirectController {

        private static final Map<String, String> ALLOWED_TARGETS = Map.of(
                "home", "/home",
                "profile", "/user/profile",
                "orders", "/orders/list");

        @GetMapping("/redirect/safe-internal")
        @NegativeRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-spring-app")
        public String safeInternalRedirect(@RequestParam(value = "target", required = false) String target) {
            // SAFE: tainted `target` is only used as a Map key; the returned value is a constant from ALLOWED_TARGETS.
            String path = ALLOWED_TARGETS.getOrDefault(target, "/home");
            return "redirect:" + path;
        }
    }
}
