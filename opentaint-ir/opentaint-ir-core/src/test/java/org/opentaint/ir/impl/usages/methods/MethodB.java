package org.opentaint.ir.impl.usages.methods;

public class MethodB {

    private void hoho() {
        new MethodA().hello();
        new MethodC().hello();
        new MethodC().hello1();
    }

}
