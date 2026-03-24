package security.ldap;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.NamingException;
import javax.naming.InitialContext;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.event.EventDirContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;

import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchScope;

import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.ldap.client.api.LdapConnection;

import org.springframework.jndi.JndiTemplate;
import org.springframework.ldap.core.LdapOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Test samples for LDAP injection sinks added in task-09 and task-14:
 * - UnboundID SDK: asyncSearch, searchForEntry
 * - Apache Directory LDAP API: LdapConnection.search
 * - Spring LDAP: LdapTemplate.authenticate, find, findOne, searchForContext, searchForObject
 * - (task-14) JNDI: Context.listBindings/lookupLink, InitialContext.doLookup
 * - (task-14) Spring/Shiro JNDI: JndiTemplate.lookup
 * - (task-14) JMX: JMXConnectorFactory.connect, JMXConnector.connect
 * - (task-14) Spring LDAP ext: findByDn, listBindings, lookupContext, rename
 */
public class LdapInjectionSinkSamples {

    // ---- UnboundID SDK ----

    @RestController
    @RequestMapping("/ldap-sink/unboundid")
    public static class UnboundIdLdapSinkController {

        private LDAPConnection connection;

        @GetMapping("/async-search")
        // TODO: Analyzer FN – taint does not propagate through new SearchRequest() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public void asyncSearch(@RequestParam("filter") String filter) throws LDAPException {
            SearchRequest request = new SearchRequest("dc=example,dc=com", SearchScope.SUB, filter);
            connection.asyncSearch(request);
        }

