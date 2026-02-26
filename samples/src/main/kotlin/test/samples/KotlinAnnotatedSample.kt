package test.samples

import java.util.function.Function

class KotlinAnnotatedSample<T> {
    private var data: T? = null
    private var items: List<String>? = null

    @Deprecated("Use other constructor")
    /*annotatedConstructor:start*/constructor()/*annotatedConstructor:end*/ {
        this.data = null
    /*annotatedConstructorExit:start*/}/*annotatedConstructorExit:end*/

    @Deprecated("Use other constructor")
    @Suppress("UNCHECKED_CAST")
    /*multiAnnotationConstructor:start*/constructor(data: T)/*multiAnnotationConstructor:end*/ {
        this.data = data
    /*multiAnnotationConstructorExit:start*/}/*multiAnnotationConstructorExit:end*/

    @Deprecated("Deprecated")
    @Suppress("UNCHECKED_CAST")
    fun <R> /*annotatedMethod:start*/transform(fn: Function<T?, R>): R/*annotatedMethod:end*/ {
        return fn.apply(data)
    /*annotatedMethodExit:start*/}/*annotatedMethodExit:end*/

    @Suppress("UNCHECKED_CAST")
    fun <E : Number> /*typeParamMethod:start*/process(input: E): E/*typeParamMethod:end*/ {
        return input
    /*typeParamMethodExit:start*/}/*typeParamMethodExit:end*/

    fun localAnnotations() {
        @Suppress("UNUSED_VARIABLE")
        /*annotatedLocal:start*/val unused = "test"/*annotatedLocal:end*/
        @Suppress("UNCHECKED_CAST")
        /*multiAnnotatedLocal:start*/val list: List<String>? = null/*multiAnnotatedLocal:end*/
        bh(unused)
        bh(list)
    }

    fun <K, V : Comparable<V>> /*complexSignature:start*/complexMethod(key: K, value: V): Map<K, V>/*complexSignature:end*/ {
        return mapOf(key to value)
    /*complexSignatureExit:start*/}/*complexSignatureExit:end*/

    private fun bh(o: Any?) {}
}

data class DataClassWithTypeParams<T, R : Comparable<R>>(
    val first: T,
    val second: R
) {
    /*dataClassMethod:start*/fun combine(): String/*dataClassMethod:end*/ {
        return "$first:$second"
    /*dataClassMethodExit:start*/}/*dataClassMethodExit:end*/
}

data class DataClassSimple/*dataClassEntry:start*/(/*dataClassPrimaryFieldName:start*/val name: String/*dataClassPrimaryFieldName:end*/, /*dataClassPrimaryFieldValue:start*/val value: Int/*dataClassPrimaryFieldValue:end*/)/*dataClassEntry:end*/

data class DataClassWithInit/*dataClassWithInitEntry:start*/(val name: String)/*dataClassWithInitEntry:end*/ {
    /*dataClassFieldInit:start*/val nameUppercase: String = name.uppercase()/*dataClassFieldInit:end*/

    init {
        /*dataClassInitBlock:start*/println(name)/*dataClassInitBlock:end*/
    /*dataClassInitBlockExit:start*/}/*dataClassInitBlockExit:end*/
}

data class DataClassAnnotated<@Suppress("unused") T : CharSequence>/*dataClassAnnotatedEntry:start*/(
    @field:Suppress("unused") val data: T
)/*dataClassAnnotatedEntry:end*/

inline fun <T> /*inlineFunctionEntry:start*/inlineFunctionSample(value: T): String/*inlineFunctionEntry:end*/ {
    return value.toString()
/*inlineFunctionExit:start*/}/*inlineFunctionExit:end*/

fun callInlineFunction(): String {
    /*inlineCallBefore:start*/val input = "test"/*inlineCallBefore:end*/
    /*inlineCall:start*/val result = /*inlineBodyToString:start*/inlineFunctionSample(input)/*inlineBodyToString:end*//*inlineCall:end*/
    /*inlineCallAfter:start*/return result/*inlineCallAfter:end*/
}

inline fun <T> inlineWithLambda(value: T, crossinline transform: (T) -> String): String {
    return transform(value)
}

fun callInlineWithLambda(): String {
    /*lambdaInlineCall:start*/val result = inlineWithLambda("hello") { it.uppercase() }/*lambdaInlineCall:end*/
    return result
}

inline fun <reified T> /*reifiedInlineEntry:start*/reifiedInlineFunction(value: Any): Boolean/*reifiedInlineEntry:end*/ {
    return value is T
/*reifiedInlineExit:start*/}/*reifiedInlineExit:end*/

fun callReifiedInline(): Boolean {
    /*reifiedInlineCall:start*/val result = reifiedInlineFunction<String>("test")/*reifiedInlineCall:end*/
    return result
}

class SuspendSample {
    suspend fun /*suspendMethod:start*/suspendFunction(input: String): String/*suspendMethod:end*/ {
        /*suspendBodyInst:start*/return input.uppercase()/*suspendBodyInst:end*/
    /*suspendMethodExit:start*/}/*suspendMethodExit:end*/

    @Suppress("unused")
    suspend fun <T> /*suspendGenericMethod:start*/suspendGenericFunction(input: T): String/*suspendGenericMethod:end*/ {
        return input.toString()
    /*suspendGenericMethodExit:start*/}/*suspendGenericMethodExit:end*/

