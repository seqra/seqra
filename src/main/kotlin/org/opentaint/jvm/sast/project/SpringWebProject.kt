package org.opentaint.jvm.sast.project

import mu.KLogging
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDeclaration
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.jvm.ext.JAVA_OBJECT
import org.opentaint.ir.api.jvm.ext.allSuperHierarchySequence
import org.opentaint.ir.api.jvm.ext.findMethodOrNull
import org.opentaint.ir.api.jvm.ext.findType
import org.opentaint.ir.api.jvm.ext.jvmName
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.impl.bytecode.JIRDeclarationImpl
import org.opentaint.ir.impl.cfg.VirtualMethodRefImpl
import org.opentaint.ir.impl.features.classpaths.VirtualLocation
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualClassImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethod
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethodImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualParameter
import org.objectweb.asm.Opcodes
import org.opentaint.dataflow.jvm.util.JIRInstListBuilder
import org.opentaint.dataflow.jvm.util.typeName
import org.opentaint.util.name
import java.util.Objects

private val logger = object : KLogging() {}.logger

private val springControllerClassAnnotations = setOf(
    "org.springframework.stereotype.Controller",
    "org.springframework.web.bind.annotation.RestController",
)

private val springControllerMethodAnnotations = setOf(
    "org.springframework.web.bind.annotation.RequestMapping",
    "org.springframework.web.bind.annotation.GetMapping",
    "org.springframework.web.bind.annotation.PostMapping",
    "org.springframework.web.bind.annotation.PutMapping",
    "org.springframework.web.bind.annotation.DeleteMapping",
    "org.springframework.web.bind.annotation.PatchMapping",
)

private const val reactorMono = "reactor.core.publisher.Mono"
private const val reactorFlux = "reactor.core.publisher.Flux"

fun ProjectClasses.springWebProjectEntryPoints(
    cp: JIRClasspath,
    classPathExtensionFeature: ProjectClassPathExtensionFeature
): List<JIRMethod> {
    val controllerEpGenerator = SpringReactorEntryPointGenerator(cp, classPathExtensionFeature)

    val springControllerMethods = allProjectClasses()
        .filter { cls -> cls.annotations.any { it.jirClass?.name in springControllerClassAnnotations } }
        .flatMap { it.publicAndProtectedMethods() }
        .filterTo(mutableListOf()) { it.isSpringControllerMethod() }

    val springEntryPoints = springControllerMethods.map { controller ->
        when (controller.returnType.typeName) {
            reactorMono, reactorFlux -> {
                logger.debug { "Reactor spring controller: $controller" }
                controllerEpGenerator.generate(controller)
            }

            else -> {
                logger.debug { "Simple spring controller: $controller" }
                controller
            }
        }
    }

    return springEntryPoints
}

private fun JIRMethod.isSpringControllerMethod(): Boolean {
    if (annotations.any { it.jirClass?.name in springControllerMethodAnnotations }) return true

    return enclosingClass.allSuperHierarchySequence
        .mapNotNull { it.findMethodOrNull(name, description) }
        .any { m -> m.annotations.any { it.jirClass?.name in springControllerMethodAnnotations } }
}

