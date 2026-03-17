package org.opentaint.jvm.dataflow.approximations.stdlib;

import org.opentaint.ir.approximation.annotation.Approximate;
import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Approximate(java.util.concurrent.ExecutorService.class)
public class ExecutorService {

    public void execute(@ArgumentTypeContext Runnable command) {
        command.run();
    }

    public Future submit(@ArgumentTypeContext Callable task) throws Exception {
        Object result = task.call();
        return CompletableFuture.completedFuture(result);
    }

    public Future submit(@ArgumentTypeContext Runnable task, Object result) {
        task.run();
        return CompletableFuture.completedFuture(result);
    }

    public Future submit(@ArgumentTypeContext Runnable task) {
        task.run();
        return CompletableFuture.completedFuture(null);
    }

    public List invokeAll(Collection tasks) throws Exception {
        List futures = new ArrayList();
        Iterator it = tasks.iterator();
        while (it.hasNext()) {
            Callable task = (Callable) it.next();
            Object result = task.call();
            futures.add(CompletableFuture.completedFuture(result));
        }
        return futures;
    }

    public List invokeAll(Collection tasks, long timeout, TimeUnit unit) throws Exception {
        List futures = new ArrayList();
        Iterator it = tasks.iterator();
        while (it.hasNext()) {
            Callable task = (Callable) it.next();
            Object result = task.call();
            futures.add(CompletableFuture.completedFuture(result));
        }
        return futures;
    }

    public Object invokeAny(Collection tasks) throws Exception {
        Iterator it = tasks.iterator();
        if (it.hasNext()) {
            Callable task = (Callable) it.next();
            return task.call();
        }
        return null;
    }

    public Object invokeAny(Collection tasks, long timeout, TimeUnit unit) throws Exception {
        Iterator it = tasks.iterator();
        if (it.hasNext()) {
            Callable task = (Callable) it.next();
            return task.call();
        }
        return null;
    }
}
