package sample.bidi;

/** Inherited dispatch targets for hierarchy-root-sensitive resolver cache tests. */
public class BidiCatalogResolverSample {
    public static class Base {
        public String getModel() { return "base"; }
    }

    /** Inherits the SAME Base method as a broad receiver, but has a smaller override subtree. */
    public static class Narrow extends Base { }
    public static class NarrowLeaf extends Narrow {
        @Override public String getModel() { return "narrow"; }
    }
    public static class Sibling extends Base {
        @Override public String getModel() { return "sibling"; }
    }
    public static class EmptyLeaf extends Base { }

    public static String broad(Base receiver) { return receiver.getModel(); }
    public static String narrow(Narrow receiver) { return receiver.getModel(); }
    public static String empty(EmptyLeaf receiver) { return receiver.getModel(); }
}
