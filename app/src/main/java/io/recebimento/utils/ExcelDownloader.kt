package io.recebimento.utils

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.recebimento.network.ApiService
import io.recebimento.network.RecebimentoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.*

object ExcelDownloader {

    private const val REQUEST_CODE_PERMISSION = 100

    suspend fun gerarExcel(
        context: Context,
        apiService: ApiService,
        storeId: String,
        viagemId: String,
    ): Result<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    context as Activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_CODE_PERMISSION
                )
                return Result.failure(Exception("Permissão negada"))
            }
        }
        return baixarExcel(context, apiService, storeId, viagemId)
    }

    suspend fun gerarXlsxItens(
        context: Context,
        viagemId: String,
        prefixo: String,
        itens: List<RecebimentoItem>,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val nomeArquivo = "${prefixo}_${viagemId.takeLast(7)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.xlsx"

            val baos = ByteArrayOutputStream()
            val zos = ZipOutputStream(baos)

            // [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
</Types>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/workbook.xml
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Itens" sheetId="1" r:id="rId1"/></sheets>
</workbook>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/_rels/workbook.xml.rels
            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/styles.xml
            zos.putNextEntry(ZipEntry("xl/styles.xml"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><sz val="11"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE5093A"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border><left style="thin"><color auto="1"/></left><right style="thin"><color auto="1"/></right><top style="thin"><color auto="1"/></top><bottom style="thin"><color auto="1"/></bottom><diagonal/></border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center"/></xf>
    <xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="right"/></xf>
  </cellXfs>
</styleSheet>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Build shared strings and sheet data
            val ss = mutableListOf<String>()
            val ssMap = mutableMapOf<String, Int>()

            fun addSs(s: String): Int {
                return ssMap.getOrPut(s) { ss.size.also { ss.add(s) } }
            }

            val headers = listOf("DEP", "SAP", "DESCRIÇÃO", "QDTE REAL", "CONTAGEM")
            val headerIndices = headers.map { addSs(it) }

            val rowsData = itens.map { item ->
                listOf(
                    item.departamento,
                    item.id_sap.toLongOrNull()?.toString() ?: item.id_sap,
                    item.descricao,
                    item.quantidade.toString(),
                    ""
                ).map { addSs(it) }
            }

            // xl/sharedStrings.xml
            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            val ssXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"${ss.size}\" uniqueCount=\"${ss.size}\">")
                ss.forEach { s ->
                    append("<si><t>${escapeXml(s)}</t></si>")
                }
                append("</sst>")
            }
            zos.write(ssXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/worksheets/sheet1.xml
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val sheetXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                append("<cols>")
                append("<col min=\"1\" max=\"1\" width=\"10\"/>")
                append("<col min=\"2\" max=\"2\" width=\"15\"/>")
                append("<col min=\"3\" max=\"3\" width=\"50\"/>")
                append("<col min=\"4\" max=\"4\" width=\"14\"/>")
                append("<col min=\"5\" max=\"5\" width=\"14\"/>")
                append("</cols>")
                append("<sheetData>")
                // Header row (style 1 = bold white on red bg)
                append("<row r=\"1\">")
                headerIndices.forEachIndexed { i, idx ->
                    val colLetter = ('A' + i)
                    append("<c r=\"$colLetter${1}\" t=\"s\" s=\"1\"><v>$idx</v></c>")
                }
                append("</row>")
                // Data rows
                rowsData.forEachIndexed { rowIdx, rowVals ->
                    val r = rowIdx + 2
                    append("<row r=\"$r\">")
                    rowVals.forEachIndexed { colIdx, idx ->
                        val colLetter = ('A' + colIdx)
                        val s = if (colIdx in 3..4) "3" else "2"
                        append("<c r=\"$colLetter$r\" t=\"s\" s=\"$s\"><v>$idx</v></c>")
                    }
                    append("</row>")
                }
                append("</sheetData>")
                val lastRow = 1 + rowsData.size
                append("<autoFilter ref=\"A1:E$lastRow\"/>")
                append("</worksheet>")
            }
            zos.write(sheetXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.close()
            val bytes = baos.toByteArray()

            val caminho = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, nomeArquivo)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(bytes)
                    }
                }
                uri?.toString()
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, nomeArquivo)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(bytes)
                }
                file.absolutePath
            }
            if (caminho != null) Result.success(caminho) else Result.failure(Exception("Erro ao salvar arquivo"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatBRL(value: Double): String {
        val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale("pt", "BR"))
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        return "R$${nf.format(value)}"
    }

    private fun escapeHtml(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun escapeXml(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    suspend fun gerarPdfItens(
        context: Context,
        titulo: String,
        prefixo: String,
        itens: List<RecebimentoItem>,
        total: Double,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val nomeArquivo = "${prefixo}_${titulo.takeLast(7)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"

            val document = PdfDocument()
            val pageW = 595
            val pageH = 842
            val margin = 40f
            val tableW = pageW - margin * 2

            val cols = floatArrayOf(40f, 55f, 250f, 50f, 120f)
            val px = FloatArray(6).apply { for (i in 1..5) this[i] = this[i-1] + cols[i-1] }

            val fontSize = 8f
            val rowH = 16f
            val headerH = 18f

            val font = Paint().apply { textSize = fontSize; isAntiAlias = true }
            val fontBold = Paint().apply { textSize = fontSize; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
            val fontTitle = Paint().apply { textSize = 14f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD; color = android.graphics.Color.parseColor("#E5093A") }
            val fontTotal = Paint().apply { textSize = 10f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD; color = android.graphics.Color.DKGRAY }
            val fontHeader = Paint().apply { textSize = 8f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD; color = android.graphics.Color.WHITE }

            val bgHeader = Paint().apply { color = android.graphics.Color.parseColor("#E5093A"); style = Paint.Style.FILL }
            val bgWhite = Paint().apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
            val bgAlt = Paint().apply { color = android.graphics.Color.parseColor("#F5F5F5"); style = Paint.Style.FILL }
            val border = Paint().apply { color = android.graphics.Color.parseColor("#CCCCCC"); style = Paint.Style.STROKE; strokeWidth = 0.5f }

            val dataCount = itens.size
            var totalH = margin + 40f + headerH + dataCount * rowH + 10f
            val pageH2 = if (totalH > pageH) totalH.toInt() else pageH

            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH2, 1).create()
            val page = document.startPage(pageInfo)
            val c = page.canvas

            c.drawText(titulo, margin, margin + 12f, fontTitle)
            c.drawText("Total: ${CurrencyFormatter.formatarMoedaComSimbolo(total)}", margin, margin + 28f, fontTotal)

            val headerY = margin + 38f
            c.drawRect(margin, headerY, margin + tableW, headerY + headerH, bgHeader)

            val headers = listOf("DEP", "SAP", "DESCRIÇÃO", "QTD", "CONFERÊNCIA")
            for (i in 0..4) {
                val x = margin + px[i] + 3f
                val textW = fontBold.measureText(headers[i])
                if (i == 3) {
                    c.drawText(headers[i], margin + px[i] + cols[i] - textW - 3f, headerY + 13f, fontHeader)
                } else {
                    c.drawText(headers[i], x, headerY + 13f, fontHeader)
                }
                c.drawRect(margin + px[i], headerY, margin + px[i] + cols[i], headerY + headerH, border)
            }

            itens.forEachIndexed { idx, item ->
                val yBase = headerY + headerH + idx * rowH
                val bg = if (idx % 2 == 0) bgWhite else bgAlt
                c.drawRect(margin, yBase, margin + tableW, yBase + rowH, bg)

                val sapAjustado = item.id_sap.toLongOrNull()?.toString() ?: item.id_sap
                val vals = listOf(
                    item.departamento,
                    sapAjustado,
                    item.descricao,
                    item.quantidade.toString(),
                    ""
                )

                for (i in 0..4) {
                    if (i == 2) {
                        val maxW = cols[i] - 6f
                        var txt = vals[i]
                        if (font.measureText(txt) > maxW) {
                            while (font.measureText("$txt...") > maxW && txt.length > 2) {
                                txt = txt.dropLast(1)
                            }
                            txt = "$txt..."
                        }
                        c.drawText(txt, margin + px[i] + 3f, yBase + 12f, font)
                    } else if (i == 3) {
                        val textW = font.measureText(vals[i])
                        c.drawText(vals[i], margin + px[i] + cols[i] - textW - 3f, yBase + 12f, font)
                    } else {
                        c.drawText(vals[i], margin + px[i] + 3f, yBase + 12f, font)
                    }
                    c.drawRect(margin + px[i], yBase, margin + px[i] + cols[i], yBase + rowH, border)
                }
            }

            document.finishPage(page)

            val bytes = ByteArrayOutputStream()
            document.writeTo(bytes)
            document.close()

            val pdfBytes = bytes.toByteArray()

            val caminho = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, nomeArquivo)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(pdfBytes)
                    }
                }
                uri?.toString()
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, nomeArquivo)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(pdfBytes)
                }
                file.absolutePath
            }
            if (caminho != null) Result.success(caminho) else Result.failure(Exception("Erro ao salvar arquivo"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun baixarExcel(
        context: Context,
        apiService: ApiService,
        storeId: String,
        viagemId: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.gerarExcelViagem(storeId, viagemId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val nomeArquivo = "viagem_${viagemId.takeLast(7)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.xlsx"
                    val caminho = salvarArquivo(context, body, nomeArquivo)
                    if (caminho != null) {
                        Result.success(caminho)
                    } else {
                        Result.failure(Exception("Erro ao salvar arquivo"))
                    }
                } else {
                    Result.failure(Exception("Resposta vazia do servidor"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Erro ${response.code()}"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun salvarArquivo(context: Context, body: ResponseBody, nome: String): String? {
        return try {
            val bytes = body.bytes()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - usar MediaStore
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, nome)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(bytes)
                        return uri.toString()
                    }
                }
                null
            } else {
                // Android 9- - usar File
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, nome)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(bytes)
                }
                file.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}