package org.opentaint.ir.impl.python

import org.opentaint.ir.api.python.*

// ─── Entity Implementations ────────────────────────────────

data class PIRModuleImpl(
    override val name: String,
    override val path: String,
    override val classes: List<PIRClass>,
    override val functions: List<PIRFunction>,
    override val fields: List<PIRField>,
    override val moduleInit: PIRFunction,
    override val imports: List<String>,
    override val diagnostics: List<PIRDiagnostic> = emptyList(),
) : PIRModule {
    // Break circular hashCode/toString: module → classes/functions → module
    override fun equals(other: Any?): Boolean = this === other || (other is PIRModuleImpl && name == other.name && path == other.path)
    override fun hashCode(): Int = name.hashCode() * 31 + path.hashCode()
    override fun toString(): String = "PIRModule($name)"
}

// Not `data class`: `module` is `lateinit var` wired post-construction, so
// synthesized copy()/componentN() would drop it. equals/hashCode/toString are
// hand-rolled below to break circular references anyway.
class PIRClassImpl(
    override val name: String,
    override val qualifiedName: String,
    override val baseClasses: List<String>,
    override val mro: List<String>,
    override val methods: List<PIRFunction>,
    override val fields: List<PIRField>,
    override val nestedClasses: List<PIRClass>,
    override val properties: List<PIRProperty>,
    override val decorators: List<PIRDecorator>,
    override val isAbstract: Boolean,
    override val isDataclass: Boolean,
    override val isEnum: Boolean,
) : PIRClass {
    // Wired after construction: chicken-and-egg — module needs its classes built first.
    override lateinit var module: PIRModule

    // Break circular hashCode/toString: class → methods → function → enclosingClass → class
    override fun equals(other: Any?): Boolean = this === other || (other is PIRClassImpl && qualifiedName == other.qualifiedName)
    override fun hashCode(): Int = qualifiedName.hashCode()
    override fun toString(): String = "PIRClass($qualifiedName)"
}

// Not `data class`: `module` is `lateinit var` wired post-construction, so
// synthesized copy()/componentN() would drop it.
class PIRFunctionImpl(
    override val name: String,
    override val qualifiedName: String,
    override val parameters: List<PIRParameter>,
    override val returnType: PIRType,
    override val cfg: PIRCFG,
    override val decorators: List<PIRDecorator>,
    override val isAsync: Boolean,
    override val isGenerator: Boolean,
    override val isStaticMethod: Boolean,
    override val isClassMethod: Boolean,
    override val isProperty: Boolean,
    override val closureVars: List<String>,
    // Mutable: set after construction to wire up circular class<->method reference
    override var enclosingClass: PIRClass?,
) : PIRFunction {
    // Wired after construction: chicken-and-egg — module needs its functions built first.
    override lateinit var module: PIRModule

    override val instList: List<PIRInstruction> get() = cfg.instList

    // Break circular hashCode/toString: function → enclosingClass → methods → function
    override fun equals(other: Any?): Boolean = this === other || (other is PIRFunctionImpl && qualifiedName == other.qualifiedName)
    override fun hashCode(): Int = qualifiedName.hashCode()
    override fun toString(): String = "PIRFunction($qualifiedName)"
}

data class PIRParameterImpl(
    override val name: String,
    override val type: PIRType,
    override val kind: PIRParameterKind,
    override val hasDefault: Boolean,
    override val defaultValue: PIRValue? = null,
    override val index: Int,
) : PIRParameter

data class PIRFieldImpl(
    override val name: String,
    override val type: PIRType,
    override val isClassVar: Boolean,
    override val hasInitializer: Boolean,
) : PIRField

data class PIRPropertyImpl(
    override val name: String,
    override val type: PIRType,
    override val getter: PIRFunction?,
    override val setter: PIRFunction?,
    override val deleter: PIRFunction?,
) : PIRProperty

data class PIRDecoratorImpl(
    override val name: String,
    override val qualifiedName: String,
    override val arguments: List<String>,
) : PIRDecorator

// ─── CFG Implementation ────────────────────────────────────

