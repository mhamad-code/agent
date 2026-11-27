package com.agent.ai.markdown

/** What a highlighted run of code is, before it is given a colour by the theme. */
enum class TokenKind {
    Plain,
    Keyword,
    String,
    Comment,
    Number,
    Type,
    Function,
    Operator,
    Property,
    Added,
    Removed,
}

/** A half-open range [start, end) of the code with one meaning. */
data class SyntaxToken(val start: Int, val end: Int, val kind: TokenKind)

/**
 * A small hand written highlighter instead of a library.
 *
 * Why: the usual Android options are Markwon based (Apache 2.0 but pulls in a whole
 * rendering stack we do not use, since the app has its own streaming Markdown renderer) or
 * highlight.js ports, which are large and often ship without a maintained Android artifact.
 * A tokenizer of about 300 lines has no dependencies, no licence questions, does not
 * allocate a parse tree per token, and only needs to classify runs so the theme can colour
 * them. Unknown languages fall back to plain text.
 */
object SyntaxHighlighter {

    private val Kotlin = Language(
        keywords = setOf(
            "as", "break", "by", "catch", "class", "companion", "const", "constructor",
            "continue", "data", "do", "else", "enum", "false", "finally", "for", "fun",
            "if", "in", "interface", "internal", "is", "null", "object", "open", "operator",
            "out", "override", "package", "private", "protected", "public", "return", "sealed",
            "super", "this", "throw", "true", "try", "typealias", "val", "var", "when", "while",
            "suspend", "inline", "reified", "lateinit", "init", "abstract", "annotation",
        ),
        types = setOf(
            "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char", "String",
            "Unit", "Any", "Nothing", "List", "Map", "Set", "Array", "Flow", "StateFlow",
        ),
        lineComments = listOf("//"),
        blockComment = "/*" to "*/",
        tripleQuotes = true,
    )

