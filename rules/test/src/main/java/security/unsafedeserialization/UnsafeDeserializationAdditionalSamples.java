package security.unsafedeserialization;

import java.beans.XMLDecoder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test samples for java-unsafe-deserialization-sinks from unsafe-deserialization-sinks.yaml.
 * Covers: Hessian, Burlap, json-io, YamlBeans, XMLDecoder, Commons Lang, Castor, JYaml, Jabsorb.
 */
public class UnsafeDeserializationAdditionalSamples {

    // =====================================================================
    // Caucho Hessian - HessianInput constructor sink
    // =====================================================================

    @WebServlet("/deserialize/hessian")
    public static class UnsafeHessianServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            com.caucho.hessian.io.HessianInput hi = new com.caucho.hessian.io.HessianInput(req.getInputStream());
            Object obj = hi.readObject();
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    @WebServlet("/deserialize/hessian/safe")
    public static class SafeHessianServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            com.caucho.hessian.io.HessianInput hi = new com.caucho.hessian.io.HessianInput(new java.io.FileInputStream("/tmp/safe-data.hessian"));
            Object obj = hi.readObject();
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    // =====================================================================
    // Caucho Hessian2Input constructor sink
    // =====================================================================

    @WebServlet("/deserialize/hessian2")
    public static class UnsafeHessian2Servlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            com.caucho.hessian.io.Hessian2Input h2 = new com.caucho.hessian.io.Hessian2Input(req.getInputStream());
            Object obj = h2.readObject();
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    // =====================================================================
    // Caucho Burlap - BurlapInput constructor sink
    // =====================================================================

    @WebServlet("/deserialize/burlap")
    public static class UnsafeBurlapServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            com.caucho.burlap.io.BurlapInput bi = new com.caucho.burlap.io.BurlapInput(req.getInputStream());
            Object obj = bi.readObject();
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    // =====================================================================
    // Alibaba/Dubbo Hessian - HessianInput constructor sink
    // =====================================================================

    @WebServlet("/deserialize/alibaba-hessian")
    public static class UnsafeAlibabaHessianServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            com.alibaba.com.caucho.hessian.io.HessianInput hi = new com.alibaba.com.caucho.hessian.io.HessianInput(req.getInputStream());
            Object obj = hi.readObject();
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    // =====================================================================
    // Alibaba/Dubbo Hessian2Input constructor sink
    // =====================================================================

    @WebServlet("/deserialize/alibaba-hessian2")
    public static class UnsafeAlibabaHessian2Servlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            com.alibaba.com.caucho.hessian.io.Hessian2Input h2 = new com.alibaba.com.caucho.hessian.io.Hessian2Input(req.getInputStream());
            Object obj = h2.readObject();
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    // =====================================================================
    // json-io (CedarSoftware) - JsonReader constructor + static jsonToJava
    // =====================================================================

    @RestController
    @RequestMapping("/api/deserialize/json-io")
    public static class JsonIoSpringController {

        @PostMapping("/static")
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-spring-app")
        public ResponseEntity<String> unsafeJsonToJava(@RequestBody String json) {
            Object obj = com.cedarsoftware.util.io.JsonReader.jsonToJava(json);
            return ResponseEntity.ok("Deserialized: " + obj);
        }

        @PostMapping("/safe")
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-spring-app")
        public ResponseEntity<String> safeJsonToJava(@RequestBody String json) {
            return ResponseEntity.ok("Length: " + json.length());
        }
    }

    @WebServlet("/deserialize/json-io/reader")
    public static class UnsafeJsonReaderServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            com.cedarsoftware.util.io.JsonReader reader = new com.cedarsoftware.util.io.JsonReader(req.getInputStream());
            Object obj = reader.readObject();
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    // =====================================================================
    // YamlBeans - YamlReader constructor sink
    // =====================================================================

    @RestController
    @RequestMapping("/api/deserialize/yamlbeans")
    public static class YamlBeansSpringController {

        @PostMapping("/unsafe")
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-spring-app")
        public ResponseEntity<String> unsafeYamlBeans(@RequestBody String yaml) throws Exception {
            com.esotericsoftware.yamlbeans.YamlReader reader = new com.esotericsoftware.yamlbeans.YamlReader(yaml);
            Object obj = reader.read();
            return ResponseEntity.ok("Deserialized: " + obj);
        }

        @PostMapping("/safe")
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-spring-app")
        public ResponseEntity<String> safeYamlBeans(@RequestBody String yaml) {
            return ResponseEntity.ok("Length: " + yaml.length());
        }
    }

    // =====================================================================
    // Java Beans XMLDecoder - constructor sink
    // =====================================================================

