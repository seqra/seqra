package test.samples

import java.util.Locale

class KotlinSpanResolverSample {
    var strValue: String = ""
    private var field: String = ""
    private val array = IntArray(10)

    fun simpleMethodCall(): String {
        return /*simpleCall:start*/strValue.uppercase(Locale.ROOT)/*simpleCall:end*/
    }

    fun assignmentWithCall() {
        /*s1:start*/val result = getFieldValue()/*s1:end*/
        println(result)
    }

    /*methodEntry:start*/fun getFieldValue(): String/*methodEntry:end*/ {
        return field
    /*methodExit:start*/}/*methodExit:end*/

    fun fieldAccess() {
        /*fieldRead:start*/val value = this.field/*fieldRead:end*/
        bh(value)
        /*fieldWrite:start*/this.field = "new value"/*fieldWrite:end*/
    }

    fun arrayAccess(): Int {
        /*arrayRead:start*/val value = array[0]/*arrayRead:end*/
        array/*arrayWrite:start*/[1]/*arrayWrite:end*/ = 42
        return value
    }

    fun createObject(): KotlinSpanResolverSample {
        /*newObject:start*/val obj = KotlinSpanResolverSample()/*newObject:end*/
        return obj
    }

    fun returnStatement(): Int {
        /*return:start*/return 42/*return:end*/
    }

    fun multipleStatementsOnLine() {
        val a = 1; val b = 2; /*multi:start*/val c = a + b/*multi:end*/
        bh(c)
    }

    fun chainedCall() {
        /*chainedCall:start*/val s = strValue.trim().uppercase(Locale.ROOT)/*chainedCall:end*/
        bh(s)
    }

    fun localVariableDeclaration() {
        val /*localVar:start*/local = "value"/*localVar:end*/
        val number = 123
        bh(local)
        bh(number)
    }

    /*full$entry:start*/fun fullMethodTest(input: String): String/*full$entry:end*/ {
        /*full$local:start*/val trimmed = input.trim()/*full$local:end*/
        /*full$fieldWrite:start*/this.field = trimmed/*full$fieldWrite:end*/
        /*full$fieldRead:start*/val current = this.field/*full$fieldRead:end*/
        /*full$call:start*/val upper = current.uppercase()/*full$call:end*/
        /*full$return:start*/return upper/*full$return:end*/
    /*full$exit:start*/}/*full$exit:end*/

    private fun bh(o: Any?) {}
}
