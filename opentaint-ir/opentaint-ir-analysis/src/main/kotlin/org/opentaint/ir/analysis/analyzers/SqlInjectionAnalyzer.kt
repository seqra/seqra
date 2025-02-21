package org.opentaint.ir.analysis.analyzers

fun SqlInjectionAnalyzerFactory(maxPathLength: Int) = TaintAnalyzerFactory(
    sqlSourceMatchers,
    sqlSanitizeMatchers,
    sqlSinkMatchers,
    maxPathLength
)

fun SqlInjectionBackwardAnalyzerFactory(maxPathLength: Int) = TaintBackwardAnalyzerFactory(
    sqlSourceMatchers,
    sqlSinkMatchers,
    maxPathLength
)

private val sqlSourceMatchers = listOf(
    "java\\.io.+",
    "java\\.lang\\.System\\#getenv",
    "java\\.sql\\.ResultSet#get.+"
)

private val sqlSanitizeMatchers = listOf(
    "java\\.sql\\.Statement#set.*",
    "java\\.sql\\.PreparedStatement#set.*"
)

private val sqlSinkMatchers = listOf(
    "java\\.sql\\.Statement#execute.*",
    "java\\.sql\\.PreparedStatement#execute.*",
)