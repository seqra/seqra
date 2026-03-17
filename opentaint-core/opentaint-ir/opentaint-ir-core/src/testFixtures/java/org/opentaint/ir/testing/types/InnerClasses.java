package org.opentaint.ir.testing.types;

import java.io.Closeable;
import java.util.List;

public class InnerClasses<W> {

    public class InnerState {
        private W stateW;

        private <W extends List<Closeable>> W method() {
            return null;
        }
    }

    public class InnerStateOverriden<W> {
        private W stateW;

        private <W extends List<Integer>> W method() {
            return null;
        }
    }

    public <T> Runnable run(T with) {
        return new Runnable() {

            private T stateT;
            private W stateW;

            @Override
            public void run() {
                stateT = with;
            }
        };
    }

    public <T> InnerState newState(W with) {
        return new InnerState() {

            private T stateT;

        };
    }

    private InnerClasses<String> innerClasses;

    private InnerClasses<String>.InnerState stateString;
    private InnerClasses<String>.InnerStateOverriden<Closeable> stateClosable;

    public static InnerClasses<String> use(InnerClasses<String>.InnerState param) {
        return null;
    }

    public static InnerClasses<String> useOverriden(InnerClasses<String>.InnerStateOverriden<Closeable> param) {
        return null;
    }

    public InnerClasses<W> useW(InnerClasses<W> param) {
        return null;
    }
}
