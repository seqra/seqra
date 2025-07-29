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
import org.opentaint.ir.api.jvm.cfg.JIRMutableInstList
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
import org.opentaint.ir.impl.cfg.JIRInstLocationImpl
import org.opentaint.ir.impl.cfg.JIRMutableInstListImpl
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
import org.opentaint.ir.impl.types.JIRTypedFieldImpl
import org.opentaint.ir.impl.types.TypeNameImpl
import org.opentaint.ir.impl.types.substition.JIRSubstitutorImpl
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class LambdaAnonymousClassFeature : JIRClasspathExtFeature {
    private val lambdaClasses = ConcurrentHashMap<String, JIRLambdaClass>()
    private val lambdaClassNames = ConcurrentHashMap<JIRInstLocation, String>()
    private val lambdaCounter = AtomicInteger(0)

    override fun tryFindClass(classpath: JIRClasspath, name: String): JIRClasspathExtFeature.JIRResolvedClassResult? {
        val clazz = lambdaClasses[name]
        if (clazz != null) {
            return JIRResolvedClassResultImpl(name, clazz)
        }
        return null
    }

    fun generateLambda(location: JIRInstLocation, lambda: JIRLambdaExpr): JIRLambdaClass? {
        val lambdaClassName = lambdaClassNames.computeIfAbsent(location) {
            "${lambda.callSiteReturnType.typeName}_\$lambda_${lambdaCounter.getAndIncrement()}"
        }

        return synchronized(lambdaClassName) {
            val generatedCls = lambdaClasses[lambdaClassName]
            if (generatedCls != null) return generatedCls

            val lambdaMethod = resolveLambdaMethod(lambda) ?: return null
            val declaredMethods = mutableListOf<JIRVirtualMethod>()
            val declaredFields = mutableListOf<JIRVirtualField>()

            val locationClass = location.method.enclosingClass

            val lambdaClass = JIRLambdaClass(
                lambdaClassName, declaredFields, declaredMethods,
                lambdaMethod.method, lambdaMethod.method.enclosingClass
            ).also {
                it.bindWithLocation(locationClass.classpath, locationClass.declaration.location)
            }

            lambdaClasses[lambdaClassName] = lambdaClass

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

        val cp = method.enclosingClass.classpath
        val lambdaType = cp.findTypeOrNull(lambdaClass.name) as? JIRClassType
            ?: error("Lambda generation failure")

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
        val cp = method.enclosingClass.classpath

        val implMethodInstructions = InstListBuilder()
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
        val locals = mutableListOf<JIRLocalVar>()
        val args = mutableListOf<JIRValue>()

        if (lambda.isNewInvokeSpecial) {
            implMethodInstructions.addInstWithLocation(implMethod) { loc ->
                val type = actualMethod.enclosingType
                val value = locals.mkLocal("new_value", type).also { args.add(it) }
                JIRAssignInst(loc, value, JIRNewExpr(type))
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
            JIRArgument(it.index, it.name ?: "arg_${it.index}", cp.findType(it.type.typeName))
        }

        val expectedArgTypes = mutableListOf<JIRType>()
        if (!actualMethod.isStatic) {
            expectedArgTypes.add(actualMethod.enclosingType)
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

        val callExpr = when {
            lambda.isNewInvokeSpecial -> {
                check(actualMethod.method.isConstructor)
                JIRSpecialCallExpr(lambda.actualMethod, args.first(), args.drop(1))
            }

            actualMethod.isStatic -> {
                JIRStaticCallExpr(lambda.actualMethod, args)
            }

            else -> {
                val instanceArg = args.first()
                val methodRef = VirtualMethodRefImpl.of(
                    instanceArg.type as JIRClassType, lambda.actualMethod.method
                )
                JIRVirtualCallExpr(methodRef, instanceArg, args.drop(1))
            }
        }

        val retVal: JIRValue?
        if (actualMethod.returnType == cp.void) {
            retVal = null
            implMethodInstructions.addInstWithLocation(implMethod) { loc ->
                JIRCallInst(loc, callExpr)
            }
        } else {
            retVal = locals.mkLocal("result", actualMethod.returnType)
            implMethodInstructions.addInstWithLocation(implMethod) { loc ->
                JIRAssignInst(loc, retVal, callExpr)
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
        val constructorInstructions = InstListBuilder()
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
                val rhs = JIRArgument(idx, "arg_$idx", field.type)
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

    class JIRLambdaClass(
        name: String,
        fields: List<JIRVirtualField>,
        methods: List<JIRVirtualMethod>,
        val lambdaMethod: JIRMethod,
        private val lambdaInterfaceType: JIRClassOrInterface
    ) : JIRVirtualClassImpl(name, initialFields = fields, initialMethods = methods) {
        init {
            check(lambdaInterfaceType.isInterface)
        }

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

        override fun toString(): String = "(lambda: $name)"

        override fun bind(classpath: JIRClasspath, virtualLocation: VirtualLocation) {
            bindWithLocation(classpath, virtualLocation)
        }

        fun bindWithLocation(classpath: JIRClasspath, location: RegisteredLocation) {
            this.classpath = classpath
            this.declarationLocation = location
        }
    }

    private class JIRLambdaMethod(
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

    private class InstListBuilder : JIRInstList<JIRInst> {
        private val mutableInstructions = mutableListOf<JIRInst>()

        override val indices: IntRange get() = mutableInstructions.indices
        override val instructions: List<JIRInst> get() = mutableInstructions
        override val lastIndex: Int get() = mutableInstructions.lastIndex
        override val size: Int get() = mutableInstructions.size

        override fun get(index: Int): JIRInst = mutableInstructions[index]
        override fun getOrNull(index: Int): JIRInst? = mutableInstructions.getOrNull(index)
        override fun iterator(): Iterator<JIRInst> = mutableInstructions.iterator()
        override fun toMutableList(): JIRMutableInstList<JIRInst> = JIRMutableInstListImpl(mutableInstructions)

        fun addInst(buildInst: (Int) -> JIRInst) {
            val idx = mutableInstructions.size
            val inst = buildInst(idx)
            check(mutableInstructions.size == idx)
            mutableInstructions += inst
        }

        fun addInstWithLocation(method: JIRMethod, buildInst: (JIRInstLocation) -> JIRInst) = addInst { idx ->
            val location = JIRInstLocationImpl(method, idx, lineNumber = -1)
            buildInst(location)
        }
    }

    companion object {
        private fun String.typeName() = TypeNameImpl(this.jvmName())
    }
}
