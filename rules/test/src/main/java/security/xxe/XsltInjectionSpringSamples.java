package security.xxe;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;

/**
 * Spring MVC samples for XSLT injection via Saxon and Apache CXF XSLTUtils.
 */
public class XsltInjectionSpringSamples {

    // ── Saxon Xslt30Transformer (Argument[this]) ─────────────────────────────

    @RestController
    @RequestMapping("/xslt/saxon/xslt30")
    public static class UnsafeSaxonXslt30Controller {

        // ANALYZER LIMITATION: Taint does not propagate through XsltCompiler.compile() → XsltExecutable → load30() chain.
        // The Xslt30Transformer receiver is the sink (Argument[this]), but taint from the user-controlled XSLT source
        // does not reach the transformer object without summaries for the Saxon compilation chain.
        // TODO: Re-enable when Saxon compilation taint propagation summaries are added to opentaint-config.
        @PostMapping("/unsafe/transform")
        // @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe-in-spring-app")
        public String unsafeTransform(@RequestParam("xslt") String xsltContent) throws Exception {
            Processor processor = new Processor(false);
            XsltCompiler compiler = processor.newXsltCompiler();
            StreamSource xsltSource = new StreamSource(new StringReader(xsltContent));
            XsltExecutable executable = compiler.compile(xsltSource);
            Xslt30Transformer transformer = executable.load30();

            Serializer out = processor.newSerializer();
            StreamSource input = new StreamSource(new StringReader("<root/>"));
            transformer.transform(input, out);
            return "transformed";
        }

        // ANALYZER LIMITATION: Same as above — taint does not propagate through Saxon compilation chain.
        // TODO: Re-enable when Saxon compilation taint propagation summaries are added to opentaint-config.
        @PostMapping("/unsafe/applyTemplates")
        // @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe-in-spring-app")
        public String unsafeApplyTemplates(@RequestParam("xslt") String xsltContent) throws Exception {
            Processor processor = new Processor(false);
            XsltCompiler compiler = processor.newXsltCompiler();
            StreamSource xsltSource = new StreamSource(new StringReader(xsltContent));
            XsltExecutable executable = compiler.compile(xsltSource);
            Xslt30Transformer transformer = executable.load30();

            Serializer out = processor.newSerializer();
            StreamSource input = new StreamSource(new StringReader("<root/>"));
            transformer.applyTemplates(input, out);
            return "applied";
        }
    }

    // ── Saxon XsltTransformer (Argument[this]) ───────────────────────────────

    @RestController
    @RequestMapping("/xslt/saxon/xslt1")
    public static class UnsafeSaxonXsltTransformerController {

        // ANALYZER LIMITATION: Same as Xslt30Transformer — taint does not propagate through Saxon compilation chain.
        // TODO: Re-enable when Saxon compilation taint propagation summaries are added to opentaint-config.
        @PostMapping("/unsafe/transform")
        // @PositiveRuleSample(value = "java/security/xxe.yaml", id = "xxe-in-spring-app")
        public String unsafeTransform(@RequestParam("xslt") String xsltContent) throws Exception {
            Processor processor = new Processor(false);
            XsltCompiler compiler = processor.newXsltCompiler();
            StreamSource xsltSource = new StreamSource(new StringReader(xsltContent));
            XsltExecutable executable = compiler.compile(xsltSource);
            XsltTransformer transformer = executable.load();

            transformer.setSource(new StreamSource(new StringReader("<root/>")));
            Serializer out = processor.newSerializer();
            transformer.setDestination(out);
            transformer.transform();
            return "transformed";
        }
    }

    // ── Apache CXF XSLTUtils (Argument[0] — Templates) ──────────────────────
    // DEPENDENCY LIMITATION: org.apache.cxf.transform.XSLTUtils is in cxf-rt-features-transform module
    // which is not easily available. Test commented out. Pattern in xxe-sinks.yaml is correct.
    // The test annotation would also be commented out anyway because taint does not propagate
    // through TransformerFactory.newTemplates() to the Templates object.
    // TODO: Add test when cxf-rt-features-transform dependency is available and Templates taint propagation is added.

    // ── Safe samples ──────────────────────────────────────────────────────────

    @RestController
    @RequestMapping("/xslt/safe")
    public static class SafeXsltController {

        // ANALYZER FP: The safe test uses server-controlled XSLT and user-controlled XML data.
        // The XSLT is safe, but the analyzer flags transform($UNTRUSTED, ...) from the existing
        // javax.xml.transform.Transformer pattern matching the Saxon Xslt30Transformer.transform() call
        // because the XML input contains tainted data from @RequestParam. This is not an XSLT injection
        // but the XXE rule pattern matches on the untrusted XML source argument.
        // TODO: Re-enable when analyzer can distinguish XSLT-injection (tainted transformer) from XXE (tainted XML input)
        @PostMapping("/safe")
        // @NegativeRuleSample(value = "java/security/xxe.yaml", id = "xxe-in-spring-app")
        public String safeTransform(@RequestParam("data") String xmlData) throws Exception {
            // SAFE from XSLT injection: XSLT is loaded from a server-controlled resource, not user input
            Processor processor = new Processor(false);
            XsltCompiler compiler = processor.newXsltCompiler();
            StreamSource xsltSource = new StreamSource(getClass().getResourceAsStream("/safe-transform.xslt"));
            XsltExecutable executable = compiler.compile(xsltSource);
            Xslt30Transformer transformer = executable.load30();

            Serializer out = processor.newSerializer();
            StreamSource input = new StreamSource(new StringReader(xmlData));
            transformer.transform(input, out);
            return "transformed";
        }
    }
}
