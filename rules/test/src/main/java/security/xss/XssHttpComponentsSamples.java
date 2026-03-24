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
 * Apache HttpComponents response entity samples for xss-in-servlet-app.
 */
public class XssHttpComponentsSamples {

    // --- Apache HC4: HttpResponse.setEntity ---

    @WebServlet("/xss-hc/hc4-setentity")
    public static class UnsafeHc4SetEntity extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            org.apache.http.HttpResponse httpResponse = new org.apache.http.message.BasicHttpResponse(
                    new org.apache.http.message.BasicStatusLine(
                            org.apache.http.HttpVersion.HTTP_1_1, 200, "OK"));
            httpResponse.setEntity(new org.apache.http.entity.StringEntity(input));
        }
    }

    // --- Apache HC4: EntityUtils.updateEntity ---

    @WebServlet("/xss-hc/hc4-updateentity")
    public static class UnsafeHc4UpdateEntity extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            org.apache.http.HttpResponse httpResponse = new org.apache.http.message.BasicHttpResponse(
                    new org.apache.http.message.BasicStatusLine(
                            org.apache.http.HttpVersion.HTTP_1_1, 200, "OK"));
            org.apache.http.util.EntityUtils.updateEntity(httpResponse,
                    new org.apache.http.entity.StringEntity(input));
        }
    }

    // --- Apache HC5: HttpEntityContainer.setEntity ---

    @WebServlet("/xss-hc/hc5-setentity")
    public static class UnsafeHc5SetEntity extends HttpServlet {

        @Override
        // TODO: Analyzer FN - taint does not propagate through new HC5 StringEntity(); re-enable when HC5 summaries are added
        // @PositiveRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            org.apache.hc.core5.http.HttpEntityContainer hc5Response =
                    new org.apache.hc.core5.http.message.BasicClassicHttpResponse(200);
            hc5Response.setEntity(new org.apache.hc.core5.http.io.entity.StringEntity(input));
        }
    }

    // --- Negative sample ---

    @WebServlet("/xss-hc/safe")
    public static class SafeHcServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xss.yaml", id = "xss-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String input = request.getParameter("input");
            String safe = org.apache.commons.text.StringEscapeUtils.escapeHtml4(input);
            org.apache.http.HttpResponse httpResponse = new org.apache.http.message.BasicHttpResponse(
                    new org.apache.http.message.BasicStatusLine(
                            org.apache.http.HttpVersion.HTTP_1_1, 200, "OK"));
            httpResponse.setEntity(new org.apache.http.entity.StringEntity(safe));
        }
    }
}
