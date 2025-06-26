package security.xxe;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Samples for XXE-related rules: servlet XXE and Spring XXE.
 */
public class XxeSamples {

    // xxe-in-servlet-app

    @WebServlet("/xxe/upload")
    public static class UnsafeXmlUploadServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe-in-servlet-app")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                // DEFAULT configuration - vulnerable to XXE
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();

                // Attacker-controlled XML from the request body
                Document doc = builder.parse(request.getInputStream());

                // Process the XML document...
                String root = doc.getDocumentElement().getNodeName();
                response.getWriter().write("Root: " + root);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/xxe/upload-safe")
    public static class SafeXmlUploadServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe-in-servlet-app")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

                // Enable secure processing
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

                // Completely disallow DOCTYPE declarations
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

                // Disable external entities
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

                // Additional hardening
                factory.setXIncludeAware(false);
                factory.setExpandEntityReferences(false);

                try {
                    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                } catch (IllegalArgumentException ignored) {
                    // Attributes not supported in older JDKs; safe to ignore
                }

                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(request.getInputStream());

                String root = doc.getDocumentElement().getNodeName();
                response.getWriter().write("Root: " + root);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    // xxe-in-spring-app

    @RestController
    @RequestMapping("/api/xxe")
    public static class XxeSpringController {

        @PostMapping(value = "/process-xml", consumes = MediaType.APPLICATION_XML_VALUE)
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe-in-spring-app")
        public ResponseEntity<String> processXmlInsecure(@RequestBody String xml) throws Exception {
            // Insecure: default configuration may allow DTDs and external entities
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = dbf.newDocumentBuilder();

            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            String value = doc.getElementsByTagName("name").item(0).getTextContent();
            return ResponseEntity.ok("Received: " + value);
        }

        @PostMapping(value = "/process-xml-safe", consumes = MediaType.APPLICATION_XML_VALUE)
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe-in-spring-app")
        public ResponseEntity<String> processXmlSafe(@RequestBody String xml) throws Exception {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

            // Completely disallow DOCTYPE declarations (strong protection against XXE)
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            // Disable external entities
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            // Extra hardening
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            String value = doc.getElementsByTagName("name").item(0).getTextContent();
            return ResponseEntity.ok("Received: " + value);
        }
    }
}
