package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRClasspathExtFeature
import org.opentaint.ir.api.jvm.JIRDeclaration
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypedField
import org.opentaint.ir.api.jvm.JIRTypedMethod
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.BsmHandleTag
import org.opentaint.ir.api.jvm.cfg.BsmMethodTypeArg
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation
import org.opentaint.ir.api.jvm.cfg.JIRLambdaExpr
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRNewExpr
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRStaticCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.JIRVirtualCallExpr
import org.opentaint.ir.api.jvm.ext.CONSTRUCTOR
import org.opentaint.ir.api.jvm.ext.findType
import org.opentaint.ir.api.jvm.ext.jvmName
import org.opentaint.ir.api.jvm.ext.void
import org.opentaint.ir.impl.bytecode.JIRDeclarationImpl
import org.opentaint.ir.impl.cfg.TypedSpecialMethodRefImpl
import org.opentaint.ir.impl.cfg.TypedStaticMethodRefImpl
import org.opentaint.ir.impl.cfg.VirtualMethodRefImpl
import org.opentaint.ir.impl.features.classpaths.AbstractJIRResolvedResult.JIRResolvedClassResultImpl
import org.opentaint.ir.impl.features.classpaths.VirtualLocation
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualClass
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualClassImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualField
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualFieldImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethod
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualMethodImpl
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualParameter
import org.opentaint.ir.impl.types.JIRClassTypeImpl
import org.opentaint.ir.impl.types.JIRTypedFieldImpl
import org.opentaint.ir.impl.types.substition.JIRSubstitutorImpl
import org.opentaint.dataflow.jvm.util.JIRInstListBuilder
import org.opentaint.dataflow.jvm.util.typeName
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap

class LambdaAnonymousClassFeature : JIRClasspathExtFeature {
    private val lambdaClasses = ConcurrentHashMap<String, JIRLambdaClass>()
    private val lambdaProxyClasses = ConcurrentHashMap<JIRMethod, JIRLambdaClass>()
    private val lambdaProxyClassesByName = ConcurrentHashMap<String, JIRLambdaClass>()

    override fun tryFindClass(classpath: JIRClasspath, name: String): JIRClasspathExtFeature.JIRResolvedClassResult? {
        val clazz = lambdaClasses[name]
        if (clazz != null) {
            return JIRResolvedClassResultImpl(name, clazz)
        }

        val proxyClass = lambdaProxyClassesByName[name]
        if (proxyClass != null) {
            return JIRResolvedClassResultImpl(name, proxyClass)
        }

        return null
    }

    fun getOrCreateLambdaProxy(
        method: JIRMethod,
        classpath: JIRClasspath,
        location: RegisteredLocation
    ): JIRLambdaClass = lambdaProxyClasses.computeIfAbsent(method) {
        val proxyClassName = lambdaProxyClassName(method)
        val proxyMethod = generateProxyMethod(method, classpath)
        val lambdaClass = JIRLambdaClass(
            name = proxyClassName,
            fields = emptyList(),
            methods = listOf(proxyMethod),
            lambdaMethod = method,
            lambdaInterfaceType = method.enclosingClass,
            lambdaLocation = null
        )
        lambdaClass.bindWithLocation(classpath, location)
        proxyMethod.bind(lambdaClass)
        lambdaProxyClassesByName[proxyClassName] = lambdaClass
        lambdaClass
    }

    private fun generateProxyMethod(originalMethod: JIRMethod, classpath: JIRClasspath): OpentaintLambdaProxyMethod {
        val instructions = JIRInstListBuilder()

        val method = OpentaintLambdaProxyMethod(
            originalMethod = originalMethod,
            name = originalMethod.name,
            returnType = originalMethod.returnType,
            description = originalMethod.description,
            parameters = originalMethod.parameters.map { JIRVirtualParameter(it.index, it.type) },
            instructions = instructions
        )

        val interfaceType = classpath.findType(originalMethod.enclosingClass.name) as? JIRClassType
            ?: error("Cannot resolve interface type: ${originalMethod.enclosingClass.name}")

        val receiverArg = JIRThis(interfaceType)
        val forwardedArgs = originalMethod.parameters.map { param ->
            JIRArgument(
                param.index,
                param.name ?: "arg|${param.index}",
                classpath.findType(param.type.typeName)
            )
        }

        val typedMethod = interfaceType.lookup.method(originalMethod.name, originalMethod.description)
            ?: error("Cannot resolve typed method: ${originalMethod.name}${originalMethod.description} in ${interfaceType.typeName}")

        val methodRef = VirtualMethodRefImpl.of(interfaceType, typedMethod)
        val callExpr = JIRVirtualCallExpr(methodRef, receiverArg, forwardedArgs)

        val isVoidReturn = originalMethod.returnType == PredefinedPrimitives.Void.typeName()
        val retVal: JIRValue? = if (isVoidReturn) {
            instructions.addInstWithLocation(method) { loc -> JIRCallInst(loc, callExpr) }
            null
        } else {
            val resultLocal = JIRLocalVar(0, "result", classpath.findType(originalMethod.returnType.typeName))
            instructions.addInstWithLocation(method) { loc -> JIRAssignInst(loc, resultLocal, callExpr) }
            resultLocal
        }

        instructions.addInstWithLocation(method) { loc -> JIRReturnInst(loc, retVal) }

        return method
    }