        @GetMapping("/search-for-entry")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public void searchForEntry(@RequestParam("baseDn") String baseDn) throws LDAPException {
            connection.searchForEntry(baseDn, SearchScope.SUB, "(objectClass=*)");
        }
    }

    // ---- Apache Directory LDAP API ----

    @RestController
    @RequestMapping("/ldap-sink/apache-directory")
    public static class ApacheDirectoryLdapSinkController {

        private LdapConnection connection;

        @GetMapping("/search")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public void search(@RequestParam("baseDn") String baseDn) throws LdapException {
            connection.search(baseDn, "(objectClass=*)",
                    org.apache.directory.api.ldap.model.message.SearchScope.SUBTREE, "*");
        }
    }

    // ---- JNDI types (pre-existing pattern coverage) ----

    @RestController
    @RequestMapping("/ldap-sink/jndi")
    public static class JndiLdapSinkController {

        private javax.naming.Context context;
        private DirContext dirContext;
        private InitialDirContext initialDirContext;
        private LdapContext ldapContext;
        private EventDirContext eventDirContext;

        @GetMapping("/ldap-name")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object ldapName(@RequestParam("dn") String dn) throws Exception {
            return new LdapName(dn);
        }

        @GetMapping("/context-lookup")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object contextLookup(@RequestParam("name") String name) throws NamingException {
            return context.lookup(name);
        }

        @GetMapping("/dir-context-lookup")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object dirContextLookup(@RequestParam("name") String name) throws NamingException {
            return dirContext.lookup(name);
        }

        @GetMapping("/initial-dir-context-lookup")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object initialDirContextLookup(@RequestParam("name") String name) throws NamingException {
            return initialDirContext.lookup(name);
        }

        @GetMapping("/ldap-context-lookup")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object ldapContextLookup(@RequestParam("name") String name) throws NamingException {
            return ldapContext.lookup(name);
        }

        @GetMapping("/event-dir-context-lookup")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object eventDirContextLookup(@RequestParam("name") String name) throws NamingException {
            return eventDirContext.lookup(name);
        }

        @GetMapping("/ldap-context-search")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object ldapContextSearch(@RequestParam("filter") String filter) throws NamingException {
            SearchControls controls = new SearchControls();
            return ldapContext.search("dc=example,dc=com", filter, controls);
        }

        @GetMapping("/event-dir-context-search")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object eventDirContextSearch(@RequestParam("filter") String filter) throws NamingException {
            SearchControls controls = new SearchControls();
            return eventDirContext.search("dc=example,dc=com", filter, controls);
        }
    }

    // ---- Spring LDAP ----

    @RestController
    @RequestMapping("/ldap-sink/spring-ldap")
    public static class SpringLdapSinkController {

        private LdapTemplate ldapTemplate;

        @GetMapping("/authenticate")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public boolean authenticate(@RequestParam("filter") String filter) {
            return ldapTemplate.authenticate("ou=users,dc=example,dc=com", filter, "password");
        }

        @GetMapping("/find")
        // TODO: Analyzer FN – taint does not propagate through LdapQueryBuilder builder chain; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object find(@RequestParam("baseDn") String baseDn) {
            LdapQuery query = LdapQueryBuilder.query().base(baseDn).where("cn").is("test");
            return ldapTemplate.find(query, Object.class);
        }

        @GetMapping("/find-one")
        // TODO: Analyzer FN – taint does not propagate through LdapQueryBuilder builder chain; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object findOne(@RequestParam("baseDn") String baseDn) {
            LdapQuery query = LdapQueryBuilder.query().base(baseDn).where("cn").is("test");
            return ldapTemplate.findOne(query, Object.class);
        }

        @GetMapping("/search-for-context")
        // TODO: Analyzer FN – taint does not propagate through LdapQueryBuilder builder chain; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object searchForContext(@RequestParam("baseDn") String baseDn) {
            LdapQuery query = LdapQueryBuilder.query().base(baseDn).where("cn").is("test");
            return ldapTemplate.searchForContext(query);
        }

        @GetMapping("/search-for-object")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object searchForObject(@RequestParam("filter") String filter) {
            return ldapTemplate.searchForObject("ou=users,dc=example,dc=com", filter, ctx -> ctx);
        }

        @GetMapping("/list")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object list(@RequestParam("baseDn") String baseDn) {
            return ldapTemplate.list(baseDn);
        }

        @GetMapping("/lookup")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object lookup(@RequestParam("dn") String dn) {
            return ldapTemplate.lookup(dn);
        }
    }

    // ---- Spring LDAP via LdapOperations interface ----

    @RestController
    @RequestMapping("/ldap-sink/spring-ldap-ops")
    public static class SpringLdapOperationsSinkController {

        private LdapOperations ldapOperations;

        @GetMapping("/authenticate")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public boolean authenticate(@RequestParam("filter") String filter) {
            return ldapOperations.authenticate("ou=users,dc=example,dc=com", filter, "password");
        }

        @GetMapping("/find")
        // TODO: Analyzer FN – taint does not propagate through LdapQueryBuilder builder chain; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object find(@RequestParam("baseDn") String baseDn) {
            LdapQuery query = LdapQueryBuilder.query().base(baseDn).where("cn").is("test");
            return ldapOperations.find(query, Object.class);
        }

        @GetMapping("/find-one")
        // TODO: Analyzer FN – taint does not propagate through LdapQueryBuilder builder chain; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object findOne(@RequestParam("baseDn") String baseDn) {
            LdapQuery query = LdapQueryBuilder.query().base(baseDn).where("cn").is("test");
            return ldapOperations.findOne(query, Object.class);
        }

        @GetMapping("/search-for-context")
        // TODO: Analyzer FN – taint does not propagate through LdapQueryBuilder builder chain; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object searchForContext(@RequestParam("baseDn") String baseDn) {
            LdapQuery query = LdapQueryBuilder.query().base(baseDn).where("cn").is("test");
            return ldapOperations.searchForContext(query);
        }

        @GetMapping("/search-for-object")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object searchForObject(@RequestParam("filter") String filter) {
            return ldapOperations.searchForObject("ou=users,dc=example,dc=com", filter, ctx -> ctx);
        }

        @GetMapping("/list")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object list(@RequestParam("baseDn") String baseDn) {
            return ldapOperations.list(baseDn);
        }

        @GetMapping("/lookup")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object lookup(@RequestParam("dn") String dn) {
            return ldapOperations.lookup(dn);
        }
    }

    // ---- JNDI extensions (task-14) ----

    @RestController
    @RequestMapping("/ldap-sink/jndi-ext")
    public static class JndiExtensionsSinkController {

        private javax.naming.Context context;

        @GetMapping("/context-list-bindings")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object contextListBindings(@RequestParam("name") String name) throws NamingException {
            return context.listBindings(name);
        }

        @GetMapping("/context-lookup-link")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object contextLookupLink(@RequestParam("name") String name) throws NamingException {
            return context.lookupLink(name);
        }

        @GetMapping("/initial-context-do-lookup")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object initialContextDoLookup(@RequestParam("name") String name) throws NamingException {
            return InitialContext.doLookup(name);
        }
    }

    // ---- Spring JNDI JndiTemplate (task-14) ----

    @RestController
    @RequestMapping("/ldap-sink/spring-jndi")
    public static class SpringJndiTemplateSinkController {

        private JndiTemplate jndiTemplate;

        @GetMapping("/lookup")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object jndiLookup(@RequestParam("name") String name) throws NamingException {
            return jndiTemplate.lookup(name);
        }
    }

    // ---- Shiro JNDI JndiTemplate (task-14) ----

    @RestController
    @RequestMapping("/ldap-sink/shiro-jndi")
    public static class ShiroJndiTemplateSinkController {

        private org.apache.shiro.jndi.JndiTemplate shiroJndiTemplate;

        @GetMapping("/lookup")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object shiroJndiLookup(@RequestParam("name") String name) throws NamingException {
            return shiroJndiTemplate.lookup(name);
        }
    }

    // ---- JMX Connector (task-14) ----

    @RestController
    @RequestMapping("/ldap-sink/jmx")
    public static class JmxConnectorSinkController {

        @GetMapping("/connector-factory-connect")
        // TODO: Analyzer FN – taint does not propagate through new JMXServiceURL() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object jmxConnectorFactoryConnect(@RequestParam("url") String url) throws Exception {
            JMXServiceURL serviceUrl = new JMXServiceURL(url);
            return JMXConnectorFactory.connect(serviceUrl);
        }

        @GetMapping("/connector-connect")
        // TODO: Analyzer FN – taint does not propagate through new JMXServiceURL() + JMXConnectorFactory.newJMXConnector(); re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public void jmxConnectorConnect(@RequestParam("url") String url) throws Exception {
            JMXServiceURL serviceUrl = new JMXServiceURL(url);
            JMXConnector connector = JMXConnectorFactory.newJMXConnector(serviceUrl, null);
            connector.connect();
        }
    }

    // ---- Spring LDAP extended methods (task-14) ----

    @RestController
    @RequestMapping("/ldap-sink/spring-ldap-ext")
    public static class SpringLdapExtendedSinkController {

        private LdapTemplate ldapTemplate;
        private LdapOperations ldapOperations;

        @GetMapping("/find-by-dn")
        // TODO: Analyzer FN – taint does not propagate through new LdapName() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object findByDn(@RequestParam("dn") String dn) throws Exception {
            javax.naming.ldap.LdapName name = new javax.naming.ldap.LdapName(dn);
            return ldapTemplate.findByDn(name, Object.class);
        }

        @GetMapping("/list-bindings")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object listBindings(@RequestParam("baseDn") String baseDn) {
            return ldapTemplate.listBindings(baseDn);
        }

        @GetMapping("/lookup-context")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object lookupContext(@RequestParam("dn") String dn) {
            return ldapTemplate.lookupContext(dn);
        }

        @GetMapping("/rename")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public void rename(@RequestParam("oldDn") String oldDn) {
            ldapTemplate.rename(oldDn, "cn=new,ou=users,dc=example,dc=com");
        }

        // LdapOperations interface variants

        @GetMapping("/ops-find-by-dn")
        // TODO: Analyzer FN – taint does not propagate through new LdapName() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object opsFindByDn(@RequestParam("dn") String dn) throws Exception {
            javax.naming.ldap.LdapName name = new javax.naming.ldap.LdapName(dn);
            return ldapOperations.findByDn(name, Object.class);
        }

        @GetMapping("/ops-list-bindings")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object opsListBindings(@RequestParam("baseDn") String baseDn) {
            return ldapOperations.listBindings(baseDn);
        }

        @GetMapping("/ops-lookup-context")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public Object opsLookupContext(@RequestParam("dn") String dn) {
            return ldapOperations.lookupContext(dn);
        }

        @GetMapping("/ops-rename")
        @PositiveRuleSample(value = "java/security/ldap.yaml", id = "ldap-injection-in-spring-app")
        public void opsRename(@RequestParam("oldDn") String oldDn) {
            ldapOperations.rename(oldDn, "cn=new,ou=users,dc=example,dc=com");
        }
    }
}
