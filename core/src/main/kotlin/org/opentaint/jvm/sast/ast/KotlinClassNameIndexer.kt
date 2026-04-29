package org.opentaint.jvm.sast.ast

import mu.KLogging
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import org.opentaint.semgrep.pattern.kotlin.antlr.KotlinLexer
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

object KotlinClassNameIndexer {
    private val logger = object : KLogging() {}.logger

    class KotlinClassIndex: ClassIndex {
        override val language: ClassIndex.Language
            get() = ClassIndex.Language.Kotlin

        val fqnLocations = hashMapOf<String, MutableSet<Path>>()
        val classLocations = hashMapOf<String, MutableSet<Path>>()
        val packagedFileLocations = hashMapOf<Pair<String, String>, MutableSet<Path>>()
        val fileLocations = hashMapOf<String, MutableSet<Path>>()
        val fileNameLocations = hashMapOf<String, MutableSet<Path>>()

        override fun lookup(fqn: String): ClassIndex.LookupResult? {
            fqnLocations[fqn]?.let { return ClassIndex.LookupResult(0, it) }

            val simpleName = fqn.substringAfterLast('.')
            val pkgName = fqn.substringBeforeLast('.', missingDelimiterValue = "")

            if (simpleName.endsWith(KT_FILE_CLASS_SUFFIX)) {
                val fileName = simpleName.removeSuffix(KT_FILE_CLASS_SUFFIX)
                packagedFileLocations[pkgName to fileName]?.let { return ClassIndex.LookupResult(1, it) }
            }

            classLocations[simpleName]?.let { return ClassIndex.LookupResult(2, it) }

            if (simpleName.endsWith(KT_FILE_CLASS_SUFFIX)) {
                val fileName = simpleName.removeSuffix(KT_FILE_CLASS_SUFFIX)
                fileLocations[fileName]?.let { return ClassIndex.LookupResult(3, it) }
            }

            fileLocations[simpleName]?.let { return ClassIndex.LookupResult(4, it) }
            return null
        }
    }

    fun createIndex(files: List<Path>): KotlinClassIndex {
        val index = KotlinClassIndex()

        for (path in files) {
            index.fileNameLocations.getOrPut(path.fileName.toString(), ::mutableSetOf).add(path)

            val fileName = path.nameWithoutExtension
            index.fileLocations.getOrPut(fileName, ::mutableSetOf).add(path)

            val classes = extractClassNames(path)
            classes.classSimpleNames.forEach {
                index.classLocations.getOrPut(it, ::mutableSetOf).add(path)
            }

            classes.fullyQualifiedNames?.forEach {
                index.fqnLocations.getOrPut(it, ::mutableSetOf).add(path)
            }

            classes.packageName?.let { pkg ->
                index.packagedFileLocations.getOrPut(pkg to fileName, ::mutableSetOf).add(path)
            }
        }

        return index
    }

    data class KotlinClassNames(
        val classSimpleNames: List<String>,
        val packageName: String?
    ) {
        val fullyQualifiedNames: List<String>? = packageName?.let { pkg ->
            classSimpleNames.map { "$pkg$DOT_SEPARATOR$it" }
        }
    }

    fun extractClassNames(path: Path): KotlinClassNames = try {
        extractClassNamesFromSource(path)
    } catch (ex: Exception) {
        logger.error(ex) { "Error extracting Kotlin classes from $path" }
        KotlinClassNames(emptyList(), packageName = null)
    }

    private fun extractClassNamesFromSource(path: Path): KotlinClassNames {
        val lexer = KotlinLexer(CharStreams.fromPath(path)).apply { removeErrorListeners() }
        val stream = CommonTokenStream(lexer)

        var packageName: String? = null
        val classNames = mutableListOf<String>()

        while (true) {
            val tkId = stream.LA(1)
            when (tkId) {
                Token.EOF -> break

                KotlinLexer.PACKAGE -> {
                    stream.consume()
                    packageName = parseQualifiedName(stream) ?: continue
                }

                KotlinLexer.CLASS, KotlinLexer.INTERFACE, KotlinLexer.OBJECT, KotlinLexer.ENUM -> {
                    stream.consume()
                    val tokenText = parseIdentifier(stream) ?: continue
                    classNames += tokenText
                }

                else -> stream.consume()
            }
        }

        return KotlinClassNames(classNames, packageName)
    }

    private fun parseQualifiedName(stream: CommonTokenStream): String? {
        val parts = mutableListOf<String>()
        while (true) {
            val tkId = stream.LA(1)
            when (tkId) {
                Token.EOF -> return parts.packageName()

                KotlinLexer.DOT -> {
                    stream.consume()
                    continue
                }

                KotlinLexer.NL, KotlinLexer.SEMICOLON -> {
                    stream.consume()
                    return parts.packageName()
                }

                KotlinLexer.WS, KotlinLexer.DelimitedComment, KotlinLexer.LineComment -> {
                    stream.consume()
                    continue
                }

                else -> {
                    val identifier = parseIdentifier(stream)
                    if (identifier != null) {
                        parts += identifier
                        stream.consume()
                        continue
                    }

                    return parts.packageName()
                }
            }
        }
    }

    private fun List<String>.packageName() = takeIf { it.isNotEmpty() }?.joinToString(DOT_SEPARATOR)

    private fun parseIdentifier(stream: CommonTokenStream): String? {
        val identifierToken = stream.LT(1) ?: return null
        if (identifierToken.type !in identifiers) return null
        return identifierToken.text
    }

    // Kotlin soft keywords that can be used as identifiers
    private val identifiers = setOf(
        KotlinLexer.Identifier,
        KotlinLexer.ABSTRACT,
        KotlinLexer.ANNOTATION,
        KotlinLexer.BY,
        KotlinLexer.CATCH,
        KotlinLexer.COMPANION,
        KotlinLexer.CONSTRUCTOR,
        KotlinLexer.CROSSINLINE,
        KotlinLexer.DATA,
        KotlinLexer.DYNAMIC,
        KotlinLexer.ENUM,
        KotlinLexer.EXTERNAL,
        KotlinLexer.FINAL,
        KotlinLexer.FINALLY,
        KotlinLexer.GET,
        KotlinLexer.IMPORT,
        KotlinLexer.INFIX,
        KotlinLexer.INIT,
        KotlinLexer.INLINE,
        KotlinLexer.INNER,
        KotlinLexer.INTERNAL,
        KotlinLexer.LATEINIT,
        KotlinLexer.NOINLINE,
        KotlinLexer.OPEN,
        KotlinLexer.OPERATOR,
        KotlinLexer.OUT,
        KotlinLexer.OVERRIDE,
        KotlinLexer.PRIVATE,
        KotlinLexer.PROTECTED,
        KotlinLexer.PUBLIC,
        KotlinLexer.REIFIED,
        KotlinLexer.SEALED,
        KotlinLexer.TAILREC,
        KotlinLexer.SET,
        KotlinLexer.VARARG,
        KotlinLexer.WHERE,
        KotlinLexer.FIELD,
        KotlinLexer.PROPERTY,
        KotlinLexer.RECEIVER,
        KotlinLexer.PARAM,
        KotlinLexer.SETPARAM,
        KotlinLexer.DELEGATE,
        KotlinLexer.FILE,
        KotlinLexer.SUSPEND,
        KotlinLexer.CONST,
    )

    private const val DOT_SEPARATOR = "."
    private const val KT_FILE_CLASS_SUFFIX = "Kt"
}
