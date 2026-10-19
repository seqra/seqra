package org.opentaint.dataflow.jvm.bidi.fixtures

import org.opentaint.dataflow.jvm.bidi.fixtures.jvmTestDeclarations
import org.opentaint.dataflow.bidi.ap.MarkTable
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.interp.MicroEdgeBuilder
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactTypeChecker
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils
import org.opentaint.dataflow.jvm.bidi.interp.JIRStatementForms
import org.opentaint.dataflow.jvm.bidi.interp.JIRTypeFilters
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRValue

/** Shared real-bytecode statements and indexed geometry; expected forms are stated in the tests. */
internal class JIRFormsTestFixtures(val cp: JIRClasspath) {
    val manager = ApManager(Cancellation(), marks = MarkTable(jvmTestDeclarations()))
    val ops = ApOps(manager)
    val checker = JIRFactTypeChecker(cp)
    val typeFilters = JIRTypeFilters(checker, manager)

    fun method(name: String) = cp.findClassOrNull(SAMPLE_CLASS)!!.declaredMethods.single { it.name == name }
    fun insts(name: String): List<JIRInst> = method(name).instList.instructions
    fun assigns(name: String): List<JIRAssignInst> = insts(name).filterIsInstance<JIRAssignInst>()
    fun assign(name: String, predicate: (JIRAssignInst) -> Boolean): JIRAssignInst = assigns(name).first(predicate)
    fun base(v: JIRValue): AccessPathBase = checkNotNull(MethodFlowFunctionUtils.accessPathBase(v))
    fun field(name: String, type: String = "java.lang.Object", owner: String = SAMPLE_CLASS): AccessorIdx =
        manager.accessors.index(FieldAccessor(owner, name, type))
    val element: AccessorIdx get() = manager.accessors.index(ElementAccessor)
    val cls: AccessorIdx get() = manager.accessors.index(ClassStaticAccessor(SAMPLE_CLASS))

    fun forms(
        aliasPaths: (JIRInst, AccessPathBase) -> List<Pair<AccessPathBase, List<AccessorIdx>>> = { _, _ -> emptyList() },
        readSources: (MicroEdgeBuilder, JIRAssignInst) -> Unit = { _, _ -> },
    ): JIRStatementForms = JIRStatementForms(manager, typeFilters, aliasPaths, readSources)

    /** Bytecode can use a separate target temporary; this fixture pins the normative same-base row. */
    fun selfRead(): JIRAssignInst {
        val original = assign("selfRead") { (it.rhv as? JIRFieldRef)?.field?.name == "next" }
        val read = original.rhv as JIRFieldRef
        return JIRAssignInst(original.location, read.instance!!, read)
    }

    companion object { const val SAMPLE_CLASS = "sample.bidi.BidiStatementSample" }
}
