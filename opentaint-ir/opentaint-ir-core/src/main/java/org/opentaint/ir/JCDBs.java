package org.opentaint.ir;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import org.jetbrains.annotations.NotNull;
import org.opentaint.ir.api.JIRDB;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class JIRDBs {

    /**
     * create {@link JIRDB} instance with default settings
     *
     @return not null {@link JIRDB} instance or throws {@link IllegalStateException}
     */
    public static JIRDB newDefault() {
        return build(new JIRDBSettings());
    }

    /**
     * create instance based on settings
     *
     * @param settings jirdb settings
     * @return not null {@link JIRDB} instance or throws {@link IllegalStateException}
     */
    public static JIRDB newFrom(JIRDBSettings settings) {
        return build(settings);
    }

    private static JIRDB build(JIRDBSettings settings) {
        AtomicReference<Object> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        JirdbKt.jirdb(settings, new Continuation<JIRDB>() {

            @Override
            public void resumeWith(@NotNull Object out) {
                result.compareAndSet(null, out);
                latch.countDown();
            }

            @NotNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }
        });
        try {
            latch.await();
        } catch (Exception ignored) {
        }
        Object r = result.get();
        if (r instanceof JIRDB) {
            return (JIRDB) r;
        } else if (r instanceof Throwable) {
            throw new IllegalStateException("Can't create jirdb instance", (Throwable) r);
        }
        throw new IllegalStateException("Can't create jirdb instance. Received result: " + r);
    }

}
