package org.opentaint.jvm.util

import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.ext.isSubClassOf
import org.opentaint.ir.api.jvm.ext.objectClass
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest


data class Package(val components: List<String>, val isConcrete: Boolean) {

    constructor(name: String) : this(
        name.removeSuffix(EXPANSION_STR)
            .removeSuffix(SEPARATOR_STR)
            .split(SEPARATOR)
            .filter { it.isNotBlank() },
        name.lastOrNull() != EXPANSION
    )

    companion object {
        const val SEPARATOR = '/'
        const val SEPARATOR_STR = SEPARATOR.toString()
        const val EXPANSION = '*'
        const val EXPANSION_STR = EXPANSION.toString()
        const val CANONICAL_SEPARATOR = '.'
        const val CANONICAL_SEPARATOR_STR = CANONICAL_SEPARATOR.toString()
        val defaultPackage = Package(EXPANSION_STR)
        val emptyPackage = Package("")
        fun parse(string: String) = Package(
            string.replace(
                CANONICAL_SEPARATOR,
                SEPARATOR
            )
        )
    }
}

internal val MethodNode.jsrInlined: MethodNode
    get() {
        val temp = JSRInlinerAdapter(null, access, name, desc, signature, exceptions?.toTypedArray())
        this.accept(temp)
        return LabelFilterer(temp).build()
    }

fun MethodNode.isSameSignature(other: MethodNode) =
    this.name == other.name && this.desc == other.desc

internal fun ClassNode.inlineJsrs() {
    this.methods = methods.map { it.jsrInlined }
}

val JarEntry.fullName get() = this.name.removeSuffix(".class")

val ClassNode.hasFrameInfo: Boolean
    get() {
        var hasInfo = false
        for (mn in methods) {
            hasInfo = hasInfo || mn.instructions.any { it is FrameNode }
        }
        return hasInfo
    }

class ClassReadException(msg: String) : Exception(msg)

data class Flags(val value: Int) : Comparable<Flags> {
    companion object {
        val readAll = Flags(0)
        val readSkipDebug = Flags(ClassReader.SKIP_DEBUG)
        val readSkipFrames = Flags(ClassReader.SKIP_FRAMES)
        val readCodeOnly = readSkipDebug + readSkipFrames

        val writeComputeNone = Flags(0)
        val writeComputeFrames = Flags(ClassWriter.COMPUTE_FRAMES)
        val writeComputeMaxs = Flags(ClassWriter.COMPUTE_MAXS)
        val writeComputeAll = writeComputeFrames
    }

    fun merge(other: Flags) = Flags(this.value or other.value)
    operator fun plus(other: Flags) = this.merge(other)

    override fun compareTo(other: Flags) = value.compareTo(other.value)
}

class JClassWriter(private val jirClassPath: JIRClasspath, flags: Flags) : ClassWriter(flags.value) {

    override fun getCommonSuperClass(type1: String, type2: String): String = try {
        val type1WithDots = type1.replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)
        val type2WithDots = type2.replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)
        var class1 = jirClassPath.findClassOrNull(type1WithDots) ?: error("$type1 is not in opentaint-ir cp")
        val class2 = jirClassPath.findClassOrNull(type2WithDots) ?: error("$type2 is not in opentaint-ir cp")

        when {
            class2.isSubClassOf(class1) -> type1
            class1.isSubClassOf(class2) -> type2
            class1.isInterface || class2.isInterface -> OBJECT_TYPE
            else -> {
                do {
                    class1 = class1.superClass ?: jirClassPath.objectClass
                } while (!class2.isSubClassOf(class1))
                class1.name.replace(Package.CANONICAL_SEPARATOR, Package.SEPARATOR)
            }
        }
    } catch (e: Throwable) {
        OBJECT_TYPE
    }

    companion object {
        private const val OBJECT_TYPE = "java/lang/Object"
    }
}

class JarBuilder(val name: String, manifest: Manifest) {
    private val jar = JarOutputStream(FileOutputStream(name), manifest)

    fun add(source: File) {
        if (source.isDirectory) {
            var name = source.path.replace("\\", "/")
            if (name.isNotEmpty()) {
                if (!name.endsWith("/"))
                    name += "/"
                val entry = JarEntry(name)
                entry.time = source.lastModified()
                jar.putNextEntry(entry)
                jar.closeEntry()
            }

        } else {
            val entry = JarEntry(source.path.replace("\\", "/"))
            entry.time = source.lastModified()
            add(entry, FileInputStream(source))
        }
    }

    operator fun plusAssign(source: File) {
        add(source)
    }

