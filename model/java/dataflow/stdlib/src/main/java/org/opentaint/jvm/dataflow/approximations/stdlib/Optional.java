package org.opentaint.jvm.dataflow.approximations.stdlib;

import org.opentaint.ir.approximation.annotation.Approximate;
import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Approximate(java.util.Optional.class)
public class Optional {
    public void ifPresent(@ArgumentTypeContext Consumer action) {
        java.util.Optional t = (java.util.Optional) (Object) this;
        if (t.isPresent()) {
            action.accept(t.get());
        }
    }

    public void ifPresentOrElse(@ArgumentTypeContext Consumer action, @ArgumentTypeContext Runnable emptyAction) {
        java.util.Optional t = (java.util.Optional) (Object) this;
        if (t.isPresent()) {
            action.accept(t.get());
        } else {
            emptyAction.run();
        }
    }

    public java.util.Optional filter(@ArgumentTypeContext Predicate predicate) {
        java.util.Optional t = (java.util.Optional) (Object) this;
        if (t.isPresent()) {
            predicate.test(t.get());
        }
        return t;
    }

    public java.util.Optional map(@ArgumentTypeContext Function mapper) {
        java.util.Optional t = (java.util.Optional) (Object) this;
        if (t.isPresent()) {
            Object result = mapper.apply(t.get());
            return java.util.Optional.ofNullable(result);
        }
        return java.util.Optional.empty();
    }

    public java.util.Optional flatMap(@ArgumentTypeContext Function mapper) {
        java.util.Optional t = (java.util.Optional) (Object) this;
        if (t.isPresent()) {
            return (java.util.Optional) mapper.apply(t.get());
        }
        return java.util.Optional.empty();
    }

    public java.util.Optional or(@ArgumentTypeContext Supplier supplier) {
        java.util.Optional t = (java.util.Optional) (Object) this;
        if (t.isPresent()) {
            return t;
        } else {
            return (java.util.Optional) supplier.get();
        }
    }

    public Object orElseGet(@ArgumentTypeContext Supplier supplier) {
        java.util.Optional t = (java.util.Optional) (Object) this;
        if (t.isPresent()) {
            return t.get();
        } else {
            return supplier.get();
        }
    }

    public Object orElseThrow(@ArgumentTypeContext Supplier exceptionSupplier) throws Throwable {
        java.util.Optional t = (java.util.Optional) (Object) this;
        if (t.isPresent()) {
            return t.get();
        } else {
            throw (Throwable) exceptionSupplier.get();
        }
    }
}
