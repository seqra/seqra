package security.ldap;

import java.io.IOException;

import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.seqra.sast.test.util.PositiveRuleSample;

/**
 * Samples for ldap-injection-in-servlet rule.
 */
class LdapInjectionService {

    private final DirContext ctx;
    private final String baseDn;

    public LdapInjectionService(DirContext ctx, String baseDn) {
        this.ctx = ctx;
        this.baseDn = baseDn;
    }

    public boolean vulnerableAuthenticate(String username, String password) throws Exception {
        // VULNERABLE: untrusted input concatenated directly into LDAP filter
        String filter = "(&(uid=" + username + ")(userPassword=" + password + "))";

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        NamingEnumeration<SearchResult> results = ctx.search(baseDn, filter, controls);
        return results.hasMore();
    }

    public boolean safeAuthenticate(String username, String password) throws Exception {
        // BASIC VALIDATION: allow only simple usernames
        if (username == null || !username.matches("[a-zA-Z0-9._-]{1,32}")) {
            return false;
        }

        String filter = "(&(uid={0})(userPassword={1}))";

        SearchControls controls = new SearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        Object[] filterArgs = new Object[]{username, password};
        NamingEnumeration<SearchResult> results = ctx.search(baseDn, filter, filterArgs, controls);
        return results.hasMore();
    }
}

/**
 * Controller/servlet samples to connect HTTP request parameters with LDAP calls
 * for the ldap-injection-in-servlet rule.
 */
public class LdapInjectionServletSamples extends HttpServlet {

    private final LdapInjectionService authService;

    public LdapInjectionServletSamples(LdapInjectionService authService) {
        this.authService = authService;
    }

    @Override
    @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-servlet-app")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // VULNERABLE: request parameters (untrusted) flow into LDAP filter via vulnerableAuthenticate()
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        try {
            authService.vulnerableAuthenticate(username, password);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
//  TODO: restore this when conditional validators are implemented
//    @NegativeRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-servlet-app")
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // SAFE: request parameters flow into safeAuthenticate(), which uses filter arguments
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        try {
            authService.safeAuthenticate(username, password);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
