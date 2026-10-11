package sample.bidi;

import java.util.function.Function;

/** Prescan extraction fixture: two real implementations reach one shared call site. */
public class BidiPrescanSample {
    public Object value;
    public static Function<Object, Object> first() { return x -> x; }
    public static Function<Object, Object> second() { return x -> "second"; }
    public static Object invoke(Function<Object, Object> fn, Object value) { return fn.apply(value); }
    public static Object invokeOther(Function<Object, Object> fn, Object value) { return fn.apply(value); }
    public static Object twoSites(Function<Object, Object> fn, Object value) {
        Object first = fn.apply(value);
        return fn.apply(first);
    }
    public static Object rootFirst(Object value) { return invoke(first(), value); }
    public static Object rootSecond(Object value) { return invoke(second(), value); }
    public static Object normal(Object value) { return value; }
    public static Object alias(BidiPrescanSample value) {
        Object local = value.value;
        return normal(local);
    }
    public static Object ruleCalls(Object value) { return target(value); }
    public static Object target(Object value) { return value; }
}
