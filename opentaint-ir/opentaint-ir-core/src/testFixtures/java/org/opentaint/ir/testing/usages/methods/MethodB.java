package org.opentaint.ir.testing.usages.methods;

public class MethodB {

    private void hoho() {
        new MethodA().hello();
        new MethodC().hello();
        new MethodC().hello1();
    }

}
