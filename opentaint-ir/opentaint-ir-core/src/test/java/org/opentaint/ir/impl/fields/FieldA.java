package org.opentaint.ir.impl.fields;

public class FieldA {

    public int a;
    public int b;
    private final FieldB fieldB = new FieldB(a);

    public FieldA(int a, int b) {
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


class FieldAImpl extends FieldA {

    public FieldAImpl() {
        super(1, 1);
    }

    void hello() {
        System.out.println(a);
    }
}


