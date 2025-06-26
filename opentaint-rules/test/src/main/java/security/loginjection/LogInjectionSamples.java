package security.loginjection;

import java.io.IOException;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.seam.annotations.Name;
import org.jboss.seam.log.Log;
import org.jboss.seam.log.Logging;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Samples for log injection rules in servlet, Spring, and Seam-style contexts.
 */
public class LogInjectionSamples {

    // log-injection-in-servlet-app

    @WebServlet("/log-injection-in-servlet/unsafe")
    public static class UnsafeLogServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/log-injection.yaml", id = "log-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String username = request.getParameter("username"); // untrusted

            Logger logger = LoggerFactory.getLogger(UnsafeLogServlet.class);

            // VULNERABLE: direct concatenation of untrusted data into log message
            logger.warn("Failed login attempt for user: " + username);

        }
    }

    @WebServlet("/log-injection-in-servlet/safe")
    public static class SafeLogServlet extends HttpServlet {

        @Override
//      TODO: restore this when conditional validators are implemented
//        @NegativeRuleSample(value = "java/security/log-injection.yaml", id = "log-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String username = request.getParameter("username");
            Logger logger = LoggerFactory.getLogger(SafeLogServlet.class);

            String safeUsername = sanitizeForLog(username);

            // SAFE: parameterized logging with sanitized value
            logger.warn("Failed login attempt for user [{}]", safeUsername);

        }
    }

    private static String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\r\\n\\t\\x00-\\x1F]", "_");
    }

    // log-injection-in-spring-app

    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping("/login/log-injection")
    public static class SpringLogInjectionController {

        private static final Logger logger = LoggerFactory.getLogger(SpringLogInjectionController.class);

        @org.springframework.web.bind.annotation.PostMapping("/unsafe")
        @PositiveRuleSample(value = "java/security/log-injection.yaml", id = "log-injection-in-spring-app")
        public org.springframework.http.ResponseEntity<String> unsafeLogin(
                @org.springframework.web.bind.annotation.RequestParam String username,
                @org.springframework.web.bind.annotation.RequestParam String password) {

            // VULNERABLE: concatenates untrusted username and password directly
            logger.warn("Failed login for user: " + username + " with password: " + password);
            return org.springframework.http.ResponseEntity.status(401).body("Login failed");
        }

        /*
        @org.springframework.web.bind.annotation.PostMapping("/safe")
        @NegativeRuleSample(value = "java/security/log-injection.yaml", id = "log-injection-in-spring-app")
        public org.springframework.http.ResponseEntity<String> safeLogin(
                @org.springframework.web.bind.annotation.RequestParam String username,
                @org.springframework.web.bind.annotation.RequestParam String password) {

            // Do not log passwords or secrets
            String safeUsername = sanitizeForLog(username);

            // SAFE: parameterized logging with sanitized username only
            logger.warn("Failed login attempt for user [{}]", safeUsername);

            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body("Login failed");
        }
        */
    }

    // seam-log-injection-in-servlet-app

    @Name("seamLoginActionServletStyle")
    public static class SeamServletStyleLoginAction {

        private static final Log seamLog = Logging.getLog(SeamServletStyleLoginAction.class);

        @PositiveRuleSample(value = "java/security/log-injection.yaml", id = "seam-log-injection")
        public void vulnerableSeamLogging() {
            Map<String, String> params = FacesContext.getCurrentInstance()
                    .getExternalContext()
                    .getRequestParameterMap();

            String username = params.get("username"); // untrusted

            // VULNERABLE: EL expression built from untrusted input
            seamLog.info("Login failed for user #{" + username + "}");
        }

        /*
        @NegativeRuleSample(value = "java/security/log-injection.yaml", id = "seam-log-injection")
        public void safeSeamLogging() {
            Map<String, String> params = FacesContext.getCurrentInstance()
                    .getExternalContext()
                    .getRequestParameterMap();

            String username = params.get("username");
            String safeUsername = sanitizeForLog(username).replace("#", "_").replace("{", "_").replace("}", "_");

            // SAFE: uses parameterized logging without EL, treats username as data
            seamLog.info("Login failed for user {0}", safeUsername);
        }
        */
    }
}
