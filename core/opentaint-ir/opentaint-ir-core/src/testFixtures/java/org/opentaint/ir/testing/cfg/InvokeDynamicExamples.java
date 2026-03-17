package org.opentaint.ir.testing.cfg;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class InvokeDynamicExamples {

    private static int runUnaryFunction(String data, Function<String, Integer> f) {
        if (data.isEmpty()) {
            return -1;
        }

        int result = f.apply(data);
        return result + 17;
    }

    private static int runSamFunction(String data, SamBase f) {
        if (data.isEmpty()) {
            return -1;
        }
        int result = f.samFunction(data);
        return result + 17;
    }

    private static int runDefaultFunction(String data, SamBase f) {
        if (data.isEmpty()) {
            return -1;
        }
        int result = f.defaultFunction(data);
        return result + 17;
    }

    private static String runComplexStringConcat(String str, int v) {
        return str + v + 'x' + str + 17 + str;
    }

    public interface SamBase {
        int samFunction(String data);

        default int defaultFunction(String data) {
            if (data.isEmpty()) {
                return -2;
            }
            return samFunction(data) + 31;
        }
    }

    private static int add(int a, int b) {
        return a + b;
    }

    public static String testUnaryFunction() {
        int res = runUnaryFunction("abc", s -> s.length());
        return res == ("abc".length() + 17) ? "OK" : "BAD";
    }

    public static String testMethodRefUnaryFunction() {
        int res = runUnaryFunction("abc", String::length);
        return res == ("abc".length() + 17) ? "OK" : "BAD";
    }

    public static String testCurryingFunction() {
        Function<Integer, Integer> add42 = x -> add(x, 42);
        int res = runUnaryFunction("abc", s -> add42.apply(s.length()));
        return res == ("abc".length() + 17 + 42) ? "OK" : "BAD";
    }

    public static String testSamFunction() {
        int res = runSamFunction("abc", s -> s.length());
        return res == ("abc".length() + 17) ? "OK" : "BAD";
    }

    public static String testSamWithDefaultFunction() {
        int res = runDefaultFunction("abc", s -> s.length());
        return res == ("abc".length() + 17 + 31) ? "OK" : "BAD";
    }

    public static String testComplexInvokeDynamic() {
        String expected = "abc42xabc17abc";
        String actual = runComplexStringConcat("abc", 42);
        return expected.equals(actual) ? "OK" : "BAD";
    }

    static class A {
        private final String prefix;

        public A(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String toString() {
            return prefix + "456";
        }
    }

    private static String invokeDynamicConstructor(Function<String, A> f){
        A a = f.apply("123");
        return a.toString();
    }

    public static String testInvokeDynamicConstructor() {
        final String result = invokeDynamicConstructor(A::new);
        return "123456".equals(result) ? "OK" : "BAD";
    }

    public static class CollectionWithInnerMap {
        private final Map<String, String> innerMap;

        public CollectionWithInnerMap() {
            innerMap = new HashMap<>();
        }

        private void add(String key, String value) {
            innerMap.put(key, value);
        }

        public void putAll(Map<String, String> map) {
            map.forEach(this::removeBindingResultIfNecessary);
            map.forEach(this::privateRemoveBindingResultIfNecessary);
            innerMap.putAll(map);
        }

        void removeBindingResultIfNecessary(String key, String value) {
            if (!key.isEmpty()) {
                add(key + "cde", value);
            }
        }

        private void privateRemoveBindingResultIfNecessary(String key, String value) {
            if (!key.isEmpty()) {
                add(key + "abc", value);
            }
        }
    }

    public static String testNonStaticLambda() {
        CollectionWithInnerMap collection = new CollectionWithInnerMap();
        Map<String, String> map = new HashMap<>();
        map.put("abc", "cde");
        map.put("dead", "beef");
        collection.putAll(map);
        String expected = "beef";
        String actual = collection.innerMap.get("deadabc");
        return expected.equals(actual) ? "OK" : "BAD";
    }
}
