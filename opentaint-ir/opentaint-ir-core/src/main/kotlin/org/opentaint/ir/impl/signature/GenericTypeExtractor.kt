package org.opentaint.ir.impl.signature

import org.objectweb.asm.signature.SignatureVisitor
import org.opentaint.ir.api.Classpath
import org.opentaint.ir.impl.signature.GenericTypeExtractor.IncompleteToken.InnerClass
import org.opentaint.ir.impl.signature.GenericTypeExtractor.IncompleteToken.TopLevelType
import org.opentaint.ir.impl.signature.GenericTypeRegistrant.RejectingSignatureVisitor
import org.opentaint.ir.impl.signature.PrimitiveType.Companion.of

class GenericTypeExtractor(private val cp: Classpath, private val genericTypeRegistrant: GenericTypeRegistrant) : RejectingSignatureVisitor(),
    GenericTypeRegistrant {

    private lateinit var incompleteToken: IncompleteToken

    override fun visitBaseType(descriptor: Char) {
        genericTypeRegistrant.register(of(descriptor, cp))
    }

    override fun visitTypeVariable(name: String) {
        genericTypeRegistrant.register(TypeVariable(cp, name))
    }

    override fun visitArrayType(): SignatureVisitor {
        return GenericTypeExtractor(cp, this)
    }

    override fun register(token: GenericType) {
        genericTypeRegistrant.register(GenericArray(cp, token))
    }

    override fun visitClassType(name: String) {
        incompleteToken = TopLevelType(cp, name)
    }

    override fun visitInnerClassType(name: String) {
        incompleteToken = InnerClass(cp, name, incompleteToken)
    }

    override fun visitTypeArgument() {
        incompleteToken.appendPlaceholder()
    }

    override fun visitTypeArgument(wildcard: Char): SignatureVisitor {
        return when (wildcard) {
            SUPER -> incompleteToken.appendLowerBound()
            EXTENDS -> incompleteToken.appendUpperBound()
            INSTANCEOF -> incompleteToken.appendDirectBound()
            else -> throw IllegalArgumentException("Unknown wildcard: $wildcard")
        }
    }

    override fun visitEnd() {
        incompleteToken.toToken()?.let {
            genericTypeRegistrant.register(it)
        }
    }

    interface IncompleteToken {

        fun appendLowerBound(): SignatureVisitor
        fun appendUpperBound(): SignatureVisitor
        fun appendDirectBound(): SignatureVisitor
        fun appendPlaceholder()

        val isParameterized: Boolean
        val name: String

        fun toToken(): GenericType?

        abstract class AbstractBase(protected val cp: Classpath) : IncompleteToken {

            protected val parameters = ArrayList<GenericType>()


            override fun appendDirectBound(): SignatureVisitor {
                return GenericTypeExtractor(cp, DirectBound())
            }

            override fun appendUpperBound(): SignatureVisitor {
                return GenericTypeExtractor(cp, UpperBound())
            }

            override fun appendLowerBound(): SignatureVisitor {
                return GenericTypeExtractor(cp, LowerBound())
            }

            override fun appendPlaceholder() {
                parameters.add(UnboundWildcard(cp))
            }

            protected inner class DirectBound : GenericTypeRegistrant {
                override fun register(token: GenericType) {
                    parameters.add(token)
                }
            }

            protected inner class UpperBound : GenericTypeRegistrant {
                override fun register(token: GenericType) {
                    parameters.add(BoundWildcard.UpperBoundWildcard(cp,token))
                }
            }

            protected inner class LowerBound : GenericTypeRegistrant {
                override fun register(token: GenericType) {
                    parameters.add(BoundWildcard.LowerBoundWildcard(cp, token))
                }
            }
        }

        class TopLevelType(cp: Classpath, private val internalName: String) : AbstractBase(cp) {

            override fun toToken(): GenericType {
                return if (isParameterized) ParameterizedType(cp, name, parameters) else RawType(cp, name)
            }

            override val isParameterized: Boolean
                get() {
                    return !parameters.isEmpty()
                }

            override val name: String
                get() {
                    return internalName.replace('/', '.')
                }
        }

        class InnerClass(cp: Classpath, private val internalName: String, private val outerTypeToken: IncompleteToken?) :
            AbstractBase(cp) {
            override fun toToken(): GenericType {
                return if (isParameterized || outerTypeToken!!.isParameterized) ParameterizedType.Nested(
                    cp,
                    name,
                    parameters,
                    outerTypeToken!!.toToken()!!
                ) else RawType(cp, name)
            }

            override val isParameterized: Boolean
                get() {
                    return !parameters.isEmpty() || !outerTypeToken!!.isParameterized
                }

            override val name: String
                get() {
                    return outerTypeToken!!.name + INNER_CLASS_SEPARATOR + internalName.replace('/', '.')
                }

            companion object {
                private const val INNER_CLASS_SEPARATOR = '$'
            }
        }
    }
}