    fun add(entry: JarEntry, fis: InputStream) {
        jar.putNextEntry(entry)
        val `in` = BufferedInputStream(fis)

        val buffer = ByteArray(1024)
        while (true) {
            val count = `in`.read(buffer)
            if (count == -1) break
            jar.write(buffer, 0, count)
        }
        jar.closeEntry()
    }

    fun close() {
        jar.close()
    }
}

internal fun readClassNode(input: InputStream, flags: Flags = Flags.readAll): ClassNode {
    val classReader = ClassReader(input)
    val classNode = ClassNode()
    classReader.accept(classNode, flags.value)
    return classNode
}

fun ByteArray.toClassNode(): ClassNode {
    val classReader = ClassReader(this.inputStream())
    val classNode = ClassNode()
    classReader.accept(classNode, Flags.readAll.value)
    return classNode
}

fun ClassNode.toByteArray(
    jirClassPath: JIRClasspath,
    flags: Flags = Flags.writeComputeAll,
    checkClass: Boolean = false
): ByteArray {
    inlineJsrs()
    //Workaround for bug with locals translation
    methods?.map { it?.localVariables?.size }
    val cw = JClassWriter(jirClassPath, flags)
    val adapter = when {
        checkClass -> CheckClassAdapter(cw)
        else -> cw
    }
    this.accept(adapter)
    return cw.toByteArray()
}

fun ClassNode.write(
    jirClassPath: JIRClasspath,
    path: Path,
    flags: Flags = Flags.writeComputeAll,
    checkClass: Boolean = false
): File =
    path.toFile().apply {
        parentFile?.mkdirs()
        this.writeBytes(this@write.toByteArray(jirClassPath, flags, checkClass))
    }

internal class LabelFilterer(private val mn: MethodNode) {

    fun build(): MethodNode {
        val instructionList = mn.instructions
        val replacementsList = MutableList(instructionList.size()) { -1 }

        val new = MethodNode(mn.access, mn.name, mn.desc, mn.signature, mn.exceptions.toTypedArray())
        var prev: Int = -1
        for ((index, inst) in instructionList.withIndex()) {
            if (prev != -1 && inst is LabelNode) {
                var actualPrev = prev
                while (replacementsList[actualPrev] != -1)
                    actualPrev = replacementsList[actualPrev]
                replacementsList[index] = actualPrev
            }
            prev = if (inst is LabelNode) index else -1
        }

        val clonedLabelsList = instructionList.map { if (it is LabelNode) LabelNode(Label()) else null }
        val newReplacements = clonedLabelsList.mapIndexedNotNullTo(mutableMapOf()) { index, label ->
            if (label != null) {
                val first = instructionList[index] as LabelNode
                val second = when {
                    replacementsList[index] != -1 -> clonedLabelsList[replacementsList[index]]!!
                    else -> label
                }
                first to second
            } else null
        }

        for ((index, inst) in instructionList.withIndex()) {
            val newInst = when (inst) {
                is LabelNode -> when {
                    replacementsList[index] != -1 -> null
                    clonedLabelsList[index] != null -> clonedLabelsList[index]!!
                    else -> inst.clone(newReplacements)
                }

                else -> inst.clone(newReplacements)
            }
            if (newInst != null) new.instructions.add(newInst)
        }

        for (tryCatch in mn.tryCatchBlocks) {
            val tcb = TryCatchBlockNode(
                newReplacements.getValue(tryCatch.start), newReplacements.getValue(tryCatch.end),
                newReplacements.getValue(tryCatch.handler), tryCatch.type
            )
            tcb.visibleTypeAnnotations = tryCatch.visibleTypeAnnotations?.toList()
            tcb.invisibleTypeAnnotations = tryCatch.invisibleTypeAnnotations?.toList()
            new.tryCatchBlocks.add(tcb)
        }

        new.visibleParameterAnnotations = mn.visibleParameterAnnotations?.clone()
        new.invisibleParameterAnnotations = mn.invisibleParameterAnnotations?.clone()

        new.visibleAnnotableParameterCount = mn.visibleAnnotableParameterCount
        new.invisibleAnnotableParameterCount = mn.invisibleAnnotableParameterCount

        new.visibleAnnotations = mn.visibleAnnotations?.toList()
        new.invisibleAnnotations = mn.invisibleAnnotations?.toList()
        new.annotationDefault = mn.annotationDefault

        new.parameters = mn.parameters?.toList().orEmpty()

        new.maxStack = mn.maxStack
        new.maxLocals = mn.maxLocals

        return new
    }
}
