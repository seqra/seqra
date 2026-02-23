package org.opentaint.jvm.sast.project.spring

import mu.KLogging
import org.opentaint.dataflow.jvm.util.JIRInstListBuilder
import org.opentaint.dataflow.jvm.util.typeName
import org.opentaint.ir.api.jvm.JIRAnnotated
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRPrimitiveType
import org.opentaint.ir.api.jvm.JIRRefType
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypedField
import org.opentaint.ir.api.jvm.JIRTypedMethod
import org.opentaint.ir.api.jvm.JIRTypedMethodParameter
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRBool
import org.opentaint.ir.api.jvm.cfg.JIRByte
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRChar
import org.opentaint.ir.api.jvm.cfg.JIRDouble
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRFloat
import org.opentaint.ir.api.jvm.cfg.JIRGotoInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation
import org.opentaint.ir.api.jvm.cfg.JIRInstRef
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
import org.opentaint.ir.api.jvm.cfg.JIRSwitchInst
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.jvm.ext.JAVA_OBJECT
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.findMethodOrNull
import org.opentaint.ir.api.jvm.ext.findType
import org.opentaint.ir.api.jvm.ext.int
import org.opentaint.ir.api.jvm.ext.isSubClassOf
import org.opentaint.ir.api.jvm.ext.jvmName
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.api.jvm.ext.void
import org.opentaint.ir.impl.cfg.TypedSpecialMethodRefImpl
import org.opentaint.ir.impl.cfg.TypedStaticMethodRefImpl
import org.opentaint.ir.impl.cfg.VirtualMethodRefImpl
import org.opentaint.ir.impl.cfg.util.isClass
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualField
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethod
import org.opentaint.ir.impl.types.JIRTypedFieldImpl
import org.opentaint.ir.impl.types.substition.JIRSubstitutorImpl
import org.opentaint.jvm.sast.dataflow.matchedAnnotations
import org.opentaint.jvm.sast.project.ProjectClassPathExtensionFeature
import org.opentaint.jvm.sast.project.ProjectClasses
import org.opentaint.jvm.sast.project.allProjectClasses
import org.opentaint.jvm.sast.project.publicAndProtectedMethods
import org.opentaint.jvm.util.toTypedMethod
import org.opentaint.jvm.util.typename

private val logger = object : KLogging() {}.logger

const val GeneratedSpringRegistry = "__spring_registry__"
const val GeneratedSpringControllerDispatcher = "__spring_dispatcher__"
const val GeneratedSpringControllerDispatcherDispatchMethod = "__dispatch__"
const val GeneratedSpringControllerDispatcherCleanupMethod = "__cleanup__"
const val GeneratedSpringControllerDispatcherInitMethod = "__init__"
const val GeneratedSpringControllerDispatcherSelectMethod = "__select__"

fun ProjectClasses.createSpringProjectContext(): SpringWebProjectContext? {
    val springControllerMethods = allProjectClasses()
        .filter { it.matchedAnnotations(String::isSpringControllerClassAnnotation).isNotEmpty() }
        .flatMap { it.publicAndProtectedMethods() }
        .filterTo(mutableSetOf()) { it.isSpringControllerMethod() }

    if (springControllerMethods.isEmpty()) return null

    val springCtx = SpringWebProjectContext(springControllerMethods, cp)

    val controllerEpGenerator = SpringControllerEntryPointGenerator(cp, this, springCtx)

    val springControllerWrappers = mutableListOf<JIRMethod>()
    springControllerMethods.mapNotNullTo(springControllerWrappers) { controller ->
        logger.debug {
            val controllerKind = when (controller.returnType.typeName) {
                ReactorMono, ReactorFlux -> "Reactor"
                else -> "Simple"
            }

            "$controllerKind spring controller: $controller"
        }

        runCatching {
            controllerEpGenerator.generate(controller)
        }.onFailure { ex ->
            logger.error(ex) { "Error while generating spring controller: $controller" }
        }.getOrNull()
    }

    springCtx.discoverConfigurationBeans(cp, this)

    springCtx.generateDispatcher(springControllerWrappers)

    springCtx.analyzeSpringRepositories(cp, this)

    return springCtx
}

