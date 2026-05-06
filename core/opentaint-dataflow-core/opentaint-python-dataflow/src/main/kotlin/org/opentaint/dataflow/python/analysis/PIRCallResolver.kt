package org.opentaint.dataflow.python.analysis

import org.opentaint.ir.api.python.*

/**
 * Resolves PIRCall instructions to concrete PIRFunction callees.
 * Uses mypy's static resolution (PIRCall.resolvedCallee), falling back to
 * type-based resolution for method calls where mypy doesn't set resolvedCallee.
 */
class PIRCallResolver(private val cp: PIRClasspath) {

    fun resolve(call: PIRCall): PIRFunction? {
        val qualifiedName = call.resolvedCallee
        if (qualifiedName != null) {
            return cp.findFunctionOrNull(qualifiedName)
        }
        return null
    }

    /**
     * Resolves a call with fallback: if resolvedCallee is null, searches the method's
     * instructions for a preceding PIRLoadAttr(target=callee, obj, attr)
     * and constructs the qualified name from obj.type + attr.
     */
    fun resolve(call: PIRCall, method: PIRFunction): PIRFunction? {
        // Primary: use resolvedCallee. The proto-to-flat layer normalizes
        // mypy's lexical names (`m.outer.inner`) to the lifter's flat-encoded
        // qualified names (`m.outer$inner`) at module-build time, so a direct
        // lookup against the classpath qn registry succeeds for any
        // fully-qualified in-module callee.
        val qualifiedName = call.resolvedCallee
        if (qualifiedName != null) {
            cp.findFunctionOrNull(qualifiedName)?.let { return it }

            // Fallback for nested functions: mypy may set resolvedCallee to just
            // the short name (e.g. "process" instead of "Module.outer.process").
            // Synthesize the lexical qn by prepending the enclosing method's qn,
            // then translate the trailing `.` separator into the lifter's `$`
            // encoding so the synthesized name matches the flat function's qn.
            // (This local synthesis happens after the proto-to-flat normalizer
            // has already run on every emitted resolvedCallee, so the path
            // `method.qn + "." + shortName` only appears here.)
            if ("." !in qualifiedName) {
                val lexicalCandidate = "${method.qualifiedName}.$qualifiedName"
                cp.findFunctionOrNull(lexicalCandidate)?.let { return it }
                val flatCandidate = "${method.qualifiedName}\$$qualifiedName"
                cp.findFunctionOrNull(flatCandidate)?.let { return it }
            }
        }

        // Fallback: find the PIRLoadAttr that loaded the callee
        val calleeValue = call.callee
        if (calleeValue is PIRLocal) {
            for (inst in method.instList) {
                if (inst is PIRLoadAttr && inst.target is PIRLocal &&
                    (inst.target as PIRLocal).name == calleeValue.name
                ) {
                    val attrName = inst.attribute

                    // Strategy 1: Use obj's type name (for instance method calls: obj.method())
                    val objType = inst.obj.type
                    val typeName = objType.typeName
                    if (typeName != "Any") {
                        val resolved1 = cp.findFunctionOrNull("$typeName.$attrName")
                        if (resolved1 != null) return resolved1
                    }

                    // Strategy 2: Use PIRGlobalRef's qualifiedName (for class-level calls: ClassName.method())
                    if (inst.obj is PIRGlobalRef) {
                        val gref = inst.obj as PIRGlobalRef
                        val resolved2 = cp.findFunctionOrNull("${gref.qualifiedName}.$attrName")
                        if (resolved2 != null) return resolved2
                    }

                    // Strategy 2b: Use PIRModuleRef directly (for module.attr() calls: os.getcwd()).
                    if (inst.obj is PIRModuleRef) {
                        val mref = inst.obj as PIRModuleRef
                        val resolved2b = cp.findFunctionOrNull("${mref.module}.$attrName")
                        if (resolved2b != null) return resolved2b
                    }

                    // Strategy 3: For local variables with unknown type, try to infer the class
                    // from the constructor call that assigned the variable
                    if (inst.obj is PIRLocal) {
                        val localName = (inst.obj as PIRLocal).name
                        val className = inferClassFromConstructor(localName, method)
                        if (className != null) {
                            val resolved3 = cp.findFunctionOrNull("$className.$attrName")
                            if (resolved3 != null) return resolved3
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Infers the class of a local variable by finding the constructor call that assigned it.
     * For `obj = MyClass()`, the PIRCall has resolvedCallee="module.MyClass".
     * Returns the qualified class name or null.
     */
    private fun inferClassFromConstructor(localName: String, method: PIRFunction): String? {
        for (inst in method.instList) {
            // Look for: PIRCall(target=PIRLocal(localName), resolvedCallee="module.ClassName")
            if (inst is PIRCall && inst.target is PIRLocal &&
                (inst.target as PIRLocal).name == localName &&
                inst.resolvedCallee != null
            ) {
                // The resolvedCallee might be a class name (for constructors)
                val callee = inst.resolvedCallee!!
                val cls = cp.findClassOrNull(callee)
                if (cls != null) return cls.qualifiedName
            }
            // Also check: PIRAssign(target=PIRLocal(localName), expr=PIRLocal(tempName))
            // where tempName was assigned from a constructor call
            if (inst is PIRAssign && inst.target is PIRLocal &&
                (inst.target as PIRLocal).name == localName &&
                inst.expr is PIRLocal
            ) {
                val tempName = (inst.expr as PIRLocal).name
                return inferClassFromConstructor(tempName, method)
            }
        }
        return null
    }
}
