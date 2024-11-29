
package org.opentaint.ir.impl;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.opentaint.ir.api.JIRClassOrInterface;
import org.opentaint.ir.api.JIRClasspath;
import org.opentaint.ir.api.JIRDatabase;
import org.opentaint.ir.impl.features.Usages;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opentaint.ir.impl.LibrariesMixinKt.getAllClasspath;

public class JavaApiTest {

    @Test
    public void createJirdb() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        try (JIRDatabase instance = Opentaint-IR.async(new JIRSettings().installFeatures(Usages.INSTANCE)).get()) {
            System.out.println("Database is ready: " + instance);
        }
    }

    @Test
    public void createClasspath() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        try (JIRDatabase instance = Opentaint-IR.async(new JIRSettings().installFeatures(Usages.INSTANCE)).get()) {
            try (JIRClasspath classpath = instance.asyncClasspath(Lists.newArrayList()).get()) {
                JIRClassOrInterface clazz = classpath.findClassOrNull("java.lang.String");
                assertNotNull(clazz);
                assertNotNull(classpath.asyncRefreshed(false).get());
            }
            System.out.println("Database is ready: " + instance);
        }
    }

    @Test
    public void jirdbOperations() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        try (JIRDatabase instance = Opentaint-IR.async(new JIRSettings().installFeatures(Usages.INSTANCE)).get()) {
            instance.asyncLoad(getAllClasspath()).get();
            System.out.println("asyncLoad finished");
            instance.asyncRefresh().get();
            System.out.println("asyncRefresh finished");
            instance.asyncRebuildFeatures().get();
            System.out.println("asyncRebuildFeatures finished");
            instance.asyncAwaitBackgroundJobs().get();
            System.out.println("asyncAwaitBackgroundJobs finished");
        }
    }

}
