package org.opentaint.jvm.sast.project

import mu.KLogging
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDeclaration
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRPrimitiveType
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypedMethod
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRBool
import org.opentaint.ir.api.jvm.cfg.JIRByte
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRChar
import org.opentaint.ir.api.jvm.cfg.JIRDouble
import org.opentaint.ir.api.jvm.cfg.JIRFloat
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRInt
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRLong
import org.opentaint.ir.api.jvm.cfg.JIRNewExpr
import org.opentaint.ir.api.jvm.cfg.JIRNullConstant
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRShort
import org.opentaint.ir.api.jvm.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.jvm.ext.JAVA_OBJECT
import org.opentaint.ir.api.jvm.ext.allSuperHierarchySequence
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.findMethodOrNull
import org.opentaint.ir.api.jvm.ext.findType
import org.opentaint.ir.api.jvm.ext.isSubClassOf
import org.opentaint.ir.api.jvm.ext.jvmName
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.api.jvm.ext.void
import org.opentaint.ir.impl.bytecode.JIRDeclarationImpl
import org.opentaint.ir.impl.cfg.TypedSpecialMethodRefImpl
import org.opentaint.ir.impl.cfg.TypedStaticMethodRefImpl
import org.opentaint.ir.impl.cfg.VirtualMethodRefImpl
import org.opentaint.ir.impl.features.classpaths.VirtualLocation
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualClassImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethod
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethodImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualParameter
import org.objectweb.asm.Opcodes
import org.opentaint.dataflow.jvm.util.JIRInstListBuilder
import org.opentaint.dataflow.jvm.util.typeName
import java.util.Objects

private val logger = object : KLogging() {}.logger

private const val SpringPackage = "org.springframework"

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

private const val SpringModelAttribute = "org.springframework.web.bind.annotation.ModelAttribute"
private const val SpringPathVariable = "org.springframework.web.bind.annotation.PathVariable"

private const val SpringValidator = "org.springframework.validation.Validator"
private const val SpringBindingResult = "org.springframework.validation.BindingResult"
private const val SpringBeanBindingResult = "org.springframework.validation.BeanPropertyBindingResult"

private const val ReactorMono = "reactor.core.publisher.Mono"
private const val ReactorFlux = "reactor.core.publisher.Flux"

fun ProjectClasses.springWebProjectEntryPoints(cp: JIRClasspath): List<JIRMethod> {
    val controllerEpGenerator = SpringControllerEntryPointGenerator(cp, this)

    val springEntryPoints = mutableListOf<JIRMethod>()

    val springControllerMethods = allProjectClasses()
        .filter { cls -> cls.annotations.any { it.jirClass?.name in springControllerClassAnnotations } }
        .flatMap { it.publicAndProtectedMethods() }
        .filterTo(mutableListOf()) { it.isSpringControllerMethod() }

    springControllerMethods.mapTo(springEntryPoints) { controller ->
        when (controller.returnType.typeName) {
            ReactorMono, ReactorFlux -> {
                logger.debug { "Reactor spring controller: $controller" }
                controllerEpGenerator.generate(controller)
            }

            else -> {
                logger.debug { "Simple spring controller: $controller" }
                controllerEpGenerator.generate(controller)
            }
        }
    }

    return springEntryPoints
}

fun JIRAnnotation.isSpringAutowiredAnnotation(): Boolean =
    jirClass?.name == "org.springframework.beans.factory.annotation.Autowired"

private fun JIRMethod.isSpringControllerMethod(): Boolean {
    if (annotations.any { it.jirClass?.name in springControllerMethodAnnotations }) return true

    return enclosingClass.allSuperHierarchySequence
        .mapNotNull { it.findMethodOrNull(name, description) }
        .any { m -> m.annotations.any { it.jirClass?.name in springControllerMethodAnnotations } }
}

fun JIRAnnotation.isSpringValidated(): Boolean =
    jirClass?.name == "jakarta.validation.Valid"

fun JIRAnnotation.isSpringPathVariable(): Boolean =
    jirClass?.name == SpringPathVariable

