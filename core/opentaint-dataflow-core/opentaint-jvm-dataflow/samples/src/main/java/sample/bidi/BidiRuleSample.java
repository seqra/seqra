package sample.bidi;

/** Real declared types for bidirectional rule positions and field-value temporaries. */
public class BidiRuleSample {
    public static Object sField;
    public static String staticText;
    public Object f;
    public Object g;
    public BidiRuleSample next;
    public Holder holder;
    public int primitive;
    public String text;
    public byte[] bytes;

    public static class Holder {
        public Object value;
        public int primitive;
        public String text;
    }

    public Object plain(BidiRuleSample x) { return x.f; }
    public Object nested(BidiRuleSample x) { return x.next.f; }
    public Object nestedOwner(BidiRuleSample x) { return x.holder.value; }
    public int primitiveField(BidiRuleSample x) { return x.primitive; }
    public String stringField(BidiRuleSample x) { return x.text; }
    public String stringArg(String x) { return x; }
    public int primitiveArg(int x) { return x; }
    public Object arrayArg(Object[] x) { return x[0]; }
    public Integer boxedArg(Integer x) { return x; }
    public Object staticRead() { return sField; }
}