class PIRCFGImpl(
    override val blocks: List<PIRBasicBlock>,
    override val instList: List<PIRInstruction>,
    private val entryLabel: Int,
    private val exitLabels: Set<Int>,
    private val instToBlock: List<Int>,
) : PIRCFG {
    private val blocksByLabel = blocks.associateBy { it.label }

    override val entry: PIRInstruction
        get() = instList.first()

    override val exits: Set<PIRInstruction>
        get() = exitLabels.mapNotNullTo(hashSetOf()) { block(it).instructions.lastOrNull() }

    override val entryBlock: PIRBasicBlock
        get() = blocksByLabel[entryLabel] ?: blocks.first()

    override val exitBlocks: Set<PIRBasicBlock>
        get() = exitLabels.mapTo(hashSetOf()) { block(it) }

    override fun successors(inst: PIRInstruction): List<PIRInstruction> =
        when (inst) {
            is PIRBranchingInst -> inst.successors.map { instList[it] }
            is PIRTerminatingInst -> emptyList()
            else -> instList.getOrNull(inst.location.index + 1)?.let { listOf(it) }
                ?: error("Unexpected non-terminating last instruction: $inst")
        }

    override fun successors(block: PIRBasicBlock): List<PIRBasicBlock> {
        val last = block.instructions.lastOrNull() ?: return emptyList()
        return when (last) {
            is PIRBranchingInst -> last.blockSuccessors.map { block(it) }
            is PIRTerminatingInst -> emptyList()
            else -> error("Unexpected block last instruction: $last")
        }
    }

    override fun predecessors(block: PIRBasicBlock): List<PIRBasicBlock> {
        return blocks.filter { block in successors(it) }
    }

    override fun predecessors(inst: PIRInstruction): List<PIRInstruction> {
        return instList.filter { inst in successors(it) }
    }

    override fun exceptionalSuccessors(block: PIRBasicBlock): List<PIRBasicBlock> {
        return block.exceptionHandlers.mapNotNull { blocksByLabel[it] }
    }

    override fun block(label: Int): PIRBasicBlock {
        return blocksByLabel[label] ?: throw IllegalArgumentException("No block with label $label")
    }

    override fun block(inst: PIRInstruction): PIRBasicBlock {
        return block(instToBlock[inst.location.index])
    }
}

// ─── Unknown Entity Implementations ────────────────────────
// Returned when a module fails to build (e.g. mypy syntax error).
// All collections are empty; lookups return further Unknown entities.

private val EMPTY_CFG = PIRCFGImpl(emptyList(), emptyList(), 0, emptySet(), emptyList())

class PIRUnknownModule(
    override val name: String,
    override val diagnostics: List<PIRDiagnostic>,
) : PIRModule {
    override val path: String = ""
    override val classes: List<PIRClass> = emptyList()
    override val functions: List<PIRFunction> = emptyList()
    override val fields: List<PIRField> = emptyList()
    override val imports: List<String> = emptyList()
    override val isUnknown: Boolean = true
    override val moduleInit: PIRFunction = PIRUnknownFunction(
        "__module_init__", "$name.__module_init__", this
    )
}

class PIRUnknownClass(
    override val name: String,
    override val qualifiedName: String,
    override val module: PIRModule,
) : PIRClass {
    override val baseClasses: List<String> = emptyList()
    override val mro: List<String> = emptyList()
    override val methods: List<PIRFunction> = emptyList()
    override val fields: List<PIRField> = emptyList()
    override val nestedClasses: List<PIRClass> = emptyList()
    override val properties: List<PIRProperty> = emptyList()
    override val decorators: List<PIRDecorator> = emptyList()
    override val isAbstract: Boolean = false
    override val isDataclass: Boolean = false
    override val isEnum: Boolean = false
}

class PIRUnknownFunction(
    override val name: String,
    override val qualifiedName: String,
    override val module: PIRModule,
) : PIRFunction {
    override val parameters: List<PIRParameter> = emptyList()
    override val returnType: PIRType = PIRAnyType
    override val cfg: PIRCFG = EMPTY_CFG
    override val instList: List<PIRInstruction> = emptyList()
    override val decorators: List<PIRDecorator> = emptyList()
    override val isAsync: Boolean = false
    override val isGenerator: Boolean = false
    override val isStaticMethod: Boolean = false
    override val isClassMethod: Boolean = false
    override val isProperty: Boolean = false
    override val closureVars: List<String> = emptyList()
    override val enclosingClass: PIRClass? = null
}
