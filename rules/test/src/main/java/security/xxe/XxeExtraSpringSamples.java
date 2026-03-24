package security.xxe;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for pre-existing XXE sink patterns:
 * TransformerFactory.newTransformer and XMLDecoder constructors.
 */
public class XxeExtraSpringSamples {

    // ── TransformerFactory.newTransformer(Source) ────────────────────────────

    @RestController
    @RequestMapping("/xxe/extra/transformer")
    public static class UnsafeTransformerFactoryController {

        @PostMapping("/unsafe/newTransformer")
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe-in-spring-app")
        public String unsafeNewTransformer(@RequestParam("xslt") String xsltContent) throws Exception {
            TransformerFactory factory = TransformerFactory.newInstance();
            StreamSource source = new StreamSource(new StringReader(xsltContent));
            // VULNERABLE: untrusted XSLT loaded into transformer
            factory.newTransformer(source);
            return "ok";
        }
    }

    // ── XMLDecoder 1-arg constructor ────────────────────────────────────────

    @RestController
    @RequestMapping("/xxe/extra/xmldecoder")
    public static class UnsafeXMLDecoderController {

        @PostMapping("/unsafe/decoder1arg")
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe-in-spring-app")
        public String unsafeDecoder1Arg(@RequestParam("xml") String xmlContent) throws Exception {
            InputStream in = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
            java.beans.XMLDecoder decoder = new java.beans.XMLDecoder(in);
            Object result = decoder.readObject();
            decoder.close();
            return String.valueOf(result);
        }

        @PostMapping("/unsafe/decoder2arg")
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe-in-spring-app")
        public String unsafeDecoder2Arg(@RequestParam("xml") String xmlContent) throws Exception {
            InputStream in = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
            java.beans.XMLDecoder decoder = new java.beans.XMLDecoder(in, this);
            Object result = decoder.readObject();
            decoder.close();
            return String.valueOf(result);
        }

        @PostMapping("/unsafe/decoder3arg")
        @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe-in-spring-app")
        public String unsafeDecoder3Arg(@RequestParam("xml") String xmlContent) throws Exception {
            InputStream in = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
            java.beans.XMLDecoder decoder = new java.beans.XMLDecoder(in, this, null);
            Object result = decoder.readObject();
            decoder.close();
            return String.valueOf(result);
        }
    }
}
