package me.rerere.document

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

private data class SheetRef(
    val name: String,
    val target: String
)

private fun XmlPullParser.attributeValue(name: String): String? {
    for (i in 0 until attributeCount) {
        if (getAttributeName(i) == name) return getAttributeValue(i)
    }
    return null
}

object XlsxParser {
    fun parse(file: File): String {
        return try {
            ZipFile(file).use { zip ->
                val sheets = resolveSheets(zip)
                if (sheets.isEmpty()) return "No worksheets found in XLSX file"

                val sharedStrings = parseSharedStrings(zip)

                val result = StringBuilder()
                sheets.forEach { sheet ->
                    val entry = zip.getEntry(sheet.target) ?: return@forEach
                    val content = zip.getInputStream(entry).use { parseSheet(it, sharedStrings) }
                    if (content.isNotBlank()) {
                        result.append("## Sheet: ${sheet.name}\n\n")
                        result.append(content)
                        result.append("\n")
                    }
                }

                result.toString().trim().ifEmpty { "No readable content found in XLSX file" }
            }
        } catch (e: Exception) {
            "Error parsing XLSX file: ${e.message}"
        }
    }

    private fun resolveSheets(zip: ZipFile): List<SheetRef> {
        val workbookEntry = zip.getEntry("xl/workbook.xml") ?: return emptyList()
        val relsEntry = zip.getEntry("xl/_rels/workbook.xml.rels") ?: return emptyList()

        val rels = zip.getInputStream(relsEntry).use { parseRels(it) }
        val order = zip.getInputStream(workbookEntry).use { parseWorkbook(it) }
        return order.mapNotNull { (name, id) ->
            val target = rels[id] ?: return@mapNotNull null
            SheetRef(name, target.removePrefix("/").ifEmpty { target })
        }
    }

    private fun parseRels(inputStream: InputStream): Map<String, String> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        val rels = mutableMapOf<String, String>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.attributeValue("Id") ?: ""
                val target = parser.attributeValue("Target") ?: ""
                if (id.isNotEmpty() && target.isNotEmpty()) {
                    rels[id] = target
                }
            }
            parser.next()
        }
        return rels
    }

    private fun parseWorkbook(inputStream: InputStream): List<Pair<String, String>> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        val sheets = mutableListOf<Pair<String, String>>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.attributeValue("name") ?: ""
                val id = parser.attributeValue("id") ?: ""
                if (name.isNotEmpty() && id.isNotEmpty()) {
                    sheets.add(name to id)
                }
            }
            parser.next()
        }
        return sheets
    }

    private fun parseSharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        return zip.getInputStream(entry).use { stream ->
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            val strings = mutableListOf<String>()
            val current = StringBuilder()
            var inStringItem = false
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "si" -> {
                            inStringItem = true
                            current.setLength(0)
                        }
                        "t" -> if (inStringItem) {
                            parser.next()
                            if (parser.eventType == XmlPullParser.TEXT) current.append(parser.text ?: "")
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "si") {
                        strings.add(current.toString())
                        inStringItem = false
                    }
                }
                parser.next()
            }
            strings
        }
    }

    private fun parseSheet(inputStream: InputStream, sharedStrings: List<String>): String {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            val rows = mutableListOf<List<String>>()
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "row") {
                    val cells = mutableListOf<Pair<Int, String>>()
                    parseRow(parser, sharedStrings, cells)
                    if (cells.isNotEmpty()) {
                        val maxCol = cells.maxOfOrNull { it.first } ?: 0
                        val row = Array(maxCol + 1) { "" }
                        cells.forEach { (col, value) -> row[col] = value }
                        rows.add(row.toList())
                    }
                }
                parser.next()
            }

            formatTable(rows)
        } catch (e: Exception) {
            "Error parsing worksheet XML: ${e.message}"
        }
    }

    private fun parseRow(parser: XmlPullParser, sharedStrings: List<String>, cells: MutableList<Pair<Int, String>>) {
        val rowStartDepth = parser.depth
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> if (parser.name == "c") {
                    val cellRef = parser.attributeValue("r") ?: ""
                    val cellType = parser.attributeValue("t")
                    val value = extractCellValue(parser, cellType, sharedStrings)
                    cells.add(columnIndexOf(cellRef) to value)
                }
                XmlPullParser.END_TAG -> if (parser.name == "row" && parser.depth == rowStartDepth) return
            }
        }
    }

    private fun extractCellValue(parser: XmlPullParser, cellType: String?, sharedStrings: List<String>): String {
        val cellStartDepth = parser.depth
        var value = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "v" -> {
                        parser.next()
                        if (parser.eventType == XmlPullParser.TEXT) value = parser.text ?: ""
                    }
                    "is" -> {
                        parser.next()
                        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "t") {
                            parser.next()
                            if (parser.eventType == XmlPullParser.TEXT) value = parser.text ?: ""
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "c" && parser.depth == cellStartDepth) break
            }
        }
        return when (cellType) {
            "s" -> value.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
            "b" -> if (value == "1") "TRUE" else if (value == "0") "FALSE" else value
            "inlineStr" -> value
            else -> value
        }
    }

    private fun columnIndexOf(cellRef: String): Int {
        if (cellRef.isEmpty()) return 0
        val letters = cellRef.takeWhile { it.isLetter() }
        var index = 0
        for (ch in letters) {
            index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return index - 1
    }

    private fun formatTable(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
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
