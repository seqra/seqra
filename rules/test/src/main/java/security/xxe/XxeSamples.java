package security.xxe;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;

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
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.helpers.DefaultHandler;

import org.dom4j.io.SAXReader;

import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.digester3.Digester;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringWriter;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.beans.XMLDecoder;

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

    @WebServlet("/xxe/upload")
    public static class UnsafeXmlUploadServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
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
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
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

    // SAXParserFactory - Servlet samples

    @WebServlet("/xxe/sax-upload")
    public static class UnsafeSaxParserServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                // DEFAULT configuration - vulnerable to XXE
                SAXParserFactory factory = SAXParserFactory.newInstance();
                SAXParser parser = factory.newSAXParser();

                // Attacker-controlled XML from the request body
                parser.parse(request.getInputStream(), new DefaultHandler());

                response.getWriter().write("Parsed successfully");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/xxe/sax-upload-safe")
    public static class SafeSaxParserServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                SAXParserFactory factory = SAXParserFactory.newInstance();

                // Completely disallow DOCTYPE declarations (strong protection against XXE)
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

                // Disable external entities
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

                SAXParser parser = factory.newSAXParser();
                parser.parse(request.getInputStream(), new DefaultHandler());

                response.getWriter().write("Parsed successfully");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    // xxe

    @RestController
    @RequestMapping("/api/xxe")
    public static class XxeSpringController {

        @PostMapping(value = "/process-xml", consumes = MediaType.APPLICATION_XML_VALUE)
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> processXmlInsecure(@RequestBody String xml) throws Exception {
            // Insecure: default configuration may allow DTDs and external entities
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = dbf.newDocumentBuilder();

            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            String value = doc.getElementsByTagName("name").item(0).getTextContent();
            return ResponseEntity.ok("Received: " + value);
        }

        @PostMapping(value = "/process-xml-safe", consumes = MediaType.APPLICATION_XML_VALUE)
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
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

    // SAXParserFactory - Spring samples

    @RestController
    @RequestMapping("/api/xxe/sax")
    public static class SaxParserSpringController {

        @PostMapping(value = "/process-xml", consumes = MediaType.APPLICATION_XML_VALUE)
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> processXmlInsecure(@RequestBody String xml) throws Exception {
            // Insecure: default configuration allows DTDs and external entities
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();

            parser.parse(new InputSource(new StringReader(xml)), new DefaultHandler());
            return ResponseEntity.ok("Parsed successfully");
        }

        @PostMapping(value = "/process-xml-safe", consumes = MediaType.APPLICATION_XML_VALUE)
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> processXmlSafe(@RequestBody String xml) throws Exception {
            SAXParserFactory factory = SAXParserFactory.newInstance();

            // Completely disallow DOCTYPE declarations (strong protection against XXE)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            // Disable external entities
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            SAXParser parser = factory.newSAXParser();
            parser.parse(new InputSource(new StringReader(xml)), new DefaultHandler());
            return ResponseEntity.ok("Parsed successfully");
        }
    }

    // SAXReader (dom4j) - Servlet samples

    @WebServlet("/xxe/saxreader-upload")
    public static class UnsafeSaxReaderServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                // DEFAULT configuration - vulnerable to XXE
                SAXReader reader = new SAXReader();

                // Attacker-controlled XML from the request body
                org.dom4j.Document doc = reader.read(request.getInputStream());

                // Process the XML document...
                String root = doc.getRootElement().getName();
                response.getWriter().write("Root: " + root);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/xxe/saxreader-upload-safe")
    public static class SafeSaxReaderServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                SAXReader reader = new SAXReader();

                // Completely disallow DOCTYPE declarations (strong protection against XXE)
                reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

                // Disable external entities
                reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
                reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

                org.dom4j.Document doc = reader.read(request.getInputStream());

                String root = doc.getRootElement().getName();
                response.getWriter().write("Root: " + root);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    // SAXReader (dom4j) - Spring samples

    @RestController
    @RequestMapping("/api/xxe/saxreader")
    public static class SaxReaderSpringController {

        @PostMapping(value = "/process-xml", consumes = MediaType.APPLICATION_XML_VALUE)
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> processXmlInsecure(@RequestBody String xml) throws Exception {
            // Insecure: default configuration allows DTDs and external entities
            SAXReader reader = new SAXReader();

            org.dom4j.Document doc = reader.read(new StringReader(xml));
            String root = doc.getRootElement().getName();
            return ResponseEntity.ok("Root element: " + root);
        }

        @PostMapping(value = "/process-xml-safe", consumes = MediaType.APPLICATION_XML_VALUE)
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> processXmlSafe(@RequestBody String xml) throws Exception {
            SAXReader reader = new SAXReader();

            // Completely disallow DOCTYPE declarations (strong protection against XXE)
            reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            // Disable external entities
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            org.dom4j.Document doc = reader.read(new StringReader(xml));
            String root = doc.getRootElement().getName();
            return ResponseEntity.ok("Root element: " + root);
        }
    }

    // XMLReaderFactory - Servlet samples

    @WebServlet("/xxe/xmlreader-upload")
    public static class UnsafeXmlReaderServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                // DEFAULT configuration - vulnerable to XXE
                XMLReader reader = XMLReaderFactory.createXMLReader();

                // Attacker-controlled XML from the request body
                reader.parse(new InputSource(request.getInputStream()));

                response.getWriter().write("Parsed successfully");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/xxe/xmlreader-upload-safe")
    public static class SafeXmlReaderServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                XMLReader reader = XMLReaderFactory.createXMLReader();

                // Completely disallow DOCTYPE declarations (strong protection against XXE)
                reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

                // Disable external entities
                reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
                reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

                reader.parse(new InputSource(request.getInputStream()));

                response.getWriter().write("Parsed successfully");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    // XMLReaderFactory - Spring samples

    @RestController
    @RequestMapping("/api/xxe/xmlreader")
    public static class XmlReaderSpringController {

        @PostMapping(value = "/process-xml", consumes = MediaType.APPLICATION_XML_VALUE)
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> processXmlInsecure(@RequestBody String xml) throws Exception {
            // Insecure: default configuration allows DTDs and external entities
            XMLReader reader = XMLReaderFactory.createXMLReader();

            reader.parse(new InputSource(new StringReader(xml)));
            return ResponseEntity.ok("Parsed successfully");
        }

        @PostMapping(value = "/process-xml-safe", consumes = MediaType.APPLICATION_XML_VALUE)
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> processXmlSafe(@RequestBody String xml) throws Exception {
            XMLReader reader = XMLReaderFactory.createXMLReader();

            // Completely disallow DOCTYPE declarations (strong protection against XXE)
            reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            // Disable external entities
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            reader.parse(new InputSource(new StringReader(xml)));
            return ResponseEntity.ok("Parsed successfully");
        }
    }

    // XMLInputFactory (StAX) - Servlet samples

    @WebServlet("/xxe/stax-upload")
    public static class UnsafeStaxServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                // DEFAULT configuration - vulnerable to XXE
                XMLInputFactory factory = XMLInputFactory.newInstance();

                // Attacker-controlled XML from the request body
                XMLStreamReader reader = factory.createXMLStreamReader(request.getInputStream());

                // Process the XML...
                while (reader.hasNext()) {
                    reader.next();
                }
                reader.close();

                response.getWriter().write("Parsed successfully");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/xxe/stax-upload-safe")
    public static class SafeStaxServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                XMLInputFactory factory = XMLInputFactory.newInstance();

                // Disable external entities and DTD support to prevent XXE
                factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
                factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

                XMLStreamReader reader = factory.createXMLStreamReader(request.getInputStream());

                // Process the XML...
                while (reader.hasNext()) {
                    reader.next();
                }
                reader.close();

                response.getWriter().write("Parsed successfully");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    // XMLInputFactory (StAX) - Spring samples

    @RestController
    @RequestMapping("/api/xxe/stax")
    public static class StaxSpringController {

        @PostMapping(value = "/process-xml", consumes = MediaType.APPLICATION_XML_VALUE)
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> processXmlInsecure(@RequestBody String xml) throws Exception {
            // Insecure: default configuration allows DTDs and external entities
            XMLInputFactory factory = XMLInputFactory.newInstance();

            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));

            // Process the XML...
            while (reader.hasNext()) {
                reader.next();
            }
            reader.close();

            return ResponseEntity.ok("Parsed successfully");
        }

        @PostMapping(value = "/process-xml-safe", consumes = MediaType.APPLICATION_XML_VALUE)
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> processXmlSafe(@RequestBody String xml) throws Exception {
            XMLInputFactory factory = XMLInputFactory.newInstance();

            // Disable external entities and DTD support to prevent XXE
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));

            // Process the XML...
            while (reader.hasNext()) {
                reader.next();
            }
            reader.close();

            return ResponseEntity.ok("Parsed successfully");
        }
    }

    // Apache Commons Digester3 - Servlet samples
    // NOTE: Digester has NO built-in XXE protection. Safe samples must avoid untrusted input.

    @WebServlet("/xxe/digester-upload")
    public static class UnsafeDigesterServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                // Digester has NO XXE protection - always vulnerable with untrusted input
                Digester digester = new Digester();

                // Attacker-controlled XML from the request body
                digester.parse(request.getInputStream());

                response.getWriter().write("Parsed successfully");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/xxe/digester-upload-safe")
    public static class SafeDigesterServlet extends HttpServlet {

        // Trusted, static XML content - not from user input
        private static final String TRUSTED_XML = "<config><setting name=\"default\"/></config>";

        @Override
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                // Safe: Digester used with trusted static content, NOT untrusted input
                // Digester has no XXE protection, so the ONLY safe approach is to
                // not use it with user-controlled data
                Digester digester = new Digester();

                // Parse trusted, static XML - not from request
                digester.parse(new StringReader(TRUSTED_XML));

                response.getWriter().write("Parsed static config successfully");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    // Apache Commons Digester3 - Spring samples

    @RestController
    @RequestMapping("/api/xxe/digester")
    public static class DigesterSpringController {

        @PostMapping(value = "/process-xml", consumes = MediaType.APPLICATION_XML_VALUE)
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> processXmlInsecure(@RequestBody String xml) throws Exception {
            // Digester has NO XXE protection - always vulnerable with untrusted input
            Digester digester = new Digester();

            digester.parse(new StringReader(xml));
            return ResponseEntity.ok("Parsed successfully");
        }

        // Trusted, static XML content - not from user input
        private static final String TRUSTED_XML = "<config><setting name=\"default\"/></config>";

        @PostMapping(value = "/process-xml-safe", consumes = MediaType.APPLICATION_XML_VALUE)
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> processXmlSafe(@RequestBody String xml) throws Exception {
            // Safe: Digester used with trusted static content, NOT untrusted input
            // Digester has no XXE protection, so the ONLY safe approach is to
            // not use it with user-controlled data
            Digester digester = new Digester();

            // Parse trusted, static XML - not from request body
            digester.parse(new StringReader(TRUSTED_XML));

            return ResponseEntity.ok("Parsed static config successfully");
        }
    }

    // Transformer/TransformerFactory - Servlet samples
    // NOTE: Transformer APIs have NO built-in XXE protection. Safe samples must avoid untrusted input.

    @WebServlet("/xxe/transformer-factory-upload")
    public static class UnsafeTransformerFactoryServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                // Transformer APIs have NO XXE protection - always vulnerable with untrusted input
                TransformerFactory factory = TransformerFactory.newInstance();

                // Attacker-controlled XSLT from the request body - vulnerable to XXE
                StreamSource xsltSource = new StreamSource(request.getInputStream());
                Transformer transformer = factory.newTransformer(xsltSource);

                response.getWriter().write("Transformer created");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/xxe/transformer-transform-upload")
    public static class UnsafeTransformerServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                // Transformer APIs have NO XXE protection - always vulnerable with untrusted input
                TransformerFactory factory = TransformerFactory.newInstance();
                Transformer transformer = factory.newTransformer();

                // Attacker-controlled XML source - vulnerable to XXE
                StreamSource xmlSource = new StreamSource(request.getInputStream());
                StringWriter writer = new StringWriter();
                StreamResult result = new StreamResult(writer);

                transformer.transform(xmlSource, result);

                response.getWriter().write("Transformed: " + writer.toString());
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/xxe/transformer-upload-safe")
    public static class SafeTransformerServlet extends HttpServlet {

        // Trusted, static XML content - not from user input
        private static final String TRUSTED_XML = "<data><value>static content</value></data>";

        @Override
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                // Safe: Transformer used with trusted static content, NOT untrusted input
                // Transformer APIs have no XXE protection, so the ONLY safe approach is to
                // not use them with user-controlled data
                TransformerFactory factory = TransformerFactory.newInstance();
                Transformer transformer = factory.newTransformer();

                // Transform trusted, static XML - not from request
                StreamSource xmlSource = new StreamSource(new StringReader(TRUSTED_XML));
                StringWriter writer = new StringWriter();
                StreamResult result = new StreamResult(writer);

                transformer.transform(xmlSource, result);

                response.getWriter().write("Transformed static content");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/xxe/transformer-factory-secure-processing")
    public static class SafeTransformerFactoryServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                TransformerFactory factory = TransformerFactory.newInstance();
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

                StreamSource xsltSource = new StreamSource(request.getInputStream());
                Transformer transformer = factory.newTransformer(xsltSource);

                response.getWriter().write("Transformer created safely");
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/xxe/transformer-transform-secure-processing")
    public static class SafeTransformerTransformServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                TransformerFactory factory = TransformerFactory.newInstance();
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                Transformer transformer = factory.newTransformer();

                StreamSource xmlSource = new StreamSource(request.getInputStream());
                StringWriter writer = new StringWriter();
                StreamResult result = new StreamResult(writer);

                transformer.transform(xmlSource, result);

                response.getWriter().write("Transformed safely: " + writer);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    // Transformer/TransformerFactory - Spring samples

    @RestController
    @RequestMapping("/api/xxe/transformer")
    public static class TransformerSpringController {

        @PostMapping(value = "/new-transformer", consumes = MediaType.APPLICATION_XML_VALUE)
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> newTransformerInsecure(@RequestBody String xslt) throws Exception {
            // Transformer APIs have NO XXE protection - always vulnerable with untrusted input
            TransformerFactory factory = TransformerFactory.newInstance();

            // Attacker-controlled XSLT - vulnerable to XXE
            StreamSource xsltSource = new StreamSource(new StringReader(xslt));
            Transformer transformer = factory.newTransformer(xsltSource);

            return ResponseEntity.ok("Transformer created");
        }

        @PostMapping(value = "/transform", consumes = MediaType.APPLICATION_XML_VALUE)
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> transformInsecure(@RequestBody String xml) throws Exception {
            // Transformer APIs have NO XXE protection - always vulnerable with untrusted input
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();

            // Attacker-controlled XML source - vulnerable to XXE
            StreamSource xmlSource = new StreamSource(new StringReader(xml));
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);

            transformer.transform(xmlSource, result);

            return ResponseEntity.ok("Transformed: " + writer.toString());
        }

        // Trusted, static XML content - not from user input
        private static final String TRUSTED_XML = "<data><value>static content</value></data>";

        @PostMapping(value = "/transform-safe", consumes = MediaType.APPLICATION_XML_VALUE)
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> transformSafe(@RequestBody String xml) throws Exception {
            // Safe: Transformer used with trusted static content, NOT untrusted input
            // Transformer APIs have no XXE protection, so the ONLY safe approach is to
            // not use them with user-controlled data
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();

            // Transform trusted, static XML - not from request body
            StreamSource xmlSource = new StreamSource(new StringReader(TRUSTED_XML));
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);

            transformer.transform(xmlSource, result);

            return ResponseEntity.ok("Transformed static content");
        }

        @PostMapping(value = "/new-transformer-safe", consumes = MediaType.APPLICATION_XML_VALUE)
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> newTransformerSafe(@RequestBody String xslt) throws Exception {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            StreamSource xsltSource = new StreamSource(new StringReader(xslt));
            Transformer transformer = factory.newTransformer(xsltSource);

            return ResponseEntity.ok("Transformer created safely");
        }

        @PostMapping(value = "/transform-secure-processing", consumes = MediaType.APPLICATION_XML_VALUE)
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> transformSafeWithSecureProcessing(@RequestBody String xml) throws Exception {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();

            StreamSource xmlSource = new StreamSource(new StringReader(xml));
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);

            transformer.transform(xmlSource, result);

            return ResponseEntity.ok("Transformed safely: " + writer);
        }
    }

    // XMLDecoder - Servlet samples
    // NOTE: XMLDecoder has NO safety features - it's inherently dangerous with untrusted input.
    // It can deserialize arbitrary objects, similar to Java deserialization vulnerabilities.

    @WebServlet("/xxe/xmldecoder-upload")
    public static class UnsafeXmlDecoderServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                // XMLDecoder has NO safety features - always dangerous with untrusted input
                // Can deserialize arbitrary objects leading to RCE
                XMLDecoder decoder = new XMLDecoder(request.getInputStream());

                Object obj = decoder.readObject();
                decoder.close();

                response.getWriter().write("Decoded object: " + obj.getClass().getName());
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/xxe/xmldecoder-upload-safe")
    public static class SafeXmlDecoderServlet extends HttpServlet {

        // Trusted, static XML content - not from user input
        private static final String TRUSTED_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><java><string>safe value</string></java>";

        @Override
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            try {
                // Safe: XMLDecoder used with trusted static content, NOT untrusted input
                // XMLDecoder has no safety features, so the ONLY safe approach is to
                // not use it with user-controlled data
                ByteArrayInputStream trustedInput = new ByteArrayInputStream(
                        TRUSTED_XML.getBytes(StandardCharsets.UTF_8));
                XMLDecoder decoder = new XMLDecoder(trustedInput);

                Object obj = decoder.readObject();
                decoder.close();

                response.getWriter().write("Decoded trusted object: " + obj);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    // XMLDecoder - Spring samples

    @RestController
    @RequestMapping("/api/xxe/xmldecoder")
    public static class XmlDecoderSpringController {

        @PostMapping(value = "/decode", consumes = MediaType.APPLICATION_XML_VALUE)
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> decodeInsecure(@RequestBody String xml) throws Exception {
            // XMLDecoder has NO safety features - always dangerous with untrusted input
            // Can deserialize arbitrary objects leading to RCE
            ByteArrayInputStream inputStream = new ByteArrayInputStream(
                    xml.getBytes(StandardCharsets.UTF_8));
            XMLDecoder decoder = new XMLDecoder(inputStream);

            Object obj = decoder.readObject();
            decoder.close();

            return ResponseEntity.ok("Decoded object: " + obj.getClass().getName());
        }

        // Trusted, static XML content - not from user input
        private static final String TRUSTED_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><java><string>safe value</string></java>";

        @PostMapping(value = "/decode-safe", consumes = MediaType.APPLICATION_XML_VALUE)
        @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe")
        public ResponseEntity<String> decodeSafe(@RequestBody String xml) throws Exception {
            // Safe: XMLDecoder used with trusted static content, NOT untrusted input
            // XMLDecoder has no safety features, so the ONLY safe approach is to
            // not use it with user-controlled data
            ByteArrayInputStream trustedInput = new ByteArrayInputStream(
                    TRUSTED_XML.getBytes(StandardCharsets.UTF_8));
            XMLDecoder decoder = new XMLDecoder(trustedInput);

            Object obj = decoder.readObject();
            decoder.close();

            return ResponseEntity.ok("Decoded trusted object: " + obj);
        }
    }
}
