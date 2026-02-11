package org.opentaint.jvm.sast.ast

import mu.KLogging
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import org.opentaint.semgrep.pattern.antlr.JavaLexer
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

object JavaClassNameExtractor {
    private val logger = object : KLogging() {}.logger

    fun extractClassNames(path: Path, isKotlin: Boolean): List<String> = try {
        extractClassNamesFromSource(path, isKotlin)
    } catch (ex: Throwable) {
        logger.error(ex) { "Error extracting Java classes from $path" }
        listOf(path.nameWithoutExtension)
    }

    private fun extractClassNamesFromSource(path: Path, isKotlin: Boolean): List<String> {
        val lexer = JavaLexer(CharStreams.fromPath(path)).apply { removeErrorListeners() }
        val stream = CommonTokenStream(lexer)

        var packageName: String? = null
        val classNames = mutableListOf<String>()

        while (true) {
            val tkId = stream.LA(1)
            when (tkId) {
                Token.EOF -> break

                JavaLexer.PACKAGE -> {
                    stream.consume()
                    packageName = parseQualifiedName(stream, isKotlin) ?: continue
                }

                JavaLexer.CLASS, JavaLexer.INTERFACE, JavaLexer.RECORD, JavaLexer.ENUM -> {
                    stream.consume()
                    val tokenText = parseIdentifier(stream) ?: continue
                    classNames += tokenText
                }

                else -> stream.consume()
            }
        }

        if (packageName != null) {
            return classNames.map { "$packageName$DOT_SEPARATOR$it" }
        }

        return classNames
    }

    private fun parseQualifiedName(stream: CommonTokenStream, isKotlin: Boolean): String? {
        var prevIdentifier: Token? = null
        val parts = mutableListOf<String>()
        while (true) {
            val tkId = stream.LA(1)
            when (tkId) {
                Token.EOF -> return null

                JavaLexer.DOT -> {
                    stream.consume()
                    continue
                }

                JavaLexer.SEMI -> {
                    stream.consume()
                    return parts.joinToString(DOT_SEPARATOR)
                }

                else -> {
                    val identifierToken = stream.LT(1) ?: return null
                    if (isKotlin && prevIdentifier != null && identifierToken.line != prevIdentifier.line) {
                        return parts.joinToString(DOT_SEPARATOR)
                    }
                    prevIdentifier = identifierToken

                    parts += parseIdentifier(stream) ?: return null
                    stream.consume()
                    continue
                }
            }
        }
    }

    private fun parseIdentifier(stream: CommonTokenStream): String? {
        val identifierToken = stream.LT(1) ?: return null
        if (identifierToken.type !in identifiers) return null
        return identifierToken.text
    }

    private val identifiers = setOf(
        JavaLexer.IDENTIFIER,
        JavaLexer.MODULE,
        JavaLexer.OPEN,
        JavaLexer.REQUIRES,
        JavaLexer.EXPORTS,
        JavaLexer.OPENS,
        JavaLexer.TO,
        JavaLexer.USES,
        JavaLexer.PROVIDES,
        JavaLexer.WITH,
        JavaLexer.TRANSITIVE,
        JavaLexer.YIELD,
        JavaLexer.SEALED,
        JavaLexer.PERMITS,
        JavaLexer.RECORD,
        JavaLexer.VAR,
    )

    private const val DOT_SEPARATOR = "."
}
