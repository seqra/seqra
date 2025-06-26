package security.ldap;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.Hashtable;

/**
 * Samples for ldap-entry-poisoning rule.
 */
public class LdapEntryPoisoningSamples {

    @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-entry-poisoning")
    public Object vulnerableLookup(String url, String baseDn, String filter) throws Exception {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, url);

        DirContext ctx = new InitialDirContext(env);

        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        // VULNERABLE: request Java objects from LDAP entries
        sc.setReturningObjFlag(true);

        NamingEnumeration<SearchResult> results = ctx.search(baseDn, filter, sc);
        if (results.hasMore()) {
            SearchResult sr = results.next();
            return sr.getObject();
        }
        return null;
    }

    @NegativeRuleSample(value = "java/security/ldap.yaml", id = "ldap-entry-poisoning")
    public String safeLookupEmail(String url, String baseDn, String filter) throws Exception {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, url);

        // Hardened system properties (in line with rule description)
        System.setProperty("com.sun.jndi.ldap.object.trustURLCodebase", "false");
        System.setProperty("java.rmi.server.useCodebaseOnly", "true");

        DirContext ctx = new InitialDirContext(env);

        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        // SAFE: do not request Java objects, only attributes
        sc.setReturningObjFlag(false);
        sc.setReturningAttributes(new String[]{"mail"});

        NamingEnumeration<SearchResult> results = ctx.search(baseDn, filter, sc);
        if (results.hasMore()) {
            SearchResult sr = results.next();
            Attributes attrs = sr.getAttributes();
            Attribute mailAttr = attrs.get("mail");
            if (mailAttr != null) {
                return (String) mailAttr.get();
            }
        }
        return null;
    }
}
