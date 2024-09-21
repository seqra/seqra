package org.opentaint.ir.impl.usages.fields;


public class FakeFieldA {

    public int a;
    public int b;
    private final FieldB fieldB = new FieldB(a);

    public FakeFieldA(int a, int b) {
        this.a = a;
        this.b = b;
    }

    public Boolean isPositive() {
        System.out.println(b);
        return a >= 0;
    }

    public void useA(int a) {
        System.out.println(b);
        this.a = a;
    }

    private void useCPrivate(int c) {
        System.out.println(a);
        fieldB.c = c;
    }

}
