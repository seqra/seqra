package test.samples.bidi;

/** Real JVM static effects; the acceptance test supplies source and sink rules. */
public class Run1LocalStaticWriterSample {
    public static Object input;
    public static Object output;

    public static Object source() { return new Object(); }
    public static void sink(Object value) { }

    public static void set(Object value) { output = value; }
    public static void setWrap(Object value) { set(value); }
    public static void setDeep(Object value) { setWrap(value); }
    public static Object get() { return output; }
    public static Object getWrap() { return get(); }
    public static void consume() { sink(getWrap()); }
    public static void consumeWrap() { consume(); }
    public static void consumeDeep() { consumeWrap(); }

    public static void sourceArgument() {
        setDeep(source());
        consumeDeep();
    }

    public static void staticArgument() {
        input = source();
        setDeep(input);
        consumeDeep();
    }

    public static void firstCaller(Object value) { setDeep(value); }
    public static void secondCaller(Object value) { setDeep(value); }
    public static void multipleCallers() {
        Object value = source();
        firstCaller(value);
        secondCaller(value);
        consumeDeep();
    }

    /** Its configured conditional source writes output; there is no IR store. */
    public static void modelStatic(Object token) { }
    public static void conditionalAction() {
        modelStatic(source());
        consumeDeep();
    }

    public static void noSource() {
        setDeep(new Object());
        consumeDeep();
    }

    public static void incompleteCondition() {
        modelStatic(new Object());
        consumeDeep();
    }
}
