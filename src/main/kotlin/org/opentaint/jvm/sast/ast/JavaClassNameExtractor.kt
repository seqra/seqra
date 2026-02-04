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

    fun extractClassNames(path: Path): List<String> = try {
        extractClassNamesFromSource(path)
    } catch (ex: Throwable) {
        logger.error(ex) { "Error extracting Java classes from $path" }
        listOf(path.nameWithoutExtension)
    }

    private fun extractClassNamesFromSource(path: Path): List<String> {
        val lexer = JavaLexer(CharStreams.fromPath(path)).apply { removeErrorListeners() }
        val stream = CommonTokenStream(lexer)

        val classNames = mutableListOf<String>()

        while (true) {
            val tkId = stream.LA(1)
            when (tkId) {
                Token.EOF -> break
                JavaLexer.CLASS, JavaLexer.INTERFACE, JavaLexer.RECORD, JavaLexer.ENUM -> {
                    stream.consume()

                    val identifierToken = stream.LT(1) ?: break
                    if (identifierToken.type != JavaLexer.IDENTIFIER) {
                        continue
                    }

                    val tokenText = identifierToken.text ?: break
                    classNames += tokenText
                }

                else -> stream.consume()
            }
        }

        return classNames
    }
}
