package org.opentaint.ir.testing;

import com.google.common.collect.Lists;
import org.opentaint.ir.api.jvm.JIRClassOrInterface;
import org.opentaint.ir.api.jvm.JIRClasspath;
import org.opentaint.ir.api.jvm.JIRDatabase;
import org.opentaint.ir.impl.features.Builders;
import org.opentaint.ir.impl.features.InMemoryHierarchy;
import org.opentaint.ir.impl.features.Usages;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.opentaint.ir.testing.LibrariesMixinKt.getAllClasspath;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JavaApiTest {
    private JIRDatabase dbGet() {
        return new WithDb(Usages.INSTANCE, Builders.INSTANCE, new InMemoryHierarchy()).getDb();
    }

    @Test
    public void createJIRdb() throws IOException {
        System.out.println("Creating database");
        try (JIRDatabase database = dbGet()) {
            assertNotNull(database);
            System.out.println("Database is ready: " + database);
        }
    }

    @Test
    public void createClasspath() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        try (JIRDatabase instance = dbGet()) {
            try (JIRClasspath classpath = instance.asyncClasspath(Lists.newArrayList()).get()) {
                JIRClassOrInterface clazz = classpath.findClassOrNull("java.lang.String");
                assertNotNull(clazz);
                assertNotNull(classpath.asyncRefreshed(false).get());
            }
            System.out.println("Database is ready: " + instance);
        }
    }

    @Test
    public void jIRdbOperations() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        try (JIRDatabase instance = dbGet()) {
            instance.asyncLoad(getAllClasspath()).get();
            System.out.println("asyncLoad finished");
            instance.asyncRefresh().get();
            System.out.println("asyncRefresh finished");
            instance.asyncRebuildFeatures().get();
            System.out.println("asyncRebuildFeatures finished");
            instance.asyncAwaitBackgroundJobs().get();
            System.out.println("asyncAwaitBackgroundJobs finished");
            instance.getFeatures();
        }
    }
}
