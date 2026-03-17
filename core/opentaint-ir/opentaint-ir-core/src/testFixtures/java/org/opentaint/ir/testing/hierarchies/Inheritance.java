package org.opentaint.ir.testing.hierarchies;

import java.beans.Statement;

public class Inheritance {

    public static Object test(String[] args) throws Exception {
        new Statement(new Private(), "run", null).execute();
        new Statement(new PrivateGeneric(), "run", new Object[] {"generic"}).execute();
        return null;
    }

    public static class Public {
        public void run() {
            throw new Error("method is overridden");
        }
    }

    static class Private extends Public {
        public void run() {
            System.out.println("default");
        }
    }

    public static class PublicGeneric<T> {
        public void run(T object) {
            throw new Error("method is overridden");
        }
    }

    static class PrivateGeneric extends PublicGeneric<String> {
        public void run(String string) {
            System.out.println(string);
        }
    }

}
