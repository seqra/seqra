package org.springframework.web.bind.annotation;

public @interface ModelAttribute {
    String value() default "";
}
