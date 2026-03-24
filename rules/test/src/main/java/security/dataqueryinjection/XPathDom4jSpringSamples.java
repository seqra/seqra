package security.dataqueryinjection;

import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;
import org.dom4j.XPath;

import org.opentaint.sast.test.util.NegativeRuleSample;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for XPath injection via dom4j and Apache CXF XPathUtils.
 */
public class XPathDom4jSpringSamples {

    // ── Apache CXF XPathUtils ─────────────────────────────────────────────────

    @RestController
    @RequestMapping("/xpath/cxf")
    public static class UnsafeCxfXPathController {

        @GetMapping("/unsafe/getValueString")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String unsafeGetValueString(@RequestParam("expr") String expression) throws Exception {
            org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().newDocument();
            @SuppressWarnings("unchecked")
            org.apache.cxf.helpers.XPathUtils xpathUtils = new org.apache.cxf.helpers.XPathUtils((Map<String, String>) null);
            return xpathUtils.getValueString(expression, doc);
        }

        @GetMapping("/unsafe/isExist")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String unsafeIsExist(@RequestParam("expr") String expression) throws Exception {
            org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().newDocument();
            @SuppressWarnings("unchecked")
            org.apache.cxf.helpers.XPathUtils xpathUtils = new org.apache.cxf.helpers.XPathUtils((Map<String, String>) null);
            return String.valueOf(xpathUtils.isExist(expression, doc, javax.xml.namespace.QName.valueOf("boolean")));
        }
    }

    // ── dom4j DocumentFactory ─────────────────────────────────────────────────

    @RestController
    @RequestMapping("/xpath/dom4j/factory")
    public static class UnsafeDom4jDocumentFactoryController {

        @GetMapping("/unsafe/createXPath")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String unsafeCreateXPath(@RequestParam("xpath") String xpath) throws Exception {
            DocumentFactory factory = DocumentFactory.getInstance();
            XPath xpathObj = factory.createXPath(xpath);
            return xpathObj.getText();
        }

        @GetMapping("/unsafe/createXPathFilter")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String unsafeCreateXPathFilter(@RequestParam("xpath") String xpath) throws Exception {
            DocumentFactory factory = DocumentFactory.getInstance();
            factory.createXPathFilter(xpath);
            return "ok";
        }
    }

    // ── dom4j DocumentHelper (static methods) ─────────────────────────────────

    @RestController
    @RequestMapping("/xpath/dom4j/helper")
    public static class UnsafeDom4jDocumentHelperController {

        @GetMapping("/unsafe/createXPath")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String unsafeCreateXPath(@RequestParam("xpath") String xpath) throws Exception {
            XPath xpathObj = DocumentHelper.createXPath(xpath);
            return xpathObj.getText();
        }

        @GetMapping("/unsafe/selectNodes")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String unsafeSelectNodes(@RequestParam("xpath") String xpath) throws Exception {
            Document doc = DocumentHelper.parseText("<root><user name='admin'/></root>");
            List nodes = DocumentHelper.selectNodes(xpath, doc.selectNodes("//user"));
            return "found " + nodes.size();
        }

        @GetMapping("/unsafe/sort")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String unsafeSort(@RequestParam("sortExpr") String sortExpr) throws Exception {
            Document doc = DocumentHelper.parseText("<root><user name='b'/><user name='a'/></root>");
            List nodes = doc.selectNodes("//user");
            DocumentHelper.sort(nodes, sortExpr);
            return "sorted";
        }
    }

    // ── dom4j Node methods ────────────────────────────────────────────────────

    @RestController
    @RequestMapping("/xpath/dom4j/node")
    public static class UnsafeDom4jNodeController {

        @GetMapping("/unsafe/selectSingleNode")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String unsafeSelectSingleNode(@RequestParam("xpath") String xpath) throws Exception {
            Document doc = DocumentHelper.parseText("<root><user name='admin'/></root>");
            Node result = doc.selectSingleNode(xpath);
            return result != null ? result.getText() : "null";
        }

        @GetMapping("/unsafe/valueOf")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String unsafeValueOf(@RequestParam("xpath") String xpath) throws Exception {
            Document doc = DocumentHelper.parseText("<root><user name='admin'/></root>");
            return doc.valueOf(xpath);
        }

        @GetMapping("/unsafe/selectNodes")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String unsafeSelectNodes(@RequestParam("xpath") String xpath) throws Exception {
            Document doc = DocumentHelper.parseText("<root><user name='admin'/></root>");
            List nodes = doc.selectNodes(xpath);
            return "found " + nodes.size();
        }

        @GetMapping("/unsafe/selectNodes2arg")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String unsafeSelectNodes2Arg(@RequestParam("sortExpr") String sortExpr) throws Exception {
            Document doc = DocumentHelper.parseText("<root><user name='b'/><user name='a'/></root>");
            List nodes = doc.selectNodes("//user", sortExpr);
            return "found " + nodes.size();
        }

        @GetMapping("/unsafe/numberValueOf")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String unsafeNumberValueOf(@RequestParam("xpath") String xpath) throws Exception {
            Document doc = DocumentHelper.parseText("<root><count>42</count></root>");
            Number result = doc.numberValueOf(xpath);
            return result.toString();
        }
    }

    // ── Safe samples ──────────────────────────────────────────────────────────

    @RestController
    @RequestMapping("/xpath/dom4j/safe")
    public static class SafeDom4jController {

        @GetMapping("/safe")
        @NegativeRuleSample(value = "java/security/data-query-injection.yaml", id = "xpath-injection-in-spring-app")
        public String safeXPath(@RequestParam("username") String username) throws Exception {
            Document doc = DocumentHelper.parseText("<root><user name='admin'/></root>");
            // SAFE: hardcoded XPath expression, user data not in XPath
            Node result = doc.selectSingleNode("//user[@name='admin']");
            return result != null ? result.getText() : "not found";
        }
    }
}
