package example;

import base.RuleSample;
import base.RuleSet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Probes whether a class-level annotation can be used in a
 * `pattern-not-inside` to suppress matches in a method enclosed by an
 * annotated class. Mirrors the shape used by the Spring XSS sink rule
 * (`pattern-not-inside: @RestController(...) class $C { ... }`).
 *
 * Expected behavior: only the method whose enclosing class is NOT annotated
 * with `@Suppress` matches; the method inside the `@Suppress` class should
 * NOT match.
 */
@RuleSet("example/RuleWithClassLevelAnnotationNotInside.yaml")
public abstract class RuleWithClassLevelAnnotationNotInside implements RuleSample {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Suppress {}

    void sink(String data) {}

    static class PlainHolder {
        void method(String data) {
            new RuleWithClassLevelAnnotationNotInside.PlainHolder() {
                @Override
                void method(String d) {}
            };
        }
    }

    final static class PositivePlainClass extends RuleWithClassLevelAnnotationNotInside {
        void target(String data) {
            sink(data);
        }

        @Override
        public void entrypoint() {
            target("tainted");
        }
    }

    @Suppress
    final static class NegativeSuppressedClass extends RuleWithClassLevelAnnotationNotInside {
        void target(String data) {
            sink(data);
        }

        @Override
        public void entrypoint() {
            target("tainted");
        }
    }
}