fun SpringWebProjectContext.springWebProjectEntryPoints(): List<JIRMethod> {
    val dispatcher = controllerDispatcherMethods.first {
        it.name == GeneratedSpringControllerDispatcherDispatchMethod
    }
    return listOf(dispatcher)
}

class SpringWebProjectContext(
    val controllers: Set<JIRMethod>,
    cp: JIRClasspath
) {
    private val location = controllers.first().enclosingClass.declaration.location

    val controllerDispatcherMethods = mutableListOf<JIRVirtualMethod>()
    val controllerDispatcher = SpringGeneratedClass(
        name = GeneratedSpringControllerDispatcher,
        fields = mutableListOf(),
        methods = controllerDispatcherMethods,
    ).also {
        val ext = cp.cpExt()
        it.bindWithLocation(cp, location)
        ext.extendClassPath(it)
    }

    private val componentFields = mutableListOf<JIRVirtualField>()
    private val componentRegistry = SpringGeneratedClass(
        name = GeneratedSpringRegistry,
        fields = componentFields,
        methods = mutableListOf()
    ).also {
        val ext = cp.cpExt()
        it.bindWithLocation(cp, location)
        ext.extendClassPath(it)
    }

    private val componentRegistryType by lazy { componentRegistry.toType() }

    data class ComponentDependency(val componentType: JIRClassOrInterface, val field: JIRTypedField?)

    val componentDependencies = hashMapOf<JIRClassOrInterface, MutableSet<ComponentDependency>>()
    val fieldDependencies = hashMapOf<JIRField, MutableSet<JIRClassOrInterface>>()

    val componentRegistryField = hashMapOf<JIRClassOrInterface, JIRTypedField>()

    fun registryField(component: JIRClassOrInterface): JIRTypedField =
        componentRegistryField[component]
            ?: error("Component field not registered: $component")

    fun registryFieldRef(component: JIRClassOrInterface): JIRFieldRef =
        JIRFieldRef(instance = null, registryField(component))

    fun addComponent(component: JIRClassOrInterface): Boolean {
        val dependencies = componentDependencies[component]
        if (dependencies != null) return false

        componentDependencies[component] = mutableSetOf()

        val field = SpringGeneratedField(component.name, component.typename)
        field.bind(componentRegistry)

        componentFields += field

        val typedField = JIRTypedFieldImpl(componentRegistryType, field, JIRSubstitutorImpl.empty)
        componentRegistryField[component] = typedField
        return true
    }

    fun allComponents(): Set<JIRClassOrInterface> = componentDependencies.keys

    val springRepositoryMethods = hashMapOf<JIRMethod, RepositoryMethodInfo>()

    data class BeanMethodInfo(
        val configurationClass: JIRClassOrInterface,
        val beanMethod: JIRMethod,
        val beanType: JIRClassOrInterface
    )

    val beanMethods = hashMapOf<JIRClassOrInterface, BeanMethodInfo>()
}

