package sample.bidi;

import java.util.function.Function;

/** Boundary and control-flow fixtures for the bidirectional graph adapter. */
public class BidiGraphSample {
    public static Object state = new Object();
    public Object f;
    public Object g;
    public BidiGraphSample next;

    public static Object normal(Object value) { return value; }
    public static void infinite(Object value) {
        while (true) { state = value; }
    }
    public static Object split(boolean exits, Object value) {
        if (exits) { return value; }
        while (true) { state = value; }
    }
    public static void throwOnly(RuntimeException failure) { throw failure; }
    public static Object caughtThrow(BidiGraphSample value, RuntimeException failure) {
        try {
            value.f = value.g;
            throw failure;
        } catch (RuntimeException caught) {
            return value.f;
        }
    }
    public static Object callInTry(BidiGraphSample value) {
        try { value.f = target(value.f); }
        catch (RuntimeException caught) { return value.g; }
        return value.f;
    }
    public static Object target(Object value) { return value; }
    public static Object staticRead() { return state; }
    public static Object lambda(Object value) {
        Function<Object, Object> action = argument -> value;
        return action.apply(value);
    }
    public static String concat(String value, Object suffix) { return value + suffix; }
    public static native Object empty(Object value);
}
