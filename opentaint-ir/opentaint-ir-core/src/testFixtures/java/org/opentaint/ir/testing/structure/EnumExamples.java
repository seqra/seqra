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

    public enum EnumWithStaticInstance {
        C1, C2;

        public static final EnumWithStaticInstance instance = C1;
    }

    public static void main(String[] args) {
        System.out.println(EnumWithStaticInstance.values().length);
    }

}

