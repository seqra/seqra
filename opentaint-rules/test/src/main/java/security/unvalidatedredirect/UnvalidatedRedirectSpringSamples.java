package security.unvalidatedredirect;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    }

    @Controller
    public static class SafeValidatedRedirectController {

        private static final Map<String, String> ALLOWED_TARGETS = Map.of(
                "home", "/home",
                "profile", "/user/profile",
                "orders", "/orders/list");

        private static final Set<String> ALLOWED_DOMAINS = Set.of("example.com", "trusted-partner.com");

        @GetMapping("/redirect/safe-internal")
        @NegativeRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-spring-app")
        public String safeInternalRedirect(@RequestParam(value = "target", required = false) String target) {
            // SAFE: only internal paths from controlled mapping
            String path = ALLOWED_TARGETS.getOrDefault(target, "/home");
            return "redirect:" + path;
        }

        @GetMapping("/redirect/safe-external")
// TODO: uncomment it when conditional sanitizers are implemented
//        @NegativeRuleSample(value = "java/security/unvalidated-redirect.yaml", id = "unvalidated-redirect-in-spring-app")
        public String safeExternalRedirect(@RequestParam("url") String url, HttpServletRequest request) {
            // SAFE: external redirects validated against an allowlist of domains
            try {
                URI uri = new URI(url);
                String host = uri.getHost();
                String scheme = uri.getScheme();

                if (host != null
                        && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                        && ALLOWED_DOMAINS.contains(host.toLowerCase())) {
                    return "redirect:" + uri.toString();
                }
            } catch (URISyntaxException e) {
                // fall through to safe default
            }

            // Fallback to a safe internal page on any failure or disallowed host
            return "redirect:/home";
        }
    }
}
