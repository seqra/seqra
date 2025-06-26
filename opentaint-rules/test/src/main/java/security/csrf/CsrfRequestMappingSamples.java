package security.csrf;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Samples for unrestricted-request-mapping rule.
 */
public class CsrfRequestMappingSamples {

    @Controller
    public static class UnsafeUnrestrictedRequestMappingController {

        /**
         * VULNERABLE: state-changing action mapped with @RequestMapping without explicit HTTP method.
         * Spring will allow GET requests, which typically bypass CSRF token checks.
         */
        @RequestMapping("/csrf/transfer/unsafe")
        @PositiveRuleSample(value = "java/security/csrf.yaml", id = "unrestricted-request-mapping")
        public String unsafeTransfer(String fromAccount, String toAccount, double amount) {
            // Imagine state-changing transfer logic here
            return "Transfer initiated";
        }
    }

    @Controller
    public static class SafeRestrictedRequestMappingController {

        /**
         * SAFE: state-changing action restricted to POST method explicitly.
         */
        @RequestMapping(value = "/csrf/transfer/safe", method = RequestMethod.POST)
        @NegativeRuleSample(value = "java/security/csrf.yaml", id = "unrestricted-request-mapping")
        public String safeTransfer(String fromAccount, String toAccount, double amount) {
            // Proper CSRF-protected transfer logic via POST
            return "Transfer initiated safely";
        }

        /**
         * Also safe: using @PostMapping shortcut explicitly defines method.
         */
        @PostMapping("/csrf/transfer/safe-shortcut")
        @NegativeRuleSample(value = "java/security/csrf.yaml", id = "unrestricted-request-mapping")
        public String safeTransferShortcut(String fromAccount, String toAccount, double amount) {
            return "Transfer initiated safely via shortcut";
        }
    }
}