private fun SpringWebProjectContext.generateDispatcher(controllerWrappers: List<JIRMethod>): JIRMethod {
    val initMethod = generateComponentInitializer()
    val cleanupMethod = generateCleanup()
    val selectMethod = generateSelect()
    val cp = controllerDispatcher.classpath

    val mutableInstructions = mutableListOf<JIRInst>()
    val instructions = JIRInstListBuilder(mutableInstructions)

    val returnType = PredefinedPrimitives.Void.typeName()
    val dispatcher = SpringGeneratedMethod(
        name = GeneratedSpringControllerDispatcherDispatchMethod,
        returnType = returnType,
        description = methodDescription(emptyList(), returnType),
        parameters = emptyList(),
        instructions = instructions
    ).also {
        controllerDispatcherMethods += it
        it.bind(controllerDispatcher)
    }

    instructions.addInstWithLocation(dispatcher) { loc ->
        val initCall = JIRStaticCallExpr(initMethod.staticMethodRef(), emptyList())
        JIRCallInst(loc, initCall)
    }

    val selectValue = JIRLocalVar(index = 0, "%sel", cp.int)

    val loopStart: JIRInstLocation
    instructions.addInstWithLocation(dispatcher) { loc ->
        loopStart = loc
        val selectCall = JIRStaticCallExpr(selectMethod.staticMethodRef(), emptyList())
        JIRAssignInst(loc, selectValue, selectCall)
    }

    val switchLoc: JIRInstLocation
    instructions.addInstWithLocation(dispatcher) { loc ->
        switchLoc = loc
        JIRAssignInst(loc, selectValue, selectValue) // nop
    }

    val blocks = controllerWrappers.map { cwm ->
        val blockStart: JIRInstLocation
        val blockEnd: JIRInstLocation

        instructions.addInstWithLocation(dispatcher) { loc ->
            blockStart = loc
            val cwmCall = JIRStaticCallExpr(cwm.staticMethodRef(), emptyList())
            JIRCallInst(loc, cwmCall)
        }

        instructions.addInstWithLocation(dispatcher) { loc ->
            blockEnd = loc
            JIRAssignInst(loc, selectValue, selectValue) // nop
        }

        blockStart to blockEnd
    }

    val loopEnd: JIRInstLocation
    instructions.addInstWithLocation(dispatcher) { loc ->
        loopEnd = loc
        val cleanupCall = JIRStaticCallExpr(cleanupMethod.staticMethodRef(), emptyList())
        JIRCallInst(loc, cleanupCall)
    }

    instructions.addInstWithLocation(dispatcher) { loc ->
        JIRGotoInst(loc, JIRInstRef(loopStart.index)) // infinite loop
    }

    instructions.addInstWithLocation(dispatcher) { loc ->
        JIRReturnInst(loc, returnValue = null)
    }

    val switchBranches = hashMapOf<JIRValue, JIRInstRef>()
    for ((i, block) in blocks.withIndex()) {
        val (blockStart, blockEnd) = block
        mutableInstructions[blockEnd.index] = JIRGotoInst(blockEnd, JIRInstRef(loopEnd.index))
        switchBranches[JIRInt(i, cp.int)] = JIRInstRef(blockStart.index)
    }

    val dispatchInst = JIRSwitchInst(switchLoc, selectValue, switchBranches, JIRInstRef(loopEnd.index))
    mutableInstructions[switchLoc.index] = dispatchInst

    return dispatcher
}

private fun SpringWebProjectContext.generateSelect(): JIRMethod {
    val instructions = JIRInstListBuilder()

    val returnType = PredefinedPrimitives.Int.typeName()
    val selectMethod = SpringGeneratedMethod(
        name = GeneratedSpringControllerDispatcherSelectMethod,
        returnType = returnType,
        description = methodDescription(emptyList(), returnType),
        parameters = emptyList(),
        instructions = instructions
    ).also {
        controllerDispatcherMethods += it
        it.bind(controllerDispatcher)
    }

    instructions.addInstWithLocation(selectMethod) { loc ->
        JIRReturnInst(loc, returnValue = JIRInt(0, controllerDispatcher.classpath.int))
    }

    return selectMethod
}

private fun SpringWebProjectContext.generateCleanup(): JIRMethod {
    val instructions = JIRInstListBuilder()

    val returnType = PredefinedPrimitives.Void.typeName()
    val cleanupMethod = SpringGeneratedMethod(
        name = GeneratedSpringControllerDispatcherCleanupMethod,
        returnType = returnType,
        description = methodDescription(emptyList(), returnType),
        parameters = emptyList(),
        instructions = instructions
    ).also {
        controllerDispatcherMethods += it
        it.bind(controllerDispatcher)
    }

    instructions.addInstWithLocation(cleanupMethod) { loc ->
        JIRReturnInst(loc, returnValue = null)
    }

    return cleanupMethod
}

private fun SpringWebProjectContext.generateComponentInitializer(): JIRMethod {
    val instructions = JIRInstListBuilder()

    val returnType = PredefinedPrimitives.Void.typeName()
    val initMethod = SpringGeneratedMethod(
        name = GeneratedSpringControllerDispatcherInitMethod,
        returnType = returnType,
        description = methodDescription(emptyList(), returnType),
        parameters = emptyList(),
        instructions = instructions
    ).also {
        controllerDispatcherMethods += it
        it.bind(controllerDispatcher)
    }

    generateComponentInitializerBody(instructions, initMethod)

    instructions.addInstWithLocation(initMethod) { loc ->
        JIRReturnInst(loc, returnValue = null)
    }

    return initMethod
}

