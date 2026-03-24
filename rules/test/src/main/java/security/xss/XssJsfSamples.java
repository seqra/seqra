package security.xss;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * JSF ResponseWriter/ResponseStream samples for xss-in-servlet-app.
 */
public class XssJsfSamples {

    // --- javax.faces ---

    @WebServlet("/xss-jsf/javax-writer-unsafe")
    public static class UnsafeJavaxResponseWriterServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            javax.faces.context.ResponseWriter writer =
                    javax.faces.context.FacesContext.getCurrentInstance().getResponseWriter();
            writer.write(input);
        }
    }

    @WebServlet("/xss-jsf/javax-stream-unsafe")
    public static class UnsafeJavaxResponseStreamServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            javax.faces.context.ResponseStream stream =
                    javax.faces.context.FacesContext.getCurrentInstance().getResponseStream();
            stream.write(input.getBytes());
        }
    }

    // --- jakarta.faces ---

    @WebServlet("/xss-jsf/jakarta-writer-unsafe")
    public static class UnsafeJakartaResponseWriterServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            jakarta.faces.context.ResponseWriter writer =
                    jakarta.faces.context.FacesContext.getCurrentInstance().getResponseWriter();
            writer.write(input);
        }
    }

    @WebServlet("/xss-jsf/jakarta-stream-unsafe")
    public static class UnsafeJakartaResponseStreamServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            jakarta.faces.context.ResponseStream stream =
                    jakarta.faces.context.FacesContext.getCurrentInstance().getResponseStream();
            stream.write(input.getBytes());
        }
    }

    // --- Negative sample ---

    @WebServlet("/xss-jsf/safe")
    public static class SafeJsfServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            String safe = org.apache.commons.text.StringEscapeUtils.escapeHtml4(input);
            javax.faces.context.ResponseWriter writer =
                    javax.faces.context.FacesContext.getCurrentInstance().getResponseWriter();
            writer.write(safe);
        }
    }
}