    fun generateLambda(location: JIRInstLocation, lambda: JIRLambdaExpr): JIRLambdaClass {
        val lambdaClassName = with(location) {
            "${method.enclosingClass.name}$${method.name}_${method.descriptionHash()}\$jIR_lambda$${index}"
        }

        return lambdaClasses.computeIfAbsent(lambdaClassName) {
            val lambdaMethod = resolveLambdaMethod(lambda)
                ?: error("Lambda method resolution failed for: $lambda")

            val declaredMethods = mutableListOf<JIRVirtualMethod>()
            val declaredFields = mutableListOf<JIRVirtualField>()

            val lambdaClass = JIRLambdaClass(
                lambdaClassName, declaredFields, declaredMethods,
                lambdaMethod.method, lambdaMethod.method.enclosingClass, location
            ).also {
                val locationClass = location.method.enclosingClass
                it.bindWithLocation(locationClass.classpath, locationClass.declaration.location)
            }

            generateLambdaClassBody(lambda, lambdaClass, declaredFields, declaredMethods)

            lambdaClass
        }
    }

    private fun generateLambdaClassBody(
        lambda: JIRLambdaExpr,
        lambdaClass: JIRLambdaClass,
        declaredFields: MutableList<JIRVirtualField>,
        declaredMethods: MutableList<JIRVirtualMethod>,
    ) {
        val method = lambdaClass.lambdaMethod

        val lambdaType = with(lambdaClass) {
            // note: avoid classpath lookup since class wat not initialized yet
            JIRClassTypeImpl(classpath, name, outerType = null, JIRSubstitutorImpl.empty, nullable = false, annotations)
        }

        val fields = lambda.callSiteArgTypes.mapIndexed { fieldIdx, fieldType ->
            val typeName = fieldType.typeName.typeName()
            val field = JIRLambdaField(name = "lambdaCSArg$${fieldIdx}", type = typeName)
                .also { it.bind(lambdaClass) }

            declaredFields += field
            JIRTypedFieldImpl(lambdaType, field, JIRSubstitutorImpl.empty)
        }

        generateConstructorBody(fields, declaredMethods, lambdaClass, lambdaType)
        generateLambdaMethodImplBody(method, declaredMethods, lambdaClass, lambda, fields, lambdaType)
    }