private fun SpringWebProjectContext.generateComponentInitializerBody(
    instructions: JIRInstListBuilder, initMethod: JIRMethod
) {
    val generatedComponents = mutableMapOf<JIRClassOrInterface, JIRValue>()
    componentDependencies.keys.forEach {
        instructions.generateComponentInitialization(this, initMethod, it, generatedComponents)
    }
}

private fun JIRInstListBuilder.generateComponentInitialization(
    springCtx: SpringWebProjectContext,
    initMethod: JIRMethod,
    component: JIRClassOrInterface,
    generatedComponents: MutableMap<JIRClassOrInterface, JIRValue>
): JIRValue? {
    val generatedValue = generatedComponents[component]
    if (generatedValue != null) return generatedValue

    if (component !in springCtx.componentRegistryField) {
        logger.warn("Component missed in registry: $component")
        return null
    }

    val componentType = component.toType()
    val componentValue = nextLocalVar(component.name, componentType)
    generatedComponents[component] = componentValue

    val beanMethodInfo = springCtx.beanMethods[component]
    if (beanMethodInfo != null) {
        generateBeanMethodCall(springCtx, initMethod, beanMethodInfo, componentValue, generatedComponents)
    } else {
        addInstWithLocation(initMethod) { loc ->
            JIRAssignInst(loc, componentValue, JIRNewExpr(componentType))
        }

        generateComponentCtorCall(springCtx, initMethod, component, componentValue, generatedComponents)
    }

    generatePostConstructCalls(initMethod, component, componentValue)

    val componentFieldRef = springCtx.registryFieldRef(component)
    addInstWithLocation(initMethod) { loc ->
        JIRAssignInst(loc, componentFieldRef, componentValue)
    }

    return componentValue
}

private fun JIRInstListBuilder.generateBeanMethodCall(
    springCtx: SpringWebProjectContext,
    initMethod: JIRMethod,
    beanMethodInfo: SpringWebProjectContext.BeanMethodInfo,
    beanValue: JIRLocalVar,
    generatedComponents: MutableMap<JIRClassOrInterface, JIRValue>
) {
    val configClass = beanMethodInfo.configurationClass
    val beanMethod = beanMethodInfo.beanMethod

    val configInstance = generateComponentInitialization(
        springCtx, initMethod, configClass, generatedComponents
    ) ?: return

    val configType = configClass.toType()
    val typedBeanMethod = beanMethod.toTypedMethod

    val beanMethodArgs = typedBeanMethod.parameters.map { param ->
        generateComponentParamValue(param, springCtx, initMethod, generatedComponents)
            ?: return
    }

    val beanMethodRef = VirtualMethodRefImpl.of(configType, typedBeanMethod)
    val beanMethodCall = JIRVirtualCallExpr(beanMethodRef, configInstance, beanMethodArgs)

    addInstWithLocation(initMethod) { loc ->
        JIRAssignInst(loc, beanValue, beanMethodCall)
    }
}

private fun JIRInstListBuilder.generatePostConstructCalls(
    initMethod: JIRMethod,
    component: JIRClassOrInterface,
    instance: JIRValue
) {
    val componentType = component.toType()
    val postConstructMethods = component.declaredMethods.filter { method ->
        method.matchedAnnotations(String::isPostConstruct).isNotEmpty() &&
            method.parameters.isEmpty()
    }

    for (postConstruct in postConstructMethods) {
        val typedMethod = postConstruct.toTypedMethod

        val methodRef = VirtualMethodRefImpl.of(componentType, typedMethod)
        val methodCall = JIRVirtualCallExpr(methodRef, instance, emptyList())

        addInstWithLocation(initMethod) { loc ->
            JIRCallInst(loc, methodCall)
        }
    }
}

private fun JIRInstListBuilder.generateComponentCtorCall(
    springCtx: SpringWebProjectContext,
    initMethod: JIRMethod,
    component: JIRClassOrInterface,
    instance: JIRValue,
    generatedComponents: MutableMap<JIRClassOrInterface, JIRValue>
) {
    val componentCtor = component.findComponentConstructor()
        ?: return

    val typedCtor = componentCtor.toTypedMethod
    val ctorArgs = typedCtor.parameters.map {
        generateComponentParamValue(it, springCtx, initMethod, generatedComponents)
            ?: return
    }

    val ctorCall = JIRSpecialCallExpr(componentCtor.specialMethodRef(), instance, ctorArgs)
    addInstWithLocation(initMethod) { loc ->
        JIRCallInst(loc, ctorCall)
    }
}