fun JIRAnnotation.isSpringModelAttribute(): Boolean =
    jirClass?.name == SpringModelAttribute

private class SpringControllerEntryPointGenerator(
    private val cp: JIRClasspath,
    private val projectClasses: ProjectClasses
) {
    private val validators by lazy {
        val springValidatorCls = cp.findClass(SpringValidator)
        projectClasses.allProjectClasses().filterTo(mutableListOf()) { cls ->
            cls.isSubClassOf(springValidatorCls)
        }
    }

    // According to https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/bind/annotation/ModelAttribute.html
    private fun defaultModelAttributeNameForType(type: JIRType): String? {
        if (type !is JIRClassType || type.typeParameters.isNotEmpty()) {
            // TODO
            return null
        }
        return type.jirClass.simpleName.let { name ->
            name.replaceFirst(name[0], name[0].lowercaseChar())
        }
    }

    fun generate(controller: JIRMethod): JIRMethod {
        val cls = controllerClass(controller.enclosingClass)

        val controllerType = controller.enclosingClass.toType()
        val typedMethod = controllerType
            .findMethodOrNull(controller.name, controller.description)
            ?: error("Controller method $controller not found")

        val instructions = JIRInstListBuilder()

        val epReturnType = PredefinedPrimitives.Void.typeName()

        val entryPointMethod = SpringGeneratedMethod(
            name = controller.name,
            returnType = epReturnType,
            description = methodDescription(emptyList(), epReturnType),
            parameters = emptyList(),
            instructions = instructions
        ).also {
            cls.methods += it
            it.bind(cls)
        }

        val controllerInstance = instructions.loadSpringComponent(
            entryPointMethod, controllerType.jirClass, "controller"
        )

        val bindingResultCls = cp.findClass(SpringBindingResult)
        val bindingResultInstance by lazy {
            val bindingResultImplCls = cp.findClass(SpringBeanBindingResult)
            instructions.loadSpringComponent(entryPointMethod, bindingResultImplCls, "binding_result")
        }

        val pathVariables = hashMapOf<String, JIRValue>()
        val modelAttributes = hashMapOf<String, JIRValue>()

        fun getOrCreateNewArgument(typedMethod: JIRTypedMethod, index: Int): JIRValue {
            val param = typedMethod.parameters[index]
            val jirParam = typedMethod.method.parameters[index]

            val pathVariable = jirParam.annotations
                .singleOrNull { it.isSpringPathVariable() }
                ?.let { pathVariableAnnotation ->
                    pathVariableAnnotation.values["value"] as? String ?: jirParam.name
                }

            if (pathVariable != null) {
                pathVariables[pathVariable]?.let { return it }
            }

            val modelAttribute = jirParam.annotations
                .singleOrNull { it.isSpringModelAttribute() }
                ?.let { modelAttributeAnnotation ->
                    modelAttributeAnnotation.values["value"] as? String
                        ?: defaultModelAttributeNameForType(param.type)
                }.takeIf { pathVariable == null }

            if (modelAttribute != null) {
                modelAttributes[modelAttribute]?.let { return it }
            }

            val paramName = if (pathVariable != null) {
                "pathVariable_$pathVariable"
            } else if (modelAttribute != null) {
                "modelAttribute_$modelAttribute"
            } else {
                "${typedMethod.name}_param_$index"
            }

            val paramValue = JIRLocalVar(instructions.nextLocalVarIdx(), name = paramName, param.type)

            val valueToAssign = when (val type = param.type) {
                is JIRPrimitiveType -> generateStubValue(type)

                is JIRClassType -> {
                    val paramCls = type.jirClass
                    when {
                        paramCls.name.startsWith("java.lang") -> generateStubValue(type)
                        paramCls.isSubClassOf(bindingResultCls) -> bindingResultInstance
                        paramCls.declaration.location in projectClasses.projectLocations -> {
                            instructions.addInstWithLocation(entryPointMethod) { loc ->
                                JIRAssignInst(loc, paramValue, JIRNewExpr(type))
                            }

                            val ctor = paramCls.declaredMethods
                                .singleOrNull { it.isConstructor && it.parameters.isEmpty() }

                            if (ctor != null) {
                                val ctorCall = JIRSpecialCallExpr(ctor.specialMethodRef(), paramValue, emptyList())
                                instructions.addInstWithLocation(entryPointMethod) { loc ->
                                    JIRCallInst(loc, ctorCall)
                                }
                            } else {
                                logger.warn { "No constructor for $paramCls" }
                            }

                            null // paramValue already assigned with new expr
                        }

                        paramCls.packageName.startsWith(SpringPackage) -> {
                            instructions.loadSpringComponent(entryPointMethod, paramCls, "param")
                        }

                        else -> {
                            logger.warn { "Unsupported parameter class: $paramCls" }
                            JIRNullConstant(type)
                        }
                    }
                }

                else -> {
                    logger.warn { "Unsupported parameter class: ${type.typeName}" }
                    JIRNullConstant(type)
                }
            }

            if (valueToAssign != null) {
                instructions.addInstWithLocation(entryPointMethod) { loc ->
                    JIRAssignInst(loc, paramValue, valueToAssign)
                }
            }

            if (jirParam.annotations.any { it.isSpringValidated() }) {
                // todo: better validator resolution
                for (validator in validators) {
                    val validatorType = validator.toType()
                    val validateMethod = validatorType.methods.firstOrNull {
                        it.name == "validate" && it.parameters.size == 2
                    } ?: continue

                    val validatorInstance = instructions.loadSpringComponent(
                        entryPointMethod, validator, "validator"
                    )

                    val validateMethodRef = VirtualMethodRefImpl.of(validatorType, validateMethod)
                    val validateMethodCall = JIRVirtualCallExpr(
                        validateMethodRef, validatorInstance,
                        listOf(paramValue, bindingResultInstance)
                    )

                    instructions.addInstWithLocation(entryPointMethod) { loc ->
                        JIRCallInst(loc, validateMethodCall)
                    }
                }
            }

            return paramValue.also {
                if (pathVariable != null) {
                    pathVariables[pathVariable] = it
                }
                if (modelAttribute != null) {
                    modelAttributes[modelAttribute] = it
                }
            }
        }

        fun generateMethodCall(typedMethod: JIRTypedMethod, returnValueVarName: String? = null): JIRLocalVar? {
            val methodRef = VirtualMethodRefImpl.of(controllerType, typedMethod)
            val methodCall = JIRVirtualCallExpr(
                methodRef,
                controllerInstance,
                typedMethod.parameters.indices.map { getOrCreateNewArgument(typedMethod, it) }
            )

            return if (typedMethod.returnType == cp.void) {
                instructions.addInstWithLocation(entryPointMethod) { loc ->
                    JIRCallInst(loc, methodCall)
                }
                null
            } else {
                val controllerResult = JIRLocalVar(
                    instructions.nextLocalVarIdx(),
                    name = returnValueVarName ?: "${typedMethod.name}_result",
                    typedMethod.returnType
                )
                instructions.addInstWithLocation(entryPointMethod) { loc ->
                    JIRAssignInst(loc, controllerResult, methodCall)
                }

                controllerResult
            }
        }

        controllerType.methods.forEach { method ->
            // Adding calls to methods annotated with @ModelAttribute
            // TODO: call these methods in proper order
            //  (https://github.com/spring-projects/spring-framework/commit/56a82c1cbe8276408f9fff06cfb1ac9da7961a80)
            val modelAttributeAnnotation = method.method.annotations.singleOrNull { it.isSpringModelAttribute() }
                ?: return@forEach

            val modelAttributeName = modelAttributeAnnotation.values["value"] as? String
                ?: defaultModelAttributeNameForType(method.returnType)

            val returnValueVarName = modelAttributeName?.let { "modelAttribute_$it" }
            val result = generateMethodCall(method, returnValueVarName) ?: return@forEach

            if (modelAttributeName != null) {
                modelAttributes[modelAttributeName] = result
            }
        }

        val controllerResult = generateMethodCall(typedMethod)

        if (controllerResult != null) {
            val returnType = controller.returnType.typeName
            if (returnType == ReactorMono || returnType == ReactorFlux) {
                generateReactorMonoBlock(instructions, entryPointMethod, returnType, controllerResult)
            }
        }

        instructions.addInstWithLocation(entryPointMethod) { loc ->
            JIRReturnInst(loc, returnValue = null)
        }

        return entryPointMethod
    }

    private fun generateStubValue(type: JIRType): JIRValue = when (type) {
        is JIRPrimitiveType -> when (type.typeName) {
            PredefinedPrimitives.Boolean -> JIRBool(true, type)
            PredefinedPrimitives.Byte -> JIRByte(0, type)
            PredefinedPrimitives.Char -> JIRChar('x', type)
            PredefinedPrimitives.Short -> JIRShort(0, type)
            PredefinedPrimitives.Int -> JIRInt(0, type)
            PredefinedPrimitives.Long -> JIRLong(0, type)
            PredefinedPrimitives.Float -> JIRFloat(0f, type)
            PredefinedPrimitives.Double -> JIRDouble(0.0, type)
            else -> TODO("Unsupported stub type: $type")
        }

        is JIRRefType -> when (type.typeName) {
            "java.lang.String" -> JIRStringConstant("stub", type)
            else -> {
                logger.warn { "Unsupported stub type: ${type.typeName}" }
                JIRNullConstant(type)
            }
        }

        else -> TODO("Unsupported stub type: $type")
    }

    private fun generateReactorMonoBlock(
        epMethodInstructions: JIRInstListBuilder,
        entryPointMethod: SpringGeneratedMethod,
        controllerTypeName: String,
        controllerResult: JIRValue
    ) {
        val monoType = cp.findType(ReactorMono) as JIRClassType

        val controllerResultMono = when (controllerTypeName) {
            ReactorMono -> controllerResult
            ReactorFlux -> {
                val fluxType = cp.findType(ReactorFlux) as JIRClassType
                val fluxCollectListMethod = fluxType.findMethodOrNull(
                    "collectList", methodDescription(emptyList(), ReactorMono.typeName())
                ) ?: error("Flux has no collectList method")

                val collectListMethodRef = VirtualMethodRefImpl.of(fluxType, fluxCollectListMethod)
                val collectListMethodCall = JIRVirtualCallExpr(collectListMethodRef, controllerResult, emptyList())
                val monoResult = JIRLocalVar(
                    epMethodInstructions.nextLocalVarIdx(),
                    name = "mono_result",
                    monoType
                )

                epMethodInstructions.addInstWithLocation(entryPointMethod) { loc ->
                    JIRAssignInst(loc, monoResult, collectListMethodCall)
                }

                monoResult
            }

            else -> TODO("Unexpected return value type: $controllerTypeName")
        }

        val monoBlockMethod = monoType.findMethodOrNull("block", methodDescription(emptyList(), JAVA_OBJECT.typeName()))
            ?: error("Mono type has no block method")

        val monoBlockMethodRef = VirtualMethodRefImpl.of(monoType, monoBlockMethod)
        val monoBlockMethodCall = JIRVirtualCallExpr(monoBlockMethodRef, controllerResultMono, emptyList())

        epMethodInstructions.addInstWithLocation(entryPointMethod) { loc ->
            JIRCallInst(loc, monoBlockMethodCall)
        }
    }

    private fun controllerClass(controller: JIRClassOrInterface): SpringGeneratedClass {
        val controllerClsName = "${controller.name}_Opentaint_EntryPoint"
        return springGeneratedClass(cp, controllerClsName, controller)
    }
}

