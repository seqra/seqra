package org.opentaint.sast.test.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Usage example: PositiveRuleSample("lib/java/rule.yaml", id="rule-id")
 * */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PositiveRuleSample {
    String value();

    String id() default "";
}
