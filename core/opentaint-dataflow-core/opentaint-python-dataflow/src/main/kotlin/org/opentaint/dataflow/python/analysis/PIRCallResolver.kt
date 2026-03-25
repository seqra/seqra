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
     * instructions for a preceding PIRAssign(target=callee, expr=PIRAttrExpr(obj, attr))
     * and constructs the qualified name from obj.type + attr.
     */
    fun resolve(call: PIRCall, method: PIRFunction): PIRFunction? {
        // Primary: use resolvedCallee
        val qualifiedName = call.resolvedCallee
        if (qualifiedName != null) {
            cp.findFunctionOrNull(qualifiedName)?.let { return it }

            // Fallback for nested functions: mypy may set resolvedCallee to just
            // the short name (e.g. "process" instead of "Module.outer.process").
            // Try prepending the enclosing method's qualified name.
            if ("." !in qualifiedName) {
                val candidate = "${method.qualifiedName}.$qualifiedName"
                cp.findFunctionOrNull(candidate)?.let { return it }
            }
        }

        // Fallback: find the AttrExpr that loaded the callee
        val calleeValue = call.callee
        if (calleeValue is PIRLocal) {
            for (block in method.cfg.blocks) {
                for (inst in block.instructions) {
                    if (inst is PIRAssign && inst.target is PIRLocal &&
                        (inst.target as PIRLocal).name == calleeValue.name &&
                        inst.expr is PIRAttrExpr
                    ) {
                        val attrExpr = inst.expr as PIRAttrExpr
                        val attrName = attrExpr.attribute

                        // Strategy 1: Use obj's type name (for instance method calls: obj.method())
                        // Works when mypy preserves the type, e.g. "SimpleObject.MyClass"
                        val objType = attrExpr.obj.type
                        val typeName = objType.typeName
                        if (typeName != "Any") {
                            val resolved1 = cp.findFunctionOrNull("$typeName.$attrName")
                            if (resolved1 != null) return resolved1
                        }

                        // Strategy 2: Use PIRGlobalRef's module.name (for class-level calls: ClassName.method())
                        if (attrExpr.obj is PIRGlobalRef) {
                            val gref = attrExpr.obj as PIRGlobalRef
                            val qualName = if (gref.module.isNotEmpty()) "${gref.module}.${gref.name}" else gref.name
                            val resolved2 = cp.findFunctionOrNull("$qualName.$attrName")
                            if (resolved2 != null) return resolved2
                        }

                        // Strategy 3: For local variables with unknown type, try to infer the class
                        // from the constructor call that assigned the variable
                        if (attrExpr.obj is PIRLocal) {
                            val localName = (attrExpr.obj as PIRLocal).name
                            val className = inferClassFromConstructor(localName, method)
                            if (className != null) {
                                val resolved3 = cp.findFunctionOrNull("$className.$attrName")
                                if (resolved3 != null) return resolved3
                            }
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
        for (block in method.cfg.blocks) {
            for (inst in block.instructions) {
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
        }
        return null
    }
}
