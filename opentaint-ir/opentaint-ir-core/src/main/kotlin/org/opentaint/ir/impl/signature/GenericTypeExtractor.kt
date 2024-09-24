package org.opentaint.ir.impl.signature

import org.objectweb.asm.signature.SignatureVisitor
import org.opentaint.ir.impl.signature.GenericTypeExtractor.IncompleteToken.InnerClass
import org.opentaint.ir.impl.signature.GenericTypeExtractor.IncompleteToken.TopLevelType
import org.opentaint.ir.impl.signature.GenericTypeRegistrant.RejectingSignatureVisitor
import org.opentaint.ir.impl.signature.PrimitiveType.Companion.of

class GenericTypeExtractor(private val genericTypeRegistrant: GenericTypeRegistrant) : RejectingSignatureVisitor(),
    GenericTypeRegistrant {

    private lateinit var incompleteToken: IncompleteToken

    override fun visitBaseType(descriptor: Char) {
        genericTypeRegistrant.register(of(descriptor))
    }

    override fun visitTypeVariable(name: String) {
        genericTypeRegistrant.register(TypeVariable(name))
    }

    override fun visitArrayType(): SignatureVisitor {
        return GenericTypeExtractor(this)
    }

    override fun register(token: GenericType) {
        genericTypeRegistrant.register(GenericArray(token))
    }

    override fun visitClassType(name: String) {
        incompleteToken = TopLevelType(name)
    }

    override fun visitInnerClassType(name: String) {
        incompleteToken = InnerClass(name, incompleteToken)
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

        abstract class AbstractBase : IncompleteToken {

            protected val parameters = ArrayList<GenericType>()


            override fun appendDirectBound(): SignatureVisitor {
                return GenericTypeExtractor(DirectBound())
            }

            override fun appendUpperBound(): SignatureVisitor {
                return GenericTypeExtractor(UpperBound())
            }

            override fun appendLowerBound(): SignatureVisitor {
                return GenericTypeExtractor(LowerBound())
            }

            override fun appendPlaceholder() {
                parameters.add(UnboundWildcard)
            }

            protected inner class DirectBound : GenericTypeRegistrant {
                override fun register(token: GenericType) {
                    parameters.add(token)
                }
            }

            protected inner class UpperBound : GenericTypeRegistrant {
                override fun register(token: GenericType) {
                    parameters.add(UpperBoundWildcard(token))
                }
            }

            protected inner class LowerBound : GenericTypeRegistrant {
                override fun register(token: GenericType) {
                    parameters.add(LowerBoundWildcard(token))
                }
            }
        }

        class TopLevelType(private val internalName: String) : AbstractBase() {

            override fun toToken(): GenericType {
                return if (isParameterized) ParameterizedType(name, parameters) else RawType(name)
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

        class InnerClass(private val internalName: String, private val outerTypeToken: IncompleteToken?) :
            AbstractBase() {
            override fun toToken(): GenericType {
                return if (isParameterized || outerTypeToken!!.isParameterized) ParameterizedType.Nested(
                    name,
                    parameters,
                    outerTypeToken!!.toToken()!!
                ) else RawType(name)
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