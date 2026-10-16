package sample.bidi;

/** Declaring owners and program-only static needs for prescan metadata tests. */
public class BidiStaticMetadataSample {
    public static String aliasRule() { Object local = Parent.inherited; return local.toString(); }
    public enum Processes { WEASYPRINT }
    public static Object nestedAlias() { Object local = Processes.WEASYPRINT; touch(local); return local; }
    public static Object inheritedAlias() { Object local = Child.inherited; touch(local); return local; }
    public static void touch(Object value) { }
    public static class Box { public Object field; }
    public static class Boxes { public static Box box; }
    public static void aliasWrite(Object value) { Box local = Boxes.box; local.field = value; }
    public static class Parent { public static Object inherited; }
    public static class Child extends Parent {
        public static Object read() { return inherited; }
        public static void writeConstant() { inherited = null; }
        public static void write(Object value) { inherited = value; }
    }
}
