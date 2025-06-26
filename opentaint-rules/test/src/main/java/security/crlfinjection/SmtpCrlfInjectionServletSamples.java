package security.crlfinjection;

import org.seqra.sast.test.util.PositiveRuleSample;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Properties;

/**
 * Servlet samples for java-servlet-smtp-crlf-injection.
 */
public class SmtpCrlfInjectionServletSamples {

    private static Session getMailSession() {
        // Simple, non-functional mail session for demonstration purposes.
        return Session.getInstance(new Properties());
    }

    @WebServlet("/smtp-crlf/servlet/unsafe")
    public static class UnsafeSmtpServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/crlf-injection.yaml", id = "java-servlet-smtp-crlf-injection")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String to = request.getParameter("to");
            String subject = request.getParameter("subject");
            String body = request.getParameter("message");
            String trackingId = request.getParameter("trackingId");

            Session session = getMailSession();
            MimeMessage message = new MimeMessage(session);

            try {
                message.setFrom(new InternetAddress("noreply@example.com"));
                message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));

                // VULNERABLE: raw user-controlled values placed into headers via setHeader
                message.setHeader("Subject", subject);
                message.setHeader("X-Tracking-Id", trackingId);

                message.setText(body == null ? "" : body);
                Transport.send(message);
            } catch (MessagingException e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/smtp-crlf/servlet/safe")
    public static class SafeSmtpServlet extends HttpServlet {

        private boolean containsCRLF(String value) {
            return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
        }

        @Override
//      TODO: restore this when conditional validators are implemented
//        @NegativeRuleSample(value = "java/security/crlf-injection.yaml", id = "java-servlet-smtp-crlf-injection")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String to = request.getParameter("to");
            String subject = request.getParameter("subject");
            String body = request.getParameter("message");
            String trackingId = request.getParameter("trackingId");

            if (to == null || subject == null || trackingId == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameters");
                return;
            }

            if (containsCRLF(to) || containsCRLF(subject) || containsCRLF(trackingId)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid characters");
                return;
            }

            try {
                InternetAddress toAddress = new InternetAddress(to, true);

                String safeSubject = subject.replaceAll("[\r\n]", "").trim();
                String safeTrackingId = trackingId.replaceAll("[^A-Za-z0-9\\-]", "");

                Session session = getMailSession();
                MimeMessage message = new MimeMessage(session);

                message.setFrom(new InternetAddress("noreply@example.com"));
                message.setRecipient(Message.RecipientType.TO, toAddress);

                // Use high-level API for subject; custom header only after sanitization
                message.setSubject(safeSubject, "UTF-8");
                if (!safeTrackingId.isEmpty()) {
                    message.setHeader("X-Tracking-Id", safeTrackingId);
                }

                message.setText(body == null ? "" : body, "UTF-8");
                Transport.send(message);
            } catch (MessagingException e) {
                throw new ServletException(e);
            }
        }
    }
}
