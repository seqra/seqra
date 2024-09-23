package org.opentaint.ir.impl.usages;

import java.util.Collection;
import java.util.List;

public class A<T extends Collection<?>> {

    public <T extends Collection<?>> void merge(A<T> a){

    }

    public static void main(String[] args) {
        new A<List<String>>();
    }
}