package security.insecuredesign;

import java.security.AccessController;
import java.security.PrivilegedAction;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ServerWebExchange;

/**
 * Samples for rules in java/security/insecure-design.yaml.
 */
public class InsecureDesignSamples {


    // === do-privileged-use ===

    @PositiveRuleSample(value = "java/security/insecure-design.yaml", id = "do-privileged-use")
    public void runWithElevatedPrivilegesInsecure() {
        // Insecure by design: unnecessarily broad privileged block performing multiple actions
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            System.setProperty("app.debug", "true");
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                System.out.println("User home: " + userHome);
            }
            return null;
        });
    }

    @NegativeRuleSample(value = "java/security/insecure-design.yaml", id = "do-privileged-use")
    public void avoidPrivilegedBlockSecure() {
        // Secure alternative: avoid doPrivileged and rely on normal permission checks
        String value = System.getProperty("app.mode");
        if (value != null) {
            System.out.println("Mode: " + value);
        }
    }

    // === trust-boundary-violation ===

    @PositiveRuleSample(value = "java/security/insecure-design.yaml", id = "trust-boundary-violation")
    @GetMapping("/insecure-design/trust-boundary-violation/unsafe")
    public void mixesTrustedAndUntrustedInSessionInsecure(HttpServletRequest request) {
        // Insecure design: store raw request parameter alongside trusted data in the same session attribute
        String username = (String) request.getSession().getAttribute("username");
        String theme = request.getParameter("theme"); // untrusted
        request.getSession().setAttribute("userProfile", username + ":" + theme);
    }

    @NegativeRuleSample(value = "java/security/insecure-design.yaml", id = "trust-boundary-violation")
    @GetMapping("/insecure-design/trust-boundary-violation/safe")
    public void validateBeforeCrossingTrustBoundarySecure(HttpServletRequest request) {
        String username = (String) request.getSession().getAttribute("username");
        String version = System.getenv("PROFILE_VERSION");
        request.getSession().setAttribute("userProfile", username + "." + version);
    }

    // === cookie-missing-httponly ===

    @PositiveRuleSample(value = "java/security/insecure-design.yaml", id = "cookie-missing-httponly")
    public void createSessionCookieWithoutHttpOnlyInsecure(HttpServletResponse response) {
        Cookie sessionCookie = new Cookie("SESSION", "secret-token");
        // Insecure: HttpOnly flag is never set
        response.addCookie(sessionCookie);
    }

    @NegativeRuleSample(value = "java/security/insecure-design.yaml", id = "cookie-missing-httponly")
    public void createSessionCookieWithHttpOnlySecure(HttpServletResponse response) {
        Cookie sessionCookie = new Cookie("SESSION", "secret-token");
        sessionCookie.setHttpOnly(true);
        response.addCookie(sessionCookie);
    }

    // === persistent-cookie ===

    @PositiveRuleSample(value = "java/security/insecure-design.yaml", id = "persistent-cookie")
    public void createPersistentSensitiveCookieInsecure(HttpServletResponse response) {
        Cookie authCookie = new Cookie("AUTH_TOKEN", "some-sensitive-token");
        // Insecure: long-lived cookie (>= 1 year)
        authCookie.setMaxAge(31536000);
        response.addCookie(authCookie);
    }

    @NegativeRuleSample(value = "java/security/insecure-design.yaml", id = "persistent-cookie")
    public void createShortLivedCookieSecure(HttpServletResponse response) {
        Cookie tempCookie = new Cookie("TEMP_TOKEN", "short-lived");
        tempCookie.setMaxAge(300); // 5 minutes
        tempCookie.setHttpOnly(true);
        response.addCookie(tempCookie);
    }

    // === permissive-cors ===

    @PositiveRuleSample(value = "java/security/insecure-design.yaml", id = "permissive-cors")
    public void setPermissiveCorsHeadersInServlet(HttpServletResponse response) {
        // Insecure: allow any origin
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }

    @PositiveRuleSample(value = "java/security/insecure-design.yaml", id = "permissive-cors")
    public void setPermissiveCorsHeadersInSpring(HttpHeaders headers) {
        // Insecure: allow any origin via HttpHeaders
        headers.set("Access-Control-Allow-Origin", "*");
    }

/* todo: header values is a vararg.
   in bytecode:
   x = new String[]
   x[0] = "*"
   header(..., x)
*/

//    @PositiveRuleSample(value = "java/security/insecure-design.yaml", id = "permissive-cors")
//    public ResponseEntity<String> setPermissiveCorsHeadersInResponseEntity() {
//        // Insecure: ResponseEntity builder with wildcard origin
//        return ResponseEntity.ok()
//                .header("Access-Control-Allow-Origin", "*")
//                .body("ok");
//    }

    @PositiveRuleSample(value = "java/security/insecure-design.yaml", id = "permissive-cors")
    public void setPermissiveCorsHeadersInReactive(ServerHttpResponse response) {
        // Insecure: reactive ServerHttpResponse with wildcard origin
        response.getHeaders().add("Access-Control-Allow-Origin", "*");
    }

    @PositiveRuleSample(value = "java/security/insecure-design.yaml", id = "permissive-cors")
    public void setPermissiveCorsHeadersInServerWebExchange(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().add("Access-Control-Allow-Origin", "*");
    }

    @NegativeRuleSample(value = "java/security/insecure-design.yaml", id = "permissive-cors")
    public void setRestrictedCorsHeadersSecure(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if ("https://app.example.com".equals(origin) || "https://admin.example.com".equals(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST");
        }
    }
}