private class SpringReactorEntryPointGenerator(
    private val cp: JIRClasspath,
    private val classPathExtensionFeature: ProjectClassPathExtensionFeature
) {
    private val controllerEpClasses = hashMapOf<JIRClassOrInterface, SpringEntryPointClass>()

    fun generate(controller: JIRMethod): JIRMethod {
        val cls = controllerClass(controller.enclosingClass)

        val controllerType = controller.enclosingClass.toType()
        val typedMethod = controllerType
            .findMethodOrNull(controller.name, controller.description)
            ?: error("Controller method $controller not found")

        val epMethodInstructions = JIRInstListBuilder()

        val epParams = listOf(
            JIRVirtualParameter(index = 0, controllerType.name.typeName())
        ) + controller.parameters.mapIndexed { index, parameter ->
            JIRVirtualParameter(index + 1, parameter.type)
        }

        val epReturnType = PredefinedPrimitives.Void.typeName()

        val entryPointMethod = SpringEntryPointMethod(
            name = controller.name,
            returnType = epReturnType,
            description = methodDescription(epParams.map { it.type }, epReturnType),
            parameters = epParams,
            instructions = epMethodInstructions
        ).also {
            cls.methods += it
            it.bind(cls)
        }

        val controllerMethodRef = VirtualMethodRefImpl.of(controllerType, typedMethod)
        val controllerCall = JIRVirtualCallExpr(
            controllerMethodRef,
            JIRArgument(index = 0, name = "controller", controllerType),
            typedMethod.parameters.mapIndexed { index, parameter ->
                val argIdx = index + 1
                JIRArgument(argIdx, parameter.name ?: "arg_$argIdx", parameter.type)
            }
        )

        val controllerResult = JIRLocalVar(index = 0, name = "controller_result", typedMethod.returnType)

        epMethodInstructions.addInstWithLocation(entryPointMethod) { loc ->
            JIRAssignInst(loc, controllerResult, controllerCall)
        }

        val monoType = cp.findType(reactorMono) as JIRClassType

        val controllerResultMono = when (controller.returnType.typeName) {
            reactorMono -> controllerResult
            reactorFlux -> {
                val fluxType = cp.findType(reactorFlux) as JIRClassType
                val fluxCollectListMethod = fluxType.findMethodOrNull(
                    "collectList", methodDescription(emptyList(), reactorMono.typeName())
                ) ?: error("Flux has no collectList method")

                val collectListMethodRef = VirtualMethodRefImpl.of(fluxType, fluxCollectListMethod)
                val collectListMethodCall = JIRVirtualCallExpr(collectListMethodRef, controllerResult, emptyList())
                val monoResult = JIRLocalVar(index = 1, name = "mono_result", monoType)

                epMethodInstructions.addInstWithLocation(entryPointMethod) { loc ->
                    JIRAssignInst(loc, monoResult, collectListMethodCall)
                }

                monoResult
            }

            else -> TODO("Unexpected return value type: $controller")
        }

        val monoBlockMethod = monoType.findMethodOrNull("block", methodDescription(emptyList(), JAVA_OBJECT.typeName()))
            ?: error("Mono type has no block method")

        val monoBlockMethodRef = VirtualMethodRefImpl.of(monoType, monoBlockMethod)
        val monoBlockMethodCall = JIRVirtualCallExpr(monoBlockMethodRef, controllerResultMono, emptyList())

        epMethodInstructions.addInstWithLocation(entryPointMethod) { loc ->
            JIRCallInst(loc, monoBlockMethodCall)
        }

        epMethodInstructions.addInstWithLocation(entryPointMethod) { loc ->
            JIRReturnInst(loc, returnValue = null)
        }

        return entryPointMethod
    }

    private fun controllerClass(controller: JIRClassOrInterface): SpringEntryPointClass =
        controllerEpClasses.getOrPut(controller) {
            SpringEntryPointClass("${controller.name}_EntryPoint", mutableListOf()).also {
                it.bindWithLocation(cp, controller.declaration.location)
                classPathExtensionFeature.extendClassPath(it)
            }
        }
}

class SpringEntryPointClass(
    name: String,
    val methods: MutableList<JIRVirtualMethod>,
) : JIRVirtualClassImpl(name, initialFields = emptyList(), initialMethods = methods) {
    private lateinit var declarationLocation: RegisteredLocation

    override val isAnonymous: Boolean get() = false

    override val interfaces: List<JIRClassOrInterface> get() = emptyList()

    override val declaration: JIRDeclaration
        get() =  JIRDeclarationImpl.of(declarationLocation, this)

    override fun hashCode(): Int = name.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is SpringEntryPointClass && name == other.name
    }

    override fun toString(): String = "(spring: $name)"

    override fun bind(classpath: JIRClasspath, virtualLocation: VirtualLocation) {
        bindWithLocation(classpath, virtualLocation)
    }

    fun bindWithLocation(classpath: JIRClasspath, location: RegisteredLocation) {
        this.classpath = classpath
        this.declarationLocation = location
    }
}

private class SpringEntryPointMethod(
    name: String,
    returnType: TypeName,
    description: String,
    parameters: List<JIRVirtualParameter>,
    private val instructions: JIRInstList<JIRInst>
) : JIRVirtualMethodImpl(
    name,
    access = Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
    returnType = returnType,
    parameters = parameters,
    description = description
) {
    override val instList: JIRInstList<JIRInst> get() = instructions

    override fun hashCode(): Int = Objects.hash(name, enclosingClass)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        return other is SpringEntryPointMethod && name == other.name && enclosingClass == other.enclosingClass
    }
}

private fun methodDescription(argumentTypes: List<TypeName>, returnType: TypeName): String = buildString {
    append("(")
    argumentTypes.forEach {
        append(it.typeName.jvmName())
    }
    append(")")
    append(returnType.typeName.jvmName())
}
