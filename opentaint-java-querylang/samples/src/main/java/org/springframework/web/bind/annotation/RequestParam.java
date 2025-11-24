package org.springframework.web.bind.annotation;

public @interface RequestParam {
    String value() default "";
}
