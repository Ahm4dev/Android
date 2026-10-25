package com.auditcafe.wallet.io

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** قارئ/كاتب XLSX خفيف بدون مكتبات خارجية (يتحمل مئات الآلاف من الصفوف بالتدفق). */
object XlsxIO {
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val HDR = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    private fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, 'A' + n % 26)
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '"' -> sb.append("&quot;")
                c.code < 32 && c != '\n' && c != '\t' -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    /** rows: دالة تستدعي emit لكل صف (قيم String أو Number). */
    fun export(out: OutputStream, sheetName: String, header: List<String>, rows: ((List<Any?>) -> Unit) -> Unit) {
        ZipOutputStream(BufferedOutputStream(out, 64 * 1024)).use { z ->
            z.put(
                "[Content_Types].xml",
                HDR + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                    "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                    "</Types>"
            )
            z.put(
                "_rels/.rels",
                HDR + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>"
            )
            z.put(
                "xl/workbook.xml",
                HDR + "<workbook xmlns=\"$NS\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<sheets><sheet name=\"${esc(sheetName)}\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
            )
            z.put(
                "xl/_rels/workbook.xml.rels",
                HDR + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                    "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                    "</Relationships>"
            )
            z.put(
                "xl/styles.xml",
                HDR + "<styleSheet xmlns=\"$NS\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                    "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
                    "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
                    "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                    "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                    "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                    "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs></styleSheet>"
            )
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val w = z.bufferedWriter(Charsets.UTF_8)
            w.write(HDR + "<worksheet xmlns=\"$NS\">")
            w.write("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
            w.write("<cols><col min=\"1\" max=\"${header.size}\" width=\"22\" customWidth=\"1\"/></cols><sheetData>")
            var r = 1
            fun writeRow(values: List<Any?>, style: Boolean) {
                w.write("<row r=\"$r\">")
                values.forEachIndexed { i, v ->
                    val ref = colName(i) + r
                    val s = if (style) " s=\"1\"" else ""
                    when (v) {
                        null -> {}
                        is Number -> w.write("<c r=\"$ref\"$s><v>$v</v></c>")
                        else -> w.write("<c r=\"$ref\" t=\"inlineStr\"$s><is><t xml:space=\"preserve\">${esc(v.toString())}</t></is></c>")
                    }
                }
                w.write("</row>")
                r++
            }
            writeRow(header, true)
            rows { writeRow(it, false) }
            w.write("</sheetData></worksheet>")
            w.flush()
            z.closeEntry()
        }
    }

    // ---------------- القراءة ----------------
    private fun parseShared(b: ByteArray): List<String> {
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(b), null)
        val list = ArrayList<String>()
        var inSi = false
        var inT = false
        val sb = StringBuilder()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "t" -> if (inSi) inT = true
                }
                XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> inT = false
                    "si" -> { list.add(sb.toString()); inSi = false }
                }
            }
            ev = p.next()
        }
        return list
    }

    private fun colIndex(ref: String?): Int {
        if (ref == null) return 0
        var n = 0
        for (c in ref) {
            if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1) else if (c in 'a'..'z') n = n * 26 + (c - 'a' + 1) else break
        }
        return n - 1
    }

    private fun parseSheet(b: ByteArray, shared: List<String>): List<List<String>> {
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(b), null)
        val rows = ArrayList<List<String>>()
        var row = HashMap<Int, String>()
        var col = 0
        var ctype: String? = null
        var inV = false
        var inT = false
        val sb = StringBuilder()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = HashMap()
                    "c" -> { col = colIndex(p.getAttributeValue(null, "r")); ctype = p.getAttributeValue(null, "t"); sb.setLength(0) }
                    "v" -> inV = true
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inV || inT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        val raw = sb.toString()
                        row[col] = if (ctype == "s") shared.getOrElse(raw.trim().toIntOrNull() ?: -1) { "" } else raw
                        sb.setLength(0)
                    }
                    "row" -> {
                        val size = (row.keys.maxOrNull() ?: -1) + 1
                        val r = row
                        rows.add(List(size) { r[it] ?: "" })
                    }
                }
            }
            ev = p.next()
        }
        return rows
    }

    fun read(input: InputStream): List<List<String>> {
        var sharedBytes: ByteArray? = null
        var sheetBytes: ByteArray? = null
        var sheetName = ""
        ZipInputStream(BufferedInputStream(input)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = e.name
                if (n == "xl/sharedStrings.xml") sharedBytes = z.readBytes()
                else if (n.startsWith("xl/worksheets/sheet") && n.endsWith(".xml")) {
                    if (sheetBytes == null || n < sheetName) { sheetBytes = z.readBytes(); sheetName = n }
                }
                e = z.nextEntry
            }
        }
        val sb = sheetBytes ?: return emptyList()
        val shared = sharedBytes?.let { parseShared(it) } ?: emptyList()
        return parseSheet(sb, shared)
    }
}
