package security.passthrough;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.PushBuilder;

/**
 * Regression samples for the explicit {@code HttpServletRequest} accessor models in
 * {@code model/java/config/javax.servlet-javax.servlet-api-4.0.1.yaml}.
 *
 * These models replaced the engine's implicit {@code get*} passthrough, so every accessor
 * that used to be covered by that default now needs its own rule. Each servlet below pins
 * one accessor; if its model is dropped or its slot is renamed on one side only, the
 * corresponding positive turns into a false negative.
 */
public class PassthroughServletAccessorSamples {

    /** getRequestURI(). */
    @WebServlet("/passthrough/servlet/request-uri")
    public static class RequestUriServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Runtime.getRuntime().exec("cat " + request.getRequestURI());
        }
    }

    /** getQueryString(). */
    @WebServlet("/passthrough/servlet/query-string")
    public static class QueryStringServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Runtime.getRuntime().exec("cat " + request.getQueryString());
        }
    }

    /** getServletPath(). */
    @WebServlet("/passthrough/servlet/servlet-path")
    public static class ServletPathServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Runtime.getRuntime().exec("cat " + request.getServletPath());
        }
    }

    /** getPathInfo(). */
    @WebServlet("/passthrough/servlet/path-info")
    public static class PathInfoServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Runtime.getRuntime().exec("cat " + request.getPathInfo());
        }
    }

    /** getRequestURL() returns a StringBuffer, so the buffer model has to carry it. */
    @WebServlet("/passthrough/servlet/request-url")
    public static class RequestUrlServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Runtime.getRuntime().exec("curl " + request.getRequestURL().toString());
        }
    }

    /** getRemoteUser(). */
    @WebServlet("/passthrough/servlet/remote-user")
    public static class RemoteUserServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Runtime.getRuntime().exec("id " + request.getRemoteUser());
        }
    }

    /** getParameterMap() - the map values keep the request taint. */
    @WebServlet("/passthrough/servlet/parameter-map")
    public static class ParameterMapServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Map<String, String[]> parameters = request.getParameterMap();
            String[] files = parameters.get("file");
            Runtime.getRuntime().exec("cat " + files[0]);
        }
    }

    /** getParameterValues() - the array elements keep the request taint. */
    @WebServlet("/passthrough/servlet/parameter-values")
    public static class ParameterValuesServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            String[] files = request.getParameterValues("file");
            Runtime.getRuntime().exec("cat " + files[0]);
        }
    }

    /** getCookies() - the cookie array elements keep the request taint. */
    @WebServlet("/passthrough/servlet/cookies")
    public static class CookiesServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Cookie[] cookies = request.getCookies();
            Runtime.getRuntime().exec("cat " + cookies[0].getValue());
        }
    }

    /** getHeaderNames() - the enumeration elements keep the request taint. */
    @WebServlet("/passthrough/servlet/header-names")
    public static class HeaderNamesServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Enumeration<String> names = request.getHeaderNames();
            Runtime.getRuntime().exec("cat " + names.nextElement());
        }
    }

    /** getReader() - the request body reader keeps the request taint. */
    @WebServlet("/passthrough/servlet/reader")
    public static class ReaderServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            BufferedReader reader = request.getReader();
            Runtime.getRuntime().exec("cat " + reader.readLine());
        }
    }

    /** getInputStream() + read(byte[]) - the body lands in the caller's array, not in the int result. */
    @WebServlet("/passthrough/servlet/input-stream")
    public static class InputStreamServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            byte[] body = new byte[1024];
            request.getInputStream().read(body);
            Runtime.getRuntime().exec("cat " + new String(body));
        }
    }

    /**
     * setAttribute/getAttribute on the request is a store-and-read pair, not a read of
     * request data: what comes back out of getAttribute is whatever was put in. The models
     * carry that through a named {@code attributes} slot, so the round trip below stays
     * tainted even when nothing else about the request is.
     */
    @WebServlet("/passthrough/servlet/request-attribute")
    public static class RequestAttributeRoundTripServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            request.setAttribute("file", request.getParameter("file"));
            String file = (String) request.getAttribute("file");
            Runtime.getRuntime().exec("cat " + file);
        }
    }

    /**
     * The same pair on the session. This one matters on its own: the session object is
     * deliberately not a source (the trust-boundary rule excludes getSession, since the
     * session is trusted-side state), so the only thing that can make a session read
     * tainted is an earlier store - which is exactly what the attributes slot models.
     */
    @WebServlet("/passthrough/servlet/session-attribute")
    public static class SessionAttributeRoundTripServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            javax.servlet.http.HttpSession session = request.getSession();
            session.setAttribute("file", request.getParameter("file"));
            String file = (String) session.getAttribute("file");
            Runtime.getRuntime().exec("cat " + file);
        }
    }

    /** PushBuilder keeps each fluent property in its own slot. */
    @WebServlet("/passthrough/servlet/push-builder")
    public static class PushBuilderPathServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            PushBuilder builder = request.newPushBuilder().path(request.getParameter("file"));
            Runtime.getRuntime().exec("cat " + builder.getPath());
        }
    }

    @WebServlet("/passthrough/servlet/push-builder-header")
    public static class PushBuilderHeaderServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            PushBuilder builder = request.newPushBuilder()
                    .addHeader("X-File", request.getParameter("file"));
            Runtime.getRuntime().exec("cat " + builder.getHeader("X-File"));
        }
    }

    @WebServlet("/passthrough/servlet/push-builder-header-name")
    public static class PushBuilderHeaderNameServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            PushBuilder builder = request.newPushBuilder()
                    .addHeader(request.getParameter("name"), "constant");
            Runtime.getRuntime().exec("cat " + builder.getHeaderNames().iterator().next());
        }
    }

    /**
     * Negative twin: the same accessors are called, and both attribute stores are
     * exercised with constants, but the executed command is built from constants only, so
     * none of the accessor models may produce a finding here.
     */
    @WebServlet("/passthrough/servlet/safe")
    public static class SafeAccessorsServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            request.getRequestURI();
            request.getQueryString();
            request.getServletPath();
            request.getPathInfo();
            request.getRequestURL();
            request.getRemoteUser();
            request.getParameterMap();
            request.getParameterValues("file");
            request.getCookies();
            request.getHeaderNames();
            request.setAttribute("file", "motd");
            request.getAttribute("file");
            request.getAttributeNames();
            PushBuilder builder = request.newPushBuilder().path("/assets/app.js");
            builder.queryString(request.getParameter("ignored"));
            builder.addHeader("X-Ignored", request.getParameter("ignored"));
            Runtime.getRuntime().exec("cat " + builder.getPath());
            Runtime.getRuntime().exec("cat /etc/motd");
        }
    }
}
