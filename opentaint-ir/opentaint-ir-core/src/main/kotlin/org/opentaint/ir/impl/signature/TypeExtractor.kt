package org.opentaint.ir.impl.signature

import org.objectweb.asm.signature.SignatureVisitor
import org.opentaint.ir.impl.signature.SPrimitiveType.Companion.of
import org.opentaint.ir.impl.signature.TypeExtractor.IncompleteToken.InnerClass
import org.opentaint.ir.impl.signature.TypeExtractor.IncompleteToken.TopLevelType
import org.opentaint.ir.impl.signature.TypeRegistrant.RejectingSignatureVisitor

internal class TypeExtractor(private val typeRegistrant: TypeRegistrant) :
    RejectingSignatureVisitor(),
    TypeRegistrant {

    private lateinit var incompleteToken: IncompleteToken

    override fun visitBaseType(descriptor: Char) {
        typeRegistrant.register(of(descriptor))
    }

    override fun visitTypeVariable(name: String) {
        typeRegistrant.register(STypeVariable(name))
    }

    override fun visitArrayType(): SignatureVisitor {
        return TypeExtractor(this)
    }

    override fun register(token: SType) {
        typeRegistrant.register(SArrayType(token))
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
            typeRegistrant.register(it)
        }
    }

    interface IncompleteToken {

        fun appendLowerBound(): SignatureVisitor
        fun appendUpperBound(): SignatureVisitor
        fun appendDirectBound(): SignatureVisitor
        fun appendPlaceholder()

        val isParameterized: Boolean
        val name: String

        fun toToken(): SType?

        abstract class AbstractBase() : IncompleteToken {

            protected val parameters = ArrayList<SType>()


            override fun appendDirectBound(): SignatureVisitor {
                return TypeExtractor(DirectBound())
            }

            override fun appendUpperBound(): SignatureVisitor {
                return TypeExtractor(UpperBound())
            }

            override fun appendLowerBound(): SignatureVisitor {
                return TypeExtractor(LowerBound())
            }

            override fun appendPlaceholder() {
                parameters.add(SUnboundWildcard)
            }

            protected inner class DirectBound : TypeRegistrant {
                override fun register(token: SType) {
                    parameters.add(token)
                }
            }

            protected inner class UpperBound : TypeRegistrant {
                override fun register(token: SType) {
                    parameters.add(SBoundWildcard.SUpperBoundWildcard(token))
                }
            }

            protected inner class LowerBound : TypeRegistrant {
                override fun register(token: SType) {
                    parameters.add(SBoundWildcard.SLowerBoundWildcard(token))
                }
            }
        }

        class TopLevelType(private val internalName: String) : AbstractBase() {

            override fun toToken(): SType {
                return if (isParameterized) SParameterizedType(name, parameters) else SClassRefType(name)
            }

            override val isParameterized: Boolean
                get() {
                    return parameters.isNotEmpty()
                }

            override val name: String
                get() {
                    return internalName.replace('/', '.')
                }
        }

        class InnerClass(private val internalName: String, private val outerTypeToken: IncompleteToken?) :
            AbstractBase() {
            override fun toToken(): SType {
                return if (isParameterized || outerTypeToken!!.isParameterized) SParameterizedType.SNestedType(
                    name,
                    parameters,
                    outerTypeToken!!.toToken()!!
                ) else SClassRefType(name)
            }

            override val isParameterized: Boolean
                get() {
                    return parameters.isNotEmpty() || !outerTypeToken!!.isParameterized
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