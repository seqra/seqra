package org.opentaint.ir.testing.structure;

public class EnumExamples {
    public enum SimpleEnum {
        SUCCESS, ERROR
    }

    public enum EnumWithField {
        OK(200), NOT_FOUND(404);

        EnumWithField(int statusCode) {
            this.statusCode = statusCode;
        }

        final int statusCode;
    }

}

