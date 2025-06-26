package security.dataqueryinjection;

import javax.servlet.http.HttpServletRequest;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

// NOTE: We intentionally keep MongoDB types as simple placeholders so the focus remains on Semgrep patterns
// and not on interacting with a real database driver.

/**
 * Spring MVC-style samples for data-query-injection rules:
 * - xpath-injection-in-spring-app
 * - mongodb-injection-in-spring-app
 */
public class DataQueryInjectionSpringSamples {

    @Controller
    public static class UnsafeXPathController {

        private final javax.xml.xpath.XPath xPath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        private final org.w3c.dom.Document usersDoc = null; // simplified

        @GetMapping("/data-query/xpath/spring/unsafe")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String unsafeXPath(@RequestParam("username") String username,
                                  @RequestParam("password") String password) throws Exception {
            // VULNERABLE: user data concatenated into XPath
            String expression = "/users/user[username='" + username + "' and password='" + password + "']";
            javax.xml.xpath.XPathExpression compiled = xPath.compile(expression);
            compiled.evaluate(usersDoc, javax.xml.xpath.XPathConstants.NODE);
            return "ok";
        }
    }

    @Controller
    public static class SafeXPathController {

        private final javax.xml.xpath.XPath xPath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        private final org.w3c.dom.Document usersDoc = null; // simplified

        @GetMapping("/data-query/xpath/spring/safe")
        @NegativeRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String safeXPath(@RequestParam("username") String username,
                                @RequestParam("password") String password) throws Exception {
            if (username == null || password == null
                    || username.length() > 50 || password.length() > 100
                    || !username.matches("[A-Za-z0-9._-]+")) {
                return "invalid";
            }

            javax.xml.xpath.XPathExpression compiled = xPath.compile("/users/user");
            org.w3c.dom.NodeList users = (org.w3c.dom.NodeList) compiled.evaluate(usersDoc, javax.xml.xpath.XPathConstants.NODESET);
            for (int i = 0; i < users.getLength(); i++) {
                org.w3c.dom.Element user = (org.w3c.dom.Element) users.item(i);
                String u = user.getElementsByTagName("username").item(0).getTextContent();
                String p = user.getElementsByTagName("password").item(0).getTextContent();
                if (username.equals(u) && password.equals(p)) {
                    return "ok";
                }
            }
            return "fail";
        }
    }

    @Controller
    public static class UnsafeMongoController {

        private final com.mongodb.DB db = null; // simplified placeholder

        @GetMapping("/data-query/mongo/unsafe")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "mongodb-injection-in-spring-app")
        public String unsafeMongo(HttpServletRequest request) {
            String username = request.getParameter("username");
            String password = request.getParameter("password");

            com.mongodb.DBCollection users = db.getCollection("users");
            String whereClause = "this.username == '" + username + "' && this.password == '" + password + "'";
            com.mongodb.DBObject query = new com.mongodb.BasicDBObject("$where", whereClause);
            com.mongodb.DBCursor cursor = users.find(query);
            return cursor.hasNext() ? "ok" : "fail";
        }
    }

    @Controller
    public static class SafeMongoController {

        private final com.mongodb.client.MongoDatabase db = null; // simplified placeholder

        @GetMapping("/data-query/mongo/safe")
        @NegativeRuleSample(value = "java/security/data-query-injection.yaml", id = "mongodb-injection-in-spring-app")
        public String safeMongo(@RequestParam("username") String username,
                                @RequestParam("password") String password) {
            com.mongodb.client.MongoCollection<org.bson.Document> users = db.getCollection("users");
            // SAFE: uses typed filters API instead of string-based queries; actual DB interaction is not important for the sample
            users.find(
                    com.mongodb.client.model.Filters.and(
                            com.mongodb.client.model.Filters.eq("username", username),
                            com.mongodb.client.model.Filters.eq("password", password)
                    )
            );
            return "ok";
        }

    }
}
