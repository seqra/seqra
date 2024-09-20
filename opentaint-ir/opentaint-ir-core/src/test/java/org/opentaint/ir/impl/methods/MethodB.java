package org.opentaint.ir.impl.methods;

public class MethodB {

    private void hoho() {
        new MethodA().hello();
        new MethodC().hello();
        new MethodC().hello1();
    }

}
