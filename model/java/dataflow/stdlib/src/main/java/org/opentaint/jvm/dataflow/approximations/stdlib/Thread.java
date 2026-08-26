package org.opentaint.jvm.dataflow.approximations.stdlib;

import org.opentaint.ir.approximation.annotation.Approximate;
import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;
import org.opentaint.jvm.dataflow.approximations.OpentaintNdUtil;

@Approximate(java.lang.Thread.class)
public class Thread {

    public void start() {
        java.lang.Thread t = (java.lang.Thread) (Object) this;
        if (OpentaintNdUtil.nextBool()) {
            t.run();
        }
    }

    public Thread(@ArgumentTypeContext Runnable target) {
        if (OpentaintNdUtil.nextBool()) {
            target.run();
        }
    }

    public Thread(@ArgumentTypeContext Runnable target, String name) {
        if (OpentaintNdUtil.nextBool()) {
            target.run();
        }
    }

    public Thread(ThreadGroup group, @ArgumentTypeContext Runnable target) {
        if (OpentaintNdUtil.nextBool()) {
            target.run();
        }
    }

    public Thread(ThreadGroup group, @ArgumentTypeContext Runnable target, String name) {
        if (OpentaintNdUtil.nextBool()) {
            target.run();
        }
    }

    public Thread(ThreadGroup group, @ArgumentTypeContext Runnable target, String name, long stackSize) {
        if (OpentaintNdUtil.nextBool()) {
            target.run();
        }
    }

    public Thread(ThreadGroup group, @ArgumentTypeContext Runnable target, String name, long stackSize, boolean inheritThreadLocals) {
        if (OpentaintNdUtil.nextBool()) {
            target.run();
        }
    }
}
