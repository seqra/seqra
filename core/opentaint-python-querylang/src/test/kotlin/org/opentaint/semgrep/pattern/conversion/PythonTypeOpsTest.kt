package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PythonTypeOpsTest {
    private val noInfo = ResolvedMetaVarInfo(emptySet(), emptyMap())
    private fun unify(a: TypeConstraint, b: TypeConstraint) = unifyTypeConstraint(a, b, noInfo, PythonTypeOps)

    @Test fun sameNamedUnifies() =
        assertEquals(pythonNamed("flask.views.View"), unify(pythonNamed("flask.views.View"), pythonNamed("flask.views.View")))

    @Test fun differentNamedFails() = assertNull(unify(pythonNamed("A"), pythonNamed("B")))

    @Test fun anyAbsorbs() = assertEquals(pythonNamed("T"), unify(TypeConstraint.Any, pythonNamed("T")))
}
