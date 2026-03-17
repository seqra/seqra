package org.opentaint.ir.testing.usages.methods;

public class MethodC extends MethodA {

    @Override
    public void hello() {
        super.hello();
        System.out.println("from MethodC");
    }

    @Override
    public void hello1() {
        System.out.println("from MethodC");
    }
}
