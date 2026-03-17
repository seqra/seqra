package org.opentaint.ir.testing;

public class Common {

    public interface CommonInterface {

        default void defaultMethod() {
            System.out.println("Hello");
        }
    }

    public static class CommonClass implements CommonInterface {

        public void run() {
            defaultMethod();
        }

    }

    public static class Common1 {
        public int publicField;
        protected int protectedField;
        private int privateField;
        int packageField;

        public void publicMethod() {
        }

        protected void protectedMethod() {
        }

        private void privateMethod() {
        }

        void packageMethod() {
        }

    }
}
