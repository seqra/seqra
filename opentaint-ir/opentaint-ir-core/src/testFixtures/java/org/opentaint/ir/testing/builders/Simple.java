package org.opentaint.ir.testing.builders;

public class Simple {

    public static class SimpleBuilder {

        private Simple property;

        public Simple getProperty() {
            return property;
        }

        public void setProperty(Simple property) {
            this.property = property;
        }

        public Simple build() {
            return new Simple();
        }

        public Simple justReturnThis(Simple simple) {
            return simple;
        }
    }

    private Simple() {

    }
}
