package org.opentaint.jvm.dataflow.approximations.stdlib;

import org.opentaint.ir.approximation.annotation.Approximate;
import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;

import java.util.Comparator;
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

@Approximate(java.util.stream.Stream.class)
public class Stream {

    private static final int GENERATOR_ARRAY_SIZE = 0;

    public java.util.stream.Stream filter(@ArgumentTypeContext Predicate predicate) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            predicate.test(it.next());
        }
        return t;
    }

    public java.util.stream.Stream map(@ArgumentTypeContext Function mapper) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            Object result = mapper.apply(it.next());
            return java.util.stream.Stream.of(result);
        }
        return java.util.stream.Stream.empty();
    }

    public IntStream mapToInt(@ArgumentTypeContext ToIntFunction mapper) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            int result = mapper.applyAsInt(it.next());
            return IntStream.of(result);
        }
        return IntStream.empty();
    }

    public LongStream mapToLong(@ArgumentTypeContext ToLongFunction mapper) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            long result = mapper.applyAsLong(it.next());
            return LongStream.of(result);
        }
        return LongStream.empty();
    }

    public DoubleStream mapToDouble(@ArgumentTypeContext ToDoubleFunction mapper) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            double result = mapper.applyAsDouble(it.next());
            return DoubleStream.of(result);
        }
        return DoubleStream.empty();
    }

    public java.util.stream.Stream flatMap(@ArgumentTypeContext Function mapper) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            return (java.util.stream.Stream) mapper.apply(it.next());
        }
        return java.util.stream.Stream.empty();
    }

    public IntStream flatMapToInt(@ArgumentTypeContext Function mapper) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            return (IntStream) mapper.apply(it.next());
        }
        return IntStream.empty();
    }

    public LongStream flatMapToLong(@ArgumentTypeContext Function mapper) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            return (LongStream) mapper.apply(it.next());
        }
        return LongStream.empty();
    }

    public DoubleStream flatMapToDouble(@ArgumentTypeContext Function mapper) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            return (DoubleStream) mapper.apply(it.next());
        }
        return DoubleStream.empty();
    }

    public java.util.stream.Stream mapMulti(@ArgumentTypeContext BiConsumer mapper) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            mapper.accept(it.next(), (Consumer) result -> {});
        }
        return t;
    }

    public java.util.stream.Stream peek(@ArgumentTypeContext Consumer action) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            action.accept(it.next());
        }
        return t;
    }

    public java.util.stream.Stream sorted(@ArgumentTypeContext Comparator comparator) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            Object e = it.next();
            comparator.compare(e, e);
        }
        return t;
    }

    public java.util.stream.Stream takeWhile(@ArgumentTypeContext Predicate predicate) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            predicate.test(it.next());
        }
        return t;
    }

    public java.util.stream.Stream dropWhile(@ArgumentTypeContext Predicate predicate) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            predicate.test(it.next());
        }
        return t;
    }

    public void forEach(@ArgumentTypeContext Consumer action) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            action.accept(it.next());
        }
    }

    public void forEachOrdered(@ArgumentTypeContext Consumer action) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            action.accept(it.next());
        }
    }

    public Object[] toArray(@ArgumentTypeContext IntFunction generator) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        return (Object[]) generator.apply(GENERATOR_ARRAY_SIZE);
    }

    public Object reduce(Object identity, @ArgumentTypeContext BinaryOperator accumulator) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            return accumulator.apply(identity, it.next());
        }
        return identity;
    }

    public java.util.Optional reduce(@ArgumentTypeContext BinaryOperator accumulator) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            Object e = it.next();
            Object result = accumulator.apply(e, e);
            return java.util.Optional.ofNullable(result);
        }
        return java.util.Optional.empty();
    }

    public Object reduce(Object identity, @ArgumentTypeContext BiFunction accumulator, @ArgumentTypeContext BinaryOperator combiner) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            Object partial = accumulator.apply(identity, it.next());
            return combiner.apply(partial, partial);
        }
        return identity;
    }

    public Object collect(@ArgumentTypeContext Supplier supplier, @ArgumentTypeContext BiConsumer accumulator, @ArgumentTypeContext BiConsumer combiner) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Object container = supplier.get();
        Iterator it = t.iterator();
        if (it.hasNext()) {
            accumulator.accept(container, it.next());
            combiner.accept(container, container);
        }
        return container;
    }

    public java.util.Optional min(@ArgumentTypeContext Comparator comparator) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            Object e = it.next();
            comparator.compare(e, e);
            return java.util.Optional.of(e);
        }
        return java.util.Optional.empty();
    }

    public java.util.Optional max(@ArgumentTypeContext Comparator comparator) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            Object e = it.next();
            comparator.compare(e, e);
            return java.util.Optional.of(e);
        }
        return java.util.Optional.empty();
    }

    public boolean anyMatch(@ArgumentTypeContext Predicate predicate) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            return predicate.test(it.next());
        }
        return false;
    }

    public boolean allMatch(@ArgumentTypeContext Predicate predicate) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            return predicate.test(it.next());
        }
        return true;
    }

    public boolean noneMatch(@ArgumentTypeContext Predicate predicate) {
        java.util.stream.Stream t = (java.util.stream.Stream) (Object) this;
        Iterator it = t.iterator();
        if (it.hasNext()) {
            return !predicate.test(it.next());
        }
        return true;
    }
}
