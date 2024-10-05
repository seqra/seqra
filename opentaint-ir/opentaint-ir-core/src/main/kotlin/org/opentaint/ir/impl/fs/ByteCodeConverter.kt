package org.opentaint.ir.impl.fs

import kotlinx.collections.immutable.toImmutableList
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import org.opentaint.ir.impl.types.*

interface ByteCodeConverter {

    fun ClassNode.asClassInfo() = ClassInfo(
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
        annotations = visibleAnnotations.asAnnotationInfos(true) + invisibleAnnotations.asAnnotationInfos(false)
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

    private fun AnnotationNode.asAnnotationInfo(visible: Boolean) = AnnotationInfo(
        className = Type.getType(desc).className,
        visible = visible,
        values = values.orEmpty()
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