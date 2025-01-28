package org.opentaint.ir.testing;

import org.opentaint.ir.api.cfg.JIRArgument;
import org.opentaint.ir.api.cfg.JIRExpr;
import org.opentaint.ir.api.cfg.TypedExprResolver;
import org.opentaint.ir.impl.JIRCacheSettings;
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

}
