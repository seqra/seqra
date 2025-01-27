package org.opentaint.ir.testing.hierarchies;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class Overrides {

    public interface Iface<T> {

        public T runMain(T in);

        default public T runMain(Closeable in) {
            return null;
        }
    }

    public static class Main<T> {

        private T main;
        protected T protectedMain;
        public T publicMain;

        public T runMain(List<T> in) {
            return null;
        }

        public T runMain(T in) {
            return null;
        }
    }

    public static class Impl1 extends Main<String> {
        @Override
        public String runMain(List<String> in) {
            return super.runMain(in);
        }

        @Override
        public String runMain(String in) {
            return super.runMain(in);
        }
    }

    public static class Impl2 extends Impl1 implements Iface<String> {
        private String main;
        protected List<Closeable> protectedMain1;
        public List<Closeable> publicMain1;

        @Override
        public String runMain(List<String> in) {
            return super.runMain(in);
        }

        @Override
        public String runMain(String in) {
            return super.runMain(in);
        }

        void xxx1(){

        }

        private void xxx2(){

        }

        interface A {
            void a();
        }

        interface B extends A {
            void b();
        }

        public String runMain12123123(Closeable in, B b) throws IOException {
            runMain("null");
            xxx1();
            xxx2();
            runMain(in);
            in.close();
            b.a();
            return Iface.super.runMain(in);
        }
    }
}
