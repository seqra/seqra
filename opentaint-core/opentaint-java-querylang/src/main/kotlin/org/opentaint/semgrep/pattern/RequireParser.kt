package org.opentaint.semgrep.pattern

fun parseRequires(input: String): SemgrepTaintRequires? {
    val toks = tokenize(input).takeIf { it.isNotEmpty() } ?: return null

    val parser = Parser(toks)
    val res = parser.parseOr() ?: return null
    if (!parser.allTokensParsed()) return null

    return res
}

private sealed interface Tok
private data class Id(val s: String) : Tok
private data object AndTok : Tok
private data object OrTok : Tok
private data object NotTok : Tok
private data object LParen : Tok
private data object RParen : Tok

private fun tokenize(s: String): List<Tok> {
    val tokens = mutableListOf<Tok>()
    var i = 0
    while (i < s.length) {
        if (s[i].isWhitespace()) {
            i++
            continue
        }
        when (s[i]) {
            '(' -> { tokens += LParen; i++ }
            ')' -> { tokens += RParen; i++ }
            else -> {
                val sb = StringBuilder()
                while (i < s.length && !s[i].isWhitespace() && s[i] != '(' && s[i] != ')') {
                    sb.append(s[i]); i++
                }
                val word = sb.toString()
                when (word.lowercase()) {
                    "and" -> tokens += AndTok
                    "or" -> tokens += OrTok
                    "not" -> tokens += NotTok
                    else -> tokens += Id(word)
                }
            }
        }
    }
    return tokens
}

private class Parser(val tokens: List<Tok>) {
    private var pos = 0

    fun peek(): Tok? = tokens.getOrNull(pos)
    fun eat(): Tok = tokens[pos++]

    fun allTokensParsed(): Boolean = pos == tokens.size

    fun parsePrimary(): SemgrepTaintRequires? {
        return when (val t = peek()) {
            is Id -> {
                eat()
                SemgrepTaintLabel(t.s)
            }

            is LParen -> {
                eat()

                val expr = parseOr()
                if (peek() !is RParen) return null

                eat()
                expr
            }

            is NotTok -> {
                eat()
                val child = parsePrimary() ?: return null
                SemgrepTaintNot(child)
            }

            null,
            is AndTok,
            is OrTok,
            is RParen -> null
        }
    }

    fun parseNot(): SemgrepTaintRequires? {
        if (peek() is NotTok) {
            eat()
            val child = parseNot() ?: return null
            return SemgrepTaintNot(child)
        }
        return parsePrimary()
    }

    fun parseAnd(): SemgrepTaintRequires? {
        var left = parseNot() ?: return null
        while (peek() is AndTok) {
            eat()
            val right = parseNot() ?: return null
            left = SemgrepTaintAnd(left, right)
        }
        return left
    }

    fun parseOr(): SemgrepTaintRequires? {
        var left = parseAnd() ?: return null
        while (peek() is OrTok) {
            eat()
            val right = parseAnd() ?: return null
            left = SemgrepTaintOr(left, right)
        }
        return left
    }
}
