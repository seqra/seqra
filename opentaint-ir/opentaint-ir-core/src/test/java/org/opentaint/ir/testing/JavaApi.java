package org.opentaint.ir.testing;

import org.opentaint.ir.api.JIRDatabase;
import org.opentaint.ir.api.cfg.JIRArgument;
import org.opentaint.ir.api.cfg.JIRExpr;
import org.opentaint.ir.api.cfg.TypedExprResolver;
import org.opentaint.ir.impl.Opentaint-IR;
import org.opentaint.ir.impl.JIRCacheSettings;
import org.opentaint.ir.impl.JIRSettings;
import org.opentaint.ir.impl.features.Usages;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class JavaApi {
    private static class ArgumentResolver extends TypedExprResolver<JIRArgument> {

        @Override
        public void ifMatches(@NotNull JIRExpr jIRExpr) {
            if (jIRExpr instanceof JIRArgument) {
                getResult().add((JIRArgument) jIRExpr);
            }
        }

    }

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
