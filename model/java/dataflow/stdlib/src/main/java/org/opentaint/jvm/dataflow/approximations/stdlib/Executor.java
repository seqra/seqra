package org.opentaint.jvm.dataflow.approximations.stdlib;

import org.opentaint.ir.approximation.annotation.Approximate;
import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;

@Approximate(java.util.concurrent.Executor.class)
public class Executor {

    public void execute(@ArgumentTypeContext Runnable command) {
        command.run();
    }
}
