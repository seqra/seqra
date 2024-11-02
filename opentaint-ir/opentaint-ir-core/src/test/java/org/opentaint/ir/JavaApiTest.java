package org.opentaint.ir;

import org.junit.jupiter.api.Test;
import org.opentaint.ir.api.JIRDB;
import org.opentaint.ir.impl.index.Usages;

import java.util.concurrent.ExecutionException;

public class JavaApiTest {

    @Test
    public void createJirdb() throws ExecutionException, InterruptedException {
        System.out.println("Starting create database");
        JIRDB instance = JirdbKt.futureJirdb(new JIRDBSettings().installFeatures(Usages.INSTANCE)).get();
        System.out.println("Database is ready: " + instance);
    }
}
