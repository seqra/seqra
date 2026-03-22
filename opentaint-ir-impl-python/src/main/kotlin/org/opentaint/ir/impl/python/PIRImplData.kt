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
    override val classpath: PIRClasspath,
    override val diagnostics: List<PIRDiagnostic> = emptyList(),
) : PIRModule

data class PIRClassImpl(
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
    override val module: PIRModule,
) : PIRClass

data class PIRFunctionImpl(
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
    override val enclosingClass: PIRClass?,
    override val module: PIRModule,
) : PIRFunction

data class PIRParameterImpl(
    override val name: String,
    override val type: PIRType,
    override val kind: PIRParameterKind,
    override val hasDefault: Boolean,
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
    private val entryLabel: Int,
    private val exitLabels: Set<Int>,
) : PIRCFG {
    private val blocksByLabel = blocks.associateBy { it.label }

    override val entry: PIRBasicBlock
        get() = blocksByLabel[entryLabel] ?: blocks.first()

    override val exits: Set<PIRBasicBlock>
        get() = blocks.filter { it.label in exitLabels }.toSet()

    override fun successors(block: PIRBasicBlock): List<PIRBasicBlock> {
        val last = block.instructions.lastOrNull() ?: return emptyList()
        return when (last) {
            is PIRGoto -> listOfNotNull(blocksByLabel[last.targetBlock])
            is PIRBranch -> listOfNotNull(
                blocksByLabel[last.trueBlock],
                blocksByLabel[last.falseBlock]
            )
            is PIRNextIter -> listOfNotNull(
                blocksByLabel[last.bodyBlock],
                blocksByLabel[last.exitBlock]
            )
            is PIRReturn, is PIRRaise, is PIRUnreachable -> emptyList()
            else -> emptyList()
        }
    }

    override fun predecessors(block: PIRBasicBlock): List<PIRBasicBlock> {
        return blocks.filter { block in successors(it) }
    }

    override fun exceptionalSuccessors(block: PIRBasicBlock): List<PIRBasicBlock> {
        return block.exceptionHandlers.mapNotNull { blocksByLabel[it] }
    }

    override fun block(label: Int): PIRBasicBlock {
        return blocksByLabel[label] ?: throw IllegalArgumentException("No block with label $label")
    }
}
