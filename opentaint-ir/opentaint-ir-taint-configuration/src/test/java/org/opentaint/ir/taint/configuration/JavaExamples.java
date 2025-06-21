package org.opentaint.ir.taint.configuration;

public class JavaExamples {
    void someMethodWithAnnotatedParameter(@MyParameterAnnotation int someParameter) {
        // some code
    }

    void someMethodWithoutAnnotatedParameter(int someParameter) {
        // some code
    }

    @MyMethodAnnotation
    void someAnnotatedMethod(int someParameter) {
        // some code
    }

    void someNotAnnotatedMethod(int someParameter) {
        // some code
    }
}
