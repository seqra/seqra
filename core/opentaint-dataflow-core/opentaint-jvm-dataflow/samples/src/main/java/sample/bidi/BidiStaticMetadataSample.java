package sample.bidi;

/** Declaring owners and program-only static needs for prescan metadata tests. */
public class BidiStaticMetadataSample {
    public static String aliasRule() { Object local = Parent.inherited; return local.toString(); }
    public static class Parent { public static Object inherited; }
    public static class Child extends Parent {
        public static Object read() { return inherited; }
        public static void writeConstant() { inherited = null; }
        public static void write(Object value) { inherited = value; }
    }
}
