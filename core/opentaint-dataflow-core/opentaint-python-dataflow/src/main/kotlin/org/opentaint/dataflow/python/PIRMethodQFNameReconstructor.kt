package org.opentaint.dataflow.python

import org.opentaint.dataflow.python.graph.PIRApplicationGraph
import org.opentaint.ir.api.python.PIRAssign
import org.opentaint.ir.api.python.PIRBindFunctionExpr
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRClass
import org.opentaint.ir.api.python.PIRClassType
import org.opentaint.ir.api.python.PIRDictExpr
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRGlobalNameRef
import org.opentaint.ir.api.python.PIRInstruction
import org.opentaint.ir.api.python.PIRListExpr
import org.opentaint.ir.api.python.PIRLoadAttr
import org.opentaint.ir.api.python.PIRModuleNameRef
import org.opentaint.ir.api.python.PIRParameter
import org.opentaint.ir.api.python.PIRReadNameExpr
import org.opentaint.ir.api.python.PIRSetExpr
import org.opentaint.ir.api.python.PIRStringExpr
import org.opentaint.ir.api.python.PIRTupleExpr
import org.opentaint.ir.api.python.PIRType
import org.opentaint.ir.api.python.PIRUnionType
import org.opentaint.ir.api.python.PythonNames

class PIRMethodQFNameReconstructor private constructor(
    method: PIRFunction,
    applicationGraph: PIRApplicationGraph,
) : PIRMethodIntraproceduralWalker<PIRMethodQFNameReconstructor.Binding>(method, applicationGraph) {
    private val cp = applicationGraph.cp
    private val result = mutableMapOf<PIRInstruction, MutableSet<String>>()

    sealed interface Binding {
        val name: String

        data class Module(override val name: String) : Binding
        data class Class(override val name: String) : Binding
        data class Function(override val name: String) : Binding
        data class Instance(override val name: String) : Binding

        @ConsistentCopyVisibility
        data class External private constructor(override val name: String, val size: UInt) : Binding {
            fun step(attribute: String) = External("$name.$attribute", size + 1u)

            companion object {
                fun root(name: String) = External(name, 1u)
            }
        }
    }

    private data class MroMember(val owner: String, val bindings: List<Binding>)

    private fun compute(): Map<PIRInstruction, Set<String>> {
        walk()
        return result
    }

    override fun seed(inst: PIRInstruction): List<Binding> = when (inst) {
        is PIRAssign -> when (val rhv = inst.expr) {
            is PIRBindFunctionExpr -> listOf(Binding.Function(rhv.function.qualifiedName))

            is PIRReadNameExpr -> when (val ref = rhv.ref) {
                is PIRGlobalNameRef -> global(ref.qualifiedName)
                is PIRModuleNameRef -> global(ref.module)
            }

            is PIRListExpr -> listOf(Binding.External.root(BUILTIN_LIST))
            is PIRTupleExpr -> listOf(Binding.External.root(BUILTIN_TUPLE))
            is PIRSetExpr -> listOf(Binding.External.root(BUILTIN_SET))
            is PIRDictExpr -> listOf(Binding.External.root(BUILTIN_DICT))
            is PIRStringExpr -> listOf(Binding.External.root(BUILTIN_STR))

            else -> emptyList()
        }

        is PIRCall -> inst.resolvedCallee
            ?.let { resolved -> global(resolved).flatMap { call(inst, it) } }
            .orEmpty()

        else -> emptyList()
    }

    override fun attributeRead(inst: PIRLoadAttr, obj: Binding): List<Binding> {
        val attribute = inst.attribute
        val qn = "${obj.name}.$attribute"
        if (obj is Binding.External && obj.size >= SEGMENT_SIZE_LIMIT && projectEntity(qn) == null) return emptyList()

        record(inst, qn)
        return when (obj) {
            is Binding.External -> listOf(projectEntity(qn) ?: obj.step(attribute))

            is Binding.Module -> global(qn)

            is Binding.Class, is Binding.Instance -> mroMember(obj.name, attribute).flatMap { (owner, bindings) ->
                if (owner != obj.name) record(inst, "$owner.$attribute")
                bindings
            }

            is Binding.Function -> emptyList()
        }
    }

    override fun call(inst: PIRCall, callee: Binding): List<Binding> = when (callee) {
        is Binding.Function -> {
            record(inst, callee.name)
            bind(cp.findFunctionOrNull(callee.name)?.returnType)
        }

        is Binding.Class -> {
            initCallees(callee.name).forEach { record(inst, it) }
            listOf(Binding.Instance(callee.name))
        }

        is Binding.Instance -> mroMember(callee.name, PythonNames.CALL_METHOD).flatMap { it.bindings }.flatMap {
            when (it) {
                is Binding.Function, is Binding.External -> call(inst, it)
                else -> emptyList()
            }
        }

        is Binding.External -> {
            record(inst, callee.name)
            emptyList()
        }

        is Binding.Module -> emptyList()
    }

    override fun seedParameter(param: PIRParameter): List<Binding> = bind(param.type).ifEmpty { receiver(param) }

    private fun receiver(param: PIRParameter): List<Binding> {
        val cls = method.enclosingClass ?: return emptyList()
        if (param.index != 0 || method.isStaticMethod) return emptyList()
        return listOf(if (method.isClassMethod) Binding.Class(cls.qualifiedName) else Binding.Instance(cls.qualifiedName))
    }

    private fun global(qn: String): List<Binding> {
        projectEntity(qn)?.let { return listOf(it) }
        val owner = qn.substringBeforeLast('.', missingDelimiterValue = "")
        val module = cp.findModuleOrNull(owner) ?: return listOf(Binding.External.root(qn))
        return bind(module.fields.find { it.name == qn.substringAfterLast('.') }?.type)
    }

    private fun projectEntity(qn: String): Binding? = when {
        cp.findModuleOrNull(qn) != null -> Binding.Module(qn)
        cp.findClassOrNull(qn) != null -> Binding.Class(qn)
        cp.findFunctionOrNull(qn) != null -> Binding.Function(qn)
        else -> null
    }

    private fun bind(type: PIRType?): List<Binding> = when (type) {
        is PIRClassType -> when {
            type.qualifiedName.isEmpty() -> emptyList()
            cp.findClassOrNull(type.qualifiedName) != null -> listOf(Binding.Instance(type.qualifiedName))
            else -> listOf(Binding.External.root(type.qualifiedName))
        }
        is PIRUnionType -> type.members.flatMap { bind(it) }
        else -> emptyList()
    }

    private fun mroMember(className: String, attribute: String): List<MroMember> = buildList {
        for (ownerName in mro(className)) {
            val owner = cp.findClassOrNull(ownerName)
            if (owner == null) {
                if (ownerName != BUILTIN_OBJECT) add(MroMember(ownerName, listOf(Binding.External.root(ownerName).step(attribute))))
                continue
            }
            ownMember(owner, attribute)?.let {
                add(MroMember(ownerName, it))
                return@buildList
            }
        }
    }

    private fun ownMember(owner: PIRClass, attribute: String): List<Binding>? {
        owner.properties.find { it.name == attribute }?.let { return bind(it.type) }
        owner.methods.find { it.name == attribute }?.let { return listOf(Binding.Function(it.qualifiedName)) }
        owner.nestedClasses.find { it.name == attribute }?.let { return listOf(Binding.Class(it.qualifiedName)) }
        owner.fields.find { it.name == attribute }?.let { return bind(it.type) }
        return null
    }

    private fun initCallees(className: String): List<String> =
        mroMember(className, PythonNames.INIT_METHOD)
            .map { (owner, bindings) -> bindings.filterIsInstance<Binding.Function>().singleOrNull()?.name ?: owner }
            .ifEmpty { listOf(className) }

    private fun mro(className: String): List<String> =
        cp.findClassOrNull(className)?.mro?.ifEmpty { null } ?: listOf(className)

    private fun record(inst: PIRInstruction, qfName: String) {
        result.getOrPut(inst) { hashSetOf() }.add(qfName)
    }

    companion object {
        const val SEGMENT_SIZE_LIMIT = 7u

        private const val BUILTIN_OBJECT = "builtins.object"
        private const val BUILTIN_LIST = "builtins.list"
        private const val BUILTIN_TUPLE = "builtins.tuple"
        private const val BUILTIN_SET = "builtins.set"
        private const val BUILTIN_DICT = "builtins.dict"
        private const val BUILTIN_STR = "builtins.str"

        fun compute(method: PIRFunction, applicationGraph: PIRApplicationGraph) =
            PIRMethodQFNameReconstructor(method, applicationGraph).compute()
    }
}
