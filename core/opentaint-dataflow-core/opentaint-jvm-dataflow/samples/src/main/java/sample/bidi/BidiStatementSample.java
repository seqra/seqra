package sample.bidi;

/** Separate fixtures for the bidirectional statement forms. */
public class BidiStatementSample {
    public static Object sField;
    public Object f;
    public Object g;
    public BidiStatementSample next;

    public static class Parent { public Object inherited; }
    public static class Child extends Parent { }

    public Object copy(Object y) { Object x = y; return x; }
    public Object constant() { Object x = "constant"; return x; }
    public Object nullConstant() { Object x = null; return x; }
    public String cast(Object y) { return (String) y; }
    public Object fieldRead(BidiStatementSample y) { return y.f; }
    public void fieldWrite(BidiStatementSample y, Object x) { y.f = x; }
    public void fieldToField(BidiStatementSample y, BidiStatementSample x) { y.f = x.g; }
    public Object staticRead() { return sField; }
    public void staticWrite(Object x) { sField = x; }
    public Object arrayRead(Object[] y) { return y[0]; }
    public void arrayWrite(Object[] y, Object x) { y[0] = x; }
    public byte byteRead(byte[] y) { return y[0]; }
    public void byteWrite(byte[] y, byte x) { y[0] = x; }
    public byte copyByte(byte y) { byte x = y; return x; }
    public Byte copyBoxed(Byte y) { Byte x = y; return x; }
    public void selfWrite(BidiStatementSample a) { a.f = a; }
    public BidiStatementSample selfRead(BidiStatementSample x) { x = x.next; return x; }
    public Object inheritedRead(Child y) { return y.inherited; }
    public void inheritedWrite(Child y, Object x) { y.inherited = x; }
    public int binary(int a, int b) { return a + b; }
    public int selfBinary(int a, int b) { a = a + b; return a; }
    public Object newObject() { return new Object(); }
    public Object[] newArray(int size) { return new Object[size]; }
    public Object[][] newMultiArray(int size) { return new Object[size][size]; }
    public boolean instanceOf(Object y) { return y instanceof String; }
    public int negate(int y) { return -y; }
    public int arrayLength(byte[] y) { return y.length; }
    public Object phi(boolean choose, Object a, Object b) {
        Object x;
        if (choose) x = a; else x = b;
        return x;
    }
    public void voidReturn() { return; }
    public void throwValue(RuntimeException t) { throw t; }
    public Object callAssign(Object y) { Object x = y.toString(); return x; }
    public void callOnly(Object y) { y.toString(); }
    public void unchanged(Object y) { synchronized (y) { } }
}
