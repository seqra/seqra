package security.passthrough;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

/**
 * Regression samples for the serialization/deserialization models.
 *
 * A serializer has to carry the taint of the object it writes into the produced text or
 * bytes, and a deserializer has to carry the taint of its input into the produced object -
 * including into the object's fields, which works because a copy re-roots the whole
 * subtree under the source position. Each unsafe method walks one such pair into
 * Runtime.exec; the safe twin serialises constants.
 */
@RestController
@RequestMapping("/passthrough/serialization")
public class PassthroughSerializationSamples {

    private static final String CONSTANT = "release-notes.txt";

    public static class FileRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;

        public FileRequest() {
        }

        public FileRequest(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    // === jackson: object -> text ===

    /**
     * The serialised value is the tainted one. Serialising a bean whose <em>field</em> is
     * tainted is a different, unsolved case: the copy preserves the field path, so the mark
     * lands on {@code json.name} rather than on {@code json}, and a plain ContainsMark sink
     * never sees it. Expressing that collapse needs an AnyField read, which the models do
     * not use.
     */
    @GetMapping("/jackson-write/unsafe")
    public void jacksonWriteUnsafe(@RequestParam String input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(input);
        Runtime.getRuntime().exec("cat " + json);
    }

    @GetMapping("/jackson-write/safe")
    public void jacksonWriteSafe(@RequestParam String input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(CONSTANT);
        Runtime.getRuntime().exec("cat " + json);
    }

    // === jackson: text -> object, and the field read off the produced object ===

    @GetMapping("/jackson-read/unsafe")
    public void jacksonReadUnsafe(@RequestParam String input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        FileRequest request = mapper.readValue(input, FileRequest.class);
        Runtime.getRuntime().exec("cat " + request.getName());
    }

    @GetMapping("/jackson-read/safe")
    public void jacksonReadSafe(@RequestParam String input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        FileRequest request = mapper.readValue(CONSTANT, FileRequest.class);
        Runtime.getRuntime().exec("cat " + request.getName());
    }

    /** The byte[] overload - its signature only started matching once the leading-dot types were fixed. */
    @GetMapping("/jackson-read-bytes/unsafe")
    public void jacksonReadBytesUnsafe(@RequestParam String input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        FileRequest request = mapper.readValue(input.getBytes(), FileRequest.class);
        Runtime.getRuntime().exec("cat " + request.getName());
    }

    @GetMapping("/jackson-read-bytes/safe")
    public void jacksonReadBytesSafe(@RequestParam String input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        FileRequest request = mapper.readValue(CONSTANT.getBytes(), FileRequest.class);
        Runtime.getRuntime().exec("cat " + request.getName());
    }

    @GetMapping("/jackson-read-tree/unsafe")
    public void jacksonReadTreeUnsafe(@RequestParam String input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(input);
        Runtime.getRuntime().exec("cat " + node.toString());
    }

    @GetMapping("/jackson-read-tree/safe")
    public void jacksonReadTreeSafe(@RequestParam String input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(CONSTANT);
        Runtime.getRuntime().exec("cat " + node.toString());
    }

    @GetMapping("/jackson-convert/unsafe")
    public void jacksonConvertUnsafe(@RequestParam String input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        FileRequest request = mapper.convertValue(new FileRequest(input), FileRequest.class);
        Runtime.getRuntime().exec("cat " + request.getName());
    }

    @GetMapping("/jackson-convert/safe")
    public void jacksonConvertSafe(@RequestParam String input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        FileRequest request = mapper.convertValue(new FileRequest(CONSTANT), FileRequest.class);
        Runtime.getRuntime().exec("cat " + request.getName());
    }

    // === snakeyaml ===

    @GetMapping("/yaml-dump/unsafe")
    public void yamlDumpUnsafe(@RequestParam String input) throws IOException {
        Yaml yaml = new Yaml();
        String dumped = yaml.dump(input);
        Runtime.getRuntime().exec("cat " + dumped);
    }

    @GetMapping("/yaml-dump/safe")
    public void yamlDumpSafe(@RequestParam String input) throws IOException {
        Yaml yaml = new Yaml();
        String dumped = yaml.dump(CONSTANT);
        Runtime.getRuntime().exec("cat " + dumped);
    }

    @GetMapping("/yaml-load/unsafe")
    public void yamlLoadUnsafe(@RequestParam String input) throws IOException {
        Yaml yaml = new Yaml();
        FileRequest request = yaml.loadAs(input, FileRequest.class);
        Runtime.getRuntime().exec("cat " + request.getName());
    }

    @GetMapping("/yaml-load/safe")
    public void yamlLoadSafe(@RequestParam String input) throws IOException {
        Yaml yaml = new Yaml();
        FileRequest request = yaml.loadAs(CONSTANT, FileRequest.class);
        Runtime.getRuntime().exec("cat " + request.getName());
    }

    // === properties: text <-> store ===

    @GetMapping("/properties-store/unsafe")
    public void propertiesStoreUnsafe(@RequestParam String input) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("name", input);
        StringWriter writer = new StringWriter();
        properties.store(writer, "comment");
        Runtime.getRuntime().exec("cat " + writer.toString());
    }

    @GetMapping("/properties-store/safe")
    public void propertiesStoreSafe(@RequestParam String input) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("name", CONSTANT);
        StringWriter writer = new StringWriter();
        properties.store(writer, "comment");
        Runtime.getRuntime().exec("cat " + writer.toString());
    }
}
