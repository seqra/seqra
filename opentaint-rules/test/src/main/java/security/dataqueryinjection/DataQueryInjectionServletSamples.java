package security.dataqueryinjection;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;




/**
 * Servlet-based samples for data-query-injection rules:
 * - xpath-injection-in-servlet-app
 * - mongodb-injection-in-servlet-app
 */
public class DataQueryInjectionServletSamples {

    /**
     * Vulnerable XPath usage: untrusted servlet data concatenated into an XPath expression.
     */
    @WebServlet("/data-query/xpath/unsafe")
    public static class UnsafeXPathServlet extends HttpServlet {

        private final XPath xPath = XPathFactory.newInstance().newXPath();
        private final Document usersDoc = null; // simplified for sample; assume initialized elsewhere

        @Override
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String username = request.getParameter("username");
            String password = request.getParameter("password");

            // VULNERABLE: user-controlled data concatenated directly into XPath expression
            String expression = "/users/user[username='" + username + "' and password='" + password + "']";
            try {
                XPathExpression compiled = xPath.compile(expression);
                Object result = compiled.evaluate(usersDoc, XPathConstants.NODE);
                response.getWriter().println(result != null ? "ok" : "fail");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    /**
     * Safe XPath usage: static XPath and comparisons done in Java code.
     */
    @WebServlet("/data-query/xpath/safe")
    public static class SafeXPathServlet extends HttpServlet {

        private final XPath xPath = XPathFactory.newInstance().newXPath();
        private final Document usersDoc = null; // simplified for sample; assume initialized elsewhere

        @Override
        @NegativeRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String username = request.getParameter("username");
            String password = request.getParameter("password");

            if (username == null || password == null
                    || username.length() > 50 || password.length() > 100
                    || !username.matches("[A-Za-z0-9._-]+")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid input");
                return;
            }

            try {
                XPathExpression compiled = xPath.compile("/users/user");
                NodeList users = (NodeList) compiled.evaluate(usersDoc, XPathConstants.NODESET);
                boolean authenticated = false;
                for (int i = 0; i < users.getLength(); i++) {
                    Element user = (Element) users.item(i);
                    String u = user.getElementsByTagName("username").item(0).getTextContent();
                    String p = user.getElementsByTagName("password").item(0).getTextContent();
                    if (username.equals(u) && password.equals(p)) {
                        authenticated = true;
                        break;
                    }
                }
                response.getWriter().println(authenticated ? "ok" : "fail");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    /**
     * Vulnerable MongoDB usage: $where with untrusted servlet data concatenated into JavaScript expression.
     */
    @WebServlet("/data-query/mongodb/unsafe")
    public static class UnsafeMongoServlet extends HttpServlet {

        private final DB legacyDb = null; // simplified for sample; assume initialized elsewhere

        @Override
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "mongodb-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String username = request.getParameter("username");
            String password = request.getParameter("password");

            DBCollection users = legacyDb.getCollection("users");

            // VULNERABLE: user-controlled data concatenated directly into $where JavaScript expression
            String whereClause = "this.username == '" + username + "' && this.password == '" + password + "'";

            DBObject query = new BasicDBObject("$where", whereClause);
            DBCursor cursor = users.find(query);
            boolean authenticated = cursor.hasNext();

            response.getWriter().println(authenticated ? "ok" : "fail");
        }
    }

    /**
     * Safe MongoDB usage: field-based query, no $where or JavaScript concatenation.
     */
    @WebServlet("/data-query/mongodb/safe")
    public static class SafeMongoServlet extends HttpServlet {

        private final MongoDatabase mongoDatabase = null; // simplified for sample; assume initialized elsewhere


        @Override
        @NegativeRuleSample(value = "java/security/data-query-injection.yaml", id = "mongodb-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String username = request.getParameter("username");
            String password = request.getParameter("password");

            if (username == null || password == null
                    || username.length() > 50 || password.length() > 100
                    || !username.matches("[A-Za-z0-9._-]+")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid input");
                return;
            }

            MongoCollection<org.bson.Document> users = mongoDatabase.getCollection("users");

            org.bson.Document user = users.find(


                    Filters.and(
                            Filters.eq("username", username),
                            Filters.eq("password", password) // in real apps, compare password hashes
                    )
            ).first();

            boolean authenticated = user != null;
            response.getWriter().println(authenticated ? "ok" : "fail");
        }
    }
}
