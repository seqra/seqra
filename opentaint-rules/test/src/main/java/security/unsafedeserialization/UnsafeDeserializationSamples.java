package security.unsafedeserialization;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;
import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Samples for unsafe-deserialization rules from java/security/unsafe-deserialization.yaml.
 */
public class UnsafeDeserializationSamples {

    // unsafe-object-mapper-in-servlet-app

    @WebServlet("/deserialize/unsafe-object-input-stream")
    public static class UnsafeObjectInputStreamServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-object-mapper-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // VULNERABLE: directly deserialize from request input stream
            try (ObjectInputStream ois = new ObjectInputStream(req.getInputStream())) {
                Object obj = ois.readObject();
                resp.getWriter().println("Deserialized: " + obj);
            } catch (ClassNotFoundException e) {
                throw new ServletException(e);
            }
        }
    }

    @WebServlet("/deserialize/safe-object-input-stream")
    public static class SafeObjectInputStreamServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-object-mapper-in-servlet-app")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
          try (ObjectInputStream ois = new ObjectInputStream(new java.io.FileInputStream("/tmp/last_resource"))) {
            Object obj = ois.readObject();
            resp.getWriter().println("Deserialized: " + obj);
          } catch (ClassNotFoundException e) {
            throw new ServletException(e);
          }
        }
    }

    // unsafe-object-mapper-in-spring-app

    @RestController
    @RequestMapping("/api/deserialize/object-input-stream")
    public static class ObjectInputStreamSpringController {

        @PostMapping(path = "/unsafe", consumes = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-object-mapper-in-spring-app")
        public ResponseEntity<String> unsafeDeserialize(@RequestBody byte[] body) {
            try (ObjectInputStream ois = new ObjectInputStream(new java.io.ByteArrayInputStream(body))) {
                Object obj = ois.readObject();
                return ResponseEntity.ok("Deserialized: " + obj);
            } catch (IOException | ClassNotFoundException e) {
                return ResponseEntity.status(500).body("Error: " + e.getMessage());
            }
        }

        @PostMapping(path = "/safe", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-object-mapper-in-spring-app")
        public ResponseEntity<SafeDto> safeDeserialize(@RequestBody SafeDto dto) {
            // SAFE: rely on Spring's JSON binding into a constrained DTO type
            if (dto == null || dto.name == null || dto.name.length() > 100) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(dto);
        }
    }

    public static class SafeDto implements Serializable {
        public String name;
        public int age;
    }

    // insecure-jms-deserialization

    public static class InsecureJmsListener implements MessageListener {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "insecure-jms-deserialization")
        public void onMessage(Message message) {
            try {
                if (message instanceof ObjectMessage) {
                    ObjectMessage objectMessage = (ObjectMessage) message;
                    // VULNERABLE: blindly calling getObject on ObjectMessage
                    Object payload = objectMessage.getObject();
                    System.out.println("Received: " + payload);
                }
            } catch (JMSException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class SafeJmsListener implements MessageListener {

        @Override
// TODO: no rules for such validation for now
//        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "insecure-jms-deserialization")
        public void onMessage(Message message) {
            try {
                // SAFE-ish: only accept a specific expected type and ignore others
                if (message instanceof ObjectMessage) {
                    ObjectMessage objectMessage = (ObjectMessage) message;
                    Object obj = objectMessage.getObject();
                    if (!(obj instanceof SafeDto)) {
                        throw new IllegalArgumentException("Unexpected JMS payload type");
                    }
                    SafeDto dto = (SafeDto) obj;
                    System.out.println("Processed: " + dto.name);
                }
            } catch (JMSException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // unsafe-jackson-deserialization-in-servlet-app

    @WebServlet("/deserialize/jackson/unsafe")
    @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-jackson-deserialization-in-servlet-app")
    public static class UnsafeJacksonServlet extends HttpServlet {

        // VULNERABLE: default typing enabled globally -> potential RCE gadget exploitation
        private final ObjectMapper mapper = new ObjectMapper().enableDefaultTyping();

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // VULNERABLE: no type restriction or configuration hardening
            Object obj = mapper.readValue(req.getInputStream(), Object.class);
            resp.getWriter().println("Deserialized: " + obj);
        }
    }

    @WebServlet("/deserialize/jackson/safe")
    @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-jackson-deserialization-in-servlet-app")
    public static class SafeJacksonServlet extends HttpServlet {

        private final ObjectMapper mapper;

        public SafeJacksonServlet() {
            mapper = new ObjectMapper();
            // SAFE-ish: disable default typing and only use simple DTOs
            mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            SafeDto dto = mapper.readValue(req.getInputStream(), SafeDto.class);
            if (dto.name == null) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing name");
                return;
            }
            resp.getWriter().println("Hello, " + dto.name);
        }
    }

    // unsafe-jackson-deserialization-in-spring-app

    @RestController
    @RequestMapping("/api/deserialize/jackson")
    @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-jackson-deserialization-in-spring-app")
    public static class JacksonSpringController {

        // VULNERABLE: default typing enabled globally -> potential RCE gadget exploitation
        private final ObjectMapper mapper = new ObjectMapper().enableDefaultTyping();

        @PostMapping(path = "/unsafe", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<Object> unsafeJackson(@RequestBody String json) throws IOException {
            // VULNERABLE: deserialize untrusted JSON into arbitrary Object
            Object obj = mapper.readValue(json, Object.class);
            return ResponseEntity.ok(obj);
        }

        @PostMapping(path = "/safe", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-jackson-deserialization-in-spring-app")
        public ResponseEntity<SafeDto> safeJackson(@RequestBody SafeDto dto) {
            if (dto == null || dto.name == null || dto.name.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(dto);
        }
    }

    // server-dangerous-object-deserialization (Java RMI interface with arbitrary param type)

    public interface DangerousRemoteService extends Remote {

        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "server-dangerous-object-deserialization")
        Object invoke(RemoteCommand command) throws RemoteException;
    }

    public static class RemoteCommand implements Serializable {
        private final String operation;

        public RemoteCommand(String operation) {
            this.operation = operation;
        }

        public String getOperation() {
            return operation;
        }
    }

    public interface SafeRemoteService extends Remote {

        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "server-dangerous-object-deserialization")
        String invokeById(long commandId) throws RemoteException;
    }

    // java-servlet-unsafe-snake-yaml-deserialization / spring-unsafe-snake-yaml-deserialization

    @WebServlet("/yaml/unsafe")
    public static class UnsafeSnakeYamlServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "java-servlet-unsafe-snake-yaml-deserialization")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // VULNERABLE: use default SnakeYAML constructor on user-provided data
            Yaml yaml = new Yaml();
            Object obj = yaml.load(req.getInputStream());
            resp.getWriter().println("Parsed: " + obj);
        }
    }

    @WebServlet("/yaml/safe")
    public static class SafeSnakeYamlServlet extends HttpServlet {

        @Override
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "java-servlet-unsafe-snake-yaml-deserialization")
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            // SAFE-ish: treat YAML as plain text or use a safe subset parser (simulated here)
            String body = new String(req.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (body.length() > 4096) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "YAML too large");
                return;
            }
            resp.getWriter().println("YAML received length: " + body.length());
        }
    }

    @RestController
    @RequestMapping("/api/yaml")
    public static class SnakeYamlSpringController {

        @PostMapping(path = "/unsafe", consumes = "application/x-yaml")
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "spring-unsafe-snake-yaml-deserialization")
        public ResponseEntity<Object> unsafeYaml(@RequestBody byte[] yamlBytes) {
            Yaml yaml = new Yaml();
            Object obj = yaml.load(new java.io.ByteArrayInputStream(yamlBytes));
            return ResponseEntity.ok(obj);
        }

        @PostMapping(path = "/safe", consumes = "application/x-yaml")
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "spring-unsafe-snake-yaml-deserialization")
        public ResponseEntity<String> safeYaml(@RequestBody String yamlText) {
            if (yamlText.length() > 4096) {
                return ResponseEntity.badRequest().body("YAML too large");
            }
            return ResponseEntity.ok("YAML length: " + yamlText.length());
        }
    }

    // insecure-resteasy-deserialization / default-resteasy-provider-abuse

    @Path("/resteasy/unsafe")
    public static class InsecureResteasyResource {

        @Consumes({"application/x-java-serialized-object", MediaType.WILDCARD})
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "insecure-resteasy-deserialization")
        public Object handleUnsafe(Object body) {
            // VULNERABLE: accepts serialized objects via wildcard media type
            return body;
        }
    }

    @Path("/resteasy/safe")
    public static class SafeResteasyResource {

        @Consumes({MediaType.APPLICATION_JSON})
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "insecure-resteasy-deserialization")
        public SafeDto handleSafe(SafeDto dto) {
            return dto;
        }
    }

    @Path("/resteasy/default")
    public static class DefaultResteasyResource {

        @Path("/unsafe-endpoint")
        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "default-resteasy-provider-abuse")
        public SafeDto unsafeWithoutConsumes() {
            // VULNERABLE: no @Consumes on method or class; defaults allow serialized objects
            return new SafeDto();
        }
    }

    @Path("/resteasy/default-safe")
    @Consumes({MediaType.APPLICATION_JSON})
    public static class DefaultSafeResteasyResource {

        @Path("/safe-endpoint")
        @GET
        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "default-resteasy-provider-abuse")
        public SafeDto safeWithConsumes() {
            return new SafeDto();
        }
    }

    // apache-rpc-enabled-extensions

    public static class InsecureApacheXmlRpcConfig {

        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "apache-rpc-enabled-extensions")
        public void enableExtensionsServer() {
            XmlRpcServerConfigImpl config = new XmlRpcServerConfigImpl();
            config.setEnabledForExtensions(true);
        }

        @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "apache-rpc-enabled-extensions")
        public void enableExtensionsClient() {
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setEnabledForExtensions(true);
        }
    }

    public static class SafeApacheXmlRpcConfig {

        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "apache-rpc-enabled-extensions")
        public void disableExtensionsServer() {
            XmlRpcServerConfigImpl config = new XmlRpcServerConfigImpl();
            config.setEnabledForExtensions(false);
        }

        @NegativeRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "apache-rpc-enabled-extensions")
        public void disableExtensionsClient() {
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setEnabledForExtensions(false);
        }
    }
}
