package org.opentaint.ir.impl.fs

import kotlinx.collections.immutable.toImmutableList
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.impl.types.AnnotationInfo
import org.opentaint.ir.impl.types.AnnotationValue
import org.opentaint.ir.impl.types.AnnotationValues
import org.opentaint.ir.impl.types.ClassInfo
import org.opentaint.ir.impl.types.ClassRef
import org.opentaint.ir.impl.types.EnumRef
import org.opentaint.ir.impl.types.FieldInfo
import org.opentaint.ir.impl.types.MethodInfo
import org.opentaint.ir.impl.types.OuterClassRef
import org.opentaint.ir.impl.types.ParameterInfo
import org.opentaint.ir.impl.types.PrimitiveValue

interface ByteCodeConverter {

    fun ClassNode.asClassInfo(bytecode: ByteArray) = ClassInfo(
        name = Type.getObjectType(name).className,
        signature = signature,
        access = access,

        outerClass = outerClassName(),
        innerClasses = innerClasses.map {
            Type.getObjectType(it.name).className
        }.toImmutableList(),
        outerMethod = outerMethod,
        outerMethodDesc = outerMethodDesc,
        superClass = superName?.let { Type.getObjectType(it).className },
        interfaces = interfaces.map { Type.getObjectType(it).className }.toImmutableList(),
        methods = methods.map { it.asMethodInfo() }.toImmutableList(),
        fields = fields.map { it.asFieldInfo() }.toImmutableList(),
        annotations = visibleAnnotations.asAnnotationInfos(true) + invisibleAnnotations.asAnnotationInfos(false),
        bytecode = bytecode
    )

    private fun ClassNode.outerClassName(): OuterClassRef? {
        val innerRef = innerClasses.firstOrNull { it.name == name }

        val direct = outerClass?.let { Type.getObjectType(it).className }
        if (direct == null && innerRef != null) {
            return OuterClassRef(Type.getObjectType(innerRef.outerName).className, innerRef.innerName)
        }
        return direct?.let {
            OuterClassRef(it, innerRef?.innerName)
        }
    }

    private fun Any.toAnnotationValue(): AnnotationValue {
        return when (this) {
            is Type -> ClassRef(className)
            is AnnotationNode -> asAnnotationInfo(true)
            is List<*> -> AnnotationValues(mapNotNull { it?.toAnnotationValue() })
            is Array<*> -> EnumRef(Type.getType((get(0) as String)).className, get(1) as String)
            is String, is Short, is Byte, is Boolean, is Long, is Double, is Float, is Int -> PrimitiveValue(this::class.java.simpleName, this)
            else -> throw IllegalStateException("Unknown type: ${javaClass.name}")
        }
    }

    private fun AnnotationNode.asAnnotationInfo(visible: Boolean): AnnotationInfo = AnnotationInfo(
        className = Type.getType(desc).className,
        visible = visible,
        values = values?.map { it.toAnnotationValue() }.orEmpty()
    )

    private fun List<AnnotationNode>?.asAnnotationInfos(visible: Boolean): List<AnnotationInfo> =
        orEmpty().map { it.asAnnotationInfo(visible) }.toImmutableList()

    private fun MethodNode.asMethodInfo(): MethodInfo {
        val params = Type.getArgumentTypes(desc).map { it.className }.toImmutableList()
        return MethodInfo(
            name = name,
            signature = signature,
            desc = desc,
            access = access,
            annotations = visibleAnnotations.asAnnotationInfos(true) + invisibleAnnotations.asAnnotationInfos(false),
            parametersInfo = parameters?.mapIndexed { index, node ->
                ParameterInfo(
                    index = index,
                    name = node.name,
                    access = node.access,
                    type = params[index],
                    annotations = (visibleParameterAnnotations?.get(index)?.let { it.asAnnotationInfos(true) }
                        .orEmpty() +
                            invisibleParameterAnnotations?.get(index)?.let { it.asAnnotationInfos(false) }
                                .orEmpty())
                        .takeIf { it.isNotEmpty() }
                )
            }.orEmpty()
        )
    }

    private fun FieldNode.asFieldInfo() = FieldInfo(
        name = name,
        signature = signature,
        access = access,
        type = Type.getType(desc).className,
        annotations = visibleAnnotations.asAnnotationInfos(true) + visibleAnnotations.asAnnotationInfos(false)
    )
}