package security.ldap;

import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Samples for ldap-injection-in-spring rule.
 *
 * These samples model a Spring MVC controller scenario at the API level, but use
 * plain JNDI under the hood to avoid depending on the full Spring LDAP stack.
 */
class LdapInjectionSpringService {

    private final DirContext ctx;
    private final String baseDn;

    public LdapInjectionSpringService(DirContext ctx, String baseDn) {
        this.ctx = ctx;
        this.baseDn = baseDn;
    }

    public boolean vulnerableSearch(String username) throws Exception {
        // VULNERABLE: untrusted username concatenated into LDAP filter
        String filter = "(uid=" + username + ")";
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        NamingEnumeration<SearchResult> results = ctx.search(baseDn, filter, controls);
        return results.hasMore();
    }

    public boolean safeSearch(String username) throws Exception {
        if (username == null || !username.matches("[a-zA-Z0-9._-]{1,32}")) {
            return false;
        }

        String filter = "(uid={0})";
        Object[] args = new Object[]{username};
        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        NamingEnumeration<SearchResult> results = ctx.search(baseDn, filter, args, controls);
        return results.hasMore();
    }
}


/**
 * Spring MVC controller samples that connect HTTP request parameters with LDAP
 * operations implemented in {@link LdapInjectionSpringService}.
 */
public class LdapInjectionSpringSamples {

    @RestController
    @RequestMapping("/ldap-injection-in-spring-app")
    public static class UnsafeLdapSpringController {

        private final LdapInjectionSpringService ldapService;

        public UnsafeLdapSpringController(LdapInjectionSpringService ldapService) {
            this.ldapService = ldapService;
        }

        /**
         * Unsafe endpoint that passes untrusted request parameters into a vulnerable
         * LDAP search method which concatenates them into the LDAP filter.
         */
        @PostMapping("/unsafe")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public boolean unsafeSearch(@RequestParam("username") String username) throws Exception {
            // VULNERABLE: username flows into vulnerableSearch(), which builds an LDAP filter via concatenation
            return ldapService.vulnerableSearch(username);
        }
    }

    @RestController
    @RequestMapping("/ldap-injection-in-spring-app")
    public static class SafeLdapSpringController {

        private final LdapInjectionSpringService ldapService;

        public SafeLdapSpringController(LdapInjectionSpringService ldapService) {
            this.ldapService = ldapService;
        }

        @GetMapping("/safe")
// TODO: restore this when conditional validators are implemented
//        @NegativeRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public boolean safeSearch(@RequestParam("username") String username) throws Exception {
            return ldapService.safeSearch(username);
        }
    }
}