    suspend fun useSuspend(): String {
        /*suspendCall:start*/val result = suspendFunction("test")/*suspendCall:end*/
        return result
    }

    suspend fun /*complexSuspendEntry:start*/complexSuspendFunction(items: List<String>): String/*complexSuspendEntry:end*/ {
        val results = mutableListOf<String>()
        for (item in items) {
            val processed = suspendFunction(item)
            results.add(processed)
        }
        val joined = results.joinToString(",")
        return joined
    /*complexSuspendExit:start*/}/*complexSuspendExit:end*/

    /*complexMethodEntry:start*/fun complexMethodWithLoops(items: List<String>): String/*complexMethodEntry:end*/ {
        /*complexLocalInit:start*/val results = mutableListOf<String>()/*complexLocalInit:end*/
        /*complexForLoop:start*/for (item in items)/*complexForLoop:end*/ {
            /*complexLoopCall:start*/val processed = item.uppercase()/*complexLoopCall:end*/
            /*complexLoopAdd:start*/results.add(processed)/*complexLoopAdd:end*/
        }
        /*complexWhileInit:start*/var index = 0/*complexWhileInit:end*/
        /*complexWhileLoop:start*/while (index < results.size)/*complexWhileLoop:end*/ {
            /*complexWhileGet:start*/val current = results[index]/*complexWhileGet:end*/
            /*complexWhileUpdate:start*/results[index] = current.trim()/*complexWhileUpdate:end*/
            /*complexWhileIncrement:start*/index++/*complexWhileIncrement:end*/
        }
        /*complexFinalCall:start*/val joined = results.joinToString(",")/*complexFinalCall:end*/
        /*complexReturn:start*/return joined/*complexReturn:end*/
    /*complexMethodExit:start*/}/*complexMethodExit:end*/
}

class ExtensionSample {
    fun /*extensionMethod:start*/String.myExtension(): String/*extensionMethod:end*/ {
        return this.uppercase()
    /*extensionMethodExit:start*/}/*extensionMethodExit:end*/

    fun useExtension(): String {
        /*extensionCall:start*/val result = "test".myExtension()/*extensionCall:end*/
        return result
    }
}

class OperatorSample {
    var value: Int = 0

    /*operatorMethod:start*/operator fun plus(other: OperatorSample): OperatorSample/*operatorMethod:end*/ {
        val result = OperatorSample()
        result.value = this.value + other.value
        return result
    /*operatorMethodExit:start*/}/*operatorMethodExit:end*/

    fun useOperator(): OperatorSample {
        val a = OperatorSample()
        a.value = 1
        val b = OperatorSample()
        b.value = 2
        /*operatorCall:start*/val result = a + b/*operatorCall:end*/
        return result
    }
}

class MultipleTypeParamSample<A, B : Comparable<B>, C : Collection<A>> {
    private var first: A? = null
    private var second: B? = null
    private var third: C? = null

    /*multiTypeParamConstructor:start*/constructor(first: A, second: B, third: C)/*multiTypeParamConstructor:end*/ {
        this.first = first
        this.second = second
        this.third = third
    /*multiTypeParamConstructorExit:start*/}/*multiTypeParamConstructorExit:end*/

    fun <D : A> /*multiTypeParamMethod:start*/transform(input: D): String/*multiTypeParamMethod:end*/ {
        return input.toString()
    /*multiTypeParamMethodExit:start*/}/*multiTypeParamMethodExit:end*/
}

object SingletonSample {
    private var counter: Int = 0

    /*objectMethod:start*/fun increment(): Int/*objectMethod:end*/ {
        counter++
        return counter
    /*objectMethodExit:start*/}/*objectMethodExit:end*/
}

class CompanionSample {
    companion object {
        private var sharedValue: String = ""

        /*companionMethod:start*/fun getShared(): String/*companionMethod:end*/ {
            return sharedValue
        /*companionMethodExit:start*/}/*companionMethodExit:end*/
    }
}

data class DataClassWithDefaults(
    val orgId: String,
    val window: String?,
    val enabled: Boolean = false,
    val count: Int = 0,
    /*dataClassDefaultsTimestamp:start*/val timestamp: Long = System.currentTimeMillis()/*dataClassDefaultsTimestamp:end*/,
)

class ExpressionBodySample(
    private val processor: StringProcessor,
) {
    /*expressionBodyEntry:start*/fun processWithParams(
        input: String,
        flag: Boolean?,
        count: Int?,
        prefix: String?,
    ): ProcessedResult/*expressionBodyEntry:end*/ =
        processor./*expressionBodyCall:start*/process(
            input = input,
            flag = flag,
            count = count,
            prefix = prefix,
        )/*expressionBodyCall:end*/

    fun processWithParamsExit(
        input: String,
        flag: Boolean?,
        count: Int?,
        prefix: String?,
    ): ProcessedResult /*expressionBodyExit:start*/=
        processor.process(
            input = input,
            flag = flag,
            count = count,
            prefix = prefix,
        )/*expressionBodyExit:end*/

    /*simpleExpressionBodyEntry:start*/fun simpleExpression(x: Int): Int/*simpleExpressionBodyEntry:end*/ = /*simpleExpressionBodyExpr:start*/x * 2/*simpleExpressionBodyExpr:end*/
}

interface StringProcessor {
    fun process(
        input: String,
        flag: Boolean?,
        count: Int?,
        prefix: String?,
    ): ProcessedResult
}

data class ProcessedResult(val value: String)