    private fun generateLambdaMethodImplBody(
        method: JIRMethod,
        declaredMethods: MutableList<JIRVirtualMethod>,
        lambdaClass: JIRVirtualClass,
        lambda: JIRLambdaExpr,
        fields: List<JIRTypedField>,
        lambdaType: JIRClassType
    ) {
        val implMethodInstructions = JIRInstListBuilder()
        val implMethod = JIRLambdaMethod(
            name = method.name,
            returnType = method.returnType,
            description = method.description,
            parameters = method.parameters.map { JIRVirtualParameter(it.index, it.type) },
            instructions = implMethodInstructions
        ).also {
            declaredMethods += it
            it.bind(lambdaClass)
        }

        val actualMethod = lambda.actualMethod.method
        val actualMethodClass = actualMethod.enclosingType as? JIRClassType
            ?: error("Unexpected lambda method: $actualMethod")

        val locals = mutableListOf<JIRLocalVar>()
        val args = mutableListOf<JIRValue>()

        if (lambda.lambdaInvokeKind == BsmHandleTag.MethodHandle.NEW_INVOKE_SPECIAL) {
            implMethodInstructions.addInstWithLocation(implMethod) { loc ->
                val value = locals.mkLocal("new_value", actualMethodClass).also { args.add(it) }
                JIRAssignInst(loc, value, JIRNewExpr(actualMethodClass))
            }
        }

        for ((i, field) in fields.withIndex()) {
            implMethodInstructions.addInstWithLocation(implMethod) { loc ->
                val value = locals.mkLocal("call_site_${i}", field.type).also { args.add(it) }
                val fieldValue = JIRFieldRef(JIRThis(lambdaType), field)
                JIRAssignInst(loc, value, fieldValue)
            }
        }

        method.parameters.mapTo(args) {
            JIRArgument(it.index, it.name ?: "arg|${it.index}", lambdaClass.classpath.findType(it.type.typeName))
        }

        val expectedArgTypes = mutableListOf<JIRType>()
        if (lambda.lambdaInvokeKind != BsmHandleTag.MethodHandle.INVOKE_STATIC) {
            expectedArgTypes.add(actualMethodClass)
        }
        actualMethod.parameters.mapTo(expectedArgTypes) { it.type }

        check(args.size == expectedArgTypes.size) { "Method arguments count mismatch" }

        for (i in args.indices) {
            if (args[i].type == expectedArgTypes[i]) continue
            implMethodInstructions.addInstWithLocation(implMethod) { loc ->
                val value = locals.mkLocal("v$i", expectedArgTypes[i])
                val cast = JIRCastExpr(expectedArgTypes[i], args[i])
                args[i] = value
                JIRAssignInst(loc, value, cast)
            }
        }

        val argumentTypeNames = actualMethod.method.parameters.map { it.type }
        val returnTypeName = actualMethod.method.returnType

        val callExpr = when (lambda.lambdaInvokeKind) {
            BsmHandleTag.MethodHandle.NEW_INVOKE_SPECIAL,
            BsmHandleTag.MethodHandle.INVOKE_SPECIAL -> {
                val methodRef = TypedSpecialMethodRefImpl(
                    actualMethodClass, actualMethod.name, argumentTypeNames, returnTypeName
                )
                JIRSpecialCallExpr(methodRef, args.first(), args.drop(1))
            }

            BsmHandleTag.MethodHandle.INVOKE_STATIC -> {
                val methodRef = TypedStaticMethodRefImpl(
                    actualMethodClass, actualMethod.name, argumentTypeNames, returnTypeName
                )
                JIRStaticCallExpr(methodRef, args)
            }

            BsmHandleTag.MethodHandle.INVOKE_VIRTUAL,
            BsmHandleTag.MethodHandle.INVOKE_INTERFACE -> {
                val methodRef = VirtualMethodRefImpl.of(actualMethodClass, actualMethod)
                val instanceArg = args.first()
                JIRVirtualCallExpr(methodRef, instanceArg, args.drop(1))
            }
        }

        val retVal: JIRValue? = when {
            lambda.lambdaInvokeKind == BsmHandleTag.MethodHandle.NEW_INVOKE_SPECIAL -> {
                implMethodInstructions.addInstWithLocation(implMethod) { loc ->
                    JIRCallInst(loc, callExpr)
                }

                // Constructor doesn't have a return value. An allocated instance should be returned instead
                args.first()
            }

            actualMethod.returnType == lambdaClass.classpath.void -> {
                implMethodInstructions.addInstWithLocation(implMethod) { loc ->
                    JIRCallInst(loc, callExpr)
                }

                // Void methods have no return value
                null
            }

            else -> {
                val retVal = locals.mkLocal("result", actualMethod.returnType)
                implMethodInstructions.addInstWithLocation(implMethod) { loc ->
                    JIRAssignInst(loc, retVal, callExpr)
                }

                retVal
            }
        }

        implMethodInstructions.addInstWithLocation(implMethod) { loc ->
            JIRReturnInst(loc, retVal)
        }
    }

    private fun generateConstructorBody(
        fields: List<JIRTypedField>,
        declaredMethods: MutableList<JIRVirtualMethod>,
        lambdaClass: JIRVirtualClass,
        lambdaType: JIRClassType
    ) {
        val constructorInstructions = JIRInstListBuilder()
        val constructorArgs = fields.mapIndexed { idx, field -> JIRVirtualParameter(idx, field.field.type) }
        val constructorArgTypes = constructorArgs.map { it.type }
        val constructorReturnType = PredefinedPrimitives.Void.typeName()

        val constructorMethod = JIRLambdaMethod(
            name = CONSTRUCTOR,
            returnType = constructorReturnType,
            description = methodDescription(constructorArgTypes, constructorReturnType),
            instructions = constructorInstructions,
            parameters = constructorArgs
        ).also {
            declaredMethods += it
            it.bind(lambdaClass)
        }

        for ((idx, field) in fields.withIndex()) {
            constructorInstructions.addInstWithLocation(constructorMethod) { loc ->
                val rhs = JIRArgument(idx, "arg|$idx", field.type)
                val lhs = JIRFieldRef(JIRThis(lambdaType), field)
                JIRAssignInst(loc, lhs, rhs)
            }
        }

        constructorInstructions.addInstWithLocation(constructorMethod) { loc ->
            JIRReturnInst(loc, returnValue = null)
        }
    }

