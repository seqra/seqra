package org.opentaint.jvm.dataflow.approximations.stdlib;

import org.opentaint.ir.approximation.annotation.Approximate;
import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;
import org.opentaint.jvm.dataflow.approximations.OpentaintNdUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

@Approximate(java.util.concurrent.CompletionStage.class)
public class CompletionStage {

    public java.util.concurrent.CompletionStage thenApply(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        Object result = fn.apply(cf.get());
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage thenApplyAsync(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        Object result = fn.apply(cf.get());
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage thenApplyAsync(@ArgumentTypeContext Function fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        Object result = fn.apply(cf.get());
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage thenAccept(@ArgumentTypeContext Consumer action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        action.accept(cf.get());
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage thenAcceptAsync(@ArgumentTypeContext Consumer action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        action.accept(cf.get());
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage thenAcceptAsync(@ArgumentTypeContext Consumer action, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        action.accept(cf.get());
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage thenRun(@ArgumentTypeContext Runnable action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage thenRunAsync(@ArgumentTypeContext Runnable action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage thenRunAsync(@ArgumentTypeContext Runnable action, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage thenCombine(java.util.concurrent.CompletionStage other, @ArgumentTypeContext BiFunction fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        CompletableFuture otherCf = (CompletableFuture) other;
        Object result = fn.apply(cf.get(), otherCf.get());
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage thenCombineAsync(java.util.concurrent.CompletionStage other, @ArgumentTypeContext BiFunction fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        CompletableFuture otherCf = (CompletableFuture) other;
        Object result = fn.apply(cf.get(), otherCf.get());
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage thenCombineAsync(java.util.concurrent.CompletionStage other, @ArgumentTypeContext BiFunction fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        CompletableFuture otherCf = (CompletableFuture) other;
        Object result = fn.apply(cf.get(), otherCf.get());
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage thenAcceptBoth(java.util.concurrent.CompletionStage other, @ArgumentTypeContext BiConsumer action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        CompletableFuture otherCf = (CompletableFuture) other;
        action.accept(cf.get(), otherCf.get());
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage thenAcceptBothAsync(java.util.concurrent.CompletionStage other, @ArgumentTypeContext BiConsumer action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        CompletableFuture otherCf = (CompletableFuture) other;
        action.accept(cf.get(), otherCf.get());
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage thenAcceptBothAsync(java.util.concurrent.CompletionStage other, @ArgumentTypeContext BiConsumer action, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        CompletableFuture otherCf = (CompletableFuture) other;
        action.accept(cf.get(), otherCf.get());
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage runAfterBoth(java.util.concurrent.CompletionStage other, @ArgumentTypeContext Runnable action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage runAfterBothAsync(java.util.concurrent.CompletionStage other, @ArgumentTypeContext Runnable action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage runAfterBothAsync(java.util.concurrent.CompletionStage other, @ArgumentTypeContext Runnable action, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage applyToEither(java.util.concurrent.CompletionStage other, @ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        Object result = fn.apply(cf.get());
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage applyToEitherAsync(java.util.concurrent.CompletionStage other, @ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        Object result = fn.apply(cf.get());
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage applyToEitherAsync(java.util.concurrent.CompletionStage other, @ArgumentTypeContext Function fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        Object result = fn.apply(cf.get());
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage acceptEither(java.util.concurrent.CompletionStage other, @ArgumentTypeContext Consumer action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        action.accept(cf.get());
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage acceptEitherAsync(java.util.concurrent.CompletionStage other, @ArgumentTypeContext Consumer action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        action.accept(cf.get());
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage acceptEitherAsync(java.util.concurrent.CompletionStage other, @ArgumentTypeContext Consumer action, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        action.accept(cf.get());
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage runAfterEither(java.util.concurrent.CompletionStage other, @ArgumentTypeContext Runnable action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage runAfterEitherAsync(java.util.concurrent.CompletionStage other, @ArgumentTypeContext Runnable action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage runAfterEitherAsync(java.util.concurrent.CompletionStage other, @ArgumentTypeContext Runnable action, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        action.run();
        return CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletionStage thenCompose(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        return (java.util.concurrent.CompletionStage) fn.apply(cf.get());
    }

    public java.util.concurrent.CompletionStage thenComposeAsync(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        return (java.util.concurrent.CompletionStage) fn.apply(cf.get());
    }

    public java.util.concurrent.CompletionStage thenComposeAsync(@ArgumentTypeContext Function fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        return (java.util.concurrent.CompletionStage) fn.apply(cf.get());
    }

    public java.util.concurrent.CompletionStage handle(@ArgumentTypeContext BiFunction fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        Object result = fn.apply(cf.get(), null);
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage handleAsync(@ArgumentTypeContext BiFunction fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        Object result = fn.apply(cf.get(), null);
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage handleAsync(@ArgumentTypeContext BiFunction fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        Object result = fn.apply(cf.get(), null);
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage whenComplete(@ArgumentTypeContext BiConsumer action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        action.accept(cf.get(), null);
        return t;
    }

    public java.util.concurrent.CompletionStage whenCompleteAsync(@ArgumentTypeContext BiConsumer action) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        action.accept(cf.get(), null);
        return t;
    }

    public java.util.concurrent.CompletionStage whenCompleteAsync(@ArgumentTypeContext BiConsumer action, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        CompletableFuture cf = (CompletableFuture) t;
        action.accept(cf.get(), null);
        return t;
    }

    public java.util.concurrent.CompletionStage exceptionally(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return t;
        Object result = fn.apply(null);
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage exceptionallyAsync(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return t;
        Object result = fn.apply(null);
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage exceptionallyAsync(@ArgumentTypeContext Function fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return t;
        Object result = fn.apply(null);
        return CompletableFuture.completedFuture(result);
    }

    public java.util.concurrent.CompletionStage exceptionallyCompose(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return t;
        return (java.util.concurrent.CompletionStage) fn.apply(null);
    }

    public java.util.concurrent.CompletionStage exceptionallyComposeAsync(@ArgumentTypeContext Function fn) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return t;
        return (java.util.concurrent.CompletionStage) fn.apply(null);
    }

    public java.util.concurrent.CompletionStage exceptionallyComposeAsync(@ArgumentTypeContext Function fn, Executor executor) throws Throwable {
        java.util.concurrent.CompletionStage t = (java.util.concurrent.CompletionStage) (Object) this;
        if (OpentaintNdUtil.nextBool()) return t;
        return (java.util.concurrent.CompletionStage) fn.apply(null);
    }
}
