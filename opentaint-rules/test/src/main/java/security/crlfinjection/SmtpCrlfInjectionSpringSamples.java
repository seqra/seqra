package security.crlfinjection;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;


/**
 * Spring MVC samples for spring-smtp-crlf-injection.
 */
public class SmtpCrlfInjectionSpringSamples {

    @Controller
    public static class UnsafeSpringSmtpController {

        @PostMapping("/smtp-crlf/spring/unsafe")
        @PositiveRuleSample(value = "java/security/crlf-injection.yaml", id = "spring-smtp-crlf-injection")
        public void unsafe(@RequestParam("to") String to,
                           @RequestParam("subject") String subject,
                           @RequestParam(value = "trackingId", required = false) String trackingId,
                           @RequestParam(value = "message", required = false) String body)
                throws MessagingException {

            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);

            mimeMessage.setFrom(new InternetAddress("noreply@example.com"));
            mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(to));

            // VULNERABLE: raw subject and custom header derived from user input
            mimeMessage.setHeader("Subject", subject);
            if (trackingId != null) {
                mimeMessage.setHeader("X-Tracking-Id", trackingId);
            }

            mimeMessage.setText(body == null ? "" : body);
            Transport.send(mimeMessage);
        }
    }


    @Controller
    public static class SafeSpringSmtpController {

        private boolean containsCRLF(String value) {
            return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
        }

        @PostMapping("/smtp-crlf/spring/safe")
//      TODO: restore this when conditional validators are implemented
//        @NegativeRuleSample(value = "java/security/crlf-injection.yaml", id = "spring-smtp-crlf-injection")
        public void safe(@RequestParam("to") String to,
                         @RequestParam("subject") String subject,
                         @RequestParam(value = "trackingId", required = false) String trackingId,
                         @RequestParam(value = "message", required = false) String body)
                throws MessagingException {

            if (to == null || subject == null) {
                return;
            }

            if (containsCRLF(to) || containsCRLF(subject) || containsCRLF(trackingId)) {
                return;
            }

            String safeSubject = subject.replaceAll("[\r\n]", "").trim();
            String safeTrackingId =
                    trackingId == null ? null : trackingId.replaceAll("[^A-Za-z0-9\\-]", "");

            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);

            mimeMessage.setFrom(new InternetAddress("noreply@example.com"));
            mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            mimeMessage.setSubject(safeSubject, "UTF-8");

            if (safeTrackingId != null && !safeTrackingId.isEmpty()) {
                mimeMessage.setHeader("X-Tracking-Id", safeTrackingId);
            }

            mimeMessage.setText(body == null ? "" : body, "UTF-8");
            Transport.send(mimeMessage);
        }
    }

}