private fun JIRInstListBuilder.generateComponentParamValue(
    parameter: JIRTypedMethodParameter,
    springCtx: SpringWebProjectContext,
    initMethod: JIRMethod,
    generatedComponents: MutableMap<JIRClassOrInterface, JIRValue>
): JIRValue? {
    val paramType = parameter.type
    var paramValue: JIRValue? = null
    if (paramType is JIRRefType) {
        val paramCls = paramType.jIRClass
        paramValue = generateComponentInitialization(springCtx, initMethod, paramCls, generatedComponents)
    }

    if (paramValue == null) {
        paramValue = generateStubValue(paramType)
    }

    return paramValue
}

private fun SpringWebProjectContext.registerComponent(
    projectClasses: ProjectClasses,
    cp: JIRClasspath,
    cls: JIRClassOrInterface
) {
    // note: recursive dependency?
    if (!addComponent(cls)) return

    val clsType = cls.toType()
    val dependencies = componentDependencies.getValue(cls)

    val autowiredFields = cls.autowiredFields()
    for (awField in autowiredFields) {
        val dependency = cp.resolveAutowiredField(awField)
            ?: continue

        val typedAwField = clsType.declaredFields.first { it.name == awField.name }
        dependencies += SpringWebProjectContext.ComponentDependency(dependency, typedAwField)
        fieldDependencies.getOrPut(typedAwField.field, ::mutableSetOf).add(dependency)

        registerComponent(projectClasses, cp, dependency)
    }

    if (!projectClasses.isProjectClass(cls)) return

    val componentCtor = cls.findComponentConstructor()
    if (componentCtor != null) {
        for (param in componentCtor.parameters) {
            val dependency = cp.resolveComponentConstructorParam(componentCtor, param.index)
                ?: continue

            val dependencyField = clsType.declaredFields.firstOrNull {
                val type = it.type
                type is JIRClassType && type.jIRClass == dependency
            }

            dependencies += SpringWebProjectContext.ComponentDependency(dependency, dependencyField)
            dependencyField?.let {
                fieldDependencies.getOrPut(it.field, ::mutableSetOf).add(dependency)
            }

            registerComponent(projectClasses, cp, dependency)
        }
    }
}

private fun SpringWebProjectContext.discoverConfigurationBeans(
    cp: JIRClasspath,
    projectClasses: ProjectClasses
) {
    val configurationClasses = projectClasses.allProjectClasses()
        .filter { it.matchedAnnotations(String::isSpringConfiguration).isNotEmpty() }

    for (configClass in configurationClasses) {
        registerComponent(projectClasses, cp, configClass)

        val beanMethods = configClass.declaredMethods.filter { method ->
            method.matchedAnnotations(String::isSpringBean).isNotEmpty()
        }

        for (beanMethod in beanMethods) {
            val beanType = cp.findClassOrNull(beanMethod.returnType.typeName)
            if (beanType == null) {
                logger.warn { "Cannot resolve bean type for @Bean method: ${configClass.name}.${beanMethod.name}" }
                continue
            }

            addComponent(beanType)

            this.beanMethods[beanType] = SpringWebProjectContext.BeanMethodInfo(
                configurationClass = configClass,
                beanMethod = beanMethod,
                beanType = beanType
            )

            for (param in beanMethod.parameters) {
                val dependency = cp.findClassOrNull(param.type.typeName) ?: continue
                registerComponent(projectClasses, cp, dependency)
            }

            logger.debug { "Registered @Bean method: ${configClass.name}.${beanMethod.name} -> ${beanType.name}" }
        }
    }
}