private const val ComponentInstanceMethodName = "getInstance"

fun springComponentGetInstance(cp: JIRClasspath, component: JIRClassOrInterface): JIRMethod =
    springComponentRegistry(cp, component)
        .declaredMethods
        .single { it.name == ComponentInstanceMethodName }

fun springComponentRegistry(cp: JIRClasspath, component: JIRClassOrInterface): JIRClassOrInterface {
    val componentClsName = "${component.name}_Opentaint_Component_Registry"
    return springGeneratedClass(cp, componentClsName, component).also {
        it.initializeSpringComponent(component)
    }
}

fun JIRInstListBuilder.loadSpringComponent(
    method: JIRMethod,
    component: JIRClassOrInterface,
    name: String = "cmp"
): JIRValue {
    val idx = nextLocalVarIdx()
    val componentValue = JIRLocalVar(idx, name = "${name}_$idx", component.toType())
    addInstWithLocation(method) { loc ->
        val getInstance = springComponentGetInstance(component.classpath, component)
        val instanceCall = JIRStaticCallExpr(getInstance.staticMethodRef(), emptyList())
        JIRAssignInst(loc, componentValue, instanceCall)
    }
    return componentValue
}

private fun SpringGeneratedClass.initializeSpringComponent(component: JIRClassOrInterface) {
    if (methods.isNotEmpty()) return

    val instructions = JIRInstListBuilder()

    val componentTypeName = component.name.typeName()
    val componentInstanceMethod = SpringGeneratedMethod(
        name = ComponentInstanceMethodName,
        returnType = componentTypeName,
        description = methodDescription(emptyList(), componentTypeName),
        parameters = emptyList(),
        instructions = instructions
    ).also {
        methods += it
        it.bind(this)
    }

    val componentType = component.toType()
    val componentInstance = JIRLocalVar(instructions.nextLocalVarIdx(), name = "component", componentType)
    instructions.addInstWithLocation(componentInstanceMethod) { loc ->
        JIRAssignInst(loc, componentInstance, JIRNewExpr(componentType))
    }

    val componentConstructor = componentType.declaredMethods
        .filter { it.method.isConstructor && it.parameters.all { param -> param.type is JIRClassType } }
        .minByOrNull { it.parameters.size }

    if (componentConstructor != null) {
        val args = mutableListOf<JIRValue>()
        for (param in componentConstructor.parameters) {
            val paramClass = (param.type as JIRClassType).jirClass
            val paramInstance = instructions.loadSpringComponent(componentInstanceMethod, paramClass, "param")
            args += paramInstance
        }

        val componentConstructorCall = JIRSpecialCallExpr(
            componentConstructor.method.specialMethodRef(), componentInstance, args
        )

        instructions.addInstWithLocation(componentInstanceMethod) { loc ->
            JIRCallInst(loc, componentConstructorCall)
        }
    } else {
        logger.error("TODO: $componentTypeName has no constructor")
    }

    instructions.addInstWithLocation(componentInstanceMethod) { loc ->
        JIRReturnInst(loc, returnValue = componentInstance)
    }
}

