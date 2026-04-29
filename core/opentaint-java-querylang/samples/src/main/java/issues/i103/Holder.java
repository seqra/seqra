package issues.i103;

public class Holder {
    public static Object src() { return "tainted"; }
    public static void sink(Object x) {}
}