    private fun resolveLambdaMethod(lambda: JIRLambdaExpr): JIRTypedMethod? {
        val methodClass = lambda.callSiteReturnType as? JIRClassType ?: return null
        val description = lambda.dynamicMethodType.methodDescription()
        return methodClass.lookup.method(lambda.callSiteMethodName, description)
    }

    private fun BsmMethodTypeArg.methodDescription(): String = methodDescription(argumentTypes, returnType)

    private fun methodDescription(argumentTypes: List<TypeName>, returnType: TypeName): String = buildString {
        append("(")
        argumentTypes.forEach {
            append(it.typeName.jvmName())
        }
        append(")")
        append(returnType.typeName.jvmName())
    }

    private fun MutableList<JIRLocalVar>.mkLocal(name: String, type: JIRType): JIRLocalVar =
        JIRLocalVar(size, name, type).also { add(it) }

    class OpentaintLambdaProxyMethod(
        val originalMethod: JIRMethod,
        name: String,
        returnType: TypeName,
        description: String,
        parameters: List<JIRVirtualParameter>,
        private val instructions: JIRInstList<JIRInst>
    ) : JIRVirtualMethodImpl(name, returnType = returnType, parameters = parameters, description = description) {
        override val instList: JIRInstList<JIRInst> get() = instructions

        override fun hashCode(): Int = originalMethod.hashCode()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is OpentaintLambdaProxyMethod && originalMethod == other.originalMethod
        }

        override fun toString(): String = "(lambda-proxy-method: ${originalMethod.enclosingClass.name}#$name$description)"
    }

    class JIRLambdaClass(
        name: String,
        fields: List<JIRVirtualField>,
        methods: List<JIRVirtualMethod>,
        val lambdaMethod: JIRMethod,
        val lambdaInterfaceType: JIRClassOrInterface,
        val lambdaLocation: JIRInstLocation?
    ) : JIRVirtualClassImpl(name, initialFields = fields, initialMethods = methods) {

        private lateinit var declarationLocation: RegisteredLocation

        override val isAnonymous: Boolean get() = true

        override val interfaces: List<JIRClassOrInterface> get() = listOf(lambdaInterfaceType)

        override val declaration: JIRDeclaration
            get() =  JIRDeclarationImpl.of(declarationLocation, this)

        override fun hashCode(): Int = name.hashCode()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is JIRLambdaClass && name == other.name
        }

        override fun toString(): String = "(lambda: $name/$lambdaMethod)"

        override fun bind(classpath: JIRClasspath, virtualLocation: VirtualLocation) {
            bindWithLocation(classpath, virtualLocation)
        }

        fun bindWithLocation(classpath: JIRClasspath, location: RegisteredLocation) {
            this.classpath = classpath
            this.declarationLocation = location
        }
    }

    class JIRLambdaMethod(
        name: String,
        returnType: TypeName,
        description: String,
        parameters: List<JIRVirtualParameter>,
        private val instructions: JIRInstList<JIRInst>
    ) : JIRVirtualMethodImpl(name, returnType = returnType, parameters = parameters, description = description) {
        override val instList: JIRInstList<JIRInst> get() = instructions

        override fun hashCode(): Int = Objects.hash(name, enclosingClass)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true

            return other is JIRLambdaMethod && name == other.name && enclosingClass == other.enclosingClass
        }
    }

    private class JIRLambdaField(name: String, type: TypeName) : JIRVirtualFieldImpl(name, type = type) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is JIRLambdaField && name == other.name
        }

        override fun hashCode(): Int = name.hashCode()
    }

    companion object {
        private const val LAMBDA_PROXY_CLASS_PREFIX = "opentaint.lambda.OpentaintLambdaProxy$"

        @OptIn(ExperimentalStdlibApi::class)
        private fun JIRMethod.descriptionHash() = description.hashCode().toHexString()

        private fun lambdaProxyClassName(method: JIRMethod): String {
            val clsName = method.enclosingClass.name.replace('.', '_')
            return "$LAMBDA_PROXY_CLASS_PREFIX${clsName}_${method.name}_${method.descriptionHash()}"
        }
    }
}
