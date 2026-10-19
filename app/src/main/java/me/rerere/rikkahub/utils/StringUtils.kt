package me.rerere.rikkahub.utils

import org.apache.commons.text.StringEscapeUtils
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun String.urlEncode(): String {
    return URLEncoder.encode(this, "UTF-8")
}

fun String.urlDecode(): String {
    return URLDecoder.decode(this, "UTF-8")
}

@OptIn(ExperimentalEncodingApi::class)
fun String.base64Encode(): String {
    return Base64.encode(this.toByteArray())
}

@OptIn(ExperimentalEncodingApi::class)
fun String.base64Decode(): String {
    return String(Base64.decode(this))
}

fun String.escapeHtml(): String {
    return StringEscapeUtils.escapeHtml4(this)
}

fun String.unescapeHtml(): String {
    return StringEscapeUtils.unescapeHtml4(this)
}

fun Number.toFixed(digits: Int = 0) = "%.${digits}f".format(this)

fun String.applyPlaceholders(
    vararg placeholders: Pair<String, String>,
): String {
    var result = this
    for ((placeholder, replacement) in placeholders) {
        result = result.replace("{$placeholder}", replacement)
    }
    return result
}

fun Long.fileSizeToString(): String {
    if (this < 1024) return "$this B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val precision = when {
        value >= 100 -> 0
        value >= 10 -> 1
        else -> 2
    }
    return "%.${precision}f %s".format(value, units[unitIndex])
}

fun Int.formatNumber(): String {
    val absValue = kotlin.math.abs(this)
    val sign = if (this < 0) "-" else ""

    return when {
        absValue < 1000 -> this.toString()
        absValue < 1000000 -> {
            val value = absValue / 1000.0
            if (value == value.toInt().toDouble()) {
                "$sign${value.toInt()}K"
            } else {
                "$sign${value.toFixed(1)}K"
            }
        }

        absValue < 1000000000 -> {
            val value = absValue / 1000000.0
            if (value == value.toInt().toDouble()) {
                "$sign${value.toInt()}M"
            } else {
                "$sign${value.toFixed(1)}M"
            }
        }

        else -> {
            val value = absValue / 1000000000.0
            if (value == value.toInt().toDouble()) {
                "$sign${value.toInt()}B"
            } else {
                "$sign${value.toFixed(1)}B"
            }
        }
    }
}

fun Float.toFixed(digits: Int = 0) = "%.${digits}f".format(this)
fun Double.toFixed(digits: Int = 0) = "%.${digits}f".format(this)

/**
 * 提取字符串中所有引号内的内容
 * 支持多种引号类型：英文双引号 "..."、英文单引号 '...'、中文双引号 "..."、中文单引号 '...'、直角引号「…」、白直角引号『…』
 * @return 所有引号内内容的列表
 */
fun String.extractQuotedContent(): List<String> {
    // Preserve speech order across different quotation-mark styles.
    // Apostrophes inside contractions (don't, it's) are not quote delimiters.
    val patterns = listOf(
        """“([^”]*?)”""",
        """‘([^’]*?)’""",
        """"([^"]*?)"""",
        """(?<![\p{L}\p{N}])'([^'\r\n]*?)'(?![\p{L}\p{N}])""",
        """「([^」]*?)」""",
        """『([^』]*?)』""",
    )
    val matches = patterns.flatMapIndexed { index, pattern ->
        Regex(pattern).findAll(this).map { index to it }.toList()
    }.sortedWith(compareBy<Pair<Int, MatchResult>> { it.second.range.first }.thenBy { it.first })

    val result = mutableListOf<String>()
    var coveredUntil = 0
    for ((_, match) in matches) {
        if (match.range.first < coveredUntil) continue // Avoid nested duplicate speech.
        val content = match.groupValues[1]
        if (content.isNotBlank()) result += content
        coveredUntil = match.range.last + 1
    }
    return result
}

/**
 * 提取字符串中所有引号内的内容并合并为一个字符串
 * @param separator 分隔符，默认为换行
 * @return 合并后的字符串，如果没有引号内容则返回 null
 */
fun String.extractQuotedContentAsText(separator: String = "\n"): String? {
    val contents = extractQuotedContent()
    return if (contents.isNotEmpty()) {
        contents.joinToString(separator)
    } else {
        null
    }
}

/**
 * 移除字符串中所有括号内的内容
 * 支持英文括号 (...) 和中文括号（...）
 * @return 移除括号内容后的字符串，如果全被移除则返回 null
 */
fun String.removeBracketedContent(): String? {
    // Paired delimiters only, including nested stage directions.
    val pairs = mapOf(
        '(' to ')', '（' to '）', '[' to ']', '［' to '］',
        '{' to '}', '｛' to '｝',
    )
    val open = ArrayDeque<Pair<Char, Int>>()
    val ranges = mutableListOf<IntRange>()
    for (i in indices) {
        val c = this[i]
        when {
            c in pairs -> open.addLast(c to i)
            open.isNotEmpty() && pairs[open.last().first] == c -> {
                val (_, from) = open.removeLast()
                if (open.isEmpty()) ranges += from..i
            }
        }
    }
    var start = 0
    val result = buildString {
        for (range in ranges) {
            append(this@removeBracketedContent, start, range.first)
            start = range.last + 1
        }
        append(this@removeBracketedContent, start, this@removeBracketedContent.length)
    }.trim()
    return result.ifBlank { null }
}