private fun springGeneratedClass(cp: JIRClasspath, name: String, proto: JIRClassOrInterface): SpringGeneratedClass {
    val ext = cp.cpExt()
    if (ext.containsClass(name)) {
        return cp.findClass(name) as SpringGeneratedClass
    }

    return SpringGeneratedClass(name, mutableListOf()).also {
        it.bindWithLocation(cp, proto.declaration.location)
        ext.extendClassPath(it)
    }
}

private fun JIRClasspath.cpExt(): ProjectClassPathExtensionFeature =
    features.orEmpty().filterIsInstance<ProjectClassPathExtensionFeature>().single()

class SpringGeneratedClass(
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
        return other is SpringGeneratedClass && name == other.name
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

private class SpringGeneratedMethod(
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

        return other is SpringGeneratedMethod && name == other.name && enclosingClass == other.enclosingClass
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

fun JIRMethod.staticMethodRef(): TypedStaticMethodRefImpl {
    val clsType = enclosingClass.toType()
    return TypedStaticMethodRefImpl(clsType, name, parameters.map { it.type }, returnType)
}

fun JIRMethod.specialMethodRef(): TypedSpecialMethodRefImpl {
    val clsType = enclosingClass.toType()
    return TypedSpecialMethodRefImpl(clsType, name, parameters.map { it.type }, returnType)
}
