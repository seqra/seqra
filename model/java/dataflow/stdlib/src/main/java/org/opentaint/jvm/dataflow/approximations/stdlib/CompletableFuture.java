package org.opentaint.jvm.dataflow.approximations.stdlib;

import org.opentaint.ir.approximation.annotation.Approximate;
import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;
import org.opentaint.jvm.dataflow.approximations.OpentaintNdUtil;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Approximate(java.util.concurrent.CompletableFuture.class)
public class CompletableFuture {

    public static java.util.concurrent.CompletableFuture supplyAsync(@ArgumentTypeContext Supplier supplier) {
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = supplier.get();
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public static java.util.concurrent.CompletableFuture supplyAsync(@ArgumentTypeContext Supplier supplier, Executor executor) {
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = supplier.get();
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public static java.util.concurrent.CompletableFuture runAsync(@ArgumentTypeContext Runnable runnable) {
        if (OpentaintNdUtil.nextBool()) return null;
        runnable.run();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public static java.util.concurrent.CompletableFuture runAsync(@ArgumentTypeContext Runnable runnable, Executor executor) {
        if (OpentaintNdUtil.nextBool()) return null;
        runnable.run();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture completeAsync(@ArgumentTypeContext Supplier supplier) {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = supplier.get();
        t.complete(result);
        return t;
    }

    public java.util.concurrent.CompletableFuture completeAsync(@ArgumentTypeContext Supplier supplier, Executor executor) {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = supplier.get();
        t.complete(result);
        return t;
    }

    public java.util.concurrent.CompletableFuture thenApply(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = fn.apply(t.get());
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture thenApplyAsync(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = fn.apply(t.get());
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture thenApplyAsync(@ArgumentTypeContext Function fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = fn.apply(t.get());
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture thenAccept(@ArgumentTypeContext Consumer action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.accept(t.get());
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture thenAcceptAsync(@ArgumentTypeContext Consumer action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.accept(t.get());
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture thenAcceptAsync(@ArgumentTypeContext Consumer action, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.accept(t.get());
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture thenRun(@ArgumentTypeContext Runnable action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture thenRunAsync(@ArgumentTypeContext Runnable action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture thenRunAsync(@ArgumentTypeContext Runnable action, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture thenCombine(CompletionStage other, @ArgumentTypeContext BiFunction fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        java.util.concurrent.CompletableFuture otherCf = (java.util.concurrent.CompletableFuture) other;
        Object result = fn.apply(t.get(), otherCf.get());
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture thenCombineAsync(CompletionStage other, @ArgumentTypeContext BiFunction fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        java.util.concurrent.CompletableFuture otherCf = (java.util.concurrent.CompletableFuture) other;
        Object result = fn.apply(t.get(), otherCf.get());
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture thenCombineAsync(CompletionStage other, @ArgumentTypeContext BiFunction fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        java.util.concurrent.CompletableFuture otherCf = (java.util.concurrent.CompletableFuture) other;
        Object result = fn.apply(t.get(), otherCf.get());
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture thenAcceptBoth(CompletionStage other, @ArgumentTypeContext BiConsumer action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        java.util.concurrent.CompletableFuture otherCf = (java.util.concurrent.CompletableFuture) other;
        action.accept(t.get(), otherCf.get());
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture thenAcceptBothAsync(CompletionStage other, @ArgumentTypeContext BiConsumer action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        java.util.concurrent.CompletableFuture otherCf = (java.util.concurrent.CompletableFuture) other;
        action.accept(t.get(), otherCf.get());
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture thenAcceptBothAsync(CompletionStage other, @ArgumentTypeContext BiConsumer action, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        java.util.concurrent.CompletableFuture otherCf = (java.util.concurrent.CompletableFuture) other;
        action.accept(t.get(), otherCf.get());
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture runAfterBoth(CompletionStage other, @ArgumentTypeContext Runnable action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture runAfterBothAsync(CompletionStage other, @ArgumentTypeContext Runnable action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture runAfterBothAsync(CompletionStage other, @ArgumentTypeContext Runnable action, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture applyToEither(CompletionStage other, @ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = fn.apply(t.get());
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture applyToEitherAsync(CompletionStage other, @ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = fn.apply(t.get());
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture applyToEitherAsync(CompletionStage other, @ArgumentTypeContext Function fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = fn.apply(t.get());
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture acceptEither(CompletionStage other, @ArgumentTypeContext Consumer action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.accept(t.get());
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture acceptEitherAsync(CompletionStage other, @ArgumentTypeContext Consumer action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.accept(t.get());
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture acceptEitherAsync(CompletionStage other, @ArgumentTypeContext Consumer action, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.accept(t.get());
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture runAfterEither(CompletionStage other, @ArgumentTypeContext Runnable action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture runAfterEitherAsync(CompletionStage other, @ArgumentTypeContext Runnable action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture runAfterEitherAsync(CompletionStage other, @ArgumentTypeContext Runnable action, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture thenCompose(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        return (java.util.concurrent.CompletableFuture) fn.apply(t.get());
    }

    public java.util.concurrent.CompletableFuture thenComposeAsync(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        return (java.util.concurrent.CompletableFuture) fn.apply(t.get());
    }

    public java.util.concurrent.CompletableFuture thenComposeAsync(@ArgumentTypeContext Function fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        return (java.util.concurrent.CompletableFuture) fn.apply(t.get());
    }

    public java.util.concurrent.CompletableFuture handle(@ArgumentTypeContext BiFunction fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = fn.apply(t.get(), null);
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture handleAsync(@ArgumentTypeContext BiFunction fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = fn.apply(t.get(), null);
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture handleAsync(@ArgumentTypeContext BiFunction fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = fn.apply(t.get(), null);
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture whenComplete(@ArgumentTypeContext BiConsumer action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.accept(t.get(), null);
        return t;
    }

    public java.util.concurrent.CompletableFuture whenCompleteAsync(@ArgumentTypeContext BiConsumer action) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.accept(t.get(), null);
        return t;
    }

    public java.util.concurrent.CompletableFuture whenCompleteAsync(@ArgumentTypeContext BiConsumer action, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.accept(t.get(), null);
        return t;
    }

    public java.util.concurrent.CompletableFuture exceptionally(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return t;
        Object result = fn.apply(null);
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture exceptionallyAsync(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return t;
        Object result = fn.apply(null);
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture exceptionallyAsync(@ArgumentTypeContext Function fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return t;
        Object result = fn.apply(null);
        return java.util.concurrent.CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletableFuture exceptionallyCompose(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return t;
        return (java.util.concurrent.CompletableFuture) fn.apply(null);
    }

    public java.util.concurrent.CompletableFuture exceptionallyComposeAsync(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return t;
        return (java.util.concurrent.CompletableFuture) fn.apply(null);
    }

    public java.util.concurrent.CompletableFuture exceptionallyComposeAsync(@ArgumentTypeContext Function fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletableFuture t = (java.util.concurrent.CompletableFuture) (Object) this;
        if (OpentaintNdUtil.nextBool()) return t;
        return (java.util.concurrent.CompletableFuture) fn.apply(null);
    }
}
