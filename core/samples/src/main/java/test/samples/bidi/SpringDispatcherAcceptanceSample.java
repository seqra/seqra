package test.samples.bidi;

/** Whole dispatcher fixture; the test creates its real generated wrapper and registry. */
public class SpringDispatcherAcceptanceSample {
    public static Object outside;
    public static native Object source();
    public static void directSink(Object value) {}
    public static void insideStaticSink(Object value) {}
    public static void registrySink(Object value) {}
    public static void outsideSink(Object value) {}

    public static class Component {
        public Object value;
        public Component() { value = source(); }

        public void store(Object dto) {
            outside = dto;
            directSink(dto);
            insideStaticSink(outside);
        }

        public void read() {
            registrySink(value);
            outsideSink(outside);
        }
    }
}
