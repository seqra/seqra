package org.opentaint.ir.analysis.impl;

import org.opentaint.ir.analysis.ifds.UnitResolver;
import org.opentaint.ir.analysis.graph.ApplicationGraphFactory;
import org.opentaint.ir.analysis.ifds.UnitResolverKt;
import org.opentaint.ir.api.JIRClassOrInterface;
import org.opentaint.ir.api.JIRClasspath;
import org.opentaint.ir.api.JIRDatabase;
import org.opentaint.ir.api.JIRMethod;
import org.opentaint.ir.api.analysis.JIRApplicationGraph;
import org.opentaint.ir.impl.Opentaint-IR;
import org.opentaint.ir.impl.JIRSettings;
import org.opentaint.ir.impl.features.InMemoryHierarchy;
import org.opentaint.ir.impl.features.Usages;
import org.opentaint.ir.testing.LibrariesMixinKt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class JavaAnalysisApiTest {
    private static JIRClasspath classpath;

    @BeforeAll
    public static void initClasspath() throws ExecutionException, InterruptedException {
        JIRDatabase instance = Opentaint-IR.async(new JIRSettings().installFeatures(Usages.INSTANCE, InMemoryHierarchy.INSTANCE)).get();
        classpath = instance.asyncClasspath(LibrariesMixinKt.getAllClasspath()).get();
    }

    @Test
    public void testJavaAnalysisApi() throws ExecutionException, InterruptedException {
        JIRClassOrInterface analyzedClass = classpath.findClassOrNull("org.opentaint.ir.testing.analysis.NpeExamples");
        Assertions.assertNotNull(analyzedClass);

        List<JIRMethod> methodsToAnalyze = analyzedClass.getDeclaredMethods();
        JIRApplicationGraph applicationGraph = ApplicationGraphFactory
                .newApplicationGraphForAnalysisAsync(classpath, null)
                .get();
        UnitResolver resolver = UnitResolverKt.getMethodUnitResolver();
        // TODO: run analysis
    }

    @Test
    public void testCustomBannedPackagesApi() throws ExecutionException, InterruptedException {
        List<String> bannedPackages = new ArrayList<>(ApplicationGraphFactory.getDefaultBannedPackagePrefixes());
        bannedPackages.add("my.package.that.wont.be.analyzed");

        JIRApplicationGraph customGraph = ApplicationGraphFactory
                .newApplicationGraphForAnalysisAsync(classpath, bannedPackages)
                .get();
        Assertions.assertNotNull(customGraph);
    }
}
