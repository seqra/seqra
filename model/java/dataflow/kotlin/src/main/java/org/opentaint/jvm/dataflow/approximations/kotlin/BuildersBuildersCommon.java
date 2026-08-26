package org.opentaint.jvm.dataflow.approximations.kotlin;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.CompletableDeferred;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.Job;
import org.opentaint.ir.approximation.annotation.ApproximateByName;
import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;
import org.opentaint.jvm.dataflow.approximations.OpentaintNdUtil;

@ApproximateByName("kotlinx.coroutines.BuildersKt__Builders_commonKt")
public class BuildersBuildersCommon {

    public static final Job launch(CoroutineScope scope, CoroutineContext ctx, CoroutineStart start, @ArgumentTypeContext Function2<? super CoroutineScope, ? super Continuation<? super Unit>, ? extends Object> body) {
        if (OpentaintNdUtil.nextBool()) return null;
        body.invoke(scope, null);
        return null;
    }

    public static Job launch$default(CoroutineScope scope, CoroutineContext ctx, CoroutineStart start, @ArgumentTypeContext Function2 body, int flags, Object unused) {
        if (OpentaintNdUtil.nextBool()) return null;
        body.invoke(scope, null);
        return null;
    }

    public static final <T> Deferred<T> async(CoroutineScope scope, CoroutineContext ctx, CoroutineStart start, @ArgumentTypeContext Function2<? super CoroutineScope, ? super Continuation<? super T>, ? extends Object> body) {
        if (OpentaintNdUtil.nextBool()) return null;
        T result = (T) body.invoke(scope, null);
        CompletableDeferred<T> resultDef = newCompletableDeferred();
        resultDef.complete(result);
        return resultDef;
    }

    public static Deferred async$default(CoroutineScope scope, CoroutineContext ctx, CoroutineStart start, @ArgumentTypeContext Function2 body, int flags, Object unused) {
        if (OpentaintNdUtil.nextBool()) return null;
        Object result = body.invoke(scope, null);
        CompletableDeferred resultDef = newCompletableDeferred();
        resultDef.complete(result);
        return resultDef;
    }

    public static final <T> Object withContext(CoroutineContext ctx, @ArgumentTypeContext Function2<? super CoroutineScope, ? super Continuation<? super T>, ? extends Object> body, Continuation<? super T> continuation) {
        if (OpentaintNdUtil.nextBool()) return null;
        return body.invoke(null, continuation);
    }

    public static final <T> Object invoke(CoroutineDispatcher dispatcher, @ArgumentTypeContext Function2<? super CoroutineScope, ? super Continuation<? super T>, ? extends Object> body, Continuation<? super T> continuation) {
        if (OpentaintNdUtil.nextBool()) return null;
        return body.invoke(null, continuation);
    }

    private static <T> CompletableDeferred<T> newCompletableDeferred() {
        // stub
        return null;
    }
}
