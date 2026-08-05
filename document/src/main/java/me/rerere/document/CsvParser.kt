package me.rerere.document

import java.io.File

object CsvParser {
    fun parse(file: File): String {
        return try {
            val text = file.readText()
            if (text.isBlank()) return "No readable content found in CSV file"
            val rows = parseCsv(text)
            if (rows.isEmpty()) return "No readable content found in CSV file"
            formatTable(rows)
        } catch (e: Exception) {
            "Error parsing CSV file: ${e.message}"
        }
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> when {
                    ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    ch == '"' -> inQuotes = false
                    else -> field.append(ch)
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> {
                    currentRow.add(field.toString())
                    field.setLength(0)
                }
                ch == '\r' || ch == '\n' -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    currentRow.add(field.toString())
                    field.setLength(0)
                    if (currentRow.any { it.isNotBlank() }) {
                        rows.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> field.append(ch)
            }
            i++
        }

        if (field.isNotBlank() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow.toList())
            }
        }
        return rows
    }

    private fun formatTable(rows: List<List<String>>): String {
        val maxCols = rows.maxOfOrNull { it.size } ?: 0
        val result = StringBuilder()
        for ((index, row) in rows.withIndex()) {
            result.append("| ")
            for (colIndex in 0 until maxCols) {
                val cellContent = if (colIndex < row.size) row[colIndex] else ""
                result.append(cellContent.replace('\n', ' ').trim()).append(" | ")
            }
            result.append("\n")
            if (index == 0) {
                result.append("| ")
                repeat(maxCols) { result.append("--- | ") }
                result.append("\n")
            }
        }
        result.append("\n")
        return result.toString()
    }
}
