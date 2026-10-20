package sample.bidi;

/** Metadata and real alias paths for the selected enum alias-target hack. */
public enum BidiEnumAliasSample {
    A { @Override public String toString() { return "A"; } },
    B;

    public static final Object BY_PROTO = new Object();
    public static BidiEnumAliasSample selected = A;
    public Object payload;

    public static void aliasFieldWrite(Object value) {
        BidiEnumAliasSample local = A;
        local.payload = value;
    }

    public static Object directRead() { return A; }
    public static void directWrite(BidiEnumAliasSample value) { selected = value; }
}
