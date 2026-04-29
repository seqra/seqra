package org.opentaint.jvm.sast.ast

import mu.KLogging
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import org.opentaint.semgrep.pattern.antlr.JavaLexer
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

object JavaClassNameIndexer {
    private val logger = object : KLogging() {}.logger

    class JavaClassIndex: ClassIndex {
        override val language: ClassIndex.Language
            get() = ClassIndex.Language.Java

        val fqnLocations = hashMapOf<String, MutableSet<Path>>()
        val classLocations = hashMapOf<String, MutableSet<Path>>()
        val fileLocations = hashMapOf<String, MutableSet<Path>>()

        override fun lookup(fqn: String): ClassIndex.LookupResult? {
            fqnLocations[fqn]?.let { return ClassIndex.LookupResult(0, it) }

            val simpleName = fqn.substringAfterLast('.')
            classLocations[simpleName]?.let { return ClassIndex.LookupResult(1, it) }
            fileLocations[simpleName]?.let { return ClassIndex.LookupResult(2, it) }
            return null
        }
    }

    fun createIndex(files: List<Path>): JavaClassIndex {
        val index = JavaClassIndex()

        for (path in files) {
            val fileName = path.nameWithoutExtension
            index.fileLocations.getOrPut(fileName, ::mutableSetOf).add(path)

            val classes = extractClassNames(path)
            classes.classSimpleNames.forEach {
                index.classLocations.getOrPut(it, ::mutableSetOf).add(path)
            }

            classes.fullyQualifiedNames?.forEach {
                index.fqnLocations.getOrPut(it, ::mutableSetOf).add(path)
            }
        }

        return index
    }

    data class JavaClassNames(
        val classSimpleNames: List<String>,
        val packageName: String?
    ) {
        val fullyQualifiedNames: List<String>? = packageName?.let { pkg ->
            classSimpleNames.map { "$pkg${DOT_SEPARATOR}$it" }
        }
    }

    fun extractClassNames(path: Path): JavaClassNames = try {
        extractClassNamesFromSource(path)
    } catch (ex: Exception) {
        logger.error(ex) { "Error extracting Java classes from $path" }
        JavaClassNames(emptyList(), packageName = null)
    }

    private fun extractClassNamesFromSource(path: Path): JavaClassNames {
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
                    packageName = parseQualifiedName(stream) ?: continue
                }

                JavaLexer.CLASS, JavaLexer.INTERFACE, JavaLexer.RECORD, JavaLexer.ENUM -> {
                    stream.consume()
                    val tokenText = parseIdentifier(stream) ?: continue
                    classNames += tokenText
                }

                else -> stream.consume()
            }
        }

        return JavaClassNames(classNames, packageName)
    }

    private fun parseQualifiedName(stream: CommonTokenStream): String? {
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
