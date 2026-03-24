package security.xss;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import hudson.util.FormValidation;
import org.kohsuke.stapler.HttpResponses;
import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Jenkins FormValidation / Stapler HttpResponses samples for xss-in-servlet-app.
 */
public class XssJenkinsSamples {

    // --- Hudson FormValidation static markup methods ---

    @WebServlet("/xss-jenkins/formvalidation-error")
    public static class UnsafeErrorWithMarkup extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            FormValidation.errorWithMarkup(input);
        }
    }

    @WebServlet("/xss-jenkins/formvalidation-ok")
    public static class UnsafeOkWithMarkup extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            FormValidation.okWithMarkup(input);
        }
    }

    @WebServlet("/xss-jenkins/formvalidation-warning")
    public static class UnsafeWarningWithMarkup extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            FormValidation.warningWithMarkup(input);
        }
    }

    @WebServlet("/xss-jenkins/formvalidation-respond")
    public static class UnsafeRespond extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            FormValidation.respond(FormValidation.Kind.ERROR, input);
        }
    }

    // --- Stapler HttpResponses ---

    @WebServlet("/xss-jenkins/httpresponses-html")
    public static class UnsafeStaplerHtml extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            HttpResponses.html(input);
        }
    }

    @WebServlet("/xss-jenkins/httpresponses-literalhtml")
    public static class UnsafeStaplerLiteralHtml extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            HttpResponses.literalHtml(input);
        }
    }

    // --- Negative sample ---

    @WebServlet("/xss-jenkins/safe")
    public static class SafeJenkinsServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            String safe = org.apache.commons.text.StringEscapeUtils.escapeHtml4(input);
            FormValidation.errorWithMarkup(safe);
        }
    }
}
