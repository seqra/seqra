package test.samples;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AsyncDataFlowSample {

    public void threadRunnableFlow() {
        String data = source();
        Runnable task = new Runnable() {
            @Override
            public void run() {
                sink(data);
            }
        };
        Thread thread = new Thread(task);
        thread.start();
    }

    public void threadLambdaFlow() {
        String data = source();
        Thread thread = new Thread(() -> sink(data));
        thread.start();
    }

    public void callableFutureFlow() throws Exception {
        String data = source();
        Callable<String> callable = () -> data;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(callable);
        String result = future.get();
        sink(result);
    }

    public void completableFutureSupplyFlow() throws Exception {
        String data = source();
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> data);
        String result = future.get();
        sink(result);
    }

    public void completableFutureThenApplyFlow() throws Exception {
        String data = source();
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> data)
                .thenApply(s -> s.trim());
        String result = future.get();
        sink(result);
    }

    public void completableFutureThenAcceptFlow() {
        String data = source();
        CompletableFuture.supplyAsync(() -> data)
                .thenAccept(s -> sink(s));
    }

    public void executorSubmitRunnableFlow() {
        String data = source();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> sink(data));
    }

    public String source() { return "tainted"; }
    public void sink(String data) { }
}