    @WebServlet("/deserialize/xml-decoder")
    public static class UnsafeXmlDecoderServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            XMLDecoder decoder = new XMLDecoder(req.getInputStream());
            Object obj = decoder.readObject();
            decoder.close();
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    @WebServlet("/deserialize/xml-decoder/safe")
    public static class SafeXmlDecoderServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            XMLDecoder decoder = new XMLDecoder(new java.io.FileInputStream("/tmp/safe-data.xml"));
            Object obj = decoder.readObject();
            decoder.close();
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    // =====================================================================
    // Apache Commons Lang SerializationUtils.deserialize
    // =====================================================================

    @WebServlet("/deserialize/commons-lang")
    public static class UnsafeCommonsLangServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            Object obj = org.apache.commons.lang.SerializationUtils.deserialize(req.getInputStream());
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    // =====================================================================
    // Apache Commons Lang3 SerializationUtils.deserialize
    // =====================================================================

    @WebServlet("/deserialize/commons-lang3")
    public static class UnsafeCommonsLang3Servlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            Object obj = org.apache.commons.lang3.SerializationUtils.deserialize(req.getInputStream());
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    @WebServlet("/deserialize/commons-lang3/safe")
    public static class SafeCommonsLang3Servlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            Object obj = org.apache.commons.lang3.SerializationUtils.deserialize(new java.io.FileInputStream("/tmp/safe-data.bin"));
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    // =====================================================================
    // Castor XML Unmarshaller
    // =====================================================================

    @WebServlet("/deserialize/castor")
    public static class UnsafeCastorServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            try {
                org.exolab.castor.xml.Unmarshaller unmarshaller = new org.exolab.castor.xml.Unmarshaller();
                Object obj = unmarshaller.unmarshal(new org.xml.sax.InputSource(req.getInputStream()));
                resp.getWriter().println("Deserialized: " + obj);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }

    // =====================================================================
    // JYaml (org.ho.yaml) - static Yaml methods
    // =====================================================================

    @RestController
    @RequestMapping("/api/deserialize/jyaml")
    public static class JYamlSpringController {

        @PostMapping("/load")
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-spring-app")
        public ResponseEntity<String> unsafeYamlLoad(@RequestBody String yamlText) {
            Object obj = org.ho.yaml.Yaml.load(yamlText);
            return ResponseEntity.ok("Deserialized: " + obj);
        }

        @PostMapping("/loadType")
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-spring-app")
        public ResponseEntity<String> unsafeYamlLoadType(@RequestBody String yamlText) {
            Object obj = org.ho.yaml.Yaml.loadType(yamlText, Object.class);
            return ResponseEntity.ok("Deserialized: " + obj);
        }

        @PostMapping("/loadStream")
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-spring-app")
        public ResponseEntity<String> unsafeYamlLoadStream(@RequestBody String yamlText) {
            Object obj = org.ho.yaml.Yaml.loadStream(new StringReader(yamlText));
            return ResponseEntity.ok("Deserialized: " + obj);
        }

        @PostMapping("/loadStreamOfType")
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-spring-app")
        public ResponseEntity<String> unsafeYamlLoadStreamOfType(@RequestBody String yamlText) {
            Object obj = org.ho.yaml.Yaml.loadStreamOfType(new StringReader(yamlText), Object.class);
            return ResponseEntity.ok("Deserialized: " + obj);
        }

        @PostMapping("/safe")
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-spring-app")
        public ResponseEntity<String> safeYaml(@RequestBody String yamlText) {
            return ResponseEntity.ok("Length: " + yamlText.length());
        }
    }

    // =====================================================================
    // JYaml (org.ho.yaml) - instance YamlConfig methods
    // =====================================================================

    @WebServlet("/deserialize/jyaml-config")
    public static class UnsafeJYamlConfigServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            org.ho.yaml.YamlConfig config = org.ho.yaml.YamlConfig.getDefaultConfig();
            Object obj = config.load(req.getInputStream());
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    // =====================================================================
    // Jabsorb - JSONSerializer.fromJSON
    // =====================================================================

    @RestController
    @RequestMapping("/api/deserialize/jabsorb")
    public static class JabsorbSpringController {

        @PostMapping("/unsafe")
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-spring-app")
        public ResponseEntity<String> unsafeFromJson(@RequestBody String json) throws Exception {
            org.jabsorb.JSONSerializer serializer = new org.jabsorb.JSONSerializer();
            Object obj = serializer.fromJSON(json);
            return ResponseEntity.ok("Deserialized: " + obj);
        }

        @PostMapping("/safe")
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-deserialization-in-spring-app")
        public ResponseEntity<String> safeFromJson(@RequestBody String json) {
            return ResponseEntity.ok("Length: " + json.length());
        }
    }
}
