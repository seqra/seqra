package org.opentaint.ir.testing;

import org.opentaint.ir.api.JIRDatabase;
import org.opentaint.ir.impl.Opentaint-IR;
import org.opentaint.ir.impl.JIRCacheSettings;
import org.opentaint.ir.impl.JIRSettings;
import org.opentaint.ir.impl.features.Usages;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class JavaApi {

    // FIXME: does not compile because of some kinda compiler bug
    //        (see https://youtrack.jetbrains.com/issue/KT-15964)
    // private static class ArgumentResolver extends TypedExprResolver<JIRArgument> {
    //
    //     @Override
    //     public void ifMatches(JIRExpr jIRExpr) {
    //         if (jIRExpr instanceof JIRArgument) {
    //             getResult().add((JIRArgument) jIRExpr);
    //         }
    //     }
    //
    // }

    public static void cacheSettings() {
        new JIRCacheSettings().types(10, Duration.of(1, ChronoUnit.MINUTES));
    }

    public static void getDatabase() {
        try {
            JIRDatabase instance = Opentaint-IR.async(new JIRSettings().installFeatures(Usages.INSTANCE)).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
