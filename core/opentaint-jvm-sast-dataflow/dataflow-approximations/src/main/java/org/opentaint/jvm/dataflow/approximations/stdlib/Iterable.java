package org.opentaint.jvm.dataflow.approximations.stdlib;

import org.opentaint.ir.approximation.annotation.Approximate;
import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;

import java.util.Iterator;
import java.util.function.Consumer;

@Approximate(java.lang.Iterable.class)
public class Iterable {

    public void forEach(@ArgumentTypeContext Consumer action) {
        java.lang.Iterable t = (java.lang.Iterable) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            action.accept(it.next());
        }
    }
}
