package sample.alias;

public class SimpleAliasSample {
    static void simpleArgAlias(Object a1, Object a2, boolean c) {
        Object result = null;
        if (c) {
            result = a1;
        } else {
            result = a2;
        }

        testSimpleArgAlias(result);
    }

    static void testSimpleArgAlias(Object obj) {
    }
}