    private val Java = Kotlin.copy(
        keywords = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "var", "record",
            "true", "false", "null",
        ),
        types = setOf("String", "Object", "Integer", "Long", "Double", "Float", "Boolean", "List", "Map"),
        tripleQuotes = false,
    )

    private val Python = Language(
        keywords = setOf(
            "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
            "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import",
            "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return",
            "True", "try", "while", "with", "yield", "self", "match", "case",
        ),
        types = setOf("int", "float", "str", "bool", "list", "dict", "set", "tuple", "bytes"),
        lineComments = listOf("#"),
        tripleQuotes = true,
    )

    private val JavaScript = Language(
        keywords = setOf(
            "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "export", "extends", "finally", "for", "from",
            "function", "if", "import", "in", "instanceof", "let", "new", "of", "return", "static",
            "super", "switch", "this", "throw", "try", "typeof", "var", "void", "while", "yield",
            "true", "false", "null", "undefined",
        ),
        types = setOf("String", "Number", "Boolean", "Object", "Array", "Promise", "Map", "Set"),
        lineComments = listOf("//"),
        blockComment = "/*" to "*/",
        tripleQuotes = false,
        backtickStrings = true,
    )

    private val TypeScript = JavaScript.copy(
        keywords = JavaScript.keywords + setOf(
            "interface", "type", "enum", "implements", "namespace", "declare", "readonly",
            "private", "public", "protected", "abstract", "as", "satisfies", "keyof", "infer",
        ),
    )

    private val Sql = Language(
        keywords = setOf(
            "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
            "create", "table", "alter", "drop", "join", "left", "right", "inner", "outer", "on",
            "group", "by", "order", "having", "limit", "offset", "as", "and", "or", "not", "null",
            "primary", "key", "foreign", "references", "index", "distinct", "union", "all", "case",
            "when", "then", "else", "end", "with", "returning", "default", "constraint",
        ),
        types = setOf("int", "integer", "text", "varchar", "boolean", "date", "timestamp", "real", "blob"),
        lineComments = listOf("--"),
        blockComment = "/*" to "*/",
        caseInsensitive = true,
    )

    private val Bash = Language(
        keywords = setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
            "function", "in", "return", "local", "export", "readonly", "echo", "cd", "source",
            "sudo", "set", "unset", "shift", "exit", "true", "false",
        ),
        lineComments = listOf("#"),
        backtickStrings = true,
    )

    private val Yaml = Language(
        keywords = setOf("true", "false", "null", "yes", "no", "on", "off"),
        lineComments = listOf("#"),
        yamlKeys = true,
    )

    private val JsonLanguage = Language(
        keywords = setOf("true", "false", "null"),
        jsonKeys = true,
    )

    private val Css = Language(
        lineComments = emptyList(),
        blockComment = "/*" to "*/",
        cssProperties = true,
        cssAtRules = setOf("@media", "@import", "@font-face", "@keyframes", "@supports", "@layer"),
    )

    private val MarkdownLanguage = Language(lineComments = emptyList(), markdown = true)

    private val Html = Language(lineComments = emptyList(), markup = true)

    private val Plain = Language(lineComments = emptyList())

    private fun languageFor(raw: String): Language = when (raw.trim().lowercase().trim('`')) {
        "kotlin", "kt", "kts" -> Kotlin
        "java" -> Java
        "python", "py" -> Python
        "javascript", "js", "jsx", "mjs", "cjs" -> JavaScript
        "typescript", "ts", "tsx" -> TypeScript
        "json", "jsonc" -> JsonLanguage
        "yaml", "yml" -> Yaml
        "xml" -> Html
        "html", "htm", "svg" -> Html
        "css", "scss", "less" -> Css
        "bash", "sh", "shell", "zsh", "console" -> Bash
        "sql", "sqlite" -> Sql
        "markdown", "md" -> MarkdownLanguage
        "diff", "patch" -> Plain
        else -> Plain
    }

    val supported: Set<String> = setOf(
        "kotlin", "java", "python", "javascript", "typescript", "json", "yaml", "xml",
        "html", "css", "bash", "sql", "markdown", "diff",
    )

    fun normalize(language: String): String = when (val value = language.trim().lowercase()) {
        "kt", "kts" -> "kotlin"
        "py" -> "python"
        "js", "jsx", "mjs", "cjs" -> "javascript"
        "ts", "tsx" -> "typescript"
        "yml" -> "yaml"
        "htm", "svg" -> "html"
        "sh", "shell", "zsh", "console" -> "bash"
        "sqlite" -> "sql"
        "md" -> "markdown"
        "patch" -> "diff"
        else -> value
    }

    fun isDiff(language: String): Boolean = normalize(language) == "diff"

    fun highlight(code: String, language: String): List<SyntaxToken> {
        if (code.isEmpty()) return emptyList()
        val name = normalize(language)
        if (name !in supported) return emptyList()
        return when (name) {
            "diff" -> diff(code)
            "xml", "html" -> markup(code)
            "markdown" -> markdown(code)
            "json" -> generic(code, JsonLanguage)
            "yaml" -> generic(code, Yaml)
            "css" -> generic(code, Css)
            else -> generic(code, languageFor(name))
        }
    }

    // ----- Generic scanner ------------------------------------------------------------

    private fun generic(code: String, language: Language): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        val n = code.length
        var i = 0

        fun add(start: Int, end: Int, kind: TokenKind) {
            if (end > start) tokens += SyntaxToken(start, end, kind)
        }

        while (i < n) {
            val char = code[i]

            val lineComment = language.lineComments.firstOrNull { code.startsWith(it, i) }
            if (lineComment != null) {
                val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                add(i, end, TokenKind.Comment)
                i = end
                continue
            }

            val block = language.blockComment
            if (block != null && code.startsWith(block.first, i)) {
                val end = code.indexOf(block.second, i + block.first.length)
                val stop = if (end < 0) n else end + block.second.length
                add(i, stop, TokenKind.Comment)
                i = stop
                continue
            }

            if (char == '"' || char == '\'' || (char == '`' && language.backtickStrings)) {
                val triple = language.tripleQuotes && code.startsWith("\"\"\"", i)
                val quote = if (triple) "\"\"\"" else char.toString()
                var end = i + quote.length
                while (end < n) {
                    if (!triple && code[end] == '\\') {
                        end += 2
                        continue
                    }
                    if (code.startsWith(quote, end)) {
                        end += quote.length
                        break
                    }
                    end++
                }
                add(i, end.coerceAtMost(n), TokenKind.String)
                i = end
                continue
            }

            if (char.isDigit()) {
                var end = i
                while (end < n && (code[end].isLetterOrDigit() || code[end] == '.' || code[end] == '_')) end++
                add(i, end, TokenKind.Number)
                i = end
                continue
            }

            if (char.isLetter() || char == '_' || char == '$' || char == '@') {
                var end = i
                while (end < n && (code[end].isLetterOrDigit() || code[end] == '_' || code[end] == '$')) end++
                val word = code.substring(i, end)
                val lower = word.lowercase()

                var lookahead = end
                while (lookahead < n && code[lookahead] == ' ') lookahead++
                val next = code.getOrNull(lookahead)

                val kind = when {
                    language.cssAtRules.any { it.equals(word, ignoreCase = true) } -> TokenKind.Keyword
                    language.yamlKeys && next == ':' -> TokenKind.Property
                    language.jsonKeys && next == ':' -> TokenKind.Property
                    language.cssProperties && next == ':' -> TokenKind.Property
                    language.keywords.any { it.equals(word, ignoreCase = language.caseInsensitive) } ->
                        TokenKind.Keyword
                    language.types.contains(word) -> TokenKind.Type
                    next == '(' -> TokenKind.Function
                    word.first().isUpperCase() -> TokenKind.Type
                    else -> null
                }
                if (kind != null) add(i, end, kind)
                i = end
                continue
            }

            if (char in "+-*/%=<>!&|?:^~") {
                add(i, i + 1, TokenKind.Operator)
            }
            i++
        }
        return tokens
    }

    // ----- XML / HTML ------------------------------------------------------------------

    private fun markup(code: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0
        while (i < code.length) {
            if (code.startsWith("<!--", i)) {
                val end = code.indexOf("-->", i + 4)
                val stop = if (end < 0) code.length else end + 3
                tokens += SyntaxToken(i, stop, TokenKind.Comment)
                i = stop
                continue
            }
            if (code[i] == '<') {
                val end = code.indexOf('>', i)
                val stop = if (end < 0) code.length else end + 1
                var j = i + 1
                if (code.getOrNull(j) == '/') j++
                val nameEnd = runWhile(code, j) { it.isLetterOrDigit() || it == ':' || it == '-' || it == '_' }
                tokens += SyntaxToken(i, nameEnd, TokenKind.Keyword)
                j = nameEnd
                while (j < stop) {
                    val c = code[j]
                    if (c == '"' || c == '\'') {
                        val close = code.indexOf(c, j + 1)
                        val stringEnd = if (close < 0 || close > stop) stop else close + 1
                        tokens += SyntaxToken(j, stringEnd, TokenKind.String)
                        j = stringEnd
                        continue
                    }
                    if (c.isLetter()) {
                        val attrEnd = runWhile(code, j) { it.isLetterOrDigit() || it == '-' || it == ':' }
                        tokens += SyntaxToken(j, attrEnd, TokenKind.Property)
                        j = attrEnd
                        continue
                    }
                    j++
                }
                i = stop
                continue
            }
            i++
        }
        return tokens
    }

    // ----- Markdown --------------------------------------------------------------------

    private fun markdown(code: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var lineStart = 0
        var inFence = false
        while (lineStart <= code.length) {
            val lineEnd = code.indexOf('\n', lineStart).let { if (it < 0) code.length else it }
            val line = code.substring(lineStart, lineEnd)
            val trimmed = line.trimStart()
            val offset = lineStart + (line.length - trimmed.length)

            when {
                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    tokens += SyntaxToken(lineStart, lineEnd, TokenKind.String)
                    inFence = !inFence
                }

                inFence -> tokens += SyntaxToken(lineStart, lineEnd, TokenKind.Plain)

                trimmed.startsWith("#") -> tokens += SyntaxToken(offset, lineEnd, TokenKind.Keyword)

                trimmed.startsWith(">") -> tokens += SyntaxToken(offset, lineEnd, TokenKind.Comment)

                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    tokens += SyntaxToken(offset, offset + 2, TokenKind.Keyword)
                    tokens += inlineRanges(line, lineStart, from = 2)
                }

                else -> tokens += inlineRanges(line, lineStart)
            }
            if (lineEnd >= code.length) break
            lineStart = lineEnd + 1
        }
        return tokens
    }

    /** Tags inline code and emphasis markers. Indices are made absolute with [lineStart]. */
    private fun inlineRanges(line: String, lineStart: Int, from: Int = 0): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = from
        while (i < line.length) {
            if (line[i] == '`') {
                val close = line.indexOf('`', i + 1)
                if (close >= 0) {
                    tokens += SyntaxToken(lineStart + i, lineStart + close + 1, TokenKind.String)
                    i = close + 1
                    continue
                }
            }
            if (line[i] == '*' || line[i] == '_') {
                tokens += SyntaxToken(lineStart + i, lineStart + i + 1, TokenKind.Keyword)
            }
            i++
        }
        return tokens
    }

    // ----- Diff ------------------------------------------------------------------------

    private fun diff(code: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var lineStart = 0
        while (lineStart <= code.length) {
            val lineEnd = code.indexOf('\n', lineStart).let { if (it < 0) code.length else it }
            if (lineEnd > lineStart) {
                val first = code[lineStart]
                val kind = when {
                    code.startsWith("+++", lineStart) || code.startsWith("---", lineStart) ||
                        code.startsWith("diff ", lineStart) || code.startsWith("index ", lineStart) -> TokenKind.Keyword
                    first == '+' -> TokenKind.Added
                    first == '-' -> TokenKind.Removed
                    code.startsWith("@@", lineStart) -> TokenKind.Property
                    else -> TokenKind.Plain
                }
                tokens += SyntaxToken(lineStart, lineEnd, kind)
            }
            if (lineEnd >= code.length) break
            lineStart = lineEnd + 1
        }
        return tokens
    }

    private inline fun runWhile(text: String, from: Int, predicate: (Char) -> Boolean): Int {
        var i = from
        while (i < text.length && predicate(text[i])) i++
        return i
    }

    private data class Language(
        val keywords: Set<String> = emptySet(),
        val types: Set<String> = emptySet(),
        val lineComments: List<String> = emptyList(),
        val blockComment: Pair<String, String>? = null,
        val tripleQuotes: Boolean = false,
        val caseInsensitive: Boolean = false,
        val backtickStrings: Boolean = false,
        val yamlKeys: Boolean = false,
        val jsonKeys: Boolean = false,
        val cssProperties: Boolean = false,
        val cssAtRules: Set<String> = emptySet(),
        val markup: Boolean = false,
        val markdown: Boolean = false,
    )
}
