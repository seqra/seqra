package org.opentaint.ir.testing;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import org.opentaint.ir.api.jvm.JIRClassOrInterface;
import org.opentaint.ir.api.jvm.JIRClasspath;
import org.opentaint.ir.api.jvm.JIRDatabase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.opentaint.ir.testing.LibrariesMixinKt.getAllClasspath;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JavaApiTest {

    private final Supplier<JIRDatabase> db = Suppliers.memoize(() -> {
        try {
            return BaseTestKt.getGlobalDb();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    @Test
    public void createJirdb() {
        System.out.println("Creating database");
        JIRDatabase database = db.get();
        assertNotNull(database);
        System.out.println("Database is ready: " + database);
    }

    @Test
    public void createClasspath() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        JIRDatabase instance = db.get();
        try (JIRClasspath classpath = instance.asyncClasspath(Lists.newArrayList()).get()) {
            JIRClassOrInterface clazz = classpath.findClassOrNull("java.lang.String");
            assertNotNull(clazz);
            assertNotNull(classpath.asyncRefreshed(false).get());
        }
        System.out.println("Database is ready: " + instance);
    }

    @Test
    public void jIRdbOperations() throws ExecutionException, InterruptedException, IOException {
        System.out.println("Creating database");
        JIRDatabase instance = db.get();
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
