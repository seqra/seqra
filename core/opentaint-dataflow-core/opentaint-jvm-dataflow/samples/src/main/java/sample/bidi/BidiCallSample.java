package sample.bidi;

/** Real call sites for the bidirectional call-plan and adapter tests. */
public class BidiCallSample {
    public static Object sField;
    public Object f;
    public Object g;
    public BidiCallSample next;

    public BidiCallSample(Object value) { f = value; }
    public static Object target(Object a, Object b) { return a; }
    public static native Object external(Object a, Object b);
    public static native byte[] externalBytes(Object a);
    public static byte[] bytesCall(Object a) { return externalBytes(a); }
    public Object instance(Object a, Object b) { f = a; return b; }
    public native Object getExternal();

    public static Object staticCall(Object a, Object b) { return target(a, b); }
    public static Object staticExternal(Object a, Object b) { return external(a, b); }
    public static Object instanceCall(BidiCallSample o, Object a, Object b) { return o.instance(a, b); }
    public static Object repeated(Object a) { return target(a, a); }
    public static Object constant(Object a) { return target(a, "literal"); }
    public static Object receiverArgument(BidiCallSample o) { return o.instance(o, o); }
    public static Object selfLhs(Object a) { a = target(a, a); return a; }
    public static BidiCallSample constructor(Object a) { return new BidiCallSample(a); }
    public static void ignored(Object a) { target(a, a); }
    public static Object getter(BidiCallSample o) { return o.getExternal(); }
    public interface Fn { Object apply(Object a); }
    public static Object lambdaImpl(Object a) { return a; }
    public static Object lambdaOther(Object a) { return sField; }
    public static native Object emptyLambda(Object a);
    public static Object lambdaCall(Fn fn, Object a) { return fn.apply(a); }

    public static Object aliasPersisted(BidiCallSample x, Object value) {
        Object a = x.next.f;
        external(value, value);
        return a;
    }
    public static Object aliasOverwritten(BidiCallSample x, Object value) {
        Object a = x.f;
        a = external(a, value);
        return a;
    }
    public static Object aliasBeforeOnly(BidiCallSample x, Object value) {
        Object a = x.f;
        external(x, value);
        return a;
    }
    public static Object aliasAllocation(Object value) {
        Object a = new Object();
        external(value, value);
        return a;
    }
    public static Object aliasConstant(Object value) {
        Object a = "literal";
        external(value, value);
        return a;
    }
    public static Object aliasSelfPath(Object value) {
        BidiCallSample a = new BidiCallSample(value);
        a.next = a;
        external(null, null);
        return a.next;
    }
    public static class Base {
        public Object virtual(Object a) { return a; }
    }
    public static class Derived extends Base {
        @Override public Object virtual(Object a) { return this; }
    }
    public static Object virtualCall(Base receiver, Object value) { return receiver.virtual(value); }
    public static Object virtualAllocated(Object value) {
        Base receiver = new Derived();
        return receiver.virtual(value);
    }
    public static Object virtualAlias(Base receiver, Object value) {
        Base local = receiver;
        return local.virtual(value);
    }
}
