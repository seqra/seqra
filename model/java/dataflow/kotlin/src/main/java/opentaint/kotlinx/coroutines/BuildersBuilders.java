package opentaint.kotlinx.coroutines;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.CoroutineScope;
import org.opentaint.ir.approximation.annotation.ApproximatedClassName;
import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;

@ApproximatedClassName("BuildersKt__BuildersKt")
public class BuildersBuilders {

    public static final <T> T runBlocking(CoroutineContext ctx, @ArgumentTypeContext Function2<? super CoroutineScope, ? super Continuation<? super T>, ? extends Object> body) {
        return (T) body.invoke(null, null);
    }

    public static Object runBlocking$default(CoroutineContext ctx, @ArgumentTypeContext Function2 body, int flags, Object unused) {
        return body.invoke(null, null);
    }
}
