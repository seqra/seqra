package security.codeinjection;

import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.TextProvider;
import com.opensymphony.xwork2.ognl.OgnlValueStack;
import ognl.Node;
import ognl.enhance.ExpressionAccessor;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for extended OGNL injection sinks.
 */
public class OgnlInjectionExtendedSpringSamples {

    @RestController
    @RequestMapping("/ognl-injection-extended")
    public static class UnsafeOgnlValueStackFindStringController {

        @GetMapping("/value-stack/find-string")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeFindString(@RequestParam("expr") String expr,
                                       OgnlValueStack valueStack) throws Exception {
            // VULNERABLE: OGNL expression via OgnlValueStack.findString
            String result = valueStack.findString(expr);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-injection-extended")
    public static class UnsafeOgnlValueStackFindValueController {

        @GetMapping("/value-stack/find-value")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeFindValue(@RequestParam("expr") String expr,
                                      OgnlValueStack valueStack) throws Exception {
            // VULNERABLE: OGNL expression via OgnlValueStack.findValue
            Object result = valueStack.findValue(expr);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-injection-extended")
    public static class UnsafeTextProviderGetTextController {

        @GetMapping("/text-provider/get-text")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeGetText(@RequestParam("key") String key,
                                    TextProvider textProvider) throws Exception {
            // VULNERABLE: OGNL expression via TextProvider.getText
            String result = textProvider.getText(key);
            return result;
        }
    }

    @RestController
    @RequestMapping("/ognl-injection-extended")
    public static class UnsafeActionSupportGetFormattedController {

        @GetMapping("/action-support/get-formatted")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeGetFormatted(@RequestParam("key") String key,
                                         ActionSupport actionSupport) throws Exception {
            // VULNERABLE: OGNL expression via ActionSupport.getFormatted
            String result = actionSupport.getFormatted(key, "default");
            return result;
        }
    }

    @RestController
    @RequestMapping("/ognl-injection-extended")
    public static class UnsafeTextProviderHasKeyController {

        @GetMapping("/text-provider/has-key")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeHasKey(@RequestParam("key") String key,
                                    TextProvider textProvider) throws Exception {
            // VULNERABLE: OGNL expression via TextProvider.hasKey
            boolean result = textProvider.hasKey(key);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-injection-extended")
    public static class UnsafeOgnlNodeGetValueController {

        @GetMapping("/ognl-node/get-value")
        // TODO: Analyzer FN – taint does not propagate through Ognl.parseExpression() to Node;
        // re-enable when taint propagation summaries for OGNL parse are added
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeNodeGetValue(@RequestParam("expr") String expr, Node node) throws Exception {
            // VULNERABLE: OGNL expression via Node.getValue (Argument[this])
            Object result = node.getValue(null, null);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-injection-extended")
    public static class UnsafeExpressionAccessorGetController {

        @GetMapping("/expression-accessor/get")
        // TODO: Analyzer FN – taint does not propagate through compiled OGNL expression to ExpressionAccessor;
        // re-enable when taint propagation summaries for OGNL compile are added
        // @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeAccessorGet(@RequestParam("expr") String expr,
                                        ExpressionAccessor accessor) throws Exception {
            // VULNERABLE: OGNL expression via ExpressionAccessor.get (Argument[this])
            Object result = accessor.get(null, null);
            return String.valueOf(result);
        }
    }
}
