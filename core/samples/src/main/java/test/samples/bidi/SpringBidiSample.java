package test.samples.bidi;

/** No Spring annotations: tests invoke the real generator directly. */
public class SpringBidiSample {
    public static Object outside;
    public static void first() {}
    public static void second() {}
    public static class ZComponent { public Object value; public ZComponent() {} }
    public static class AComponent { public Object value; public AComponent() {} }
}
