package security.codeinjection;

import com.opensymphony.xwork2.ognl.OgnlReflectionProvider;
import com.opensymphony.xwork2.ognl.OgnlUtil;
import com.opensymphony.xwork2.util.OgnlTextParser;
import com.opensymphony.xwork2.util.TextParseUtil;
import com.opensymphony.xwork2.util.ValueStack;
import com.opensymphony.xwork2.util.reflection.ReflectionProvider;
import org.apache.struts2.util.StrutsUtil;
import org.apache.struts2.util.VelocityStrutsUtil;
import org.apache.struts2.views.jsp.ui.OgnlTool;
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for OGNL injection via Struts2 types.
 * All controllers use Spring {@code @RestController} with Struts2 types injected as parameters
 * (available via compileOnly dependency on struts2-core).
 */
public class OgnlInjectionStruts2SpringSamples {

    // ═══════════════════════════════════════════════════════════════════
    // OgnlReflectionProvider (7 patterns)
    // ═══════════════════════════════════════════════════════════════════

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlReflectionProviderGetGetMethodController {

        @GetMapping("/reflection-provider/get-get-method")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeGetGetMethod(@RequestParam("name") String name,
                                         OgnlReflectionProvider provider) throws Exception {
            // VULNERABLE: OGNL expression via OgnlReflectionProvider.getGetMethod
            Object result = provider.getGetMethod(Object.class, name);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlReflectionProviderGetSetMethodController {

        @GetMapping("/reflection-provider/get-set-method")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeGetSetMethod(@RequestParam("name") String name,
                                         OgnlReflectionProvider provider) throws Exception {
            // VULNERABLE: OGNL expression via OgnlReflectionProvider.getSetMethod
            Object result = provider.getSetMethod(Object.class, name);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlReflectionProviderGetFieldController {

        @GetMapping("/reflection-provider/get-field")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeGetField(@RequestParam("name") String name,
                                     OgnlReflectionProvider provider) throws Exception {
            // VULNERABLE: OGNL expression via OgnlReflectionProvider.getField
            Object result = provider.getField(Object.class, name);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlReflectionProviderSetPropertiesController {

        @GetMapping("/reflection-provider/set-properties")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeSetProperties(@RequestParam("expr") String expr,
                                          OgnlReflectionProvider provider) throws Exception {
            // VULNERABLE: OGNL expression via OgnlReflectionProvider.setProperties
            java.util.Map<String, String> props = java.util.Map.of("key", expr);
            provider.setProperties(props, new Object(), java.util.Collections.emptyMap());
            return "done";
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlReflectionProviderSetPropertyController {

        @GetMapping("/reflection-provider/set-property")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeSetProperty(@RequestParam("expr") String expr,
                                        OgnlReflectionProvider provider) throws Exception {
            // VULNERABLE: OGNL expression via OgnlReflectionProvider.setProperty
            provider.setProperty(expr, "value", new Object(), java.util.Collections.emptyMap());
            return "done";
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlReflectionProviderGetValueController {

        @GetMapping("/reflection-provider/get-value")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeGetValue(@RequestParam("expr") String expr,
                                     OgnlReflectionProvider provider) throws Exception {
            // VULNERABLE: OGNL expression via OgnlReflectionProvider.getValue
            Object result = provider.getValue(expr, null, new Object());
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlReflectionProviderSetValueController {

        @GetMapping("/reflection-provider/set-value")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeSetValue(@RequestParam("expr") String expr,
                                     OgnlReflectionProvider provider) throws Exception {
            // VULNERABLE: OGNL expression via OgnlReflectionProvider.setValue
            provider.setValue(expr, null, new Object(), "value");
            return "done";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ReflectionProvider interface (7 patterns)
    // NOTE: translateVariables does not exist on ReflectionProvider interface
    // ═══════════════════════════════════════════════════════════════════

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeReflectionProviderGetGetMethodController {

        @GetMapping("/iface-reflection-provider/get-get-method")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeGetGetMethod(@RequestParam("name") String name,
                                         ReflectionProvider provider) throws Exception {
            Object result = provider.getGetMethod(Object.class, name);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeReflectionProviderGetSetMethodController {

        @GetMapping("/iface-reflection-provider/get-set-method")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeGetSetMethod(@RequestParam("name") String name,
                                         ReflectionProvider provider) throws Exception {
            Object result = provider.getSetMethod(Object.class, name);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeReflectionProviderGetFieldController {

        @GetMapping("/iface-reflection-provider/get-field")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeGetField(@RequestParam("name") String name,
                                     ReflectionProvider provider) throws Exception {
            Object result = provider.getField(Object.class, name);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeReflectionProviderSetPropertiesController {

        @GetMapping("/iface-reflection-provider/set-properties")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeSetProperties(@RequestParam("expr") String expr,
                                          ReflectionProvider provider) throws Exception {
            java.util.Map<String, String> props = java.util.Map.of("key", expr);
            provider.setProperties(props, new Object(), java.util.Collections.emptyMap());
            return "done";
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeReflectionProviderSetPropertyController {

        @GetMapping("/iface-reflection-provider/set-property")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeSetProperty(@RequestParam("expr") String expr,
                                        ReflectionProvider provider) throws Exception {
            provider.setProperty(expr, "value", new Object(), java.util.Collections.emptyMap());
            return "done";
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeReflectionProviderGetValueController {

        @GetMapping("/iface-reflection-provider/get-value")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeGetValue(@RequestParam("expr") String expr,
                                     ReflectionProvider provider) throws Exception {
            Object result = provider.getValue(expr, null, new Object());
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeReflectionProviderSetValueController {

        @GetMapping("/iface-reflection-provider/set-value")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeSetValue(@RequestParam("expr") String expr,
                                     ReflectionProvider provider) throws Exception {
            provider.setValue(expr, null, new Object(), "value");
            return "done";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TextParseUtil (3 static patterns)
    // NOTE: shallBeIncluded is private, cannot be tested
    // ═══════════════════════════════════════════════════════════════════

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeTextParseUtilTranslateVariablesController {

        @GetMapping("/text-parse-util/translate-variables")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeTranslateVariables(@RequestParam("expr") String expr,
                                               ValueStack stack) {
            // VULNERABLE: OGNL expression via TextParseUtil.translateVariables
            String result = TextParseUtil.translateVariables(expr, stack);
            return result;
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeTextParseUtilTranslateVariablesCollectionController {

        @GetMapping("/text-parse-util/translate-variables-collection")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeTranslateVariablesCollection(@RequestParam("expr") String expr,
                                                         ValueStack stack) {
            // VULNERABLE: OGNL expression via TextParseUtil.translateVariablesCollection
            Object result = TextParseUtil.translateVariablesCollection(expr, stack, false, null);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeTextParseUtilCommaDelimitedController {

        @GetMapping("/text-parse-util/comma-delimited")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeCommaDelimited(@RequestParam("expr") String expr) {
            // VULNERABLE: OGNL expression via TextParseUtil.commaDelimitedStringToSet
            java.util.Set<String> result = TextParseUtil.commaDelimitedStringToSet(expr);
            return String.valueOf(result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // OgnlTextParser (1 pattern)
    // NOTE: setProperties does not exist on OgnlTextParser
    // ═══════════════════════════════════════════════════════════════════

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlTextParserEvaluateController {

        @GetMapping("/ognl-text-parser/evaluate")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeEvaluate(@RequestParam("expr") String expr,
                                     OgnlTextParser parser) {
            // VULNERABLE: OGNL expression via OgnlTextParser.evaluate
            Object result = parser.evaluate(expr.toCharArray(), expr, null, 0);
            return String.valueOf(result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // OgnlUtil (5 patterns)
    // ═══════════════════════════════════════════════════════════════════

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlUtilSetPropertyController {

        @GetMapping("/ognl-util/set-property")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeSetProperty(@RequestParam("expr") String expr,
                                        OgnlUtil ognlUtil) throws Exception {
            // VULNERABLE: OGNL expression via OgnlUtil.setProperty
            ognlUtil.setProperty(expr, "value", new Object(), null, true);
            return "done";
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlUtilGetValueController {

        @GetMapping("/ognl-util/get-value")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeGetValue(@RequestParam("expr") String expr,
                                     OgnlUtil ognlUtil) throws Exception {
            // VULNERABLE: OGNL expression via OgnlUtil.getValue
            Object result = ognlUtil.getValue(expr, null, new Object());
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlUtilSetValueController {

        @GetMapping("/ognl-util/set-value")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeSetValue(@RequestParam("expr") String expr,
                                     OgnlUtil ognlUtil) throws Exception {
            // VULNERABLE: OGNL expression via OgnlUtil.setValue
            ognlUtil.setValue(expr, null, new Object(), "value");
            return "done";
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlUtilCallMethodController {

        @GetMapping("/ognl-util/call-method")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeCallMethod(@RequestParam("expr") String expr,
                                       OgnlUtil ognlUtil) throws Exception {
            // VULNERABLE: OGNL expression via OgnlUtil.callMethod
            Object result = ognlUtil.callMethod(expr, null, new Object());
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlUtilCompileController {

        @GetMapping("/ognl-util/compile")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeCompile(@RequestParam("expr") String expr,
                                    OgnlUtil ognlUtil) throws Exception {
            // VULNERABLE: OGNL expression via OgnlUtil.compile
            Object result = ognlUtil.compile(expr);
            return String.valueOf(result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // VelocityStrutsUtil (1 pattern)
    // ═══════════════════════════════════════════════════════════════════

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeVelocityStrutsUtilEvaluateController {

        @GetMapping("/velocity-struts-util/evaluate")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeEvaluate(@RequestParam("expr") String expr,
                                     VelocityStrutsUtil util) throws Exception {
            // VULNERABLE: OGNL expression via VelocityStrutsUtil.evaluate
            String result = util.evaluate(expr);
            return result;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // StrutsUtil (6 patterns)
    // ═══════════════════════════════════════════════════════════════════

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeStrutsUtilIsTrueController {

        @GetMapping("/struts-util/is-true")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeIsTrue(@RequestParam("expr") String expr,
                                   StrutsUtil util) {
            // VULNERABLE: OGNL expression via StrutsUtil.isTrue
            boolean result = util.isTrue(expr);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeStrutsUtilFindStringController {

        @GetMapping("/struts-util/find-string")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeFindString(@RequestParam("expr") String expr,
                                       StrutsUtil util) {
            // VULNERABLE: OGNL expression via StrutsUtil.findString
            Object result = util.findString(expr);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeStrutsUtilFindValueController {

        @GetMapping("/struts-util/find-value")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeFindValue(@RequestParam("expr") String expr,
                                      StrutsUtil util) throws Exception {
            // VULNERABLE: OGNL expression via StrutsUtil.findValue
            Object result = util.findValue(expr, null);
            return String.valueOf(result);
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeStrutsUtilGetTextController {

        @GetMapping("/struts-util/get-text")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeGetText(@RequestParam("key") String key,
                                    StrutsUtil util) {
            // VULNERABLE: OGNL expression via StrutsUtil.getText
            String result = util.getText(key);
            return result;
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeStrutsUtilTranslateVariablesController {

        @GetMapping("/struts-util/translate-variables")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeTranslateVariables(@RequestParam("expr") String expr,
                                               StrutsUtil util) {
            // VULNERABLE: OGNL expression via StrutsUtil.translateVariables
            String result = util.translateVariables(expr);
            return result;
        }
    }

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeStrutsUtilMakeSelectListController {

        @GetMapping("/struts-util/make-select-list")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeMakeSelectList(@RequestParam("expr") String expr,
                                           StrutsUtil util) {
            // VULNERABLE: OGNL expression via StrutsUtil.makeSelectList
            util.makeSelectList(expr, "value", "label", "size");
            return "done";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // OgnlTool (1 pattern)
    // ═══════════════════════════════════════════════════════════════════

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeOgnlToolFindValueController {

        @GetMapping("/ognl-tool/find-value")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeFindValue(@RequestParam("expr") String expr,
                                      OgnlTool tool) {
            // VULNERABLE: OGNL expression via OgnlTool.findValue
            Object result = tool.findValue(expr, null);
            return String.valueOf(result);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ValueStack (1 pattern — setParameter)
    // ═══════════════════════════════════════════════════════════════════

    @RestController
    @RequestMapping("/ognl-struts2")
    public static class UnsafeValueStackSetParameterController {

        @GetMapping("/value-stack/set-parameter")
        @PositiveRuleSample(value = "java/security/code-injection.yaml", id = "ognl-injection-in-spring-app")
        public String unsafeSetParameter(@RequestParam("expr") String expr,
                                         ValueStack stack) {
            // VULNERABLE: OGNL expression via ValueStack.setParameter
            stack.setParameter(expr, "value");
            return "done";
        }
    }
}