private class SpringControllerEntryPointGenerator(
    private val cp: JIRClasspath,
    private val projectClasses: ProjectClasses,
    private val springCtx: SpringWebProjectContext,
) {
    private val validators by lazy {
        val springValidatorCls = cp.findClass(SpringValidator)
        projectClasses.allProjectClasses().filterTo(mutableListOf()) { cls ->
            cls.isSubClassOf(springValidatorCls)
        }
    }

    private val JIRAnnotated.jakartaConstraintValidators: List<JIRClassOrInterface>
        get() {
            return matchedAnnotations(String::isJakartaConstraint).flatMap { constraintAnnotation ->
                val validatedBy = constraintAnnotation.values["validatedBy"] as? List<*>
                    ?: return@flatMap emptyList()

                validatedBy.filterIsInstance<JIRClassOrInterface>()
            }
        }

    private val bindingResultCls by lazy { cp.findClass(SpringBindingResult) }

    private inner class GenerationCtx(
        val generatedMethod: SpringGeneratedMethod,
        val controllerType: JIRClassType,
        val typedControllerMethod: JIRTypedMethod,
        val instructions: JIRInstListBuilder,
    ) {
        val generatedComponents = mutableMapOf<JIRClassOrInterface, JIRValue>()

        val pathVariables = hashMapOf<String, JIRValue>()
        val modelAttributes = hashMapOf<String, JIRValue>()

        val bindingResultInstance by lazy {
            val bindingResultImplCls = cp.findClass(SpringBeanBindingResult)
            loadSpringComponent(bindingResultImplCls, "binding_result")
        }

        val controllerInstance by lazy {
            loadSpringComponent(controllerType.jIRClass, "controller")
        }

        fun nextLocalVar(name: String, type: JIRType): JIRLocalVar =
            instructions.nextLocalVar(name, type)

        fun addInstWithLocation(buildInst: (JIRInstLocation) -> JIRInst) =
            instructions.addInstWithLocation(generatedMethod, buildInst)
    }

    private fun GenerationCtx.loadSpringComponent(
        component: JIRClassOrInterface,
        name: String = "cmp"
    ): JIRValue {
        val generatedValue = generatedComponents[component]
        if (generatedValue != null) return generatedValue

        val componentValue = nextLocalVar(name, component.toType())
        generatedComponents[component] = componentValue

        springCtx.registerComponent(projectClasses, cp, component)

        val componentFieldRef = springCtx.registryFieldRef(component)
        addInstWithLocation { loc ->
            JIRAssignInst(loc, componentValue, componentFieldRef)
        }

        return componentValue
    }

    private fun createGenerationCtx(controller: JIRMethod): GenerationCtx {
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

        return GenerationCtx(entryPointMethod, controllerType, typedMethod, instructions)
    }

    fun generate(controller: JIRMethod): JIRMethod {
        val ctx = createGenerationCtx(controller)

        ctx.generatedModelAttributeCalls()

        val controllerResult = ctx.generateControllerMethodCall(ctx.typedControllerMethod)

        if (controllerResult != null) {
            val returnType = controller.returnType.typeName
            if (returnType == ReactorMono || returnType == ReactorFlux) {
                ctx.generateReactorMonoBlock(returnType, controllerResult)
            }
        }

        ctx.addInstWithLocation { loc ->
            JIRReturnInst(loc, returnValue = null)
        }

        return ctx.generatedMethod
    }

    private fun GenerationCtx.generatedModelAttributeCalls() {
        controllerType.methods.forEach { method ->
            // Adding calls to methods annotated with @ModelAttribute
            // TODO: call these methods in proper order
            //  (https://github.com/spring-projects/spring-framework/commit/56a82c1cbe8276408f9fff06cfb1ac9da7961a80)
            val modelAttributeAnnotation =
                method.method.matchedAnnotations(String::isSpringModelAttribute).singleOrNull()
                    ?: return@forEach

            val modelAttributeName = modelAttributeAnnotation.values["value"] as? String
                ?: defaultModelAttributeNameForType(method.returnType)

            val returnValueVarName = modelAttributeName?.let { "modelAttribute_$it" }
            val result = generateControllerMethodCall(method, returnValueVarName) ?: return@forEach

            if (modelAttributeName != null) {
                modelAttributes[modelAttributeName] = result
            }
        }
    }

    // According to https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/bind/annotation/ModelAttribute.html
    private fun defaultModelAttributeNameForType(type: JIRType): String? {
        if (type !is JIRClassType || type.typeParameters.isNotEmpty()) {
            // TODO
            return null
        }
        return type.jIRClass.simpleName.let { name ->
            name.replaceFirst(name[0], name[0].lowercaseChar())
        }
    }

    private fun GenerationCtx.getOrCreateNewArgument(typedMethod: JIRTypedMethod, index: Int): JIRValue {
        val param = typedMethod.parameters[index]
        val jIRParam = typedMethod.method.parameters[index]

        val pathVariable = jIRParam.matchedAnnotations(String::isSpringPathVariable)
            .singleOrNull()
            ?.let { pathVariableAnnotation ->
                pathVariableAnnotation.values["value"] as? String ?: jIRParam.name
            }

        if (pathVariable != null) {
            pathVariables[pathVariable]?.let { return it }
        }

        val modelAttribute = jIRParam
            .matchedAnnotations(String::isSpringModelAttribute)
            .singleOrNull()
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

        val paramValue = nextLocalVar(paramName, param.type)
        val valueToAssign = generateParamValue(param, paramValue)

        if (valueToAssign != null) {
            addInstWithLocation { loc ->
                JIRAssignInst(loc, paramValue, valueToAssign)
            }
        }

        val isValidated = jIRParam.matchedAnnotations(String::isSpringValidated).any()
        if (isValidated) {
            generateParamValidatorCall(param, paramValue)
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

    private fun GenerationCtx.generateParamValidatorCall(
        param: JIRTypedMethodParameter,
        paramValue: JIRLocalVar
    ) {
        // TODO: pass annotation to validator.initialize somehow?
        val validators = (param.type as? JIRClassType)?.jIRClass?.jakartaConstraintValidators.orEmpty()

        for (validator in validators) {
            val validatorType = validator.toType()
            val initializeMethod = validatorType.methods.firstOrNull {
                it.name == "initialize" && it.parameters.size == 1
            } ?: continue
            val isValidMethod = validatorType.methods.firstOrNull {
                it.name == "isValid" && it.parameters.size == 2
            } ?: continue

            val validatorInstance = loadSpringComponent(validator, "validator")

            val initializeMethodRef = VirtualMethodRefImpl.of(validatorType, initializeMethod)
            val initializeMethodCall = JIRVirtualCallExpr(
                initializeMethodRef, validatorInstance,
                listOf(getOrCreateNewArgument(initializeMethod, 0))
            )
            addInstWithLocation { loc ->
                JIRCallInst(loc, initializeMethodCall)
            }

            val isValidMethodRef = VirtualMethodRefImpl.of(validatorType, isValidMethod)
            val isValidMethodCall = JIRVirtualCallExpr(
                isValidMethodRef, validatorInstance,
                listOf(paramValue, getOrCreateNewArgument(isValidMethod, 1))
            )
            addInstWithLocation { loc ->
                JIRCallInst(loc, isValidMethodCall)
            }
        }

        // todo: better validator resolution
        for (validator in validators) {
            val validatorType = validator.toType()
            val validateMethod = validatorType.methods.firstOrNull {
                it.name == "validate" && it.parameters.size == 2
            } ?: continue

            val validatorInstance = loadSpringComponent(validator, "validator")

            val validateMethodRef = VirtualMethodRefImpl.of(validatorType, validateMethod)
            val validateMethodCall = JIRVirtualCallExpr(
                validateMethodRef, validatorInstance,
                listOf(paramValue, bindingResultInstance)
            )

            addInstWithLocation { loc ->
                JIRCallInst(loc, validateMethodCall)
            }
        }
    }

    private fun GenerationCtx.generateControllerMethodCall(
        typedMethod: JIRTypedMethod,
        returnValueVarName: String? = null
    ): JIRLocalVar? {
        val methodRef = VirtualMethodRefImpl.of(controllerType, typedMethod)
        val methodCall = JIRVirtualCallExpr(
            methodRef,
            controllerInstance,
            typedMethod.parameters.indices.map { getOrCreateNewArgument(typedMethod, it) }
        )

        return if (typedMethod.returnType == cp.void) {
            addInstWithLocation { loc ->
                JIRCallInst(loc, methodCall)
            }
            null
        } else {
            val controllerResult = nextLocalVar(
                name = returnValueVarName ?: "${typedMethod.name}_result",
                typedMethod.returnType
            )
            addInstWithLocation { loc ->
                JIRAssignInst(loc, controllerResult, methodCall)
            }

            controllerResult
        }
    }

    private fun GenerationCtx.generateParamValue(
        param: JIRTypedMethodParameter,
        paramValue: JIRLocalVar
    ): JIRValue? = when (val type = param.type) {
        is JIRPrimitiveType -> generateStubValue(type)

        is JIRClassType -> {
            val paramCls = type.jIRClass
            when {
                paramCls.name.startsWith("java.lang") -> generateStubValue(type)
                paramCls.isSubClassOf(bindingResultCls) -> bindingResultInstance
                projectClasses.isProjectClass(paramCls) -> {
                    addInstWithLocation { loc ->
                        JIRAssignInst(loc, paramValue, JIRNewExpr(type))
                    }

                    val ctor = paramCls.declaredMethods
                        .singleOrNull { it.isConstructor && it.parameters.isEmpty() }

                    if (ctor != null) {
                        val ctorCall = JIRSpecialCallExpr(ctor.specialMethodRef(), paramValue, emptyList())
                        addInstWithLocation { loc ->
                            JIRCallInst(loc, ctorCall)
                        }
                    } else {
                        logger.warn { "No constructor for $paramCls" }
                    }

                    null // paramValue already assigned with new expr
                }

                paramCls.packageName.startsWith(SpringPackage) -> {
                    loadSpringComponent(paramCls, "param")
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

    private fun GenerationCtx.generateReactorMonoBlock(
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
                val monoResult = nextLocalVar(name = "mono_result", monoType)

                addInstWithLocation { loc ->
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

        addInstWithLocation { loc ->
            JIRCallInst(loc, monoBlockMethodCall)
        }
    }

    private fun controllerClass(controller: JIRClassOrInterface): SpringGeneratedClass {
        val controllerClsName = "${controller.name}_Opentaint_EntryPoint"
        return springGeneratedClass(cp, controllerClsName, controller)
    }
}

private fun generateStubValue(type: JIRType): JIRValue? = when (type) {
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

    else -> {
        logger.warn { "Unsupported stub type: ${type.typeName}" }
        null
    }
}

private fun JIRClassOrInterface.findComponentConstructor(): JIRMethod? =
    declaredMethods
        .filter { it.isConstructor && it.parameters.all { param -> param.type.isClass } }
        .minByOrNull { it.parameters.size }

private fun JIRClasspath.resolveComponentConstructorParam(ctor: JIRMethod, paramIdx: Int): JIRClassOrInterface? =
    findClassOrNull(ctor.parameters[paramIdx].type.typeName)

private fun JIRClassOrInterface.autowiredFields(): List<JIRField> =
    declaredFields.filter { field -> field.matchedAnnotations { it.isSpringAutowiredAnnotation() }.any() }

private fun JIRClasspath.resolveAutowiredField(field: JIRField): JIRClassOrInterface? =
    findClassOrNull(field.type.typeName)

private fun springGeneratedClass(cp: JIRClasspath, name: String, proto: JIRClassOrInterface): SpringGeneratedClass {
    val ext = cp.cpExt()
    if (ext.containsClass(name)) {
        return cp.findClass(name) as SpringGeneratedClass
    }

    return SpringGeneratedClass(name, mutableListOf(), mutableListOf()).also {
        it.bindWithLocation(cp, proto.declaration.location)
        ext.extendClassPath(it)
    }
}

private fun JIRClasspath.cpExt(): ProjectClassPathExtensionFeature =
    features.orEmpty().filterIsInstance<ProjectClassPathExtensionFeature>().single()

private fun methodDescription(argumentTypes: List<TypeName>, returnType: TypeName): String = buildString {
    append("(")
    argumentTypes.forEach {
        append(it.typeName.jvmName())
    }
    append(")")
    append(returnType.typeName.jvmName())
}

fun JIRMethod.specialMethodRef(): TypedSpecialMethodRefImpl {
    val clsType = enclosingClass.toType()
    return TypedSpecialMethodRefImpl(clsType, name, parameters.map { it.type }, returnType)
}

fun JIRMethod.staticMethodRef(): TypedStaticMethodRefImpl {
    val clsType = enclosingClass.toType()
    return TypedStaticMethodRefImpl(clsType, name, parameters.map { it.type }, returnType)
}

private fun JIRInstListBuilder.nextLocalVar(name: String, type: JIRType): JIRLocalVar {
    val idx = nextLocalVarIdx()
    return JIRLocalVar(idx, name = "%${name}_$idx", type)
}
