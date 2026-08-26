package opentaint.java.util.concurrent;

import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;

public class Executor {

    public void execute(@ArgumentTypeContext Runnable command) {
        command.run();
    }
}
